package com.sky.controller.user;

import org.springframework.context.annotation.Profile;

import com.sky.result.Result;
import com.sky.service.CategoryService;
import com.sky.vo.CategoryVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("userCategoryController")
@Profile("!bootstrap")
@RequestMapping("/api/user/category")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/list")
    public Result<List<CategoryVO>> list(
            @RequestParam(value = "type", required = false)
            Integer type) {

        return Result.success(
                categoryService.listForUser(type)
        );
    }
}
