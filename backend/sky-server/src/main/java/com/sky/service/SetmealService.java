package com.sky.service;
import com.sky.dto.SetmealCreateDTO;
import com.sky.vo.SetmealVO;

public interface SetmealService {
    com.sky.result.PageResult<SetmealVO> pageQuery(com.sky.dto.SetmealPageQueryDTO dto);
    SetmealVO getById(Long id);
    SetmealVO update(com.sky.dto.SetmealUpdateDTO dto);
    void delete(Long id);
    SetmealVO create(SetmealCreateDTO dto);
}
