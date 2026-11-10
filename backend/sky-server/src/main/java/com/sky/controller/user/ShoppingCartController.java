package com.sky.controller.user;

import com.sky.dto.CartRequest;
import com.sky.result.Result;
import com.sky.service.ShoppingCartService;
import com.sky.vo.CartLineVO;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/user/shoppingCart")
public class ShoppingCartController {
    private final ShoppingCartService carts;

    public ShoppingCartController(ShoppingCartService carts) {
        this.carts = carts;
    }

    @GetMapping("/list")
    public Result<List<CartLineVO>> list() {
        return Result.success(carts.list());
    }

    @PutMapping
    public Result<List<CartLineVO>> replace(@Valid @RequestBody CartRequest.Replace body) {
        return Result.success(carts.replace(body.getLines()));
    }

    @PostMapping("/add")
    public Result<List<CartLineVO>> add(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody CartRequest.Item body) {
        return Result.success(carts.add(key, body.getItemId(), body.getQuantity()));
    }

    @PutMapping("/{id}")
    public Result<List<CartLineVO>> setQuantity(@PathVariable("id") long id,
            @Valid @RequestBody CartRequest.Quantity body) {
        return Result.success(carts.setQuantity(id, body.getQuantity()));
    }

    @DeleteMapping("/{id}")
    public Result<List<CartLineVO>> remove(@PathVariable("id") long id) {
        return Result.success(carts.remove(id));
    }

    @DeleteMapping("/clean")
    public Result<List<CartLineVO>> clear() {
        return Result.success(carts.clear());
    }
}
