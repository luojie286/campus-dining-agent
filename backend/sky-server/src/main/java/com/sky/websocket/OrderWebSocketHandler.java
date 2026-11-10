package com.sky.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.service.AuthenticatedPrincipalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单通知连接管理。目标的过滤在服务端完成：
 * 运营收到来单/状态变更/催单，点餐用户只收到本人订单的状态变更。
 */
@Component
@Profile("!bootstrap")
public class OrderWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderWebSocketHandler.class);

    public static final String ROLE = "principalRole";
    public static final String USER_ID = "principalId";
    public static final String TOKEN = "principalToken";
    public static final String EXPIRES_AT = "principalExpiresAt";

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final AuthenticatedPrincipalService principals;
    private final ObjectMapper json;
    private final Clock clock;

    public OrderWebSocketHandler(AuthenticatedPrincipalService principals,
            ObjectMapper json, Clock clock) {
        this.principals = principals;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        String role = (String) attributes.get(ROLE);
        Long userId = (Long) attributes.get(USER_ID);
        String token = (String) attributes.get(TOKEN);
        Instant expiresAt = (Instant) attributes.get(EXPIRES_AT);

        if (role == null || userId == null || token == null || expiresAt == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessions.put(session.getId(), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("订单通知连接异常，session={}", session.getId(), exception);
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    public void broadcast(OrderEventPublisher.OrderEvent event, long ownerId) {
        String payload;
        try {
            payload = json.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            log.warn("订单通知序列化失败，type={}", event.type(), exception);
            return;
        }

        for (WebSocketSession session : sessions.values()) {
            Instant expiresAt = (Instant) session.getAttributes().get(EXPIRES_AT);
            if (expiresAt == null || !expiresAt.isAfter(clock.instant())) {
                closeQuietly(session, CloseStatus.POLICY_VIOLATION);
                continue;
            }
            if (!shouldReceive(session, event, ownerId)) continue;
            // WebSocketSession 不支持并发写，同一个会话串行发送。
            synchronized (session) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(payload));
                    }
                } catch (IOException exception) {
                    log.warn("订单通知发送失败，session={}", session.getId(), exception);
                    closeQuietly(session, CloseStatus.SESSION_NOT_RELIABLE);
                }
            }
        }
    }

    /** 握手检查不是终点：每 30 秒复验凭证有效期和账号状态，停用或过期立即断开。 */
    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void verifySessions() {
        Instant now = clock.instant();
        for (WebSocketSession session : sessions.values()) {
            Instant expiresAt = (Instant) session.getAttributes().get(EXPIRES_AT);
            if (expiresAt == null || !expiresAt.isAfter(now)) {
                closeQuietly(session, CloseStatus.SESSION_NOT_RELIABLE);
                continue;
            }
            try {
                principals.require((String) session.getAttributes().get(TOKEN));
            } catch (RuntimeException exception) {
                closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            }
        }
    }

    private boolean shouldReceive(WebSocketSession session,
            OrderEventPublisher.OrderEvent event, long ownerId) {
        Map<String, Object> attributes = session.getAttributes();
        String role = (String) attributes.get(ROLE);
        Long userId = (Long) attributes.get(USER_ID);

        if ("admin".equals(role)) {
            // 运营端接收来单提醒、客户催单和全部订单状态变更。
            return true;
        }
        if ("user".equals(role) && "ORDER_STATUS_CHANGED".equals(event.type())) {
            return userId != null && userId == ownerId;
        }
        return false;
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        try {
            if (session.isOpen()) session.close(status);
        } catch (IOException exception) {
            log.warn("关闭订单通知连接失败，session={}", session.getId(), exception);
        }
    }
}
