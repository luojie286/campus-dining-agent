package com.sky;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 验证 bootstrap 在没有数据库时启动，且不冒充已实现业务。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("bootstrap")
class EnvironmentSmokeTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test void environmentStartsWithoutDatabase() throws Exception {
        mvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(1))
            .andExpect(jsonPath("$.data.javaVersion").value(startsWith("21.")))
            .andExpect(jsonPath("$.data.springBootVersion").value(startsWith("3.4.")))
            .andExpect(jsonPath("$.data.businessImplemented").value(false));
    }

    @Test void businessIsNotImplemented() throws Exception {
        mvc.perform(get("/api/admin/dish/page")).andExpect(status().isNotFound());
        // 已实现的业务也必须在bootstrap中整体关闭，不能仅移除鉴权。
        mvc.perform(get("/api/admin/employee/list")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/category/list")).andExpect(status().isNotFound());
        mvc.perform(get("/api/user/category/list")).andExpect(status().isNotFound());
    }

    @Test void bootstrapDoesNotCreateDatabaseOrRedisConnections() {
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(context.getBeansOfType(RedisConnectionFactory.class)).isEmpty();
    }
}
