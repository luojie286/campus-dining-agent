package com.sky.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 内部清理任务：把过期的 Agent 待确认记录标记为已过期。
 * WebSocket 票据由 Redis TTL 自动过期，不需要扫描表；
 * 订单支付超时关单由 OrderTimeoutTask 处理，本任务不代替用户确认送达。
 */
@Component
@Profile("!bootstrap")
@ConditionalOnProperty(name = "sky.orders.housekeeping-task-enabled", havingValue = "true", matchIfMissing = true)
public class HousekeepingTask {

    private static final Logger log = LoggerFactory.getLogger(HousekeepingTask.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public HousekeepingTask(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void expirePendingActions() {
        int expired = jdbc.update("""
                UPDATE agent_action SET status=3
                WHERE status=0 AND expire_time<=?
                """, LocalDateTime.now(clock));
        if (expired > 0) {
            log.info("已标记 {} 条过期待确认操作", expired);
        }
    }
}
