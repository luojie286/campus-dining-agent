package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

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