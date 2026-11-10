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
@EnabledIfEnvironmentVariable(named = "RUN_ADDRESS_DB_TEST", matches = "true")
class AddressBookDatabaseTest {
    private static final String PATH = "/api/user/addressBook";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtUtil jwt;
    private long userId;
    private long otherId;

    @BeforeEach
    void setup() {
        userId = createUser();
        otherId = createUser();
    }

    @Test
    void ownerOnlyAndDefaultRemainsUnique() throws Exception {
        String token = jwt.createToken(userId, "user");
        String other = jwt.createToken(otherId, "user");
        long first = add(token, otherId);
        long second = add(token, otherId);
        mvc.perform(get(PATH + "/list").header("token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(second))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[1].isDefault").value(false));
        mvc.perform(get(PATH + "/" + first).header("token", other)).andExpect(status().isNotFound());
        mvc.perform(delete(PATH + "/" + first).header("token", other)).andExpect(status().isNotFound());
        mvc.perform(put(PATH + "/" + first + "/default").header("token", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isDefault").value(true));
        mvc.perform(delete(PATH + "/" + first).header("token", token)).andExpect(status().isOk());
        mvc.perform(get(PATH + "/list").header("token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(second))
                .andExpect(jsonPath("$.data[0].isDefault").value(false));
    }

    private long add(String token, long assertedOwner) throws Exception {
        mvc.perform(post(PATH).header("token", token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":0,"userId":%d,"consignee":"测试","phone":"13800000000",
                                 "building":"A座","detail":"801","isDefault":true}
                                """.formatted(assertedOwner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(userId));
        return jdbc.queryForObject("SELECT MAX(id) FROM address_book WHERE user_id=?", Long.class, userId);
    }

    private long createUser() {
        jdbc.update("INSERT INTO `user`(username,password,name,phone,status) VALUES (?,'unused','测试','',1)",
                "address-" + UUID.randomUUID());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
