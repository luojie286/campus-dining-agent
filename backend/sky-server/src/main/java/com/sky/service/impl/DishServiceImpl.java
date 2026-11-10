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
import com.sky.dto.DishUpdateDTO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.Objects;
import java.util.TreeSet;
import com.sky.entity.Category;
import com.sky.entity.Dish;
import com.sky.exception.BadRequestException;
import com.sky.exception.ConflictException;
import com.sky.exception.NotFoundException;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.DishMapper;
import com.sky.service.DishService;
import com.sky.service.EmployeePermissionService;
import com.sky.service.ProductImageStorage;
import com.sky.vo.DishVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sky.cache.MenuPageCache;
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
    private final MenuPageCache menuPageCache;

    @Override
    public DishVO getById(Long itemId) {
        permissionService.requireOperationsAccess();
        Dish dish = dishMapper.selectById(toDatabaseId(itemId));
        if (dish == null) {
            throw new NotFoundException("菜品不存在");
        }
        return toVO(dish, readTags(dish.getTags()));
    }

    private Long toDatabaseId(Long itemId) {
        if (itemId == null || itemId <= 0 || itemId > MAX_PUBLIC_ID || itemId % 2 != 0) {
            throw new BadRequestException("菜品ID必须是有效的正偶数");
        }
        return itemId / 2;
    }

    /**
     * 在事务内按分类ID升序、再菜品行的顺序加锁，与现有分类操作协调。
     * 初读只决定锁集合，锁定后重新核对分类，变化时返回冲突而不是使用旧值。
     */
    private Dish lockDish(Long databaseId, Long newCategoryId) {
        Dish observed = dishMapper.selectById(databaseId);
        if (observed == null) {
            return null;
        }
        TreeSet<Long> categoryIds = new TreeSet<>();
        categoryIds.add(observed.getCategoryId());
        if (newCategoryId != null) {
            categoryIds.add(newCategoryId);
        }
        for (Long categoryId : categoryIds) {
            categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                    .eq(Category::getId, categoryId).last("FOR UPDATE"));
        }
        Dish locked = dishMapper.selectOne(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getId, databaseId).last("FOR UPDATE"));
        if (locked != null && !Objects.equals(observed.getCategoryId(), locked.getCategoryId())) {
            throw new ConflictException("菜品分类已变化，请刷新后重试");
        }
        return locked;
    }

    @Override
    @Transactional
    public DishVO update(DishUpdateDTO dto) {
        permissionService.requireOperationsAccess();
        Long databaseId = toDatabaseId(dto.getId());
        Dish target = lockDish(databaseId, dto.getCategoryId());
        if (target == null) {
            throw new NotFoundException("菜品不存在");
        }
        BigDecimal price = normalizePrice(dto.getPrice());
        validateTags(dto.getTags());
        // 缺省保留旧图；显式null已由DTO拒绝，不引入隐式清图行为。
        String image = target.getImage();
        if (dto.getImage() != null) {
            validateImage(dto.getImage());
            image = dto.getImage();
        }
        Category category = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                .eq(Category::getId, dto.getCategoryId()).last("FOR UPDATE"));
        if (category == null) {
            throw new NotFoundException("分类不存在");
        }
        if (!Integer.valueOf(1).equals(category.getType())) {
            throw new BadRequestException("菜品必须关联菜品分类");
        }
        if (Integer.valueOf(1).equals(dto.getStatus()) && !Integer.valueOf(1).equals(category.getStatus())) {
            throw new ConflictException("所属分类已禁用，不能起售");
        }
        ensureUpdateNameAvailable(dto.getName(), databaseId);
        List<String> oldTags = readTags(target.getTags());
        boolean unchanged = Objects.equals(dto.getName(), target.getName())
                && Objects.equals(dto.getCategoryId(), target.getCategoryId())
                && price.compareTo(target.getPrice()) == 0
                && Objects.equals(dto.getDescription(), target.getDescription())
                && Objects.equals(dto.getStatus(), target.getStatus())
                && Objects.equals(dto.getTags(), oldTags)
                && Objects.equals(dto.getArt(), target.getArt())
                && Objects.equals(image, target.getImage());
        if (unchanged) {
            return toVO(target, oldTags);
        }
        String tagsJson = writeTags(dto.getTags());
        // 不传整实体回写。库存和创建字段均不在更新白名单中。
        Dish auditEntity = new Dish();
        LambdaUpdateWrapper<Dish> wrapper = new LambdaUpdateWrapper<Dish>()
                .eq(Dish::getId, databaseId)
                .set(Dish::getName, dto.getName())
                .set(Dish::getCategoryId, dto.getCategoryId())
                .set(Dish::getPrice, price)
                .set(Dish::getDescription, dto.getDescription())
                .set(Dish::getStatus, dto.getStatus())
                .set(Dish::getTags, tagsJson)
                .set(Dish::getArt, dto.getArt())
                .set(Dish::getImage, image);
        try {
            if (dishMapper.update(auditEntity, wrapper) != 1) {
                throw new IllegalStateException("编辑菜品失败");
            }
        } catch (DuplicateKeyException exception) {
            ensureUpdateNameAvailable(dto.getName(), databaseId);
            throw exception;
        }
        target.setName(dto.getName());
        target.setCategoryId(dto.getCategoryId());
        target.setPrice(price);
        target.setDescription(dto.getDescription());
        target.setStatus(dto.getStatus());
        target.setTags(tagsJson);
        target.setArt(dto.getArt());
        target.setImage(image);
        menuPageCache.invalidateAfterCommit();
        return toVO(target, dto.getTags());
    }

    private void ensureUpdateNameAvailable(String name, Long databaseId) {
        Dish duplicate = dishMapper.selectOne(new LambdaQueryWrapper<Dish>()
                .select(Dish::getId).eq(Dish::getName, name)
                .ne(Dish::getId, databaseId).last("FOR UPDATE"));
        if (duplicate != null) {
            throw new ConflictException("菜品名称已存在");
        }
    }

    @Override
    @Transactional
    public void delete(Long itemId) {
        permissionService.requireOperationsAccess();
        Long databaseId = toDatabaseId(itemId);
        Dish target = lockDish(databaseId, null);
        if (target == null) {
            return; // 按目标结果幂等，重复删除仍成功。
        }
        if (!Integer.valueOf(0).equals(target.getStatus())) {
            throw new ConflictException("请先停售菜品再删除");
        }
        if (dishMapper.findSetmealReference(databaseId) != null) {
            throw new ConflictException("菜品被套餐引用，不能删除");
        }
        if (dishMapper.findPendingOrderReference(databaseId) != null) {
            throw new ConflictException("菜品被未完成订单引用，不能删除");
        }
        for (String snapshot : dishMapper.selectPendingInventorySnapshots()) {
            if (snapshotContainsDish(snapshot, databaseId)) {
                throw new ConflictException("菜品被未完成订单库存快照引用，不能删除");
            }
        }
        dishMapper.deleteFlavors(databaseId);
        if (dishMapper.deleteById(databaseId) != 1) {
            throw new IllegalStateException("删除菜品失败");
        }
        menuPageCache.invalidateAfterCommit();
    }

    private boolean snapshotContainsDish(String snapshot, Long databaseId) {
        if (snapshot == null) {
            throw new IllegalStateException("订单库存快照不能为空");
        }
        try {
            JsonNode root = objectMapper.readTree(snapshot);
            if (root == null || !root.isArray()) {
                throw new IllegalStateException("订单库存快照格式错误");
            }
            for (JsonNode item : root) {
                JsonNode kind = item.get("kind");
                JsonNode id = item.get("id");
                if (!item.isObject() || kind == null || !kind.isTextual()
                        || id == null || !id.isIntegralNumber()
                        || !id.canConvertToLong() || id.longValue() <= 0) {
                    throw new IllegalStateException("订单库存快照条目错误");
                }
                if ("dish".equals(kind.textValue()) && id.longValue() == databaseId.longValue()) {
                    return true;
                }
            }
            return false;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("订单库存快照JSON格式错误", exception);
        }
    }

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
    private final ProductImageStorage imageStorage;

    public DishServiceImpl(
            DishMapper dishMapper,
            CategoryMapper categoryMapper,
            EmployeePermissionService permissionService,
            ObjectMapper objectMapper,
            ProductImageStorage imageStorage,
            @Value("${sky.dish.allowed-image-hosts:}") String imageHosts,
            // 在构造器参数列表中增加
            MenuPageCache menuPageCache) {

        this.dishMapper = dishMapper;
        this.categoryMapper = categoryMapper;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
        this.imageStorage = imageStorage;
        // 在构造器内部增加
        this.menuPageCache = menuPageCache;
        this.allowedImageHosts = new HashSet<>();

        for (String host : imageHosts.split(",")) {
            if (!host.isBlank()) {
                this.allowedImageHosts.add(
                        host.trim().toLowerCase(java.util.Locale.ROOT));
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
                        .last("FOR UPDATE"));

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
                            .last("FOR UPDATE"));
            if (duplicate != null) {
                throw new ConflictException("菜品名称已存在");
            }

            throw exception;
        }

        menuPageCache.invalidateAfterCommit();
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
                        .eq(Dish::getName, name));

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
     * 支持本服务已上传的图片和受控HTTPS图片地址。
     * 未配置时允许不传图片，但不能接受任意外部地址。
     */
    private void validateImage(String image) {
        if (image == null) {
            return;
        }

        if (image.isBlank()) {
            throw new BadRequestException("没有图片时请省略image字段");
        }

        if (image.startsWith(ProductImageStorage.PUBLIC_PATH)) {
            String filename = image.substring(ProductImageStorage.PUBLIC_PATH.length());
            if (!imageStorage.contains(filename)) {
                throw new BadRequestException("上传的菜品图片不存在，请重新选择");
            }
            return;
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
