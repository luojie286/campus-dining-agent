package com.sky;

import com.fasterxml.jackson.databind.JsonNode;
import com.sky.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实HTTP只读验收，独立于会提交新增菜品的HTTP测试开关。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@DirtiesContext
@EnabledIfEnvironmentVariable(named = "RUN_DISH_PAGE_HTTP_TEST", matches = "true")
class DishPageHttpTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;

    @Test
    void readsRealHttpPageAndChecksDatabaseOrder() {
        long actor = jdbc.queryForObject("SELECT id FROM employee WHERE status=1 ORDER BY id LIMIT 1", Long.class);
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", jwt.createToken(actor, "admin"));
        var response = http.exchange("/api/admin/dish/page", HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = response.getBody().path("data");
        assertThat(data.path("total").asLong()).isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM dish", Long.class));
        var ids = jdbc.queryForList("SELECT d.id FROM dish d LEFT JOIN category c ON c.id=d.category_id ORDER BY c.sort,c.id,d.id LIMIT 8", Long.class);
        assertThat(data.path("records").size()).isEqualTo(ids.size());
        for (int index = 0; index < ids.size(); index++) {
            assertThat(data.path("records").get(index).path("id").asLong()).isEqualTo(ids.get(index) * 2);
        }
        assertThat(http.getForEntity("/api/admin/dish/page", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.exchange("/api/admin/dish/page?pageSize=101", HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
