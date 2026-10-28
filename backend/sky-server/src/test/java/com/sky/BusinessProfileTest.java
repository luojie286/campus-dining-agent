package com.sky;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.config.JwtWebMvcConfiguration;
import com.sky.config.WebMvcConfiguration;
import com.sky.controller.admin.EmployeeController;
import com.sky.controller.user.UserController;
import com.sky.interceptor.JwtTokenAdminInterceptor;
import com.sky.interceptor.JwtTokenUserInterceptor;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.EmployeeMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.EmployeePermissionService;
import com.sky.service.impl.CategoryServiceImpl;
import com.sky.service.impl.EmployeeServiceImpl;
import com.sky.service.impl.UserServiceImpl;
import com.sky.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 仅验证local下业务组件及鉴权仍加载，使用mock Mapper，不连接数据库。 */
class BusinessProfileTest {

    @Test
    void localRegistersBusinessAndRejectsAnonymousRequests() {
        new WebApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=local")
                .withUserConfiguration(BusinessConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EmployeeServiceImpl.class);
                    assertThat(context).hasSingleBean(CategoryServiceImpl.class);
                    assertThat(context).hasSingleBean(UserServiceImpl.class);
                    assertThat(context).hasSingleBean(EmployeePermissionService.class);
                    assertThat(context).hasSingleBean(JwtWebMvcConfiguration.class);

                    MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
                    mvc.perform(get("/api/admin/employee/list"))
                            .andExpect(status().isUnauthorized());
                    mvc.perform(get("/api/admin/category/list"))
                            .andExpect(status().isUnauthorized());
                    mvc.perform(get("/api/user/category/list"))
                            .andExpect(status().isUnauthorized());
                    verifyNoInteractions(context.getBean(EmployeeMapper.class),
                            context.getBean(CategoryMapper.class), context.getBean(UserMapper.class));
                });
    }

    @TestConfiguration
    @EnableWebMvc
    @Import({WebMvcConfiguration.class, JwtWebMvcConfiguration.class,
            JwtTokenAdminInterceptor.class, JwtTokenUserInterceptor.class,
            EmployeeController.class, UserController.class,
            com.sky.controller.admin.CategoryController.class,
            com.sky.controller.user.CategoryController.class,
            EmployeeServiceImpl.class, CategoryServiceImpl.class,
            UserServiceImpl.class, EmployeePermissionService.class})
    static class BusinessConfiguration {
        @Bean EmployeeMapper employeeMapper() { return mock(EmployeeMapper.class); }
        @Bean CategoryMapper categoryMapper() { return mock(CategoryMapper.class); }
        @Bean UserMapper userMapper() { return mock(UserMapper.class); }
        @Bean JwtUtil jwtUtil() { return mock(JwtUtil.class); }
        @Bean PasswordEncoder passwordEncoder() { return mock(PasswordEncoder.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }
}
