package com.sky;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 显式开启才连接local真实库。请求经过MVC、JWT、权限、AOP及真实Mapper。
 * MockMvc在测试线程执行，使业务加入测试事务；每个用例结束后回滚写入。
 * 凭证由服务端JwtUtil针对库内现有账号签发，不读取或重置账号密码。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DISH_DB_TEST", matches = "true")
class DishApiDatabaseTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;

    private long operator(String role, int enabled) {
        return jdbc.queryForObject("SELECT id FROM employee WHERE account_role=? AND status=? ORDER BY id LIMIT 1",
                Long.class, role, enabled);
    }

    private ObjectNode body() {
        Long categoryId = jdbc.queryForObject(
                "SELECT id FROM category WHERE type=1 AND status=1 ORDER BY id LIMIT 1", Long.class);
        ObjectNode body = json.createObjectNode();
        body.put("id", 0).put("kind", "dish")
                .put("name", "接口测试_" + UUID.randomUUID().toString().substring(0, 12))
                .put("categoryId", categoryId).put("price", 18.5)
                .put("description", "事务回滚测试").put("status", 0)
                .put("stock", 30).put("art", "chicken");
        body.putArray("tags").add("不辣").add("高蛋白");
        body.putArray("composition");
        return body;
    }

    private JsonNode send(ObjectNode body, long actor, int expected) throws Exception {
        String content = mvc.perform(post("/api/admin/dish")
                        .header("token", jwt.createToken(actor, "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return json.readTree(content);
    }

    @ParameterizedTest
    @CsvSource({"ACCOUNT_ADMIN", "OPERATOR"})
    void createsAndPersistsSafeResponseWithAudit(String role) throws Exception {
        long actor = operator(role, 1);
        ObjectNode request = body();
        JsonNode reply = send(request, actor, 200);
        assertThat(reply.path("code").asInt()).isEqualTo(1);
        JsonNode data = reply.path("data");
        long publicId = data.path("id").asLong();
        assertThat(publicId).isPositive().isEven();
        assertThat(data.path("tags").isArray()).isTrue();
        assertThat(data.path("composition").size()).isZero();
        assertThat(data.has("createUser")).isFalse();
        assertThat(data.has("password")).isFalse();
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM dish WHERE id=?", publicId / 2);
        assertThat(row.get("name")).isEqualTo(request.path("name").asText());
        assertThat(((Number) row.get("create_user")).longValue()).isEqualTo(actor);
        assertThat(((Number) row.get("update_user")).longValue()).isEqualTo(actor);
        assertThat(row.get("create_time")).isNotNull().isEqualTo(row.get("update_time"));
        assertThat(json.readTree(row.get("tags").toString())).isEqualTo(request.get("tags"));
    }

    @ParameterizedTest
    @CsvSource({"price,0", "price,18.555", "stock,-1", "stock,1.5",
            "id,1", "status,2", "categoryId,9007199254740991",
            "price,\"18.50\"", "stock,\"3\"", "image,null",
            "createUser,999", "tags,[\"不辣\";\"微辣\"]",
            "tags,[\"不辣\";\"不辣\"]", "composition,[{}]"})
    void rejectsInvalidRequestsWithoutInserting(String field, String value) throws Exception {
        ObjectNode request = body();
        request.set(field, json.readTree(value.replace(';', ',')));
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM dish", Long.class);
        send(request, operator("ACCOUNT_ADMIN", 1), field.equals("categoryId") ? 404 : 400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dish", Long.class)).isEqualTo(before);
    }

    @Test
    void duplicateNameConflicts() throws Exception {
        ObjectNode request = body();
        long actor = operator("ACCOUNT_ADMIN", 1);
        send(request, actor, 200);
        send(request, actor, 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dish WHERE name=?", Long.class,
                request.path("name").asText())).isEqualTo(1);
    }

    @Test
    void validatesCategoryTypeAndSaleState() throws Exception {
        ObjectNode request = body();
        long category = request.path("categoryId").asLong();
        long actor = operator("ACCOUNT_ADMIN", 1);
        // 测试事务结束后自动恢复分类原值；不提交用户数据的修改。
        jdbc.update("UPDATE category SET status=0 WHERE id=?", category);
        request.put("status", 1);
        send(request, actor, 409);
    }

    @Test
    void rejectsSetmealCategory() throws Exception {
        ObjectNode request = body();
        Long category = jdbc.queryForObject(
                "SELECT id FROM category WHERE type=2 ORDER BY id LIMIT 1", Long.class);
        request.put("categoryId", category);
        send(request, operator("ACCOUNT_ADMIN", 1), 400);
    }

    @Test
    void acceptsPriceAndStockBoundaries() throws Exception {
        ObjectNode request = body();
        request.put("price", new java.math.BigDecimal("0.01"));
        request.put("stock", 0);
        request.put("description", "");
        request.putArray("tags");
        send(request, operator("ACCOUNT_ADMIN", 1), 200);
    }

    @Test
    void rejectsAnonymousUserRoleAndDisabledOperator() throws Exception {
        ObjectNode request = body();
        mvc.perform(post("/api/admin/dish").contentType(MediaType.APPLICATION_JSON)
                .content(request.toString())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/dish").header("token", jwt.createToken(1L, "user"))
                .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isForbidden());
        send(request, operator("OPERATOR", 0), 403);
    }
}
