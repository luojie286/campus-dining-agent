package com.sky.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.sky.interceptor.JwtTokenAdminInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import com.sky.interceptor.JwtTokenUserInterceptor;

/** 仅开发环境跨域配置。JWT 拦截器及角色鉴权由学习者补充。 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://127.0.0.1:5173", "http://localhost:5173")
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("Content-Type", "token", "Idempotency-Key")
            .maxAge(3600);
    }
    private final JwtTokenAdminInterceptor jwtTokenAdminInterceptor;
    private final JwtTokenUserInterceptor jwtTokenUserInterceptor;

    public WebMvcConfiguration(
            JwtTokenAdminInterceptor jwtTokenAdminInterceptor,
            JwtTokenUserInterceptor jwtTokenUserInterceptor) {

        this.jwtTokenAdminInterceptor = jwtTokenAdminInterceptor;
        this.jwtTokenUserInterceptor = jwtTokenUserInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/employee/login");

        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/api/user/category/**");
    }
}
