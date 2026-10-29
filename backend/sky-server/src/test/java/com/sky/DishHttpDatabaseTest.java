package com.sky;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真正启动HTTP端口并连接local MySQL。与MockMvc测试不同，本测试会提交数据。
 * 只有显式设置RUN_DISH_HTTP_TEST=true才运行，创建的停售测试菜品保留供核对。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@DirtiesContext
@EnabledIfEnvironmentVariable(named = "RUN_DISH_HTTP_TEST", matches = "true")
class DishHttpDatabaseTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;

    @Test
    void createsThroughHttpAndChecksPersistedDataAndErrors() {
        long category = jdbc.queryForObject(
                "SELECT id FROM category WHERE type=1 AND status=1 ORDER BY id LIMIT 1", Long.class);
        for (String role : new String[]{"ACCOUNT_ADMIN", "OPERATOR"}) {
            long actor = jdbc.queryForObject(
                    "SELECT id FROM employee WHERE account_role=? AND status=1 ORDER BY id LIMIT 1",
                    Long.class, role);
            ObjectNode body = json.createObjectNode();
            String name = "HTTP测试_" + UUID.randomUUID().toString().substring(0, 12);
            body.put("id", 0).put("kind", "dish").put("name", name)
                    .put("categoryId", category).put("price", 18.5)
                    .put("description", "真实HTTP验收，停售测试记录")
                    .put("status", 0).put("stock", 0).put("art", "chicken");
            body.putArray("tags").add("不辣");
            body.putArray("composition");

            // 先验证匿名请求被拒绝，没有业务写入。
            assertThat(send(body, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            String token = jwt.createToken(actor, "admin");
            ResponseEntity<JsonNode> response = send(body, token);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode reply = response.getBody();
            assertThat(reply).isNotNull();
            assertThat(reply.path("code").asInt()).isEqualTo(1);
            JsonNode data = reply.path("data");
            long publicId = data.path("id").asLong();
            assertThat(publicId).isPositive().isEven();
            assertThat(data.path("tags").isArray()).isTrue();
            assertThat(data.has("createUser")).isFalse();
            Map<String, Object> row = jdbc.queryForMap("SELECT * FROM dish WHERE id=?", publicId / 2);
            assertThat(row.get("name")).isEqualTo(name);
            assertThat(((Number) row.get("status")).intValue()).isZero();
            assertThat(((Number) row.get("stock")).intValue()).isZero();
            assertThat(((Number) row.get("create_user")).longValue()).isEqualTo(actor);
            assertThat(((Number) row.get("update_user")).longValue()).isEqualTo(actor);
            assertThat(row.get("create_time")).isNotNull().isEqualTo(row.get("update_time"));
            // 输出仅包含测试记录标识，不记录token或账户凭证。
            System.out.printf("DISH_HTTP_VERIFIED publicId=%d databaseId=%d name=%s%n",
                    publicId, publicId / 2, name);

            assertThat(send(body, token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            body.put("stock", -1);
            assertThat(send(body, token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            body.put("stock", 0);
            assertThat(send(body, jwt.createToken(actor, "user")).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dish WHERE name=?", Long.class, name))
                    .isEqualTo(1L);
        }
    }

    private ResponseEntity<JsonNode> send(ObjectNode body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("token", token);
        }
        return http.exchange("/api/admin/dish", HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
    }
}
