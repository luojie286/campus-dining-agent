package com.sky.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 本机开发允许本机前端与 Nginx 同源；生产部署按实际域名配置 sky.ws.allowed-origins 并使用 WSS。
 * 真正的身份边界是一次性票据，不是 Origin 头。
 */
@Configuration
@EnableWebSocket
@Profile("!bootstrap")
public class OrderWebSocketConfiguration implements WebSocketConfigurer {

    private final OrderWebSocketHandler handler;
    private final TicketHandshakeInterceptor interceptor;
    private final String allowedOrigins;

    public OrderWebSocketConfiguration(OrderWebSocketHandler handler,
            TicketHandshakeInterceptor interceptor,
            @Value("${sky.ws.allowed-origins:http://127.0.0.1:[*],http://localhost:[*]}") String allowedOrigins) {
        this.handler = handler;
        this.interceptor = interceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/orders")
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns(allowedOrigins.split(","));
    }
}
