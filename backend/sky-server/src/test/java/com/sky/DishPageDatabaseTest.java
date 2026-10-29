package com.sky;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 真实MySQL夹具和所有临时变更均在用例结束后回滚。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DISH_DB_TEST", matches = "true")
class DishPageDatabaseTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JwtUtil jwt;
    private String prefix;
    private String token;
    private long first;
    private long second;
    private long later;
    private long category;

    @BeforeEach
    void fixtures() {
        prefix = "分页" + UUID.randomUUID().toString().substring(0, 8);
        long actor = jdbc.queryForObject("SELECT id FROM employee WHERE status=1 ORDER BY id LIMIT 1", Long.class);
        token = jwt.createToken(actor, "admin");
        category = category(9, 1);
        later = dish(category, "普通", 1);
        long earlyCategory = category(1, 0);
        first = dish(earlyCategory, "%_!", 0);
        second = dish(earlyCategory, "普通二", 1);
    }

    private long category(int sort, int state) {
        jdbc.update("INSERT INTO category(name,type,sort,status) VALUES (?,1,?,?)",
                prefix + "分类" + sort, sort, state);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long dish(long categoryId, String suffix, int state) {
        jdbc.update("INSERT INTO dish(name,category_id,price,description,status,stock,tags,art) VALUES (?,?,18.50,'',?,0,?,'chicken')",
                prefix + suffix, categoryId, state, "[\"不辣\"]");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private JsonNode request(MockHttpServletRequestBuilder request, int expected) throws Exception {
        return json.readTree(mvc.perform(request.header("token", token))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder query() {
        return get("/api/admin/dish/page").param("name", prefix);
    }

    @Test
    void sortsBeforePagingAndPreservesTotalPastLastPage() throws Exception {
        JsonNode page = request(query().param("pageSize", "2"), 200).path("data");
        assertThat(page.path("total").asLong()).isEqualTo(3);
        assertThat(page.path("records").size()).isEqualTo(2);
        assertThat(page.path("records").get(0).path("id").asLong()).isEqualTo(first * 2);
        assertThat(page.path("records").get(1).path("id").asLong()).isEqualTo(second * 2);
        JsonNode tail = request(query().param("pageSize", "2").param("page", "2"), 200).path("data");
        assertThat(tail.path("records").get(0).path("id").asLong()).isEqualTo(later * 2);
        JsonNode empty = request(query().param("pageSize", "2").param("page", "3"), 200).path("data");
        assertThat(empty.path("total").asLong()).isEqualTo(3);
        assertThat(empty.path("records").isEmpty()).isTrue();
        JsonNode row = page.path("records").get(0);
        assertThat(row.path("tags").get(0).asText()).isEqualTo("不辣");
        assertThat(row.path("composition").isEmpty()).isTrue();
        assertThat(row.has("createUser")).isFalse();
    }

    @Test
    void combinesFiltersAndEscapesLiteralWildcards() throws Exception {
        JsonNode filtered = request(query().param("categoryId", String.valueOf(category))
                .param("status", "1"), 200).path("data");
        assertThat(filtered.path("total").asLong()).isEqualTo(1);
        assertThat(filtered.path("records").get(0).path("id").asLong()).isEqualTo(later * 2);
        for (String literal : new String[]{"%", "%_", "%_!"}) {
            JsonNode page = request(get("/api/admin/dish/page").param("name", "  " + prefix + literal + "  "), 200).path("data");
            assertThat(page.path("total").asLong()).isEqualTo(1);
        }
        assertThat(request(query().param("categoryId", "9007199254740991"), 200)
                .path("data").path("total").asLong()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=0", "page=1000001", "page=abc", "page=", "pageSize=0",
            "pageSize=101", "pageSize=", "status=2", "categoryId=0", "categoryId=1.5"})
    void rejectsInvalidParameters(String parameter) throws Exception {
        String[] pair = parameter.split("=", -1);
        request(query().param(pair[0], pair[1]), 400);
    }

    @Test
    void allowsOperatorAndRejectsAnonymousAndUser() throws Exception {
        mvc.perform(query()).andExpect(status().isUnauthorized());
        token = jwt.createToken(1L, "user");
        request(query(), 403);
        long operator = jdbc.queryForObject("SELECT id FROM employee WHERE account_role='OPERATOR' AND status=1 ORDER BY id LIMIT 1", Long.class);
        token = jwt.createToken(operator, "admin");
        request(query(), 200);
        long disabled = jdbc.queryForObject("SELECT id FROM employee WHERE status=0 ORDER BY id LIMIT 1", Long.class);
        token = jwt.createToken(disabled, "admin");
        request(query(), 403);
    }

    @Test
    void blankFiltersUseDefaultsAndQueryDoesNotChangeAudit() throws Exception {
        var before = jdbc.queryForMap("SELECT update_time,update_user FROM dish WHERE id=?", first);
        JsonNode page = request(get("/api/admin/dish/page").param("name", "   ")
                .param("status", "").param("categoryId", ""), 200).path("data");
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM dish", Long.class);
        assertThat(page.path("total").asLong()).isEqualTo(total);
        assertThat(page.path("records").size()).isEqualTo((int) Math.min(total, 8));
        assertThat(jdbc.queryForMap("SELECT update_time,update_user FROM dish WHERE id=?", first))
                .isEqualTo(before);
        request(get("/api/admin/dish/page").param("name", "a".repeat(101)), 400);
    }
}
