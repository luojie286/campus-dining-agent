package com.sky.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.exception.ServiceUnavailableException;
import com.sky.exception.UnauthorizedException;
import com.sky.service.AuthenticatedPrincipalService;
import com.sky.vo.WsTicketVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

/**
 * 单次连接票据：随机生成、Redis 保存 30 秒、GET+DEL 原子消费。
 * 浏览器握手不能自定义 token 头，所以服务端只把票据放进查询串，真实凭证留在 Redis 里。
 */
@Component
@Profile("!bootstrap")
public class WsTicketService {

    private static final Duration TTL = Duration.ofSeconds(30);

    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('get', KEYS[1])
            if value then redis.call('del', KEYS[1]) end
            return value
            """, String.class);

    private record Payload(String role, long id, String token, long expiresAtEpochSecond) {
    }

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Clock clock;
    private final AuthenticatedPrincipalService principalService;
    private final String prefix;
    private final SecureRandom random = new SecureRandom();

    public WsTicketService(StringRedisTemplate redis, ObjectMapper json, Clock clock,
            AuthenticatedPrincipalService principalService,
            @Value("${sky.ws.ticket-prefix:campus:local:ws:ticket}") String prefix) {
        this.redis = redis;
        this.json = json;
        this.clock = clock;
        this.principalService = principalService;
        this.prefix = prefix;
    }

    public WsTicketVO issue(AuthenticatedPrincipalService.Principal principal) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long expiresAtEpochSecond = clock.instant().plus(TTL).getEpochSecond();
        Payload payload = new Payload(principal.role(), principal.id(), principal.token(),
                expiresAtEpochSecond);

        try {
            redis.opsForValue().set(prefix + ":" + ticket, encode(payload), TTL);
        } catch (DataAccessException exception) {
            throw new ServiceUnavailableException("通知服务暂不可用，请稍后重试");
        }

        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(expiresAtEpochSecond), clock.getZone());
        return new WsTicketVO(ticket, expiresAt);
    }

    /** 消费后再次复验凭证和账号状态，避免票据签发后账号停用或凭证过期。 */
    public AuthenticatedPrincipalService.Principal consume(String ticket) {
        if (ticket == null || ticket.isBlank() || ticket.length() > 256) {
            throw new UnauthorizedException("连接票据无效");
        }

        String stored;
        try {
            stored = redis.execute(CONSUME_SCRIPT, List.of(prefix + ":" + ticket));
        } catch (DataAccessException exception) {
            throw new ServiceUnavailableException("通知服务暂不可用，请稍后重试");
        }
        if (stored == null) {
            throw new UnauthorizedException("连接票据无效或已被使用");
        }

        Payload payload;
        try {
            payload = json.readValue(stored, Payload.class);
        } catch (JsonProcessingException exception) {
            throw new UnauthorizedException("连接票据无效");
        }
        if (payload.expiresAtEpochSecond() <= clock.instant().getEpochSecond()) {
            throw new UnauthorizedException("连接票据已过期");
        }

        var principal = principalService.require(payload.token());
        if (principal.id() != payload.id() || !principal.role().equals(payload.role())) {
            throw new UnauthorizedException("连接票据身份不一致");
        }
        return principal;
    }

    private String encode(Payload payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("连接票据无法序列化", exception);
        }
    }
}
