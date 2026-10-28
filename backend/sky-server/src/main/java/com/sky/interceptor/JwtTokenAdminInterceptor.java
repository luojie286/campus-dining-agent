package com.sky.interceptor;

import org.springframework.context.annotation.Profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.context.BaseContext;
import com.sky.entity.Employee;
import com.sky.mapper.EmployeeMapper;
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
@Profile("!bootstrap")
public class JwtTokenAdminInterceptor implements HandlerInterceptor {
    private JwtUtil jwtUtil;
    private EmployeeMapper employeeMapper;
    private ObjectMapper objectMapper;

    public JwtTokenAdminInterceptor(
            JwtUtil jwtUtil,
            EmployeeMapper employeeMapper,
            ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.employeeMapper = employeeMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        //进入时先清理上下文
        BaseContext.removeCurrentId();
        //判断options请求,跨域预检
        if("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        //不在这里进行静态资源的处理
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String token = request.getHeader("token");

        //如果没有token就返回拒绝访问
        if(token == null ||token.isBlank()){
            return reject(response,401,"请先登录");
        }

        Long employeeId;
        String role;

        try {
            // 复用现有工具：校验签名、issuer 和过期时间。
            Claims claims = jwtUtil.parseToken(token);

            // 解析器会检查已有 exp 是否过期；
            // 这里额外拒绝缺少过期时间的 token。
            if (claims.getExpiration() == null) {
                return reject(response, 401, "登录凭证无效");
            }

            employeeId = Long.valueOf(claims.getSubject());
            role = claims.get("role", String.class);

            if (employeeId <= 0 || role == null) {
                return reject(response, 401, "登录凭证无效");
            }

            if (!"admin".equals(role) && !"user".equals(role)) {
                return reject(response, 401, "登录凭证无效");
            }

        } catch (JwtException | IllegalArgumentException exception) {
            return reject(response, 401, "登录凭证无效或已过期");
        }

        // 有效的员工端 token，也不能访问运营管理接口。
        if (!"admin".equals(role)) {
            return reject(response, 403, "无权访问管理端");
        }

        // JWT 有效，不代表账号现在仍存在、仍启用。
        Employee employee = employeeMapper.selectById(employeeId);

        if (employee == null) {
            return reject(response, 401, "登录账号不存在，请重新登录");
        }

        if (!Integer.valueOf(1).equals(employee.getStatus())) {
            return reject(response, 403, "账号已停用");
        }

        // 全部检查通过后，才保存当前身份。
        BaseContext.setCurrentId(employeeId);

        return true;
    }
    private boolean reject(HttpServletResponse response,
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

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        BaseContext.removeCurrentId();
    }
}
