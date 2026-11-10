package com.sky;

import com.sky.exception.ConflictException;
import com.sky.exception.TooManyRequestsException;
import com.sky.mapper.OrderLifecycleRepository;
import com.sky.mapper.OrderRepository;
import com.sky.mapper.OrderUrgeRepository;
import com.sky.service.OrderUrgeService;
import com.sky.service.UserTransactionLock;
import com.sky.vo.OrderVO;
import com.sky.vo.UrgeReplyVO;
import com.sky.websocket.OrderEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 催单规则：60 秒冷却、每单 5 次上限、成功后才在事务提交时推送。 */
class OrderUrgeServiceTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long ORDER_ID = 1L;
    private static final long USER_ID = 9L;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T04:00:00Z"), BUSINESS_ZONE);

    private UserTransactionLock users;
    private OrderLifecycleRepository lifecycle;
    private OrderRepository orders;
    private OrderUrgeRepository urges;
    private OrderEventPublisher publisher;
    private OrderUrgeService service;

    @BeforeEach
    void setUp() {
        users = mock(UserTransactionLock.class);
        lifecycle = mock(OrderLifecycleRepository.class);
        orders = mock(OrderRepository.class);
        urges = mock(OrderUrgeRepository.class);
        publisher = mock(OrderEventPublisher.class);
        service = new OrderUrgeService(users, lifecycle, orders, urges, publisher, clock);

        when(users.currentUserId()).thenReturn(USER_ID);
        when(lifecycle.find(ORDER_ID, USER_ID, true)).thenReturn(Optional.of(paidPreparing()));
        when(orders.findOwned(USER_ID, ORDER_ID)).thenReturn(Optional.of(order()));
    }

    @Test
    void cooldownRejectsWithRetryAfter() {
        LocalDateTime last = LocalDateTime.now(clock).minusSeconds(10);
        when(urges.findForUpdate(ORDER_ID))
                .thenReturn(Optional.of(new OrderUrgeRepository.Urge(ORDER_ID, 1, last)));

        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> service.urge(ORDER_ID));

        assertTrue(exception.getRetryAfterSeconds() > 0);
        assertTrue(exception.getRetryAfterSeconds() <= 60);
    }

    @Test
    void fifthUrgeExhaustsTheQuota() {
        when(urges.findForUpdate(ORDER_ID)).thenReturn(Optional.of(
                new OrderUrgeRepository.Urge(ORDER_ID, 5, LocalDateTime.now(clock))));

        assertThrows(ConflictException.class, () -> service.urge(ORDER_ID));
    }

    @Test
    void unpaidOrderCannotBeUrged() {
        OrderLifecycleRepository.State unpaid = new OrderLifecycleRepository.State(
                ORDER_ID, USER_ID, 1, 0, "MOCK", LocalDateTime.now(clock).plusMinutes(15),
                "[]", "[]");
        when(lifecycle.find(ORDER_ID, USER_ID, true)).thenReturn(Optional.of(unpaid));

        assertThrows(ConflictException.class, () -> service.urge(ORDER_ID));
    }

    @Test
    void acceptedUrgeReturnsRemainingAndPublishesAfterCommit() {
        when(urges.findForUpdate(ORDER_ID)).thenReturn(Optional.empty());
        when(urges.increment(eq(ORDER_ID), any())).thenReturn(1);

        UrgeReplyVO reply = service.urge(ORDER_ID);

        assertEquals(1, reply.urgeCount());
        assertEquals(4, reply.remaining());
        assertNotNull(reply.nextAllowedAt());
        verify(publisher).publishAfterCommit(eq("ORDER_URGED"), any(OrderVO.class), eq(1));
    }

    @Test
    void fifthAcceptedUrgeOmitsNextAllowedAt() {
        when(urges.findForUpdate(ORDER_ID)).thenReturn(Optional.empty());
        when(urges.increment(eq(ORDER_ID), any())).thenReturn(5);

        UrgeReplyVO reply = service.urge(ORDER_ID);

        assertEquals(0, reply.remaining());
        assertNull(reply.nextAllowedAt());
    }

    private OrderLifecycleRepository.State paidPreparing() {
        return new OrderLifecycleRepository.State(ORDER_ID, USER_ID, 2, 1, "MOCK",
                LocalDateTime.now(clock).plusMinutes(15), "[]", "[]");
    }

    private OrderVO order() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return new OrderVO(ORDER_ID, "NO-1", USER_ID, "张三", "PREPARING", "PAID", "DELIVERY",
                new BigDecimal("25.00"), now, now, now.plusMinutes(15),
                "收餐人", "13800000000", "园区A栋", "", List.of(), List.of(), "MOCK");
    }
}
