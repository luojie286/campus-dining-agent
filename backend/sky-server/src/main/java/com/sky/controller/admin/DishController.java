package com.sky.controller.admin;

import com.sky.dto.DishCreateDTO;
import com.sky.dto.DishUpdateDTO;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/admin/dish")
public class DishController {

    private final DishService dishService;

    @GetMapping("/{id}")
    public Result<DishVO> getById(@PathVariable("id") Long id) {
        return Result.success(dishService.getById(id));
    }

    @PutMapping
    public Result<DishVO> update(@Valid @RequestBody DishUpdateDTO dto) {
        return Result.success(dishService.update(dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        dishService.delete(id);
        return Result.success(null);
    }

    @GetMapping("/page")
    public Result<PageResult<DishVO>> page(
            @Valid @ModelAttribute DishPageQueryDTO dto) {
        return Result.success(dishService.pageQuery(dto));
    }

    public DishController(DishService dishService) {
        this.dishService = dishService;
    }

    @PostMapping
    public Result<DishVO> create(@Valid @RequestBody DishCreateDTO dto) {
        return Result.success(dishService.create(dto));
    }
}
