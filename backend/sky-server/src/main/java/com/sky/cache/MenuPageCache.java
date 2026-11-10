package com.sky.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.vo.MenuItemVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Profile("!bootstrap")
public class MenuPageCache {

    private static final Logger log = LoggerFactory.getLogger(MenuPageCache.class);

    private static final TypeReference<PageResult<MenuItemVO>> PAGE_TYPE = new TypeReference<>() {
    };

    // 只能释放自己取得的锁，避免锁过期后误删其他请求新取得的锁。
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String prefix;
    private final String versionKey;

    public MenuPageCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${sky.menu-cache.prefix:campus:local:menu:catalog:v1}") String prefix) {

        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.prefix = prefix;
        this.versionKey = prefix + ":version";
    }

    /**
     * Redis 不可用时返回 null，调用方直接查数据库。
     * 版本键不设置 TTL，结果键会独立过期。
     */
    public String currentVersion() {
        try {
            String version = redisTemplate.opsForValue().get(versionKey);
            if (version != null) {
                return version;
            }

            String candidate = UUID.randomUUID().toString();
            redisTemplate.opsForValue().setIfAbsent(versionKey, candidate);

            // 多个请求可能同时初始化，以最终保存的版本为准。
            return redisTemplate.opsForValue().get(versionKey);
        } catch (DataAccessException exception) {
            log.warn("读取菜单缓存版本失败，改为查询数据库");
            return null;
        }
    }

    public String pageKey(String version, DishPageQueryDTO query) {
        String name = query.getName();
        if (name == null) {
            name = "";
        } else {
            name = name.trim();
        }

        String encodedName = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(name.getBytes(StandardCharsets.UTF_8));

        String status = query.getStatus() == null
                ? "all"
                : query.getStatus().toString();

        return prefix
                + ":data:" + version
                + ":user"
                + ":category:" + query.getCategoryId()
                + ":page:" + query.getPage()
                + ":size:" + query.getPageSize()
                + ":status:" + status
                + ":name:" + encodedName;
    }

    public PageResult<MenuItemVO> read(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }

            PageResult<MenuItemVO> page = objectMapper.readValue(json, PAGE_TYPE);

            if (page == null || page.total() < 0 || page.records() == null) {
                log.warn("菜单缓存结构不合法，改为查询数据库");
                return null;
            }

            return page;
        } catch (JsonProcessingException exception) {
            log.warn("菜单缓存反序列化失败，改为查询数据库");
            return null;
        } catch (DataAccessException exception) {
            log.warn("读取菜单缓存失败，改为查询数据库");
            return null;
        }
    }

    public void write(String key, PageResult<MenuItemVO> page) {
        long seconds;

        if (page.records().isEmpty()) {
            seconds = 10;
        } else {
            // 分散过期时间，避免大量分页键同时失效。
            seconds = ThreadLocalRandom.current().nextLong(50, 71);
        }

        try {
            String json = objectMapper.writeValueAsString(page);
            redisTemplate.opsForValue().set(
                    key, json, Duration.ofSeconds(seconds));
        } catch (JsonProcessingException exception) {
            log.warn("菜单结果序列化失败，本次跳过缓存");
        } catch (DataAccessException exception) {
            log.warn("写入菜单缓存失败，本次跳过缓存");
        }
    }

    /**
     * true 表示取得锁，false 表示其他请求正在回填，
     * null 表示 Redis 不可用。
     */
    public Boolean tryLock(String key, String owner) {
        try {
            return redisTemplate.opsForValue().setIfAbsent(
                    key + ":lock", owner, Duration.ofSeconds(5));
        } catch (DataAccessException exception) {
            log.warn("菜单缓存回填锁不可用，改为查询数据库");
            return null;
        }
    }

    public void unlock(String key, String owner) {
        try {
            redisTemplate.execute(
                    UNLOCK_SCRIPT, List.of(key + ":lock"), owner);
        } catch (DataAccessException exception) {
            log.warn("释放菜单缓存回填锁失败，等待锁自动过期");
        }
    }

    /**
     * 仅在真实发生写入后调用。
     * 数据库提交成功才切换版本，回滚时不会失效缓存。
     */
    public void invalidateAfterCommit() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("菜单缓存失效必须在写事务中注册");
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        rotateVersion();
                    }
                });
    }

    private void rotateVersion() {
        try {
            redisTemplate.opsForValue().set(
                    versionKey, UUID.randomUUID().toString());
        } catch (DataAccessException exception) {
            // 此时数据库已经提交，记录失效失败并由结果 TTL 兜底。
            log.warn("数据库已提交，但菜单缓存失效失败，请检查 Redis");
        }
    }
}