package com.sky;

import com.sky.mapper.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_REPORT_DB_TEST", matches = "true")
class ReportDatabaseTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ReportRepository reports;

    @Test
    void refundedOrderStillCountsAsHistoricalPaymentButNotAsValidSale() {
        LocalDateTime from = LocalDateTime.of(2001, 1, 2, 0, 0);
        long valid = order(5, 1, 12, from.plusHours(1));
        long cancelled = order(6, 2, 18, from.plusHours(2));
        jdbc.update("""
                INSERT INTO order_detail(order_id,dish_id,name,amount,number)
                VALUES (?,1,'测试商品',12,1),(?,1,'退款商品',18,1)
                """, valid, cancelled);
        jdbc.update("""
                INSERT INTO order_refund(order_id,refund_no,channel,state,amount,reason,requested_at,refunded_at)
                VALUES (?,?,'MOCK','SUCCESS',18,'测试退款',?,?)
                """, cancelled, UUID.randomUUID().toString(), from.plusDays(1), from.plusDays(1));
        assertThat(reports.sumPaidAmount(from, from.plusDays(1))).isEqualByComparingTo("30.00");
        assertThat(reports.countPaidOrders(from, from.plusDays(1))).isEqualTo(2);
        assertThat(reports.sumRefundAmount(from, from.plusDays(1))).isEqualByComparingTo("0.00");
        assertThat(reports.sumRefundAmount(from.plusDays(1), from.plusDays(2))).isEqualByComparingTo("18.00");
        assertThat(reports.salesRows(from, from.plusDays(1), "dish", 10))
                .singleElement().satisfies(row -> assertThat(row.name()).isEqualTo("测试商品"));
    }

    private long order(int status, int payStatus, int amount, LocalDateTime time) {
        jdbc.update("""
                INSERT INTO orders(number,status,user_id,fulfillment,order_time,payment_deadline,checkout_time,
                  pay_status,amount,phone,address,user_name,consignee,inventory_snapshot,events)
                VALUES(?,?,1,'DELIVERY',?,?,?,?,?,'','','测试','测试','[]','[]')
                """, UUID.randomUUID().toString(), status, time, time.plusMinutes(15), time, payStatus, amount);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
