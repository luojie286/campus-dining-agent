package com.sky.controller.user;

import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.MenuService;
import com.sky.vo.MenuItemVO;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("userMenuController")
@Profile("!bootstrap")
@RequestMapping("/api/user/menu")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/page")
    public Result<PageResult<MenuItemVO>> page(
            @Valid @ModelAttribute DishPageQueryDTO query) {
        return Result.success(menuService.pageForUser(query));
    }
}