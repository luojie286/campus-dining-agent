package com.sky.service;
import com.sky.result.PageResult;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.dto.SetmealUpdateDTO;
import com.sky.dto.SetmealCreateDTO;
import com.sky.vo.SetmealVO;

public interface SetmealService {
    PageResult<SetmealVO> pageQuery(SetmealPageQueryDTO dto);
    SetmealVO updateStatus(Long id, Integer status);
    SetmealVO getById(Long id);
    SetmealVO update(SetmealUpdateDTO dto);
    void delete(Long id);
    SetmealVO create(SetmealCreateDTO dto);
}
