package com.sky.controller.admin;

import org.springframework.context.annotation.Profile;

import com.sky.dto.CategoryPageQueryDTO;
import com.sky.dto.CategorySaveDTO;
import com.sky.dto.CategoryStatusUpdateDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.CategoryService;
import com.sky.vo.CategoryVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("adminCategoryController")
@Profile("!bootstrap")
@RequestMapping("/api/admin/category")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public Result<CategoryVO> create(
            @Valid @RequestBody CategorySaveDTO dto) {

        return Result.success(categoryService.create(dto));
    }

    @PutMapping
    public Result<CategoryVO> update(
            @Valid @RequestBody CategorySaveDTO dto) {

        return Result.success(categoryService.update(dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @PathVariable("id") Long id) {

        categoryService.delete(id);

        return Result.success(null);
    }

    @PutMapping("/{id}/status")
    public Result<CategoryVO> updateStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody CategoryStatusUpdateDTO dto) {

        return Result.success(
                categoryService.updateStatus(id, dto)
        );
    }

    @GetMapping("/page")
    public Result<PageResult<CategoryVO>> page(
            @Valid @ModelAttribute CategoryPageQueryDTO dto) {

        return Result.success(categoryService.pageQuery(dto));
    }

    @GetMapping("/list")
    public Result<List<CategoryVO>> list(
            @RequestParam(value = "type", required = false)
            Integer type) {

        return Result.success(
                categoryService.listForAdmin(type)
        );
    }
}
