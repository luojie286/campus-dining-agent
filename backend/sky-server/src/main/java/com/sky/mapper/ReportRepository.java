package com.sky.mapper;

import com.sky.vo.WorkbenchVO;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计查询的唯一数据库入口。所有范围都是半开区间 [from, to)，
 * 由 ReportService 按 Asia/Shanghai 业务日换算后传入，避免结束日用 23:59:59 拼接。
 * 口径解释放在 Service，这里只做参数化查询。
 */
@Repository
@Profile("!bootstrap")
public class ReportRepository {

    /** 支付订单条件：含后来退款的历史支付，退款不抹掉历史支付事实。 */
    private static final String PAID = "checkout_time>=? AND checkout_time<? AND pay_status IN (1,2)";
    private static final String CREATED = "order_time>=? AND order_time<?";

    public record DailyAmount(LocalDate date, BigDecimal amount, int count) {
    }

    public record DailyCount(LocalDate date, int value) {
    }

    public record SalesRow(long itemId, String name, int quantity, BigDecimal price,
                           LocalDateTime paidAt, long detailId) {
    }

    public record ExportOrderRow(String number, LocalDateTime createdAt, int status,
                                 int payStatus, String channel, BigDecimal amount,
                                 String userName, String fulfillment, String remark) {
    }

    private final JdbcTemplate jdbc;

    public ReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BigDecimal sumPaidAmount(LocalDateTime from, LocalDateTime to) {
        BigDecimal value = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM orders WHERE " + PAID,
                BigDecimal.class, from, to);
        return value == null ? BigDecimal.ZERO : value;
    }

    public int countPaidOrders(LocalDateTime from, LocalDateTime to) {
        return count("SELECT COUNT(*) FROM orders WHERE " + PAID, from, to);
    }

    public List<DailyAmount> dailyPaid(LocalDateTime from, LocalDateTime to) {
        return jdbc.query("""
                SELECT DATE(checkout_time) AS day,COALESCE(SUM(amount),0) AS amount,COUNT(*) AS amount_count
                FROM orders WHERE checkout_time>=? AND checkout_time<? AND pay_status IN (1,2)
                GROUP BY DATE(checkout_time) ORDER BY day
                """, (row, index) -> new DailyAmount(day(row), row.getBigDecimal("amount"),
                row.getInt("amount_count")), from, to);
    }

    public BigDecimal sumRefundAmount(LocalDateTime from, LocalDateTime to) {
        BigDecimal value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM order_refund
                WHERE state='SUCCESS' AND refunded_at>=? AND refunded_at<?
                """, BigDecimal.class, from, to);
        return value == null ? BigDecimal.ZERO : value;
    }

    public List<DailyAmount> dailyRefund(LocalDateTime from, LocalDateTime to) {
        return jdbc.query("""
                SELECT DATE(refunded_at) AS day,COALESCE(SUM(amount),0) AS amount,COUNT(*) AS amount_count
                FROM order_refund WHERE state='SUCCESS' AND refunded_at>=? AND refunded_at<?
                GROUP BY DATE(refunded_at) ORDER BY day
                """, (row, index) -> new DailyAmount(day(row), row.getBigDecimal("amount"),
                row.getInt("amount_count")), from, to);
    }

    public int countUsersBefore(LocalDateTime to) {
        return count("SELECT COUNT(*) FROM `user` WHERE create_time<?", to);
    }

    public int countNewUsers(LocalDateTime from, LocalDateTime to) {
        return count("SELECT COUNT(*) FROM `user` WHERE create_time>=? AND create_time<?", from, to);
    }

    public int countOrderingUsers(LocalDateTime from, LocalDateTime to) {
        return count("SELECT COUNT(DISTINCT user_id) FROM orders WHERE " + CREATED, from, to);
    }

    public int countPayingUsers(LocalDateTime from, LocalDateTime to) {
        return count("SELECT COUNT(DISTINCT user_id) FROM orders WHERE " + PAID, from, to);
    }

    public List<DailyCount> dailyNewUsers(LocalDateTime from, LocalDateTime to) {
        return dailyCount("""
                SELECT DATE(create_time) AS day,COUNT(*) AS amount FROM `user`
                WHERE create_time>=? AND create_time<? GROUP BY DATE(create_time) ORDER BY day
                """, from, to);
    }

    public List<DailyCount> dailyOrderingUsers(LocalDateTime from, LocalDateTime to) {
        return dailyCount("""
                SELECT DATE(order_time) AS day,COUNT(DISTINCT user_id) AS amount FROM orders
                WHERE order_time>=? AND order_time<? GROUP BY DATE(order_time) ORDER BY day
                """, from, to);
    }

    public List<DailyCount> dailyPayingUsers(LocalDateTime from, LocalDateTime to) {
        return dailyCount("""
                SELECT DATE(checkout_time) AS day,COUNT(DISTINCT user_id) AS amount FROM orders
                WHERE checkout_time>=? AND checkout_time<? AND pay_status IN (1,2)
                GROUP BY DATE(checkout_time) ORDER BY day
                """, from, to);
    }

    public int countCreatedOrders(LocalDateTime from, LocalDateTime to) {
        return count("SELECT COUNT(*) FROM orders WHERE " + CREATED, from, to);
    }

    /** 创建范围内当前已付、未取消、未退款的订单。 */
    public int countValidOrders(LocalDateTime from, LocalDateTime to) {
        return count("SELECT COUNT(*) FROM orders WHERE " + CREATED + " AND pay_status=1 AND status<>6",
                from, to);
    }

    public int countCompletedOrders(LocalDateTime from, LocalDateTime to) {
        return count("SELECT COUNT(*) FROM orders WHERE " + CREATED + " AND status=5", from, to);
    }

    public int countCancelledOrders(LocalDateTime from, LocalDateTime to) {
        return count("SELECT COUNT(*) FROM orders WHERE " + CREATED + " AND status=6", from, to);
    }

    public List<DailyCount> dailyCreatedOrders(LocalDateTime from, LocalDateTime to) {
        return dailyCount("""
                SELECT DATE(order_time) AS day,COUNT(*) AS amount FROM orders
                WHERE order_time>=? AND order_time<? GROUP BY DATE(order_time) ORDER BY day
                """, from, to);
    }

    public List<DailyCount> dailyPaidOrders(LocalDateTime from, LocalDateTime to) {
        return dailyCount("""
                SELECT DATE(checkout_time) AS day,COUNT(*) AS amount FROM orders
                WHERE checkout_time>=? AND checkout_time<? AND pay_status IN (1,2)
                GROUP BY DATE(checkout_time) ORDER BY day
                """, from, to);
    }

    public List<DailyCount> dailyValidOrders(LocalDateTime from, LocalDateTime to) {
        return dailyOrderCount(from, to, "pay_status=1 AND status<>6");
    }

    public List<DailyCount> dailyCompletedOrders(LocalDateTime from, LocalDateTime to) {
        return dailyOrderCount(from, to, "status=5");
    }

    public List<DailyCount> dailyCancelledOrders(LocalDateTime from, LocalDateTime to) {
        return dailyOrderCount(from, to, "status=6");
    }

    /** 按创建日期和当前状态统计，返回状态码 -> 数量。缺的状态由 Service 补 0。 */
    public Map<Integer, Integer> statusCounts(LocalDateTime from, LocalDateTime to) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT status,COUNT(*) AS amount FROM orders
                WHERE order_time>=? AND order_time<? GROUP BY status
                """, from, to);
        for (Map<String, Object> row : rows) {
            counts.put(number(row, "status"), number(row, "amount"));
        }
        return counts;
    }

    /** 工作台待办：全部日期的未完结订单，不做近 7 天过滤。 */
    public Map<Integer, Integer> pendingCounts() {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT status,COUNT(*) AS amount FROM orders
                WHERE status NOT IN (5,6) GROUP BY status
                """);
        for (Map<String, Object> row : rows) {
            counts.put(number(row, "status"), number(row, "amount"));
        }
        return counts;
    }

    public WorkbenchVO.StockCounts dishCounts() {
        return stockCounts("dish");
    }

    public WorkbenchVO.StockCounts setmealCounts() {
        return stockCounts("setmeal");
    }

    /**
     * 有效成交明细：付款时间在范围内，当前未取消且未退款。
     * 套餐按套餐自身一行计，不把组成菜品再次计入销量。
     */
    public List<SalesRow> salesRows(LocalDateTime from, LocalDateTime to, String kind, int limit) {
        String kindFilter = switch (kind == null ? "" : kind) {
            case "dish" -> " AND d.dish_id IS NOT NULL";
            case "setmeal" -> " AND d.setmeal_id IS NOT NULL";
            default -> "";
        };
        String sql = """
                SELECT d.id AS detail_id,
                       CASE WHEN d.dish_id IS NOT NULL THEN d.dish_id*2 ELSE d.setmeal_id*2+1 END AS item_id,
                       d.name,d.amount,d.number,o.checkout_time
                FROM order_detail d JOIN orders o ON o.id=d.order_id
                WHERE o.checkout_time>=? AND o.checkout_time<? AND o.pay_status=1 AND o.status<>6
                """ + kindFilter + " ORDER BY o.checkout_time,d.id LIMIT ?";
        return jdbc.query(sql, (row, index) -> new SalesRow(
                row.getLong("item_id"), row.getString("name"), row.getInt("number"),
                row.getBigDecimal("amount"),
                row.getObject("checkout_time", LocalDateTime.class),
                row.getLong("detail_id")), from, to, limit);
    }

    /** 导出的订单明细一订单一行；刻意不取手机号、详细地址和收餐人电话。 */
    public List<ExportOrderRow> exportOrders(LocalDateTime from, LocalDateTime to, int limit) {
        return jdbc.query("""
                SELECT number,order_time,status,pay_status,payment_channel,amount,
                       user_name,fulfillment,remark
                FROM orders WHERE order_time>=? AND order_time<?
                ORDER BY order_time,id LIMIT ?
                """, (row, index) -> new ExportOrderRow(
                row.getString("number"),
                row.getObject("order_time", LocalDateTime.class),
                row.getInt("status"), row.getInt("pay_status"),
                row.getString("payment_channel"), row.getBigDecimal("amount"),
                row.getString("user_name"), row.getString("fulfillment"),
                row.getString("remark")), from, to, limit);
    }

    private List<DailyCount> dailyOrderCount(LocalDateTime from, LocalDateTime to,
            String predicate) {
        // predicate 只来自本类常量，不接收客户端字符串。
        return dailyCount("SELECT DATE(order_time) AS day,COUNT(*) AS amount FROM orders WHERE "
                + CREATED + " AND " + predicate + " GROUP BY DATE(order_time) ORDER BY day", from, to);
    }

    private List<DailyCount> dailyCount(String sql, Object... values) {
        return jdbc.query(sql, (row, index) -> new DailyCount(day(row), row.getInt("amount")), values);
    }

    private WorkbenchVO.StockCounts stockCounts(String table) {
        // table 只来自 dishCounts()/setmealCounts() 的常量，不接受外部参数。
        // 空表时 SUM 为 NULL，getInt 会返回 0，正好表示没有上架/下架商品。
        return jdbc.queryForObject("""
                SELECT SUM(CASE WHEN status=1 THEN 1 ELSE 0 END) AS on_sale,
                       SUM(CASE WHEN status<>1 THEN 1 ELSE 0 END) AS off_sale
                FROM """ + table, (row, index) -> new WorkbenchVO.StockCounts(
                row.getInt("on_sale"), row.getInt("off_sale")));
    }

    private int count(String sql, Object... values) {
        Integer value = jdbc.queryForObject(sql, Integer.class, values);
        return value == null ? 0 : value;
    }

    private int number(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private LocalDate day(java.sql.ResultSet row) throws java.sql.SQLException {
        Date date = row.getDate("day");
        return date.toLocalDate();
    }
}
