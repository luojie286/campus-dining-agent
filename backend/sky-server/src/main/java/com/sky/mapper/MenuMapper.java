package com.sky.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.dto.DishPageQueryDTO;
import com.sky.dto.MenuPartRow;
import com.sky.dto.MenuRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MenuMapper {

    IPage<MenuRow> selectMenuPage(
            Page<MenuRow> page,
            @Param("query") DishPageQueryDTO query,
            @Param("namePattern") String namePattern
    );

    List<MenuPartRow> selectSetmealParts(
            @Param("setmealIds") List<Long> setmealIds
    );
}