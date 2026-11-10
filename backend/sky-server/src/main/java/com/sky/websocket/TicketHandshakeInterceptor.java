package com.sky.websocket;

import com.sky.service.AuthenticatedPrincipalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/** 握手时原子消费票据，并把可信身份放进会话属性，不接受客户端自填的 role/userId。 */
@Component
@Profile("!bootstrap")
public class TicketHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TicketHandshakeInterceptor.class);

    private final WsTicketService tickets;

    public TicketHandshakeInterceptor(WsTicketService tickets) {
        this.tickets = tickets;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String ticket = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams().getFirst("ticket");
        if (ticket == null || ticket.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            AuthenticatedPrincipalService.Principal principal = tickets.consume(ticket);
            attributes.put(OrderWebSocketHandler.ROLE, principal.role());
            attributes.put(OrderWebSocketHandler.USER_ID, principal.id());
            attributes.put(OrderWebSocketHandler.TOKEN, principal.token());
            attributes.put(OrderWebSocketHandler.EXPIRES_AT, principal.expiresAt());
            return true;
        } catch (RuntimeException exception) {
            log.info("订单通知握手被拒绝：{}", exception.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // 握手结果由连接的建立/关闭体现，这里不需要额外处理。
    }
}
