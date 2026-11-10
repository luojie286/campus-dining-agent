package com.sky.service;

import com.sky.cache.MenuPageCache;
import com.sky.dto.InventoryStockUse;
import com.sky.dto.OrderPageQueryDTO;
import com.sky.dto.OrderPageQueryDTO.State;
import com.sky.exception.BadRequestException;
import com.sky.exception.ConflictException;
import com.sky.exception.NotFoundException;
import com.sky.mapper.OrderLifecycleRepository;
import com.sky.mapper.OrderRefundRepository;
import com.sky.mapper.OrderRepository;
import com.sky.result.PageResult;
import com.sky.vo.OrderVO;
import com.sky.vo.RefundVO;
import com.sky.websocket.OrderEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@Profile("!bootstrap")
public class AdminOrderService {
    private final EmployeePermissionService permissions;
    private final OrderRepository orders;
    private final OrderLifecycleRepository lifecycle;
    private final OrderRefundRepository refunds;
    private final MenuPageCache menuCache;
    private final Clock clock;
    private final OrderEventPublisher events;

    public AdminOrderService(EmployeePermissionService permissions, OrderRepository orders,
            OrderLifecycleRepository lifecycle, OrderRefundRepository refunds, MenuPageCache menuCache,
            Clock clock, OrderEventPublisher events) {
        this.permissions = permissions;
        this.orders = orders;
        this.lifecycle = lifecycle;
        this.refunds = refunds;
        this.menuCache = menuCache;
        this.clock = clock;
        this.events = events;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<OrderVO> page(OrderPageQueryDTO query) {
        permissions.requireOperationsAccess();
        return orders.pageForOperator(query);
    }

    public Map<String, Long> counts(OrderPageQueryDTO query) {
        permissions.requireOperationsAccess();
        return orders.countByStatus(query);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrderVO detail(long id) {
        permissions.requireOperationsAccess();
        validateId(id);
        return order(id);
    }

    public RefundVO refund(long id) {
        permissions.requireOperationsAccess();
        validateId(id);
        return refunds.find(id).orElseThrow(() -> new NotFoundException("退款记录不存在"));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderVO advance(long id, Action action) {
        permissions.requireOperationsAccess();
        validateId(id);
        var locked = state(id, true);
        OrderVO order = order(id);
        State current = State.fromCode(locked.status());
        boolean delivery = "DELIVERY".equals(order.fulfillment());
        State target;
        State expected;

        switch (action) {
            case READY -> {
                target = delivery ? State.READY_DELIVERY : State.READY_PICKUP;
                expected = State.PREPARING;
            }
            case DELIVER -> {
                if (!delivery) throw new ConflictException("自提订单不能派送");
                target = State.DELIVERING;
                expected = State.READY_DELIVERY;
            }
            case COMPLETE -> {
                target = State.COMPLETED;
                expected = delivery ? State.DELIVERING : State.READY_PICKUP;
            }
            default -> throw new IllegalStateException("订单动作不合法");
        }
        if (current == target) return order;
        if (current != expected || locked.payStatus() != 1) throw new ConflictException("当前订单状态不能执行此操作");
        lifecycle.advance(locked, target, now(), action.note);
        OrderVO updated = order(id);
        events.publishAfterCommit("ORDER_STATUS_CHANGED", updated, null);
        return updated;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderVO cancel(long id, String reason) {
        permissions.requireOperationsAccess();
        validateId(id);

        var observed = state(id, false);
        List<InventoryStockUse> stock = List.of();
        // 释放库存时先锁商品再锁订单，与其他商品写入保持一致。
        if (observed.unpaid() && observed.mock()) {
            stock = lifecycle.stockFrom(observed);
            lifecycle.lockStock(stock);
        }

        var current = state(id, true);
        if (current.status() == State.CANCELLED.code()) return order(id);
        if (!current.mock()) throw new ConflictException("微信订单需先确认关单或退款");
        if (current.unpaid()) {
            if (stock.isEmpty()) throw new ConflictException("订单状态已变化");
            lifecycle.markCancelled(current, now(), reason);
            lifecycle.releaseStock(stock);
            menuCache.invalidateAfterCommit();
        } else {
            State status = State.fromCode(current.status());
            if (current.payStatus() != 1 ||
                    (status != State.PREPARING && status != State.READY_DELIVERY && status != State.READY_PICKUP)) {
                throw new ConflictException("当前订单状态不能取消");
            }
            OffsetDateTime time = now();
            refunds.insertMockRefund(order(id), reason, time);
            lifecycle.cancelRefundedMock(current, time, reason);
        }
        OrderVO cancelled = order(id);
        events.publishAfterCommit("ORDER_STATUS_CHANGED", cancelled, null);
        return cancelled;
    }

    private OrderLifecycleRepository.State state(long id, boolean lock) {
        return lifecycle.find(id, null, lock).orElseThrow(() -> new NotFoundException("订单不存在"));
    }

    private OrderVO order(long id) {
        return orders.findAny(id).orElseThrow(() -> new NotFoundException("订单不存在"));
    }

    private void validateId(long id) {
        if (id < 1 || id > 9007199254740991L) throw new BadRequestException("订单ID不合法");
    }

    private OffsetDateTime now() { return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS); }

    public enum Action {
        READY("备餐完成"), DELIVER("开始配送"), COMPLETE("运营确认完成");

        private final String note;
        Action(String note) { this.note = note; }
    }
}
