package com.sky.controller.user;

import com.sky.result.Result;
import com.sky.service.ShopService;
import com.sky.vo.ShopVO;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("userShopController")
@Profile("!bootstrap")
@RequestMapping("/api/user/shop")
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping
    public Result<ShopVO> getShop() {
        return Result.success(shopService.getForUser());
    }
}
