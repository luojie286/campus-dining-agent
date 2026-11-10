package com.sky.mapper;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/** 催单次数与最后催单时间。计数更新与订单状态检查放在同一事务里，避免并发绕过冷却。 */
@Repository
@Profile("!bootstrap")
public class OrderUrgeRepository {

    public record Urge(long orderId, int count, LocalDateTime lastAt) {
    }

    private static final RowMapper<Urge> MAPPER = (row, index) -> new Urge(
            row.getLong("order_id"),
            row.getInt("urge_count"),
            row.getObject("last_urge_at", LocalDateTime.class));

    private final JdbcTemplate jdbc;

    public OrderUrgeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Urge> findForUpdate(long orderId) {
        return jdbc.query("""
                SELECT order_id,urge_count,last_urge_at FROM order_urge
                WHERE order_id=? FOR UPDATE
                """, MAPPER, orderId).stream().findFirst();
    }

    /** 原子累加一次，返回累加后的总次数。 */
    public int increment(long orderId, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO order_urge(order_id,urge_count,last_urge_at) VALUES (?,1,?)
                ON DUPLICATE KEY UPDATE urge_count=urge_count+1,last_urge_at=?
                """, orderId, now, now);
        Integer count = jdbc.queryForObject(
                "SELECT urge_count FROM order_urge WHERE order_id=?", Integer.class, orderId);
        if (count == null) throw new IllegalStateException("催单记录写入失败");
        return count;
    }
}
