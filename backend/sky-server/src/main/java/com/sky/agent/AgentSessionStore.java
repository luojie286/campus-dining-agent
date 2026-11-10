package com.sky.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.exception.ServiceUnavailableException;
import com.sky.result.PageResult;
import com.sky.service.AuthenticatedPrincipalService;
import com.sky.vo.AgentMessageVO;
import com.sky.vo.AgentReplyVO;
import com.sky.vo.AgentSessionVO;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Redis 会话：键里带 role+id，读取他人 sessionId 天然读不到；TTL 24 小时，最多保留 30 条可见消息。
 * 会话只保存可见对话，不保存 token、密钥或模型隐藏推理。
 */
@Component
@Profile("!bootstrap")
public class AgentSessionStore {

    public record StoredMessage(String id, String role, String content,
                                OffsetDateTime createdAt, AgentReplyVO reply) {
    }

    public record StoredSession(String id, String title, OffsetDateTime createdAt,
                                OffsetDateTime updatedAt, List<StoredMessage> messages) {
    }

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Clock clock;
    private final AgentProperties properties;

    public AgentSessionStore(StringRedisTemplate redis, ObjectMapper json, Clock clock,
            AgentProperties properties) {
        this.redis = redis;
        this.json = json;
        this.clock = clock;
        this.properties = properties;
    }

    public StoredSession create(AuthenticatedPrincipalService.Principal principal) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        String sessionId = UUID.randomUUID().toString();
        StoredSession session = new StoredSession(sessionId, "新对话", now, now, List.of());
        save(principal, session);
        return session;
    }

    public Optional<StoredSession> load(AuthenticatedPrincipalService.Principal principal,
            String sessionId) {
        try {
            String stored = redis.opsForValue().get(sessionKey(principal, sessionId));
            if (stored == null) return Optional.empty();
            return Optional.of(json.readValue(stored, StoredSession.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("会话数据无法读取", exception);
        } catch (DataAccessException exception) {
            throw new ServiceUnavailableException("会话服务暂不可用，请稍后重试");
        }
    }

    public void save(AuthenticatedPrincipalService.Principal principal, StoredSession session) {
        List<StoredMessage> visible = session.messages();
        if (visible.size() > properties.maxVisibleMessages()) {
            visible = new ArrayList<>(visible.subList(
                    visible.size() - properties.maxVisibleMessages(), visible.size()));
        }
        StoredSession trimmed = new StoredSession(session.id(), session.title(),
                session.createdAt(), OffsetDateTime.now(clock), visible);
        try {
            Duration ttl = Duration.ofHours(properties.sessionTtlHours());
            redis.opsForValue().set(sessionKey(principal, session.id()), encode(trimmed), ttl);
            redis.opsForSet().add(indexKey(principal), session.id());
            redis.expire(indexKey(principal), ttl);
        } catch (DataAccessException exception) {
            throw new ServiceUnavailableException("会话服务暂不可用，请稍后重试");
        }
    }

    public void append(AuthenticatedPrincipalService.Principal principal, String sessionId,
            StoredMessage... messages) {
        StoredSession session = load(principal, sessionId)
                .orElseThrow(() -> new com.sky.exception.NotFoundException("会话不存在或已过期"));
        List<StoredMessage> updated = new ArrayList<>(session.messages());
        for (StoredMessage message : messages) {
            updated.add(message);
        }
        String title = session.title();
        if ("新对话".equals(title) && messages.length > 0) {
            String first = messages[0].content();
            title = first.length() > 20 ? first.substring(0, 20) : first;
        }
        save(principal, new StoredSession(session.id(), title,
                session.createdAt(), OffsetDateTime.now(clock), updated));
    }

    public PageResult<AgentSessionVO> page(AuthenticatedPrincipalService.Principal principal,
            int page, int pageSize) {
        Set<String> ids;
        try {
            ids = redis.opsForSet().members(indexKey(principal));
        } catch (DataAccessException exception) {
            throw new ServiceUnavailableException("会话服务暂不可用，请稍后重试");
        }
        if (ids == null) return new PageResult<>(0, List.of());

        List<AgentSessionVO> records = new ArrayList<>();
        for (String id : ids) {
            Optional<StoredSession> session = load(principal, id);
            if (session.isEmpty()) {
                // 惰性清理已经过期但还留在索引里的会话 ID。
                redis.opsForSet().remove(indexKey(principal), id);
                continue;
            }
            StoredSession current = session.get();
            records.add(new AgentSessionVO(current.id(), current.title(),
                    current.createdAt(), current.updatedAt()));
        }
        records.sort(Comparator.comparing(AgentSessionVO::updatedAt).reversed());
        return paginate(records, page, pageSize);
    }

    public PageResult<AgentMessageVO> messages(AuthenticatedPrincipalService.Principal principal,
            String sessionId, int page, int pageSize) {
        StoredSession session = load(principal, sessionId)
                .orElseThrow(() -> new com.sky.exception.NotFoundException("会话不存在或已过期"));
        List<AgentMessageVO> records = new ArrayList<>();
        for (StoredMessage message : session.messages()) {
            records.add(new AgentMessageVO(message.id(), message.role(), message.content(),
                    message.createdAt(), message.reply()));
        }
        return paginate(records, page, pageSize);
    }

    public void delete(AuthenticatedPrincipalService.Principal principal, String sessionId) {
        try {
            redis.delete(sessionKey(principal, sessionId));
            redis.opsForSet().remove(indexKey(principal), sessionId);
        } catch (DataAccessException exception) {
            throw new ServiceUnavailableException("会话服务暂不可用，请稍后重试");
        }
    }

    private <T> PageResult<T> paginate(List<T> records, int page, int pageSize) {
        long offset = (long) (page - 1) * pageSize;
        if (offset >= records.size()) return new PageResult<>(records.size(), List.of());
        int end = Math.min(records.size(), (int) offset + pageSize);
        List<T> slice = new ArrayList<>();
        for (int index = (int) offset; index < end; index++) {
            slice.add(records.get(index));
        }
        return new PageResult<>(records.size(), slice);
    }

    private String sessionKey(AuthenticatedPrincipalService.Principal principal, String sessionId) {
        return "agent:session:" + principal.role() + ":" + principal.id() + ":" + sessionId;
    }

    private String indexKey(AuthenticatedPrincipalService.Principal principal) {
        return "agent:session-index:" + principal.role() + ":" + principal.id();
    }

    private String encode(StoredSession session) {
        try {
            return json.writeValueAsString(session);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("会话数据无法序列化", exception);
        }
    }
}
