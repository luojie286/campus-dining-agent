package com.sky;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.agent.AgentActionService;
import com.sky.agent.AgentProperties;
import com.sky.exception.ConflictException;
import com.sky.exception.NotFoundException;
import com.sky.mapper.AgentActionRepository;
import com.sky.mapper.OrderRepository;
import com.sky.service.AdminOrderService;
import com.sky.service.AuthenticatedPrincipalService;
import com.sky.service.OrderLifecycleService;
import com.sky.service.SetmealService;
import com.sky.vo.OrderVO;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 确认记录：归属隔离、过期、目标状态复查、重复确认幂等。 */
class AgentActionServiceTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String PARAMS = """
            {"toolName":"prepareCancelOrder","orderId":1,"expectedStatus":"UNPAID","expectedPayStatus":"UNPAID"}
            """;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T04:00:00Z"), BUSINESS_ZONE);
    private final AuthenticatedPrincipalService.Principal principal =
            new AuthenticatedPrincipalService.Principal("user", 9L, "token",
                    Instant.parse("2026-11-10T05:00:00Z"));

    private AgentActionRepository actions;
    private OrderRepository orders;
    private OrderLifecycleService lifecycle;
    private AgentActionService service;

    @BeforeEach
    void setUp() {
        actions = mock(AgentActionRepository.class);
        orders = mock(OrderRepository.class);
        lifecycle = mock(OrderLifecycleService.class);
        service = new AgentActionService(actions, orders, lifecycle,
                mock(AdminOrderService.class), mock(SetmealService.class),
                mock(AgentProperties.class), new ObjectMapper(), clock);
    }

    @Test
    void repeatConfirmReturnsOriginalResultWithoutExecutingAgain() {
        when(actions.find("a1")).thenReturn(Optional.of(row(
                AgentActionRepository.SUCCESS, 9L, "user", "已取消订单。",
                LocalDateTime.now(clock).plusMinutes(5))));

        var reply = service.confirm("a1", principal);

        assertEquals("已取消订单。", reply.content());
        verify(lifecycle, never()).cancel(anyLong());
    }

    @Test
    void otherOwnerIsTreatedAsNotFound() {
        when(actions.find("a1")).thenReturn(Optional.of(row(
                AgentActionRepository.PENDING, 8L, "user", null,
                LocalDateTime.now(clock).plusMinutes(5))));

        assertThrows(NotFoundException.class, () -> service.confirm("a1", principal));
    }

    @Test
    void expiredCardIsRejected() {
        when(actions.find("a1")).thenReturn(Optional.of(row(
                AgentActionRepository.PENDING, 9L, "user", null,
                LocalDateTime.now(clock).minusMinutes(1))));

        assertThrows(ConflictException.class, () -> service.confirm("a1", principal));
        verify(actions, never()).markExecuting(anyString(), anyLong(), anyString(), any());
    }

    @Test
    void changedOrderStateIsRejectedWithoutRecordingSuccess() {
        when(actions.find("a1")).thenReturn(Optional.of(row(
                AgentActionRepository.PENDING, 9L, "user", null,
                LocalDateTime.now(clock).plusMinutes(5))));
        when(actions.markExecuting(eq("a1"), eq(9L), eq("user"), any())).thenReturn(true);
        when(orders.findAny(1L)).thenReturn(Optional.of(order("PREPARING", "PAID")));

        assertThrows(ConflictException.class, () -> service.confirm("a1", principal));

        verify(lifecycle, never()).cancel(anyLong());
        verify(actions, never()).markSuccess(anyString(), anyString(), any());
    }

    @Test
    void successfulConfirmExecutesBusinessOnce() {
        when(actions.find("a1")).thenReturn(Optional.of(row(
                AgentActionRepository.PENDING, 9L, "user", null,
                LocalDateTime.now(clock).plusMinutes(5))));
        when(actions.markExecuting(eq("a1"), eq(9L), eq("user"), any())).thenReturn(true);
        when(orders.findAny(1L)).thenReturn(Optional.of(order("UNPAID", "UNPAID")));

        var reply = service.confirm("a1", principal);

        assertEquals("订单 NO-1 已取消。", reply.content());
        verify(lifecycle).cancel(1L);
        verify(actions).markSuccess(eq("a1"), anyString(), any());
    }

    private AgentActionRepository.Row row(int status, long actorId, String role,
            String result, LocalDateTime expireTime) {
        return new AgentActionRepository.Row(1L, "a1", actorId, role, "session-1",
                "prepareCancelOrder", PARAMS, "取消订单 NO-1", "取消影响说明",
                status, result, expireTime, null);
    }

    private OrderVO order(String status, String payStatus) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return new OrderVO(1L, "NO-1", 9L, "张三", status, payStatus, "DELIVERY",
                new BigDecimal("25.00"), now, null, now.plusMinutes(15),
                "收餐人", "13800000000", "园区A栋", "", List.of(), List.of(), "MOCK");
    }
}
