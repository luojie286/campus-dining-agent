package com.sky.service;

import com.sky.dto.DishCreateDTO;
import com.sky.vo.DishVO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;

public interface DishService {

    PageResult<DishVO> pageQuery(DishPageQueryDTO dto);

    DishVO create(DishCreateDTO dto);
}
