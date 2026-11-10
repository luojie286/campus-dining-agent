package com.sky.controller.user;

import com.sky.dto.OrderSubmitDTO;
import com.sky.result.Result;
import com.sky.service.UserOrderService;
import com.sky.vo.OrderVO;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/user/order")
public class UserOrderController {
    private final UserOrderService orders;

    public UserOrderController(UserOrderService orders) {
        this.orders = orders;
    }

    @PostMapping("/submit")
    public Result<OrderVO> submit(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody OrderSubmitDTO body) {
        return Result.success(orders.submit(key, body));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable("id") long id) {
        return Result.success(orders.detail(id));
    }
}
