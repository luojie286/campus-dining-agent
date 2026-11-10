package com.sky;

import com.sky.util.JwtUtil;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_CART_DB_TEST", matches = "true")
class ShoppingCartDatabaseTest {
    private static final String CART = "/api/user/shoppingCart";
    private static final String KEY = "integration-cart-0001";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtUtil jwt;
    private String token;
    private String otherToken;
    private long dishId;
    private long mealId;

    @BeforeEach
    void setup() {
        String prefix = "购物车_" + UUID.randomUUID();
        token = jwt.createToken(createUser(prefix + "_1"), "user");
        otherToken = jwt.createToken(createUser(prefix + "_2"), "user");

        jdbc.update("INSERT INTO category(type,name,status) VALUES (1,?,1)", prefix + "_菜");
        long dishCategory = lastId();
        jdbc.update("INSERT INTO category(type,name,status) VALUES (2,?,1)", prefix + "_套餐");
        long mealCategory = lastId();
        jdbc.update("INSERT INTO dish(category_id,name,price,status,stock) VALUES (?,?,10,1,3)",
                dishCategory, prefix);
        dishId = lastId();
        jdbc.update("INSERT INTO setmeal(category_id,name,price,status,stock) VALUES (?,?,20,1,4)",
                mealCategory, prefix);
        mealId = lastId();
        jdbc.update("INSERT INTO setmeal_dish(setmeal_id,dish_id,name,price,copies) VALUES (?,?,?,?,2)",
                mealId, dishId, prefix, 10);
    }

    @Test
    void retriesReturnOriginalResultAndClearingOnlyAffectsOwner() throws Exception {
        long item = dishId * 2;
        mvc.perform(get(CART + "/list")).andExpect(status().isUnauthorized());
        add(token, KEY, item, 1, 200);
        add(token, KEY, item, 1, 200);
        add(token, KEY, item, 2, 409);
        add(otherToken, KEY, item, 2, 200);
        mvc.perform(get(CART + "/list").header("token", token))
                .andExpect(jsonPath("$.data[0].quantity").value(1));
        mvc.perform(delete(CART + "/clean").header("token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get(CART + "/list").header("token", otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].quantity").value(2));
    }

    @Test
    void singleDishAndMealCompeteForSameStock() throws Exception {
        add(token, KEY, mealId * 2 + 1, 1, 200);
        add(token, "integration-cart-0002", dishId * 2, 2, 409);
        mvc.perform(get(CART + "/list").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].itemId").value(mealId * 2 + 1));
        add(token, "integration-cart-0002", dishId * 2, 1, 200);
    }

    private long createUser(String name) {
        jdbc.update("INSERT INTO `user`(username,password,name,phone,status) VALUES (?,'unused',?,'',1)", name, name);
        return lastId();
    }

    private long lastId() {
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void add(String auth, String key, long itemId, int quantity, int expected) throws Exception {
        mvc.perform(post(CART + "/add").header("token", auth).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":" + itemId + ",\"quantity\":" + quantity + "}"))
                .andExpect(status().is(expected));
    }
}
