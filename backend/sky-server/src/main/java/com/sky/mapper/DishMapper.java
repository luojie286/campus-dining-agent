package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.dto.DishPageQueryDTO;
import org.apache.ibatis.annotations.Param;
import com.sky.annotation.AutoFill;
import com.sky.entity.Dish;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import java.util.List;

@Mapper
public interface DishMapper extends BaseMapper<Dish> {

    IPage<Dish> selectDishPage(Page<Dish> page,
            @Param("query") DishPageQueryDTO query,
            @Param("namePattern") String namePattern);

    @Override
    @AutoFill(OperationType.INSERT)
    int insert(Dish dish);

    @Override
    @AutoFill(OperationType.UPDATE)
    int update(@Param(Constants.ENTITY) Dish dish,
               @Param(Constants.WRAPPER) Wrapper<Dish> updateWrapper);

    @Select("SELECT id FROM setmeal_dish WHERE dish_id=#{dishId} LIMIT 1 FOR UPDATE")
    Long findSetmealReference(@Param("dishId") Long dishId);

    @Select("""
            SELECT od.id FROM order_detail od
            INNER JOIN orders o ON o.id=od.order_id
            WHERE od.dish_id=#{dishId} AND o.status IN (1,2,3,4,7)
            LIMIT 1 FOR SHARE
            """)
    Long findPendingOrderReference(@Param("dishId") Long dishId);

    // 读取当前未完成订单，套餐扣减的菜品也可能只出现在快照中。
    @Select("SELECT inventory_snapshot FROM orders WHERE status IN (1,2,3,4,7) FOR SHARE")
    List<String> selectPendingInventorySnapshots();

    @Delete("DELETE FROM dish_flavor WHERE dish_id=#{dishId}")
    int deleteFlavors(@Param("dishId") Long dishId);
}
