package com.sky.service;

import com.sky.exception.BadRequestException;
import com.sky.exception.ConflictException;
import com.sky.exception.NotFoundException;
import com.sky.exception.TooManyRequestsException;
import com.sky.mapper.OrderLifecycleRepository;
import com.sky.mapper.OrderRepository;
import com.sky.mapper.OrderUrgeRepository;
import com.sky.vo.OrderVO;
import com.sky.vo.UrgeReplyVO;
import com.sky.websocket.OrderEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/** 客户催单：本人已付且未完结订单，60 秒冷却、每单最多 5 次，成功后才推给运营。 */
@Service
@Profile("!bootstrap")
public class OrderUrgeService {

    private static final int MAX_COUNT = 5;
    private static final Duration COOLDOWN = Duration.ofSeconds(60);
    private static final Set<Integer> URGEABLE = Set.of(2, 3, 4, 7);

    private final UserTransactionLock users;
    private final OrderLifecycleRepository lifecycle;
    private final OrderRepository orders;
    private final OrderUrgeRepository urges;
    private final OrderEventPublisher publisher;
    private final Clock clock;

    public OrderUrgeService(UserTransactionLock users, OrderLifecycleRepository lifecycle,
            OrderRepository orders, OrderUrgeRepository urges,
            OrderEventPublisher publisher, Clock clock) {
        this.users = users;
        this.lifecycle = lifecycle;
        this.orders = orders;
        this.urges = urges;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UrgeReplyVO urge(long id) {
        validateId(id);
        long userId = users.currentUserId();
        OrderLifecycleRepository.State state = lifecycle.find(id, userId, true)
                .orElseThrow(() -> new NotFoundException("订单不存在"));

        if (state.payStatus() != 1 || !URGEABLE.contains(state.status())) {
            throw new ConflictException("仅已支付且未完成的订单可以催单");
        }

        // 先锁订单行，再锁催单记录；并发催单会在这里排队，不能各自读到旧计数。
        OrderUrgeRepository.Urge current = urges.findForUpdate(id)
                .orElse(new OrderUrgeRepository.Urge(id, 0, null));
        if (current.count() >= MAX_COUNT) {
            throw new ConflictException("该订单的 5 次催单机会已用完");
        }

        OffsetDateTime now = now();
        if (current.lastAt() != null) {
            Duration elapsed = Duration.between(current.lastAt(), now.toLocalDateTime());
            if (elapsed.compareTo(COOLDOWN) < 0) {
                long retryAfter = Math.max(1, COOLDOWN.minus(elapsed).getSeconds() + 1);
                throw new TooManyRequestsException("催单太频繁，请稍后再试", (int) retryAfter);
            }
        }

        int urgeCount = urges.increment(id, now.toLocalDateTime());
        int remaining = Math.max(0, MAX_COUNT - urgeCount);
        OrderVO order = orders.findOwned(userId, id)
                .orElseThrow(() -> new NotFoundException("订单不存在"));
        publisher.publishAfterCommit("ORDER_URGED", order, urgeCount);

        OffsetDateTime nextAllowedAt = remaining > 0 ? now.plusSeconds(60) : null;
        return new UrgeReplyVO(id, now, nextAllowedAt, urgeCount, remaining);
    }

    private void validateId(long id) {
        if (id < 1 || id > 9007199254740991L) throw new BadRequestException("订单ID不合法");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
