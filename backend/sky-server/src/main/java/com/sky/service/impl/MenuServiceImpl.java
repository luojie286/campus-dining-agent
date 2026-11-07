package com.sky.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.dto.DishPageQueryDTO;
import com.sky.dto.MenuPartRow;
import com.sky.dto.MenuRow;
import com.sky.mapper.MenuMapper;
import com.sky.result.PageResult;
import com.sky.service.MenuService;
import com.sky.vo.MenuItemVO;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("!bootstrap")
public class MenuServiceImpl implements MenuService {

    private final MenuMapper menuMapper;
    private final ObjectMapper objectMapper;

    public MenuServiceImpl(MenuMapper menuMapper, ObjectMapper objectMapper) {
        this.menuMapper = menuMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<MenuItemVO> pageForUser(DishPageQueryDTO query) {
        Page<MenuRow> page = new Page<>(query.getPage(), query.getPageSize());

        // 让分页插件基于完整的合并查询统计总数。
        page.setOptimizeCountSql(false);

        IPage<MenuRow> result = menuMapper.selectMenuPage(
                page, query, buildNamePattern(query.getName()));

        List<Long> setmealIds = new ArrayList<>();
        for (MenuRow row : result.getRecords()) {
            if ("setmeal".equals(row.getKind())) {
                setmealIds.add(row.getRawId());
            }
        }

        Map<Long, List<MenuPartRow>> partsBySetmeal = new HashMap<>();
        if (!setmealIds.isEmpty()) {
            for (MenuPartRow part : menuMapper.selectSetmealParts(setmealIds)) {
                partsBySetmeal.computeIfAbsent(
                        part.getSetmealId(), id -> new ArrayList<>()
                ).add(part);
            }
        }

        List<MenuItemVO> records = new ArrayList<>();
        for (MenuRow row : result.getRecords()) {
            List<MenuPartRow> parts =
                    partsBySetmeal.getOrDefault(row.getRawId(), List.of());

            // 菜品和套餐的数据库主键可能相同，只有套餐使用组成结果。
            records.add(toVO(row, "setmeal".equals(row.getKind()) ? parts : List.of()));
        }

        return new PageResult<>(result.getTotal(), records);
    }

    private MenuItemVO toVO(MenuRow row, List<MenuPartRow> parts) {
        List<MenuItemVO.Composition> composition = new ArrayList<>();
        List<MenuItemVO.CompositionItem> compositionItems = new ArrayList<>();

        int availableStock = row.getStock();
        if ("setmeal".equals(row.getKind())) {
            if (parts.isEmpty()) {
                availableStock = 0;
            }

            for (MenuPartRow part : parts) {
                int quantity = part.getCopies();
                if (quantity <= 0) {
                    throw new IllegalStateException("套餐组成份数必须大于 0");
                }

                long dishId = part.getDishId() * 2L;
                composition.add(new MenuItemVO.Composition(dishId, quantity));
                compositionItems.add(new MenuItemVO.CompositionItem(
                        dishId, quantity, part.getDishName()
                ));

                int supported = supportedSetmealCount(part, quantity);
                availableStock = Math.min(availableStock, supported);
            }
        }

        return new MenuItemVO(
                row.getItemId(), row.getKind(), row.getCategoryId(),
                row.getName(), row.getPrice(), row.getDescription(),
                row.getStatus(), row.getStock(), readTags(row.getTags()),
                row.getArt(), row.getImage(), composition,
                availableStock, compositionItems
        );
    }

    private int supportedSetmealCount(MenuPartRow part, int quantity) {
        if (!Integer.valueOf(1).equals(part.getDishStatus())
                || !Integer.valueOf(1).equals(part.getDishCategoryStatus())
                || !Integer.valueOf(1).equals(part.getDishCategoryType())
                || part.getDishStock() == null) {
            return 0;
        }
        return part.getDishStock() / quantity;
    }

    private String buildNamePattern(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        String escaped = name.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private List<String> readTags(String json) {
        if (json == null) {
            throw new IllegalStateException("商品标签不能为空");
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                throw new IllegalStateException("商品标签必须是 JSON 数组");
            }

            List<String> tags = new ArrayList<>();
            for (JsonNode tag : root) {
                if (!tag.isTextual()) {
                    throw new IllegalStateException("商品标签必须为字符串");
                }
                tags.add(tag.textValue());
            }
            return tags;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("商品标签 JSON 不合法", exception);
        }
    }
}