package com.sky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.dto.InventoryStockUse;
import com.sky.entity.Category;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.ConflictException;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartRepository.ProductSnapshot;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.sky.mapper.ShoppingCartRepository.isDish;
import static com.sky.mapper.ShoppingCartRepository.rawId;

@Component
@Profile("!bootstrap")
public class CartCatalogValidator {
    private final CategoryMapper categories;
    private final DishMapper dishes;
    private final SetmealMapper setmeals;
    private final SetmealDishMapper ingredients;

    public CartCatalogValidator(CategoryMapper categories, DishMapper dishes,
            SetmealMapper setmeals, SetmealDishMapper ingredients) {
        this.categories = categories;
        this.dishes = dishes;
        this.setmeals = setmeals;
        this.ingredients = ingredients;
    }

    /** 调用者已锁用户行。与商品写入保持分类、菜品、套餐、组成的加锁顺序。 */
    public Map<Long, ProductSnapshot> validate(Map<Long, Integer> cart) {
        return check(cart, false).products();
    }

    public Allocation prepareOrder(Map<Long, Integer> cart) {
        return check(cart, true);
    }

    public record Allocation(Map<Long, ProductSnapshot> products, List<InventoryStockUse> stock) {}

    private Allocation check(Map<Long, Integer> cart, boolean reserve) {
        String productLock = reserve ? "FOR UPDATE" : "FOR SHARE";
        Map<Long, Setmeal> observedMeals = new TreeMap<>();
        Map<Long, Map<Long, Integer>> observedParts = new HashMap<>();
        TreeSet<Long> dishIds = new TreeSet<>();
        TreeSet<Long> categoryIds = new TreeSet<>();
        for (long itemId : cart.keySet()) {
            long id = rawId(itemId);
            if (isDish(itemId)) {
                dishIds.add(id);
            } else {
                Setmeal meal = setmeals.selectById(id);
                if (meal == null) throw unavailable();
                observedMeals.put(id, meal);
                categoryIds.add(meal.getCategoryId());
                Map<Long, Integer> parts = parts(id, false);
                if (parts.isEmpty()) throw unavailable();
                observedParts.put(id, parts);
                dishIds.addAll(parts.keySet());
            }
        }

        Map<Long, Dish> observedDishes = new TreeMap<>();
        for (long id : dishIds) {
            Dish dish = dishes.selectById(id);
            if (dish == null) throw unavailable();
            observedDishes.put(id, dish);
            categoryIds.add(dish.getCategoryId());
        }

        Map<Long, Category> lockedCategories = new HashMap<>();
        for (long id : categoryIds) {
            Category category = categories.selectOne(new LambdaQueryWrapper<Category>()
                    .eq(Category::getId, id).last("FOR SHARE"));
            if (category == null) throw unavailable();
            lockedCategories.put(id, category);
        }

        Map<Long, Dish> lockedDishes = new HashMap<>();
        for (long id : dishIds) {
            Dish dish = dishes.selectOne(new LambdaQueryWrapper<Dish>()
                    .eq(Dish::getId, id).last(productLock));
            if (dish == null || !Objects.equals(dish.getCategoryId(), observedDishes.get(id).getCategoryId())
                    || !enabled(dish.getStatus(), lockedCategories.get(dish.getCategoryId()), 1)) {
                throw unavailable();
            }
            lockedDishes.put(id, dish);
        }

        Map<Long, Setmeal> lockedMeals = new HashMap<>();
        for (var entry : observedMeals.entrySet()) {
            Setmeal meal = setmeals.selectOne(new LambdaQueryWrapper<Setmeal>()
                    .eq(Setmeal::getId, entry.getKey()).last(productLock));
            if (meal == null || !Objects.equals(meal.getCategoryId(), entry.getValue().getCategoryId())
                    || !enabled(meal.getStatus(), lockedCategories.get(meal.getCategoryId()), 2)) {
                throw unavailable();
            }
            lockedMeals.put(entry.getKey(), meal);
        }
        for (long id : observedMeals.keySet()) {
            if (!observedParts.get(id).equals(parts(id, true))) {
                throw new ConflictException("套餐组成已变化，请刷新后重试");
            }
        }

        Map<Long, Integer> dishDemand = new TreeMap<>();
        Map<Long, Integer> mealDemand = new TreeMap<>();
        Map<Long, ProductSnapshot> snapshots = new HashMap<>();
        for (var line : cart.entrySet()) {
            long itemId = line.getKey();
            long id = rawId(itemId);
            int quantity = line.getValue();
            if (isDish(itemId)) {
                Dish dish = lockedDishes.get(id);
                dishDemand.merge(id, quantity, Integer::sum);
                snapshots.put(itemId, new ProductSnapshot(dish.getName(), dish.getImage(), dish.getPrice()));
            } else {
                Setmeal meal = lockedMeals.get(id);
                if (meal.getStock() < quantity) throw insufficient();
                mealDemand.put(id, quantity);
                for (var part : observedParts.get(id).entrySet()) {
                    dishDemand.merge(part.getKey(), part.getValue() * quantity, Integer::sum);
                }
                snapshots.put(itemId, new ProductSnapshot(meal.getName(), meal.getImage(), meal.getPrice()));
            }
        }

        for (var demand : dishDemand.entrySet()) {
            if (lockedDishes.get(demand.getKey()).getStock() < demand.getValue()) throw insufficient();
        }
        List<InventoryStockUse> stock = new ArrayList<>();
        dishDemand.forEach((id, amount) -> stock.add(new InventoryStockUse("dish", id, amount)));
        mealDemand.forEach((id, amount) -> stock.add(new InventoryStockUse("setmeal", id, amount)));
        return new Allocation(snapshots, stock);
    }

    private Map<Long, Integer> parts(long mealId, boolean lock) {
        var query = new LambdaQueryWrapper<SetmealDish>().eq(SetmealDish::getSetmealId, mealId)
                .orderByAsc(SetmealDish::getDishId);
        if (lock) query.last("FOR SHARE");
        Map<Long, Integer> parts = new TreeMap<>();
        for (SetmealDish part : ingredients.selectList(query)) {
            if (part.getCopies() == null || part.getCopies() < 1 || part.getCopies() > 99) {
                throw unavailable();
            }
            parts.put(part.getDishId(), part.getCopies());
        }
        return parts;
    }

    private boolean enabled(Integer status, Category category, int type) {
        return Integer.valueOf(1).equals(status) && category != null
                && Integer.valueOf(1).equals(category.getStatus())
                && Integer.valueOf(type).equals(category.getType());
    }

    private ConflictException unavailable() {
        return new ConflictException("购物车中有商品已不可售，请调整后重试");
    }

    private ConflictException insufficient() {
        return new ConflictException("商品库存不足，请调整购物车数量");
    }
}
