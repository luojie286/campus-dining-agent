package com.sky.controller.admin;

import com.sky.dto.CancelOrderDTO;
import com.sky.dto.OrderPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.AdminOrderService;
import com.sky.service.AdminOrderService.Action;
import com.sky.vo.OrderVO;
import com.sky.vo.RefundVO;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/admin/order")
public class AdminOrderController {
    private final AdminOrderService orders;

    public AdminOrderController(AdminOrderService orders) { this.orders = orders; }

    @GetMapping("/page")
    public Result<PageResult<OrderVO>> page(@Valid @ModelAttribute OrderPageQueryDTO query) {
        return Result.success(orders.page(query));
    }

    @GetMapping("/counts")
    public Result<Map<String, Long>> counts(@Valid @ModelAttribute OrderPageQueryDTO query) {
        return Result.success(orders.counts(query));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable("id") long id) { return Result.success(orders.detail(id)); }

    @PostMapping("/{id}/ready")
    public Result<OrderVO> ready(@PathVariable("id") long id) {
        return Result.success(orders.advance(id, Action.READY));
    }

    @PostMapping("/{id}/deliver")
    public Result<OrderVO> deliver(@PathVariable("id") long id) {
        return Result.success(orders.advance(id, Action.DELIVER));
    }

    @PostMapping("/{id}/complete")
    public Result<OrderVO> complete(@PathVariable("id") long id) {
        return Result.success(orders.advance(id, Action.COMPLETE));
    }

    @PostMapping("/{id}/cancel")
    public Result<OrderVO> cancel(@PathVariable("id") long id,
            @Valid @RequestBody(required = false) CancelOrderDTO body) {
        return Result.success(orders.cancel(id, body == null ? "运营取消订单" : body.getReason()));
    }

    @GetMapping("/{id}/refund")
    public Result<RefundVO> refund(@PathVariable("id") long id) { return Result.success(orders.refund(id)); }
}
