package com.sky.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import java.util.ArrayList;
import com.sky.dto.DishCreateDTO;
import com.sky.entity.Category;
import com.sky.entity.Dish;
import com.sky.exception.BadRequestException;
import com.sky.exception.ConflictException;
import com.sky.exception.NotFoundException;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.DishMapper;
import com.sky.service.DishService;
import com.sky.service.EmployeePermissionService;
import com.sky.vo.DishVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Profile("!bootstrap")
public class DishServiceImpl implements DishService {

    private static final long MAX_PUBLIC_ID = 9007199254740991L;

    @Override
    public PageResult<DishVO> pageQuery(DishPageQueryDTO dto) {
        permissionService.requireOperationsAccess();
        Page<Dish> page = new Page<>(dto.getPage(), dto.getPageSize());
        IPage<Dish> result = dishMapper.selectDishPage(
                page, dto, buildNamePattern(dto.getName()));
        List<DishVO> records = new ArrayList<>();
        for (Dish dish : result.getRecords()) {
            records.add(toVO(dish, readTags(dish.getTags())));
        }
        return new PageResult<>(result.getTotal(), records);
    }

    private String buildNamePattern(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        // 先转义!，避免把随后添加的LIKE转义符重复转义。
        String escaped = name.replace("!", "!!")
                .replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private List<String> readTags(String tagsJson) {
        if (tagsJson == null) {
            throw new IllegalStateException("数据库菜品标签不能为空");
        }
        try {
            JsonNode root = objectMapper.readTree(tagsJson);
            if (root == null || !root.isArray()) {
                throw new IllegalStateException("数据库菜品标签必须是JSON数组");
            }
            List<String> tags = new ArrayList<>();
            for (JsonNode item : root) {
                if (!item.isTextual()) {
                    throw new IllegalStateException("数据库菜品标签必须为字符串");
                }
                tags.add(item.textValue());
            }
            return tags;
        } catch (JsonProcessingException exception) {
            // 存储数据损坏不能伪装成客户端400，也不能吞掉后返回空标签。
            throw new IllegalStateException("数据库菜品标签JSON格式错误", exception);
        }
    }

    private final DishMapper dishMapper;
    private final CategoryMapper categoryMapper;
    private final EmployeePermissionService permissionService;
    private final ObjectMapper objectMapper;
    private final Set<String> allowedImageHosts;

    public DishServiceImpl(
            DishMapper dishMapper,
            CategoryMapper categoryMapper,
            EmployeePermissionService permissionService,
            ObjectMapper objectMapper,
            @Value("${sky.dish.allowed-image-hosts:}")
            String imageHosts) {

        this.dishMapper = dishMapper;
        this.categoryMapper = categoryMapper;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;

        this.allowedImageHosts = new HashSet<>();

        for (String host : imageHosts.split(",")) {
            if (!host.isBlank()) {
                this.allowedImageHosts.add(
                        host.trim().toLowerCase(java.util.Locale.ROOT)
                );
            }
        }
    }

    @Override
    @Transactional
    public DishVO create(DishCreateDTO dto) {
        // 两种启用的运营账号均可新增菜品。
        permissionService.requireOperationsAccess();

        BigDecimal price = normalizePrice(dto.getPrice());
        validateTags(dto.getTags());
        validateImage(dto.getImage());

        /*
         * 与现有分类删除、修改类型流程锁定同一分类行。
         * 防止检查分类存在后，分类又被其他事务删除或改变类型。
         */
        Category category = categoryMapper.selectOne(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getId, dto.getCategoryId())
                        .last("FOR UPDATE")
        );

        if (category == null) {
            throw new NotFoundException("分类不存在");
        }

        if (!Integer.valueOf(1).equals(category.getType())) {
            throw new BadRequestException("菜品必须关联菜品分类");
        }

        // 停售资料可保存；请求直接起售时，分类必须启用。
        if (Integer.valueOf(1).equals(dto.getStatus())
                && !Integer.valueOf(1).equals(category.getStatus())) {
            throw new ConflictException("所属分类已禁用，不能起售");
        }

        if (nameExists(dto.getName())) {
            throw new ConflictException("菜品名称已存在");
        }

        // 明确设置允许创建的字段，不把请求整对象复制到实体。
        Dish dish = new Dish();
        dish.setName(dto.getName());
        dish.setCategoryId(dto.getCategoryId());
        dish.setPrice(price);
        dish.setDescription(dto.getDescription());
        dish.setStatus(dto.getStatus());
        dish.setStock(dto.getStock());
        dish.setTags(writeTags(dto.getTags()));
        dish.setArt(dto.getArt());
        dish.setImage(dto.getImage());

        // 不设置id和审计字段：分别由数据库和现有AOP生成。
        try {
            int rows = dishMapper.insert(dish);

            if (rows != 1) {
                throw new IllegalStateException("新增菜品失败");
            }
        } catch (DuplicateKeyException exception) {
            /*
             * 预查询不能防住并发重名，仍需数据库唯一索引兜底。
             * 只把确认属于名称冲突的情况转换成409。
             */
            // 唯一索引冲突后使用当前读，避免RR快照看不到并发刚提交的重名记录。
            Dish duplicate = dishMapper.selectOne(
                    new LambdaQueryWrapper<Dish>()
                            .select(Dish::getId)
                            .eq(Dish::getName, dto.getName())
                            .last("FOR UPDATE")
            );
            if (duplicate != null) {
                throw new ConflictException("菜品名称已存在");
            }

            throw exception;
        }

        return toVO(dish, dto.getTags());
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        try {
            // 允许18、18.5、18.50，不允许偷偷把18.555四舍五入。
            return price.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException("价格必须精确到分");
        }
    }

    private void validateTags(List<String> tags) {
        Set<String> distinctTags = new HashSet<>(tags);

        if (distinctTags.size() != tags.size()) {
            throw new BadRequestException("标签不能重复");
        }

        int spiceCount = 0;

        for (String tag : tags) {
            if ("不辣".equals(tag)
                    || "微辣".equals(tag)
                    || "辣".equals(tag)) {
                spiceCount++;
            }
        }

        if (spiceCount > 1) {
            throw new BadRequestException("辣度标签不能同时选择多个");
        }
    }

    private boolean nameExists(String name) {
        Long count = dishMapper.selectCount(
                new LambdaQueryWrapper<Dish>()
                        .eq(Dish::getName, name)
        );

        return count != null && count > 0;
    }

    private String writeTags(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("标签序列化失败", exception);
        }
    }

    /**
     * 本示例先支持受控HTTPS图片域名。
     * 未配置时允许不传图片，但不能接受任意外部地址。
     */
    private void validateImage(String image) {
        if (image == null) {
            return;
        }

        if (image.isBlank()) {
            throw new BadRequestException("没有图片时请省略image字段");
        }

        URI uri;

        try {
            uri = new URI(image);
        } catch (URISyntaxException exception) {
            throw new BadRequestException("图片地址格式不合法");
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new BadRequestException("图片必须使用受控HTTPS地址");
        }

        String host = uri.getHost()
                .toLowerCase(java.util.Locale.ROOT);

        if (!allowedImageHosts.contains(host)) {
            throw new BadRequestException("图片域名不在允许范围内");
        }
    }

    private DishVO toVO(Dish dish, List<String> tags) {
        Long databaseId = dish.getId();

        // 对外ID乘2之前先检查，防止溢出及超出JavaScript安全整数范围。
        if (databaseId == null
                || databaseId <= 0
                || databaseId > MAX_PUBLIC_ID / 2) {
            throw new IllegalStateException("菜品ID超出允许范围");
        }

        long itemId = databaseId * 2;

        return DishVO.builder()
                .id(itemId)
                .kind("dish")
                .name(dish.getName())
                .categoryId(dish.getCategoryId())
                .price(dish.getPrice())
                .description(dish.getDescription())
                .status(dish.getStatus())
                .stock(dish.getStock())
                .tags(List.copyOf(tags))
                .art(dish.getArt())
                .composition(List.of())
                .image(dish.getImage())
                .build();
    }
}
