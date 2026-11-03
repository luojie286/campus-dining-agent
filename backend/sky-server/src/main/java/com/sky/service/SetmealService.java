package com.sky.service;
import com.sky.dto.SetmealCreateDTO;
import com.sky.vo.SetmealVO;

public interface SetmealService {
    com.sky.result.PageResult<SetmealVO> pageQuery(com.sky.dto.SetmealPageQueryDTO dto);
    SetmealVO create(SetmealCreateDTO dto);
}
