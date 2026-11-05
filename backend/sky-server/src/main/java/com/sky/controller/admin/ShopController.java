package com.sky.controller.admin;

import com.sky.dto.ShopUpdateDTO;
import com.sky.result.Result;
import com.sky.service.ShopService;
import com.sky.vo.ShopVO;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("adminShopController")
@Profile("!bootstrap")
@RequestMapping("/api/admin/shop")
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping
    public Result<ShopVO> getShop() {
        return Result.success(shopService.getForAdmin());
    }

    @PutMapping
    public Result<ShopVO> updateStatus(@Valid @RequestBody ShopUpdateDTO dto) {
        return Result.success(shopService.updateStatus(dto));
    }
}
