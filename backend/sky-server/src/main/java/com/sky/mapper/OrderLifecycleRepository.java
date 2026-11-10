package com.sky.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.dto.InventoryStockUse;
import com.sky.exception.ConflictException;
import com.sky.vo.OrderVO;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("!bootstrap")
public class OrderLifecycleRepository {
    private static final String SELECT = """
            SELECT id,user_id,status,pay_status,payment_channel,payment_deadline,inventory_snapshot,events
            FROM orders WHERE id=?
            """;
    private static final RowMapper<State> STATE = (row, index) -> new State(
            row.getLong("id"), row.getLong("user_id"), row.getInt("status"), row.getInt("pay_status"),
            row.getString("payment_channel"), row.getObject("payment_deadline", LocalDateTime.class),
            row.getString("inventory_snapshot"), row.getString("events"));

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public OrderLifecycleRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<State> find(long id, Long userId, boolean lock) {
        String suffix = lock ? " FOR UPDATE" : "";
        if (userId == null) return jdbc.query(SELECT + suffix, STATE, id).stream().findFirst();
        return jdbc.query(SELECT + " AND user_id=?" + suffix, STATE, id, userId).stream().findFirst();
    }

    public List<Long> expiredMockOrders(LocalDateTime now) {
        return jdbc.queryForList("""
                SELECT id FROM orders
                WHERE status=1 AND pay_status=0 AND payment_channel='MOCK' AND payment_deadline<=?
                ORDER BY payment_deadline,id LIMIT 100
                """, Long.class, now);
    }

    public List<InventoryStockUse> stockFrom(State order) {
        try {
            List<InventoryStockUse> stock = json.readValue(order.inventory(), new TypeReference<>() {});
            if (stock == null || stock.isEmpty() || stock.stream().anyMatch(use ->
                    use == null || use.id() < 1 || use.quantity() < 1
                    || (!"dish".equals(use.kind()) && !"setmeal".equals(use.kind())))) {
                throw new IllegalStateException("订单库存快照不合法");
            }
            return stock.stream().sorted(Comparator.comparing(InventoryStockUse::kind)
                    .thenComparingLong(InventoryStockUse::id)).toList();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("订单库存快照无法读取", exception);
        }
    }

    public void lockStock(List<InventoryStockUse> stock) {
        for (InventoryStockUse item : stock) {
            jdbc.queryForList("SELECT id FROM " + stockTable(item) + " WHERE id=? FOR UPDATE",
                    Long.class, item.id());
        }
    }

    public void releaseStock(List<InventoryStockUse> stock) {
        for (InventoryStockUse item : stock) {
            if (jdbc.update("UPDATE " + stockTable(item) + " SET stock=stock+? WHERE id=?",
                    item.quantity(), item.id()) != 1) {
                throw new IllegalStateException("库存快照关联的商品不存在");
            }
        }
    }

    public void markMockPaid(State order, OffsetDateTime now) {
        int rows = jdbc.update("""
                UPDATE orders SET status=2,pay_status=1,checkout_time=?,events=?
                WHERE id=? AND status=1 AND pay_status=0 AND payment_channel='MOCK' AND payment_deadline>?
                """, now.toLocalDateTime(), append(order, "PREPARING", now, "模拟支付完成"),
                order.id(), now.toLocalDateTime());
        if (rows != 1) throw new ConflictException("订单已过期或状态已变化");
    }

    public void markCancelled(State order, OffsetDateTime now, String reason) {
        int rows = jdbc.update("""
                UPDATE orders SET status=6,cancel_time=?,cancel_reason=?,events=?
                WHERE id=? AND status=1 AND pay_status=0 AND payment_channel='MOCK'
                """, now.toLocalDateTime(), reason, append(order, "CANCELLED", now, reason), order.id());
        if (rows != 1) throw new ConflictException("订单状态已变化");
    }

    private String stockTable(InventoryStockUse use) {
        return switch (use.kind()) {
            case "dish" -> "dish";
            case "setmeal" -> "setmeal";
            default -> throw new IllegalStateException("库存类型不合法");
        };
    }

    private String append(State order, String status, OffsetDateTime now, String note) {
        try {
            List<OrderVO.Event> previous = json.readerFor(new TypeReference<List<OrderVO.Event>>() {})
                    .without(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                    .readValue(order.events());
            var events = new ArrayList<>(previous);
            events.add(new OrderVO.Event(status, now, note));
            return json.writeValueAsString(events);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("订单事件格式错误", exception);
        }
    }

    public record State(long id, long userId, int status, int payStatus, String channel,
                        LocalDateTime deadline, String inventory, String events) {
        public boolean unpaid() { return status == 1 && payStatus == 0; }
        public boolean mock() { return "MOCK".equals(channel); }
    }
}
