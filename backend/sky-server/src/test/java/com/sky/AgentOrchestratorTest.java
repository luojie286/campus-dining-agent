package com.sky;

import com.sky.agent.AgentModelClient;
import com.sky.agent.AgentOrchestrator;
import com.sky.agent.AgentProperties;
import com.sky.agent.AgentSessionStore;
import com.sky.agent.AgentToolRegistry;
import com.sky.agent.AgentToolResult;
import com.sky.exception.ServiceUnavailableException;
import com.sky.service.AuthenticatedPrincipalService;
import com.sky.vo.AgentReplyVO;
import com.sky.vo.MenuItemVO;
import com.sky.vo.ToolTraceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 工具循环、预算、推荐 ID 校验和模型不可用路径，不访问真实模型或数据库。 */
class AgentOrchestratorTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T04:00:00Z"), BUSINESS_ZONE);
    private final AuthenticatedPrincipalService.Principal principal =
            new AuthenticatedPrincipalService.Principal("user", 9L, "token",
                    Instant.parse("2026-11-10T05:00:00Z"));

    private AgentModelClient model;
    private AgentToolRegistry registry;
    private AgentSessionStore sessions;
    private AgentProperties properties;
    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        model = mock(AgentModelClient.class);
        registry = mock(AgentToolRegistry.class);
        sessions = mock(AgentSessionStore.class);
        properties = mock(AgentProperties.class);
        orchestrator = new AgentOrchestrator(model, registry, sessions, properties, clock);

        when(properties.configured()).thenReturn(true);
        when(properties.totalBudgetMs()).thenReturn(15000L);
        when(properties.maxModelCalls()).thenReturn(5);
        when(properties.maxToolCalls()).thenReturn(8);
        when(properties.maxContextChars()).thenReturn(32000);
        when(properties.maxToolResultChars()).thenReturn(8000);
        when(registry.definitionsFor("user")).thenReturn(List.of());

        OffsetDateTime now = OffsetDateTime.now(clock);
        AgentSessionStore.StoredSession session =
                new AgentSessionStore.StoredSession("session-1", "新对话", now, now, List.of());
        when(sessions.create(principal)).thenReturn(session);
        when(sessions.load(principal, "session-1")).thenReturn(Optional.of(session));
    }

    @Test
    void toolLoopFeedsObservationBackToTheModel() {
        MenuItemVO item = menuItem(2L);
        when(model.complete(anyList(), anyList()))
                .thenReturn(new AgentModelClient.ModelResponse("",
                        List.of(new AgentModelClient.ToolCall("call-1", "searchDish", "{}"))))
                .thenReturn(new AgentModelClient.ModelResponse("推荐 [ID:2] 这份套餐。", List.of()));
        when(registry.execute("searchDish", "{}", principal, "session-1"))
                .thenReturn(AgentToolResult.success("searchDish", "返回 1 个候选",
                        "[ID:2] 套餐，¥20.00", List.of(item), List.of(), null, null));

        AgentReplyVO reply = orchestrator.chat(principal, "", "推荐午餐");

        assertEquals("session-1", reply.sessionId());
        assertTrue(reply.content().contains("[ID:2]"));
        assertEquals(1, reply.items().size());
        assertTrue(reply.traces().stream().anyMatch(trace ->
                "searchDish".equals(trace.name()) && "success".equals(trace.state())));
        verify(model, org.mockito.Mockito.times(2)).complete(anyList(), anyList());
    }

    @Test
    void unverifiedProductReferenceIsRemoved() {
        when(model.complete(anyList(), anyList()))
                .thenReturn(new AgentModelClient.ModelResponse("推荐 [ID:999] 这个不存在的套餐。", List.of()));

        AgentReplyVO reply = orchestrator.chat(principal, "", "随便推荐");

        assertTrue(reply.content().contains("[ID:未验证]"));
        assertTrue(reply.traces().stream().anyMatch(trace ->
                "outputCheck".equals(trace.name()) && "failed".equals(trace.state())));
    }

    @Test
    void unavailableModelReturnsReadableFailureWithoutFakeSuccess() {
        when(model.complete(anyList(), anyList()))
                .thenThrow(new ServiceUnavailableException("模型服务暂不可用"));

        AgentReplyVO reply = orchestrator.chat(principal, "", "查订单");

        assertTrue(reply.content().contains("模型暂时不可用"));
        assertTrue(reply.traces().stream().allMatch(trace -> "failed".equals(trace.state())));
    }

    @Test
    void missingConfigurationFailsFast() {
        when(properties.configured()).thenReturn(false);

        assertThrows(ServiceUnavailableException.class,
                () -> orchestrator.chat(principal, "", "查订单"));
        verify(model, never()).complete(anyList(), anyList());
    }

    @Test
    void exhaustedBudgetDoesNotClaimSuccess() {
        when(properties.maxModelCalls()).thenReturn(1);
        when(model.complete(anyList(), anyList()))
                .thenReturn(new AgentModelClient.ModelResponse("",
                        List.of(new AgentModelClient.ToolCall("call-1", "searchDish", "{}"))));
        when(registry.execute(eq("searchDish"), any(), eq(principal), any()))
                .thenReturn(AgentToolResult.success("searchDish", "已查询", "候选文本"));

        AgentReplyVO reply = orchestrator.chat(principal, "", "查菜单");

        assertTrue(reply.content().contains("未能在预算内生成完整回答"));
        assertTrue(reply.traces().stream().anyMatch(trace -> "budget".equals(trace.name())));
    }

    private MenuItemVO menuItem(long id) {
        return new MenuItemVO(id, "setmeal", 1L, "套餐", new BigDecimal("20.00"),
                "", 1, 5, List.of("不辣"), "chicken", null, List.of(), 5, List.of());
    }
}
