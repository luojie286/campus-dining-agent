package com.sky.service;

import com.sky.dto.DishCreateDTO;
import com.sky.vo.DishVO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.dto.DishUpdateDTO;

public interface DishService {
    DishVO getById(Long itemId);

    DishVO update(DishUpdateDTO dto);

    void delete(Long itemId);

    PageResult<DishVO> pageQuery(DishPageQueryDTO dto);

    DishVO create(DishCreateDTO dto);
}
