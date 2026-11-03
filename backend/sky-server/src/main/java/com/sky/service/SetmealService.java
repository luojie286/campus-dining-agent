package com.sky.service;
import com.sky.dto.SetmealCreateDTO;
import com.sky.vo.SetmealVO;

public interface SetmealService {
    SetmealVO create(SetmealCreateDTO dto);
}
