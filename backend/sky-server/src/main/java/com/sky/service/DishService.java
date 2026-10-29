package com.sky.service;

import com.sky.dto.DishCreateDTO;
import com.sky.vo.DishVO;

public interface DishService {

    DishVO create(DishCreateDTO dto);
}