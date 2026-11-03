package com.sky.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.*;
import com.sky.mapper.*;
import com.sky.service.*;
import com.sky.vo.SetmealVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.net.*;
import java.util.*;

@Service
@Profile("!bootstrap")
public class SetmealServiceImpl implements SetmealService {
    private static final long MAX_PUBLIC_ID = 9007199254740991L;
    private final SetmealMapper setmealMapper;
    private final SetmealDishMapper relationMapper;
    private final DishMapper dishMapper;
    private final CategoryMapper categoryMapper;
    private final EmployeePermissionService permissionService;
    private final ObjectMapper objectMapper;
    private final Set<String> allowedImageHosts = new HashSet<>();

    public SetmealServiceImpl(SetmealMapper setmealMapper, SetmealDishMapper relationMapper,
            DishMapper dishMapper, CategoryMapper categoryMapper,
            EmployeePermissionService permissionService, ObjectMapper objectMapper,
            @Value("${sky.dish.allowed-image-hosts:}") String imageHosts) {
        this.setmealMapper = setmealMapper;
        this.relationMapper = relationMapper;
        this.dishMapper = dishMapper;
        this.categoryMapper = categoryMapper;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
        for (String host : imageHosts.split(",")) {
            if (!host.isBlank()) allowedImageHosts.add(host.trim().toLowerCase(Locale.ROOT));
        }
    }

    @Override
    @Transactional
    public SetmealVO create(SetmealCreateDTO dto) {
        permissionService.requireOperationsAccess();
        BigDecimal price = normalizePrice(dto.getPrice());
        validateTags(dto.getTags());
        validateImage(dto.getImage());
        Map<Long, Integer> composition = compositionMap(dto.getComposition());
        Map<Long, Dish> dishes = lockIngredients(Set.of(dto.getCategoryId()), composition.keySet());
        validateSale(dto.getCategoryId(), dto.getStatus(), composition, dishes);
        ensureNameAvailable(dto.getName(), null);
        Setmeal meal = new Setmeal();
        meal.setName(dto.getName());
        meal.setCategoryId(dto.getCategoryId());
        meal.setPrice(price);
        meal.setDescription(dto.getDescription());
        meal.setStatus(dto.getStatus());
        meal.setStock(dto.getStock());
        meal.setTags(writeTags(dto.getTags()));
        meal.setArt(dto.getArt());
        meal.setImage(dto.getImage());
        try {
            if (setmealMapper.insert(meal) != 1) throw new IllegalStateException("新增套餐失败");
        } catch (DuplicateKeyException exception) {
            ensureNameAvailable(dto.getName(), null);
            throw exception;
        }
        saveComposition(meal.getId(), composition, dishes);
        return toVO(meal, relations(meal.getId()));
    }

    private Map<Long, Integer> compositionMap(List<SetmealCompositionDTO> parts) {
        Map<Long, Integer> result = new TreeMap<>();
        for (SetmealCompositionDTO part : parts) {
            long id = part.getDishId();
            if (id < 2 || id > MAX_PUBLIC_ID || id % 2 != 0) {
                throw new BadRequestException("组成必须使用有效菜品偶数ID，不能嵌套套餐");
            }
            if (result.put(id / 2, part.getQuantity()) != null) {
                throw new BadRequestException("组成菜品不能重复");
            }
        }
        return result;
    }

    /**
     * 所有套餐写操作统一按分类ID、菜品ID、套餐行、关系行加锁。
     * 与菜品删除的分类->菜品->关系顺序兼容，检查后不会插入悬空引用。
     * 分类初读只决定锁集合，锁住菜品后重新核对，变化则要求重试。
     */
    private Map<Long, Dish> lockIngredients(Set<Long> mealCategories, Set<Long> dishIds) {
        Set<Long> categoryIds = new TreeSet<>(mealCategories);
        Map<Long, Dish> observed = new TreeMap<>();
        for (Long id : new TreeSet<>(dishIds)) {
            Dish dish = dishMapper.selectById(id);
            if (dish == null) throw new NotFoundException("组成菜品不存在");
            observed.put(id, dish);
            categoryIds.add(dish.getCategoryId());
        }
        for (Long id : categoryIds) {
            Category category = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                    .eq(Category::getId, id).last("FOR UPDATE"));
            if (category == null) throw new NotFoundException("分类不存在");
        }
        Map<Long, Dish> locked = new TreeMap<>();
        for (Long id : observed.keySet()) {
            Dish dish = dishMapper.selectOne(new LambdaQueryWrapper<Dish>()
                    .eq(Dish::getId, id).last("FOR UPDATE"));
            if (dish == null) throw new ConflictException("组成菜品已删除，请刷新重试");
            if (!Objects.equals(dish.getCategoryId(), observed.get(id).getCategoryId())) {
                throw new ConflictException("组成菜品分类已变化，请刷新重试");
            }
            locked.put(id, dish);
        }
        return locked;
    }

    private void validateSale(Long categoryId, Integer status, Map<Long, Integer> parts,
                              Map<Long, Dish> dishes) {
        Category category = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                .eq(Category::getId, categoryId).last("FOR UPDATE"));
        if (category == null) throw new NotFoundException("套餐分类不存在");
        if (!Integer.valueOf(2).equals(category.getType())) {
            throw new BadRequestException("套餐必须关联套餐分类");
        }
        if (status != 1) return;
        if (category.getStatus() != 1) throw new ConflictException("套餐分类已禁用，不能起售");
        if (parts.isEmpty() || parts.size() > 100) throw new ConflictException("套餐组成不合法");
        for (Map.Entry<Long, Integer> part : parts.entrySet()) {
            Dish dish = dishes.get(part.getKey());
            Category dishCategory = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                    .eq(Category::getId, dish.getCategoryId()).last("FOR UPDATE"));
            if (part.getValue() < 1 || part.getValue() > 99 || dish.getStatus() != 1
                    || dishCategory == null || dishCategory.getType() != 1 || dishCategory.getStatus() != 1) {
                throw new ConflictException("组成菜品或分类不可售，不能起售套餐");
            }
        }
        // 零库存允许起售展示，但不能购买；库存不是套餐自身status。
    }

    private void ensureNameAvailable(String name, Long excludedId) {
        LambdaQueryWrapper<Setmeal> query = new LambdaQueryWrapper<Setmeal>().eq(Setmeal::getName, name);
        if (excludedId != null) query.ne(Setmeal::getId, excludedId);
        if (setmealMapper.selectOne(query.last("FOR UPDATE")) != null) {
            throw new ConflictException("套餐名称已存在");
        }
    }

    private void saveComposition(Long mealId, Map<Long, Integer> parts, Map<Long, Dish> dishes) {
        for (Map.Entry<Long, Integer> part : parts.entrySet()) {
            Dish dish = dishes.get(part.getKey());
            SetmealDish relation = new SetmealDish();
            relation.setSetmealId(mealId);
            relation.setDishId(dish.getId());
            relation.setName(dish.getName());
            relation.setPrice(dish.getPrice());
            relation.setCopies(part.getValue());
            if (relationMapper.insert(relation) != 1) throw new IllegalStateException("保存套餐组成失败");
        }
    }

    private List<SetmealDish> relations(Long id) {
        return relationMapper.selectList(new LambdaQueryWrapper<SetmealDish>()
                .eq(SetmealDish::getSetmealId, id).orderByAsc(SetmealDish::getDishId));
    }

    private SetmealVO toVO(Setmeal meal, List<SetmealDish> relations) {
        if (meal.getId() == null || meal.getId() <= 0 || meal.getId() > (MAX_PUBLIC_ID - 1) / 2) {
            throw new IllegalStateException("套餐ID超出允许范围");
        }
        List<SetmealVO.CompositionVO> composition = new ArrayList<>();
        for (SetmealDish relation : relations) {
            long id = relation.getDishId();
            if (id <= 0 || id > MAX_PUBLIC_ID / 2) throw new IllegalStateException("菜品ID超出允许范围");
            composition.add(new SetmealVO.CompositionVO(id * 2, relation.getCopies()));
        }
        return SetmealVO.builder().id(meal.getId() * 2 + 1).kind("setmeal")
                .name(meal.getName()).categoryId(meal.getCategoryId()).price(meal.getPrice())
                .description(meal.getDescription()).status(meal.getStatus()).stock(meal.getStock())
                .tags(readTags(meal.getTags())).art(meal.getArt()).image(meal.getImage())
                .composition(composition).build();
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

}
