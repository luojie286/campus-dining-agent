package com.sky.mapper;

import com.sky.vo.OrderVO;
import com.sky.vo.RefundVO;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!bootstrap")
public class OrderRefundRepository {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;

    public OrderRefundRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insertMockRefund(OrderVO order, String reason, OffsetDateTime now) {
        String number = "TK" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
                INSERT INTO order_refund(order_id,refund_no,channel,state,amount,reason,requested_at,refunded_at)
                VALUES (?,?,'MOCK','SUCCESS',?,?,?,?)
                """, order.id(), number, order.amount(), reason, now.toLocalDateTime(), now.toLocalDateTime());
    }

    public Optional<RefundVO> find(long id) {
        return jdbc.query("""
                SELECT refund_no,order_id,channel,state,amount,reason,requested_at,refunded_at
                FROM order_refund WHERE order_id=?
                """, (row, index) -> new RefundVO(row.getString("refund_no"), row.getLong("order_id"),
                row.getString("channel"), row.getString("state"), row.getBigDecimal("amount"),
                row.getString("reason"), time(row.getObject("requested_at", LocalDateTime.class)),
                time(row.getObject("refunded_at", LocalDateTime.class))), id).stream().findFirst();
    }

    private OffsetDateTime time(LocalDateTime value) {
        return value == null ? null : value.atZone(BUSINESS_ZONE).toOffsetDateTime();
    }
}
