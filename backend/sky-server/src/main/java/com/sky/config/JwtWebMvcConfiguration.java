package com.sky.config;

import com.sky.interceptor.JwtTokenAdminInterceptor;
import com.sky.interceptor.JwtTokenUserInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * JWT 鉴权需要查询账户当前状态，因此随数据库业务组件一起加载。
 */
@Configuration
@Profile("!bootstrap")
public class JwtWebMvcConfiguration implements WebMvcConfigurer {

    private final JwtTokenAdminInterceptor adminInterceptor;
    private final JwtTokenUserInterceptor userInterceptor;

    public JwtWebMvcConfiguration(
            JwtTokenAdminInterceptor adminInterceptor,
            JwtTokenUserInterceptor userInterceptor) {
        this.adminInterceptor = adminInterceptor;
        this.userInterceptor = userInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/employee/login");

        registry.addInterceptor(userInterceptor)
                .addPathPatterns(
                        "/api/user/category/**",
                        "/api/user/shop",
                        "/api/user/menu/page"
                );
    }
}