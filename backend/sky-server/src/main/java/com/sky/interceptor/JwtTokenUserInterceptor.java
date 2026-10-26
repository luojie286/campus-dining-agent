package com.sky.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.context.BaseContext;
import com.sky.entity.User;
import com.sky.mapper.UserMapper;
import com.sky.result.Result;
import com.sky.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public JwtTokenUserInterceptor(
            JwtUtil jwtUtil,
            UserMapper userMapper,
            ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws IOException {

        BaseContext.removeCurrentId();

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || !(handler instanceof HandlerMethod)) {
            return true;
        }

        String token = request.getHeader("token");

        if (token == null || token.isBlank()) {
            return reject(response, 401, "请先登录");
        }

        Long userId;
        String role;

        try {
            Claims claims = jwtUtil.parseToken(token);

            if (claims.getExpiration() == null) {
                return reject(response, 401, "登录凭证无效");
            }

            userId = Long.valueOf(claims.getSubject());
            role = claims.get("role", String.class);

            if (userId <= 0
                    || (!"user".equals(role)
                    && !"admin".equals(role))) {
                return reject(response, 401, "登录凭证无效");
            }
        } catch (JwtException | IllegalArgumentException exception) {
            return reject(response, 401, "登录凭证无效或已过期");
        }

        if (!"user".equals(role)) {
            return reject(response, 403, "无权访问点餐用户接口");
        }

        User user = userMapper.selectById(userId);

        if (user == null) {
            return reject(response, 401, "登录账号不存在");
        }

        if (!Integer.valueOf(1).equals(user.getStatus())) {
            return reject(response, 403, "账号已停用");
        }

        BaseContext.setCurrentId(userId);

        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception) {

        BaseContext.removeCurrentId();
    }

    private boolean reject(
            HttpServletResponse response,
            int status,
            String message) throws IOException {

        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");

        objectMapper.writeValue(
                response.getWriter(),
                Result.error(message)
        );

        return false;
    }
}