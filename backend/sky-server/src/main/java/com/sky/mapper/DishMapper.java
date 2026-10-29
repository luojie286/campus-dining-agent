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

@Mapper
public interface DishMapper extends BaseMapper<Dish> {

    IPage<Dish> selectDishPage(Page<Dish> page,
            @Param("query") DishPageQueryDTO query,
            @Param("namePattern") String namePattern);

    @Override
    @AutoFill(OperationType.INSERT)
    int insert(Dish dish);
}
