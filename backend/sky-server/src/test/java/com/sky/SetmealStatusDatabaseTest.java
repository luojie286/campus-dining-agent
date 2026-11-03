package com.sky;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 只操作事务内新建夹具；包括实际DELETE在内的变更均于用例结束回滚。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_SETMEAL_DB_TEST", matches = "true")
class SetmealStatusDatabaseTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JwtUtil jwt;
    private long id;
    private long ingredient;
    private long actor;
    private long category;
    private String name;
    private String token;

    @BeforeEach
    void fixtures() {
        actor = jdbc.queryForObject("SELECT id FROM employee WHERE status=1 ORDER BY id LIMIT 1", Long.class);
        token = jwt.createToken(actor, "admin");
        name = "编辑删除_" + UUID.randomUUID().toString().substring(0, 10);
        jdbc.update("INSERT INTO category(name,type,sort,status) VALUES (?,2,0,1)", name);
        category = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("""
                INSERT INTO setmeal(name,category_id,price,description,status,stock,tags,art,
                                 image,create_time,update_time,create_user,update_user)
                VALUES (?,?,18.50,'',0,7,'["不辣"]','chicken',
                        'https://existing.example.invalid/old.png','2000-01-01','2000-01-01',?,?)
                """, name, category, actor, actor);
        id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        ingredient = jdbc.queryForObject("SELECT id FROM dish ORDER BY id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO setmeal_dish(setmeal_id,dish_id,name,price,copies) VALUES (?,?,'测试组成',1,1)", id, ingredient);
    }

    private String path() { return "/api/admin/setmeal/" + (id * 2 + 1); }

    private JsonNode call(MockHttpServletRequestBuilder request, int expected) throws Exception {
        return json.readTree(mvc.perform(request.header("token", token))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private ObjectNode body() {
        ObjectNode body = json.createObjectNode();
        body.put("id", id * 2 + 1).put("kind", "setmeal").put("name", name)
                .put("categoryId", category).put("price", 18.5).put("description", "")
                .put("status", 0).put("art", "chicken");
        body.putArray("tags").add("不辣");
        body.putArray("composition").addObject().put("dishId", ingredient * 2).put("quantity", 1);
        return body;
    }

    private JsonNode save(ObjectNode body, int expected) throws Exception {
        return call(put("/api/admin/setmeal").contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()), expected);
    }

    private JsonNode state(int state, int expected) throws Exception {
        return call(put(path() + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":" + state + "}"), expected);
    }

    @Test
    void saleStopAndSameValueProtectOtherFields() throws Exception {
        jdbc.update("UPDATE dish SET status=1 WHERE id=?", ingredient);
        long ingredientCategory = jdbc.queryForObject("SELECT category_id FROM dish WHERE id=?", Long.class, ingredient);
        jdbc.update("UPDATE category SET status=1 WHERE id=?", ingredientCategory);
        var before = jdbc.queryForMap("SELECT name,stock,price,create_time FROM setmeal WHERE id=?", id);
        state(1, 200);
        var audit = jdbc.queryForMap("SELECT * FROM setmeal WHERE id=?", id);
        state(1, 200);
        assertThat(jdbc.queryForMap("SELECT * FROM setmeal WHERE id=?", id)).isEqualTo(audit);
        state(0, 200);
        assertThat(jdbc.queryForMap("SELECT name,stock,price,create_time FROM setmeal WHERE id=?", id)).isEqualTo(before);
    }

    @Test
    void refusesStoppedIngredientOrDisabledCategory() throws Exception {
        jdbc.update("UPDATE dish SET status=0 WHERE id=?", ingredient);
        state(1, 409);
        jdbc.update("UPDATE dish SET status=1 WHERE id=?", ingredient);
        jdbc.update("UPDATE category SET status=0 WHERE id=?", category);
        state(1, 409);
        jdbc.update("UPDATE setmeal SET status=1 WHERE id=?", id);
        state(0, 200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":2}", "{\"status\":\"1\"}", "{\"status\":1,\"stock\":9}"})
    void rejectsInvalidStatus(String content) throws Exception {
        call(put(path()+"/status").contentType(MediaType.APPLICATION_JSON).content(content), 400);
    }

    @Test
    void statusRejectsWrongIdentityAndMissingResource() throws Exception {
        token = jwt.createToken(actor, "user");
        state(1, 403);
        token = jwt.createToken(actor, "admin");
        call(put("/api/admin/setmeal/9007199254740991/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":0}"),404);
    }
}
