package com.sky.service;

import com.sky.cache.MenuPageCache;
import com.sky.dto.InventoryStockUse;
import com.sky.exception.BadRequestException;
import com.sky.exception.ConflictException;
import com.sky.exception.NotFoundException;
import com.sky.mapper.OrderLifecycleRepository;
import com.sky.mapper.OrderLifecycleRepository.State;
import com.sky.mapper.OrderRepository;
import com.sky.mapper.OrderRefundRepository;
import com.sky.vo.RefundVO;
import com.sky.vo.OrderVO;
import com.sky.vo.PaymentStatusVO;
import com.sky.websocket.OrderEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Profile("!bootstrap")
public class OrderLifecycleService {
    private final UserTransactionLock users;
    private final OrderRepository orders;
    private final OrderRefundRepository refunds;
    private final OrderLifecycleRepository lifecycle;
    private final MenuPageCache menuCache;
    private final Clock clock;
    private final PaymentChannel paymentChannel;
    private final OrderEventPublisher events;

    public OrderLifecycleService(UserTransactionLock users, OrderRepository orders,
            OrderLifecycleRepository lifecycle, OrderRefundRepository refunds, MenuPageCache menuCache, Clock clock,
            @Value("${sky.payment.channel:MOCK}") PaymentChannel paymentChannel,
            OrderEventPublisher events) {
        this.users = users;
        this.orders = orders;
        this.refunds = refunds;
        this.lifecycle = lifecycle;
        this.menuCache = menuCache;
        this.clock = clock;
        this.paymentChannel = paymentChannel;
        this.events = events;
    }

    @Transactional
    public OrderVO payMock(long id) {
        validateId(id);
        long userId = users.currentUserId();
        State order = lockOwned(userId, id);
        if (paymentChannel != PaymentChannel.MOCK || !order.mock()) {
            throw new ConflictException("当前订单不能使用模拟支付");
        }
        if (order.payStatus() == 1 && order.status() != 6) return findOwned(userId, id);
        OffsetDateTime now = now();
        if (!order.unpaid() || !order.deadline().isAfter(now.toLocalDateTime())) {
            throw new ConflictException("订单已过期或不能支付");
        }
        lifecycle.markMockPaid(order, now);
        OrderVO paid = findOwned(userId, id);
        // 合同规定：付款成功进入制作中才提醒运营，创建未支付订单不提醒。
        events.publishAfterCommit("NEW_ORDER", paid, null);
        return paid;
    }

    @Transactional(readOnly = true)
    public PaymentStatusVO payment(long id) {
        validateId(id);
        OrderVO order = findOwned(users.currentUserId(), id);
        String state = "NOT_STARTED";
        if ("PAID".equals(order.payStatus()) || "REFUNDED".equals(order.payStatus())) state = "SUCCESS";
        else if ("CANCELLED".equals(order.status())) state = "CLOSED";
        return new PaymentStatusVO(order.id(), order.number(), order.paymentChannel(), state,
                order.amount(), order.paidAt());
    }

    @Transactional(readOnly = true)
    public RefundVO refund(long id) {
        validateId(id);
        findOwned(users.currentUserId(), id);
        return refunds.find(id).orElseThrow(() -> new NotFoundException("退款记录不存在"));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderVO cancel(long id) {
        validateId(id);
        long userId = users.currentUserId();
        State observed = lifecycle.find(id, userId, false)
                .orElseThrow(() -> new NotFoundException("订单不存在"));
        if (observed.status() == 6) return findOwned(userId, id);
        if (!observed.unpaid()) throw new ConflictException("仅待支付订单可以取消");
        if (!observed.mock()) throw new ConflictException("该订单需先确认微信关单结果");

        var stock = lifecycle.stockFrom(observed);
        // 商品管理也会在持有商品行锁时查询订单，因此先锁库存、后锁订单。
        lifecycle.lockStock(stock);
        State current = lockOwned(userId, id);
        if (current.status() == 6) return findOwned(userId, id);
        if (!current.unpaid() || !current.mock()) throw new ConflictException("订单状态已变化");
        close(current, stock, now(), "用户取消未支付订单");
        return findOwned(userId, id);
    }

    @Transactional
    public boolean cancelExpiredMock(long id) {
        var candidate = lifecycle.find(id, null, false);
        if (candidate.isEmpty()) return false;
        State observed = candidate.get();
        if (!expiredMock(observed, now())) return false;

        var stock = lifecycle.stockFrom(observed);
        lifecycle.lockStock(stock);
        var latest = lifecycle.find(id, null, true);
        OffsetDateTime now = now();
        if (latest.isEmpty() || !expiredMock(latest.get(), now)) return false;
        close(latest.get(), stock, now, "支付超时，自动关闭订单");
        return true;
    }

    private void close(State order, List<InventoryStockUse> stock,
            OffsetDateTime now, String reason) {
        lifecycle.markCancelled(order, now, reason);
        lifecycle.releaseStock(stock);
        menuCache.invalidateAfterCommit();
        OrderVO cancelled = orders.findAny(order.id())
                .orElseThrow(() -> new IllegalStateException("取消后的订单不存在"));
        events.publishAfterCommit("ORDER_STATUS_CHANGED", cancelled, null);
    }

    private boolean expiredMock(State order, OffsetDateTime now) {
        return order.mock() && order.unpaid() && !order.deadline().isAfter(now.toLocalDateTime());
    }

    private State lockOwned(long userId, long id) {
        return lifecycle.find(id, userId, true).orElseThrow(() -> new NotFoundException("订单不存在"));
    }

    private OrderVO findOwned(long userId, long id) {
        return orders.findOwned(userId, id).orElseThrow(() -> new NotFoundException("订单不存在"));
    }

    private void validateId(long id) {
        if (id < 1 || id > 9007199254740991L) throw new BadRequestException("订单ID不合法");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
