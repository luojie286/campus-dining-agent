package com.sky.controller.user;

import com.sky.dto.OrderSubmitDTO;
import com.sky.dto.OrderPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.vo.CartLineVO;
import java.util.List;
import com.sky.result.Result;
import com.sky.service.UserOrderService;
import com.sky.service.OrderLifecycleService;
import com.sky.service.OrderUrgeService;
import com.sky.vo.PaymentStatusVO;
import com.sky.vo.RefundVO;
import com.sky.vo.OrderVO;
import com.sky.vo.UrgeReplyVO;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/user/order")
public class UserOrderController {
    private final UserOrderService orders;

    private final OrderLifecycleService lifecycle;

    private final OrderUrgeService urges;

    public UserOrderController(UserOrderService orders, OrderLifecycleService lifecycle,
            OrderUrgeService urges) {
        this.orders = orders;
        this.lifecycle = lifecycle;
        this.urges = urges;
    }

    @PostMapping("/submit")
    public Result<OrderVO> submit(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody OrderSubmitDTO body) {
        return Result.success(orders.submit(key, body));
    }

    @GetMapping("/page")
    public Result<PageResult<OrderVO>> page(@Valid @ModelAttribute OrderPageQueryDTO query) {
        return Result.success(orders.page(query));
    }

    @PostMapping("/{id}/reorder")
    public Result<List<CartLineVO>> reorder(@PathVariable("id") long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return Result.success(orders.reorder(id, key));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable("id") long id) {
        return Result.success(orders.detail(id));
    }

    @PostMapping("/{id}/pay")
    public Result<OrderVO> payMock(@PathVariable("id") long id) {
        return Result.success(lifecycle.payMock(id));
    }

    @GetMapping("/{id}/payment")
    public Result<PaymentStatusVO> payment(@PathVariable("id") long id) {
        return Result.success(lifecycle.payment(id));
    }

    @PostMapping("/{id}/cancel")
    public Result<OrderVO> cancel(@PathVariable("id") long id) {
        return Result.success(lifecycle.cancel(id));
    }

    @PostMapping("/{id}/urge")
    public Result<UrgeReplyVO> urge(@PathVariable("id") long id) {
        return Result.success(urges.urge(id));
    }
    @GetMapping("/{id}/refund")
    public Result<RefundVO> refund(@PathVariable("id") long id) {
        return Result.success(lifecycle.refund(id));
    }
}
