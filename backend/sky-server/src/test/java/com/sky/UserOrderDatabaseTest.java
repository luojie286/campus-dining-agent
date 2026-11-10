package com.sky;

import com.sky.util.JwtUtil;
import com.sky.context.BaseContext;
import com.sky.exception.ConflictException;
import com.sky.service.AdminOrderService;
import com.sky.service.SetmealService;
import com.sky.task.OrderTimeoutTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_ORDER_DB_TEST", matches = "true")
class UserOrderDatabaseTest {
    private static final String SUBMIT = "/api/user/order/submit";
    private static final String KEY = "integration-order-0001";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtUtil jwt;
    @Autowired private AdminOrderService adminOrders;
    @Autowired private SetmealService setmeals;
    @Autowired private OrderTimeoutTask timeoutTask;
    private long user;
    private long dish;
    private long meal;
    private long address;
    private String token;

    @BeforeEach
    void setup() {
        user = newUser();
        token = jwt.createToken(user, "user");
        String name = "test-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO shop(id,name,status,opening_hours,pickup_point)
                VALUES(1,'测试食堂',1,'','自提点') ON DUPLICATE KEY UPDATE status=1
                """);
        jdbc.update("INSERT INTO category(type,name,status) VALUES (1,?,1)", name);
        long dishCategory = lastId();
        jdbc.update("INSERT INTO category(type,name,status) VALUES (2,?,1)", name + "-meal");
        long mealCategory = lastId();
        jdbc.update("INSERT INTO dish(category_id,name,price,status,stock) VALUES (?,?,10,1,5)", dishCategory, name);
        dish = lastId();
        jdbc.update("INSERT INTO setmeal(category_id,name,price,status,stock) VALUES (?,?,18,1,4)", mealCategory, name);
        meal = lastId();
        jdbc.update("INSERT INTO setmeal_dish(setmeal_id,dish_id,name,price,copies) VALUES (?,?,?,?,2)", meal, dish, name, 10);
        address = newAddress(user);
        jdbc.update("INSERT INTO shopping_cart(user_id,dish_id,number,name,amount) VALUES (?,?,1,?,1)", user, dish, name);
        jdbc.update("INSERT INTO shopping_cart(user_id,setmeal_id,number,name,amount) VALUES (?,?,1,?,1)", user, meal, name);
    }

    @Test
    void reservesActualStockAndReplaysWithoutDuplicatingOrder() throws Exception {
        submit(address, "", 200);
        long orderId = jdbc.queryForObject("SELECT id FROM orders WHERE user_id=?", Long.class, user);
        assertThat(jdbc.queryForObject("SELECT stock FROM dish WHERE id=?", Integer.class, dish)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT stock FROM setmeal WHERE id=?", Integer.class, meal)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT amount FROM orders WHERE id=?", java.math.BigDecimal.class, orderId))
                .isEqualByComparingTo("28.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shopping_cart WHERE user_id=?", Integer.class, user)).isZero();

        submit(address, "", 200);
        submit(address, "不同备注", 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=?", Integer.class, user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT stock FROM dish WHERE id=?", Integer.class, dish)).isEqualTo(2);

        mvc.perform(get("/api/user/order/" + orderId).header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNPAID"))
                .andExpect(jsonPath("$.data.lines.length()").value(2));
        mvc.perform(get("/api/user/order/" + orderId).header("token", jwt.createToken(newUser(), "user")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsForeignAddressOrInsufficientStockWithoutMutatingCart() throws Exception {
        submit(newAddress(newUser()), "", 404);
        jdbc.update("UPDATE dish SET stock=2 WHERE id=?", dish);
        submit(address, "", 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=?", Integer.class, user)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shopping_cart WHERE user_id=?", Integer.class, user)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT stock FROM setmeal WHERE id=?", Integer.class, meal)).isEqualTo(4);
    }

    @Test
    void mockPaymentIsRepeatableAndDoesNotDeductTwice() throws Exception {
        submit(address, "", 200);
        long id = ownOrderId();
        String path = "/api/user/order/" + id;
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(path + "/pay").header("token", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PREPARING"))
                    .andExpect(jsonPath("$.data.paymentChannel").value("MOCK"))
                    .andExpect(jsonPath("$.data.events.length()").value(2));
        }
        mvc.perform(get(path + "/payment").header("token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("SUCCESS"));
        mvc.perform(post(path + "/cancel").header("token", token)).andExpect(status().isConflict());
        assertThat(stock("dish", dish)).isEqualTo(2);
        assertThat(stock("setmeal", meal)).isEqualTo(3);
    }

    @Test
    void ownerCanCancelBeforeDeadlineWithoutReturningStockTwice() throws Exception {
        submit(address, "", 200);
        String path = "/api/user/order/" + ownOrderId() + "/cancel";
        String other = jwt.createToken(newUser(), "user");
        mvc.perform(post(path).header("token", other)).andExpect(status().isNotFound());
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(path).header("token", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.data.events.length()").value(2));
        }
        assertThat(stock("dish", dish)).isEqualTo(5);
        assertThat(stock("setmeal", meal)).isEqualTo(4);
    }

    @Test
    void expiredMockOrderClosesOnceAndWechatOrderWaitsForConfirmation() throws Exception {
        submit(address, "", 200);
        long id = ownOrderId();
        String path = "/api/user/order/" + id;
        jdbc.update("UPDATE orders SET payment_deadline='2000-01-01 00:00:00', payment_channel='WECHAT' WHERE id=?", id);
        timeoutTask.closeExpiredOrders();
        assertThat(stock("dish", dish)).isEqualTo(2);
        mvc.perform(post(path + "/pay").header("token", token)).andExpect(status().isConflict());
        mvc.perform(post(path + "/cancel").header("token", token)).andExpect(status().isConflict());

        jdbc.update("UPDATE orders SET payment_channel='MOCK' WHERE id=?", id);
        mvc.perform(post(path + "/pay").header("token", token)).andExpect(status().isConflict());
        timeoutTask.closeExpiredOrders();
        timeoutTask.closeExpiredOrders();
        mvc.perform(post(path + "/cancel").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.events.length()").value(2));
        mvc.perform(get(path + "/payment").header("token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("CLOSED"));
        assertThat(stock("dish", dish)).isEqualTo(5);
        assertThat(stock("setmeal", meal)).isEqualTo(4);
    }

    @Test
    void oldSubmitReplayWithoutChannelStillReturnsMockChannel() throws Exception {
        submit(address, "", 200);
        jdbc.update("UPDATE order_submit_replay SET response_json=JSON_REMOVE(response_json,'$.paymentChannel') WHERE user_id=?", user);
        mvc.perform(post(SUBMIT).header("token", token).header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fulfillment":"DELIVERY","addressId":%d,"remark":""}
                        """.formatted(address)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentChannel").value("MOCK"));
        assertThat(stock("dish", dish)).isEqualTo(2);
    }

    @Test
    void historyIsOwnedAndOrderedWithAccurateTotals() throws Exception {
        submit(address, "", 200);
        long first = ownOrderId();
        jdbc.update("UPDATE dish SET stock=10 WHERE id=?", dish);
        reorder(first, token, 200);
        mvc.perform(post(SUBMIT).header("token", token).header("Idempotency-Key", "history-order-0002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fulfillment":"DELIVERY","addressId":%d,"remark":""}
                                """.formatted(address)))
                .andExpect(status().isOk());

        String page = "/api/user/order/page";
        mvc.perform(get(page).header("token", token).param("pageSize", "1").param("status", "UNPAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].lines.length()").value(2));
        mvc.perform(get(page).header("token", token).param("pageSize", "1").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records[0].id").value(first));
        mvc.perform(get(page).header("token", jwt.createToken(newUser(), "user")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get(page).header("token", token).param("begin", "2026-02-30"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(page).header("token", token).param("status", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reorderChecksCurrentStockAndReplaysWithoutIncreasingAgain() throws Exception {
        submit(address, "", 200);
        long id = ownOrderId();
        reorder(id, jwt.createToken(newUser(), "user"), 404);
        reorder(id, token, 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shopping_cart WHERE user_id=?", Integer.class, user)).isZero();

        jdbc.update("UPDATE dish SET stock=5 WHERE id=?", dish);
        reorder(id, token, 200);
        reorder(id, token, 200);
        assertThat(jdbc.queryForObject("SELECT SUM(number) FROM shopping_cart WHERE user_id=?", Integer.class, user)).isEqualTo(2);
        assertThat(stock("dish", dish)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=?", Integer.class, user)).isEqualTo(1);
    }

    private void reorder(long id, String auth, int expected) throws Exception {
        mvc.perform(post("/api/user/order/" + id + "/reorder").header("token", auth)
                        .header("Idempotency-Key", "history-reorder-0001"))
                .andExpect(status().is(expected));
    }

    @Test
    void operatorSearchCountsAndDeliveryFollowTheStateMachine() throws Exception {
        submit(address, "", 200);
        long id = ownOrderId();
        String admin = operatorToken();
        String path = "/api/admin/order/" + id;
        String number = jdbc.queryForObject("SELECT number FROM orders WHERE id=?", String.class, id);
        mvc.perform(get("/api/admin/order/counts").header("token", token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/order/page").header("token", admin).param("name", number))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/admin/order/counts").header("token", admin)
                        .param("name", number).param("status", "COMPLETED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.UNPAID").value(1))
                .andExpect(jsonPath("$.data.COMPLETED").value(0));
        mvc.perform(post(path + "/ready").header("token", admin)).andExpect(status().isConflict());
        mvc.perform(post("/api/user/order/" + id + "/pay").header("token", token)).andExpect(status().isOk());
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(path + "/ready").header("token", admin))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("READY_DELIVERY"))
                    .andExpect(jsonPath("$.data.events.length()").value(3));
        }
        mvc.perform(post(path + "/complete").header("token", admin)).andExpect(status().isConflict());
        mvc.perform(post(path + "/deliver").header("token", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DELIVERING"));
        mvc.perform(post(path + "/cancel").header("token", admin)).andExpect(status().isConflict());
        mvc.perform(post(path + "/complete").header("token", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertThat(jdbc.queryForObject("SELECT finish_time FROM orders WHERE id=?", java.sql.Timestamp.class, id)).isNotNull();
    }

    @Test
    void operatorCancellationRefundsMockExactlyOnceWithoutReturningFoodStock() throws Exception {
        submit(address, "", 200);
        long id = ownOrderId();
        String admin = operatorToken();
        String path = "/api/admin/order/" + id;
        mvc.perform(post("/api/user/order/" + id + "/pay").header("token", token)).andExpect(status().isOk());
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(path + "/cancel").header("token", admin).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"备餐调整\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.data.payStatus").value("REFUNDED"))
                    .andExpect(jsonPath("$.data.refundStatus").value("SUCCESS"));
        }
        mvc.perform(get(path + "/refund").header("token", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.amount").value(28.0));
        mvc.perform(get("/api/user/order/" + id + "/refund").header("token", jwt.createToken(newUser(), "user")))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_refund WHERE order_id=?", Integer.class, id)).isEqualTo(1);
        assertThat(stock("dish", dish)).isEqualTo(2);
        assertThat(stock("setmeal", meal)).isEqualTo(3);
    }

    @Test
    void agentBusinessGuardsRejectStalePreviewsAtWriteTime() throws Exception {
        submit(address, "", 200);
        long order = ownOrderId();
        mvc.perform(post("/api/user/order/" + order + "/pay").header("token", token))
                .andExpect(status().isOk());

        String operator = operatorToken();
        BaseContext.setCurrentId(Long.valueOf(jwt.parseToken(operator).getSubject()));
        try {
            assertThatThrownBy(() -> adminOrders.cancelUnpaidForAgent(order, "测试确认"))
                    .isInstanceOf(ConflictException.class);
            assertThatThrownBy(() -> setmeals.updateStatusIfCurrent(meal * 2 + 1, 0, 0))
                    .isInstanceOf(ConflictException.class);
        } finally {
            BaseContext.removeCurrentId();
        }
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", Integer.class, order)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_refund WHERE order_id=?", Integer.class, order)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM setmeal WHERE id=?", Integer.class, meal)).isEqualTo(1);
    }

    private String operatorToken() {
        jdbc.update("""
                INSERT INTO employee(username,password,name,phone,status,account_role)
                VALUES (?,'unused','测试运营','13800000000',1,'OPERATOR')
                """, "operator-" + UUID.randomUUID());
        return jwt.createToken(lastId(), "admin");
    }

    private long ownOrderId() {
        return jdbc.queryForObject("SELECT id FROM orders WHERE user_id=?", Long.class, user);
    }

    private int stock(String table, long id) {
        return jdbc.queryForObject("SELECT stock FROM " + table + " WHERE id=?", Integer.class, id);
    }

    private void submit(long addressId, String remark, int expected) throws Exception {
        mvc.perform(post(SUBMIT).header("token", token).header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fulfillment":"DELIVERY","addressId":%d,"remark":"%s"}
                        """.formatted(addressId, remark)))
                .andExpect(status().is(expected));
    }

    private long newAddress(long owner) {
        jdbc.update("INSERT INTO address_book(user_id,consignee,phone,building,detail) VALUES (?,'测试','13800000000','A座','801')", owner);
        return lastId();
    }

    private long newUser() {
        jdbc.update("INSERT INTO `user`(username,password,name,phone,status) VALUES (?,'unused','测试','',1)",
                "order-" + UUID.randomUUID());
        return lastId();
    }

    private long lastId() { return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class); }
}
