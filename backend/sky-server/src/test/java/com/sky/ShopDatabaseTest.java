package com.sky;

import com.sky.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用真实 MySQL 和 JWT 拦截器。店铺状态写入在测试事务结束后回滚。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_SHOP_DB_TEST", matches = "true")
class ShopDatabaseTest {

    private static final String ADMIN_SHOP = "/api/admin/shop";
    private static final String USER_SHOP = "/api/user/shop";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtUtil jwt;

    @ParameterizedTest
    @ValueSource(strings = {"ACCOUNT_ADMIN", "OPERATOR"})
    void bothOperatorRolesCanSetStatusAndBothClientsReadSameValue(String role) throws Exception {
        String operator = operatorToken(role, 1);
        String customer = userToken();
        int initial = currentStatus();
        boolean targetOpen = initial == 0;
        var initialDetails = jdbc.queryForMap(
                "SELECT name, opening_hours, pickup_point FROM shop WHERE id = 1");

        mvc.perform(update(operator, "{\"open\":" + targetOpen + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.open").value(targetOpen));

        assertThat(currentStatus()).isEqualTo(targetOpen ? 1 : 0);
        assertThat(jdbc.queryForMap("SELECT name, opening_hours, pickup_point FROM shop WHERE id = 1"))
                .isEqualTo(initialDetails);

        mvc.perform(get(ADMIN_SHOP).header("token", operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.open").value(targetOpen));
        mvc.perform(get(USER_SHOP).header("token", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.open").value(targetOpen))
                .andExpect(jsonPath("$.data.name").value(initialDetails.get("name")))
                .andExpect(jsonPath("$.data.openingHours").value(initialDetails.get("opening_hours")))
                .andExpect(jsonPath("$.data.pickupPoint").value(initialDetails.get("pickup_point")));
    }

    @Test
    void sameStatusDoesNotWriteAgain() throws Exception {
        // 把时间放到过去，避免数据库时间精度掩盖多余的写入。
        jdbc.update("UPDATE shop SET update_time = '2000-01-01 00:00:00' WHERE id = 1");
        var initial = jdbc.queryForMap("SELECT status, update_time FROM shop WHERE id = 1");
        boolean open = currentStatus() == 1;

        mvc.perform(update(operatorToken("OPERATOR", 1), "{\"open\":" + open + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.open").value(open));

        assertThat(jdbc.queryForMap("SELECT status, update_time FROM shop WHERE id = 1"))
                .isEqualTo(initial);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"open\":null}", "{\"open\":\"false\"}", "{\"open\":0}",
            "{\"open\":true,\"name\":\"新名称\"}"})
    void invalidPayloadCannotChangeShop(String body) throws Exception {
        var initial = jdbc.queryForMap("SELECT * FROM shop WHERE id = 1");

        mvc.perform(update(operatorToken("OPERATOR", 1), body))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForMap("SELECT * FROM shop WHERE id = 1")).isEqualTo(initial);
    }

    @Test
    void anonymousClientsCannotReadOrWrite() throws Exception {
        mvc.perform(get(USER_SHOP)).andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN_SHOP)).andExpect(status().isUnauthorized());
        mvc.perform(put(ADMIN_SHOP).contentType(MediaType.APPLICATION_JSON).content("{\"open\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientsCannotUseOtherRoleOrDisabledAccount() throws Exception {
        mvc.perform(update(userToken(), "{\"open\":true}")).andExpect(status().isForbidden());
        mvc.perform(get(USER_SHOP).header("token", operatorToken("ACCOUNT_ADMIN", 1)))
                .andExpect(status().isForbidden());
        mvc.perform(update(operatorToken("OPERATOR", 0), "{\"open\":true}"))
                .andExpect(status().isForbidden());
    }

    private int currentStatus() {
        return jdbc.queryForObject("SELECT status FROM shop WHERE id = 1", Integer.class);
    }

    private String operatorToken(String role, int state) {
        Long id = jdbc.queryForObject("""
                SELECT id FROM employee
                WHERE account_role = ? AND status = ?
                ORDER BY id LIMIT 1
                """, Long.class, role, state);
        return jwt.createToken(id, "admin");
    }

    private String userToken() {
        Long id = jdbc.queryForObject(
                "SELECT id FROM `user` WHERE status = 1 ORDER BY id LIMIT 1", Long.class);
        return jwt.createToken(id, "user");
    }

    private MockHttpServletRequestBuilder update(String token, String body) {
        return put(ADMIN_SHOP).header("token", token)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
