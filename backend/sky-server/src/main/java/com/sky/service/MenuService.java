package com.sky.service;

import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.vo.MenuItemVO;

public interface MenuService {
    PageResult<MenuItemVO> pageForUser(DishPageQueryDTO query);
}