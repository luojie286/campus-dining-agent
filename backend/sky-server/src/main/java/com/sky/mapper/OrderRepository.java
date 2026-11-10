package com.sky.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.dto.InventoryStockUse;
import com.sky.dto.OrderPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.exception.ConflictException;
import com.sky.vo.OrderVO;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Profile("!bootstrap")
public class OrderRepository {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public OrderRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void deductStock(List<InventoryStockUse> stock) {
        for (InventoryStockUse item : stock) {
            String sql = switch (item.kind()) {
                case "dish" -> "UPDATE dish SET stock=stock-? WHERE id=? AND stock>=?";
                case "setmeal" -> "UPDATE setmeal SET stock=stock-? WHERE id=? AND stock>=?";
                default -> throw new IllegalStateException("库存类型不合法");
            };
            if (jdbc.update(sql, item.quantity(), item.id(), item.quantity()) != 1) {
                throw new ConflictException("商品库存不足，请重新确认购物车");
            }
        }
    }

    public long insert(OrderVO order, Long addressId, List<InventoryStockUse> stock) {
        String inventoryJson = encode(stock);
        String eventsJson = encode(order.events());
        var keys = new GeneratedKeyHolder();
        int inserted = jdbc.update(connection -> {
            var sql = connection.prepareStatement("""
                    INSERT INTO orders(number,user_id,address_book_id,fulfillment,order_time,payment_deadline,
                      amount,remark,phone,address,user_name,consignee,inventory_snapshot,events,status,pay_status,payment_channel)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,1,0,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            sql.setString(1, order.number());
            sql.setLong(2, order.userId());
            sql.setObject(3, addressId);
            sql.setString(4, order.fulfillment());
            sql.setObject(5, order.createdAt().toLocalDateTime());
            sql.setObject(6, order.paymentDeadline().toLocalDateTime());
            sql.setBigDecimal(7, order.amount());
            sql.setString(8, order.remark());
            sql.setString(9, order.phone());
            sql.setString(10, order.address());
            sql.setString(11, order.userName());
            sql.setString(12, order.recipient());
            sql.setString(13, inventoryJson);
            sql.setString(14, eventsJson);
            sql.setString(15, order.paymentChannel());
            return sql;
        }, keys);
        Number key = keys.getKey();
        if (inserted != 1 || key == null) throw new IllegalStateException("创建订单失败");
        return key.longValue();
    }

    public void insertLine(long orderId, OrderVO.Line line, String image) {
        boolean dish = ShoppingCartRepository.isDish(line.itemId());
        long rawId = ShoppingCartRepository.rawId(line.itemId());
        jdbc.update("""
                INSERT INTO order_detail(order_id,dish_id,setmeal_id,name,image,amount,number)
                VALUES (?,?,?,?,?,?,?)
                """, orderId, dish ? rawId : null, dish ? null : rawId,
                line.name(), image, line.price(), line.quantity());
    }

    private static final String SELECT_ORDERS = """
            SELECT id,number,user_id,user_name,status,pay_status,fulfillment,amount,order_time,
                   checkout_time,payment_deadline,consignee,phone,address,remark,events,payment_channel
            FROM orders
            """;

    private final RowMapper<OrderVO> header = (row, index) -> new OrderVO(
            row.getLong("id"), row.getString("number"), row.getLong("user_id"), row.getString("user_name"),
            OrderPageQueryDTO.State.fromCode(row.getInt("status")).name(),
            paymentStatus(row.getInt("pay_status")), row.getString("fulfillment"),
            row.getBigDecimal("amount"), time(row.getObject("order_time", LocalDateTime.class)),
            time(row.getObject("checkout_time", LocalDateTime.class)),
            time(row.getObject("payment_deadline", LocalDateTime.class)), row.getString("consignee"),
            row.getString("phone"), row.getString("address"), row.getString("remark"),
            List.of(), events(row.getString("events")), row.getString("payment_channel"));

    public Optional<OrderVO> findOwned(long userId, long id) {
        var records = jdbc.query(SELECT_ORDERS + " WHERE user_id=? AND id=?", header, userId, id);
        return withLines(records).stream().findFirst();
    }

    public PageResult<OrderVO> pageOwned(long userId, OrderPageQueryDTO query, LocalDate begin, LocalDate end) {
        StringBuilder where = new StringBuilder(" WHERE user_id=?");
        List<Object> values = new ArrayList<>();
        values.add(userId);
        if (query.getStatus() != null) {
            where.append(" AND status=?");
            values.add(query.getStatus().code());
        }
        if (query.getName() != null && !query.getName().isBlank()) {
            String pattern = "%" + query.getName().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            where.append(" AND (number LIKE ? ESCAPE '!' OR user_name LIKE ? ESCAPE '!')");
            values.add(pattern);
            values.add(pattern);
        }
        if (begin != null) {
            where.append(" AND order_time>=?");
            values.add(begin.atStartOfDay());
        }
        if (end != null && !end.equals(LocalDate.of(9999, 12, 31))) {
            where.append(" AND order_time<?");
            values.add(end.plusDays(1).atStartOfDay());
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM orders" + where, Long.class, values.toArray());
        long offset = (query.getPage() - 1L) * query.getPageSize();
        if (offset >= total) return new PageResult<>(total, List.of());

        values.add(query.getPageSize());
        values.add(offset);
        List<OrderVO> records = jdbc.query(SELECT_ORDERS + where
                + " ORDER BY order_time DESC,id DESC LIMIT ? OFFSET ?", header, values.toArray());
        return new PageResult<>(total, withLines(records));
    }

    private List<OrderVO> withLines(List<OrderVO> orders) {
        if (orders.isEmpty()) return List.of();
        List<Long> ids = orders.stream().map(OrderVO::id).toList();
        String slots = String.join(",", Collections.nCopies(ids.size(), "?"));
        Map<Long, List<OrderVO.Line>> linesByOrder = new HashMap<>();
        jdbc.query("""
                SELECT order_id,CASE WHEN dish_id IS NOT NULL THEN dish_id*2 ELSE setmeal_id*2+1 END AS item_id,
                       CASE WHEN dish_id IS NOT NULL THEN 'dish' ELSE 'setmeal' END AS kind,
                       name,amount,number FROM order_detail WHERE order_id IN (
                """ + slots + ") ORDER BY order_id,id", row -> {
            var line = new OrderVO.Line(row.getLong("item_id"), row.getString("name"),
                    row.getString("kind"), row.getBigDecimal("amount"), row.getInt("number"));
            linesByOrder.computeIfAbsent(row.getLong("order_id"), ignored -> new ArrayList<>()).add(line);
        }, ids.toArray());

        return orders.stream().map(order -> new OrderVO(order.id(), order.number(), order.userId(),
                order.userName(), order.status(), order.payStatus(), order.fulfillment(), order.amount(),
                order.createdAt(), order.paidAt(), order.paymentDeadline(), order.recipient(), order.phone(),
                order.address(), order.remark(), linesByOrder.getOrDefault(order.id(), List.of()),
                order.events(), order.paymentChannel())).toList();
    }

    public Optional<OrderVO> replay(long userId, String key, String hash) {
        var entries = jdbc.query("""
                SELECT request_hash,response_json FROM order_submit_replay
                WHERE user_id=? AND request_key=? AND expires_at > CURRENT_TIMESTAMP
                """, (row, index) -> new Replay(row.getString(1), row.getString(2)), userId, key);
        if (entries.isEmpty()) return Optional.empty();
        Replay previous = entries.getFirst();
        if (!previous.hash().equals(hash)) {
            throw new ConflictException("同一幂等键不能用于不同的下单请求");
        }
        try {
            OrderVO order = json.readerFor(OrderVO.class)
                    .without(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                    .readValue(previous.response());
            if (order.paymentChannel() == null) {
                // 迁移前只存在模拟支付订单，旧幂等快照也采用这个渠道。
                order = new OrderVO(order.id(), order.number(), order.userId(), order.userName(),
                        order.status(), order.payStatus(), order.fulfillment(), order.amount(),
                        order.createdAt(), order.paidAt(), order.paymentDeadline(), order.recipient(),
                        order.phone(), order.address(), order.remark(), order.lines(), order.events(), "MOCK");
            }
            return Optional.of(order);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("下单幂等记录无法读取", exception);
        }
    }

    public void saveReplay(long userId, String key, String hash, OrderVO order) {
        jdbc.update("DELETE FROM order_submit_replay WHERE user_id=? AND request_key=? AND expires_at<=CURRENT_TIMESTAMP",
                userId, key);
        jdbc.update("""
                INSERT INTO order_submit_replay(user_id,request_key,request_hash,response_json,expires_at)
                VALUES (?,?,?,?,CURRENT_TIMESTAMP + INTERVAL 24 HOUR)
                """, userId, key, hash, encode(order));
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("订单快照无法序列化", exception);
        }
    }

    private List<OrderVO.Event> events(String value) {
        try {
            return json.readerFor(new TypeReference<List<OrderVO.Event>>() {})
                    .without(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                    .readValue(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("订单事件无法读取", exception);
        }
    }

    private OffsetDateTime time(LocalDateTime value) {
        return value == null ? null : value.atZone(BUSINESS_ZONE).toOffsetDateTime();
    }

    private String paymentStatus(int code) {
        return switch (code) {
            case 0 -> "UNPAID";
            case 1 -> "PAID";
            case 2 -> "REFUNDED";
            default -> throw new IllegalStateException("支付状态不合法");
        };
    }

    private record Replay(String hash, String response) {}
}
