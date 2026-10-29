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
@EnabledIfEnvironmentVariable(named = "RUN_DISH_DB_TEST", matches = "true")
class DishEditDeleteDatabaseTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JwtUtil jwt;
    private long id;
    private long actor;
    private long category;
    private String name;
    private String token;

    @BeforeEach
    void fixtures() {
        actor = jdbc.queryForObject("SELECT id FROM employee WHERE status=1 ORDER BY id LIMIT 1", Long.class);
        token = jwt.createToken(actor, "admin");
        name = "编辑删除_" + UUID.randomUUID().toString().substring(0, 10);
        jdbc.update("INSERT INTO category(name,type,sort,status) VALUES (?,1,0,1)", name);
        category = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("""
                INSERT INTO dish(name,category_id,price,description,status,stock,tags,art,
                                 image,create_time,update_time,create_user,update_user)
                VALUES (?,?,18.50,'',0,7,'["不辣"]','chicken',
                        'https://existing.example.invalid/old.png','2000-01-01','2000-01-01',?,?)
                """, name, category, actor, actor);
        id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private String path() { return "/api/admin/dish/" + id * 2; }

    private JsonNode call(MockHttpServletRequestBuilder request, int expected) throws Exception {
        return json.readTree(mvc.perform(request.header("token", token))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private ObjectNode body() {
        ObjectNode body = json.createObjectNode();
        body.put("id", id * 2).put("kind", "dish").put("name", name)
                .put("categoryId", category).put("price", 18.5).put("description", "")
                .put("status", 0).put("art", "chicken");
        body.putArray("tags").add("不辣");
        body.putArray("composition");
        return body;
    }

    private JsonNode save(ObjectNode body, int expected) throws Exception {
        return call(put("/api/admin/dish").contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()), expected);
    }

    @Test
    void detailAndEditPreserveStockCreationAndOmittedImage() throws Exception {
        var original = jdbc.queryForMap("SELECT * FROM dish WHERE id=?", id);
        assertThat(call(get(path()), 200).path("data").has("createUser")).isFalse();
        ObjectNode request = body();
        request.put("stock", 999).put("name", name + "改");
        JsonNode reply = save(request, 200).path("data");
        assertThat(reply.path("stock").asInt()).isEqualTo(7);
        assertThat(reply.path("image").asText()).isEqualTo(original.get("image"));
        var updated = jdbc.queryForMap("SELECT * FROM dish WHERE id=?", id);
        assertThat(updated.get("create_time")).isEqualTo(original.get("create_time"));
        assertThat(updated.get("update_time")).isNotEqualTo(original.get("update_time"));
        assertThat(((Number) updated.get("update_user")).longValue()).isEqualTo(actor);
        save(request, 200);
        assertThat(jdbc.queryForMap("SELECT * FROM dish WHERE id=?", id)).isEqualTo(updated);
    }

    @Test
    void sameValuesDoNotWriteAudit() throws Exception {
        var before = jdbc.queryForMap("SELECT * FROM dish WHERE id=?", id);
        save(body(), 200);
        assertThat(jdbc.queryForMap("SELECT * FROM dish WHERE id=?", id)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "price", "stock", "role", "image", "composition", "tags"})
    void rejectsInvalidEdit(String field) throws Exception {
        ObjectNode request = body();
        switch (field) {
            case "id" -> request.put("id", 3);
            case "price" -> request.put("price", 18.555);
            case "stock" -> request.put("stock", -1);
            case "role" -> request.put("createUser", 999);
            case "image" -> request.putNull("image");
            case "composition" -> request.putArray("composition").addObject().put("dishId", 2);
            case "tags" -> request.putArray("tags").add("不辣").add("辣");
        }
        save(request, 400);
    }

    @Test
    void rejectsNameConflictAndDisabledCategorySale() throws Exception {
        jdbc.update("INSERT INTO dish(name,category_id,price,tags,art) VALUES (?,?,1,'[]','chicken')", name + "重复", category);
        ObjectNode request = body();
        request.put("name", name + "重复");
        save(request, 409);
    }

    @Test
    void rejectsSaleInDisabledCategory() throws Exception {
        jdbc.update("UPDATE category SET status=0 WHERE id=?", category);
        ObjectNode request = body();
        request.put("status", 1);
        save(request, 409);
    }

    @Test
    void deletesFlavorsAndRepeatedDeleteSucceeds() throws Exception {
        jdbc.update("INSERT INTO dish_flavor(dish_id,name,value) VALUES (?,'测试','[]')", id);
        call(delete(path()), 200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dish WHERE id=?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dish_flavor WHERE dish_id=?", Long.class, id)).isZero();
        call(delete(path()), 200);
        call(get(path()), 404);
        save(body(), 404);
    }

    @Test
    void refusesSellingDish() throws Exception {
        jdbc.update("UPDATE dish SET status=1 WHERE id=?", id);
        call(delete(path()), 409);
    }

    @Test
    void refusesSetmealReferenceEvenWhenSetmealStopped() throws Exception {
        jdbc.update("INSERT INTO setmeal(name,category_id,price,status) VALUES (?,?,1,0)", name, category);
        long setmeal = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO setmeal_dish(setmeal_id,dish_id,name,price,copies) VALUES (?,?,?,1,1)", setmeal, id, name);
        call(delete(path()), 409);
    }

    private long order(String snapshot, int state) {
        jdbc.update("""
                INSERT INTO orders(number,status,user_id,fulfillment,payment_deadline,amount,
                  phone,address,user_name,consignee,inventory_snapshot,events)
                VALUES (?,?,1,'PICKUP',NOW(),1,'13800000000','测试','测试','测试',?,'[]')
                """, UUID.randomUUID().toString(), state, snapshot);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    void refusesInventorySnapshotReferenceWithoutDirectOrderDetail() throws Exception {
        order("[{\"kind\":\"dish\",\"id\":" + id + ",\"quantity\":1}]", 1);
        call(delete(path()), 409);
    }

    @Test
    void refusesPendingDetailButPreservesHistoricalDetail() throws Exception {
        long order = order("[]", 2);
        jdbc.update("INSERT INTO order_detail(name,order_id,dish_id,number,amount) VALUES (?,?,?,1,1)", name, order, id);
        call(delete(path()), 409);
    }

    @Test
    void historicalOrderSurvivesDeletion() throws Exception {
        long order = order("[{\"kind\":\"dish\",\"id\":" + id + ",\"quantity\":1}]", 5);
        jdbc.update("INSERT INTO order_detail(name,order_id,dish_id,number,amount) VALUES (?,?,?,1,1)", name, order, id);
        call(delete(path()), 200);
        assertThat(jdbc.queryForObject("SELECT name FROM order_detail WHERE order_id=?", String.class, order)).isEqualTo(name);
    }

    @Test
    void rejectsUnauthorizedRequestsAndBadIds() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(delete(path())).andExpect(status().isUnauthorized());
        token = jwt.createToken(actor, "user");
        call(delete(path()), 403);
        token = jwt.createToken(actor, "admin");
        call(get("/api/admin/dish/3"), 400);
        call(delete("/api/admin/dish/0"), 400);
    }
}
