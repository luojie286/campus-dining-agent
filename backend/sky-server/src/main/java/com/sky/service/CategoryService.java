package com.sky.service;

import com.sky.dto.CategoryPageQueryDTO;
import com.sky.dto.CategorySaveDTO;
import com.sky.dto.CategoryStatusUpdateDTO;
import com.sky.result.PageResult;
import com.sky.vo.CategoryVO;

import java.util.List;

public interface CategoryService {

    CategoryVO create(CategorySaveDTO dto);

    CategoryVO update(CategorySaveDTO dto);

    void delete(Long id);

    CategoryVO updateStatus(
            Long id,
            CategoryStatusUpdateDTO dto
    );

    PageResult<CategoryVO> pageQuery(CategoryPageQueryDTO dto);

    List<CategoryVO> listForAdmin(Integer type);

    List<CategoryVO> listForUser(Integer type);
}