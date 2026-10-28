package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.sky.annotation.AutoFill;
import com.sky.enumeration.OperationType;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    @Override
    @AutoFill(OperationType.INSERT)
    int insert(Category category);

    @Override
    @AutoFill(OperationType.UPDATE)
    int update(
            @Param(Constants.ENTITY) Category category,
            @Param(Constants.WRAPPER) Wrapper<Category> updateWrapper
    );

    @Select("""
            SELECT
                EXISTS(
                    SELECT 1
                    FROM dish
                    WHERE category_id = #{categoryId}
                )
                OR EXISTS(
                    SELECT 1
                    FROM setmeal
                    WHERE category_id = #{categoryId}
                )
            """)

    boolean hasReferences(
            @Param("categoryId") Long categoryId
    );
}
