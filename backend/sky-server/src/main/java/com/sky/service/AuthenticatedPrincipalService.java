package com.sky.service;

import com.sky.exception.ForbiddenException;
import com.sky.constant.EmployeeRoleConstants;
import com.sky.exception.UnauthorizedException;
import com.sky.mapper.EmployeeMapper;
import com.sky.mapper.UserMapper;
import com.sky.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 拦截器之外的鉴权入口统一走这里：WebSocket 票据、握手和存活检查、Agent。
 * 每次都查数据库确认账号仍然存在且启用，不能只信任 JWT 里签发时的身份。
 */
@Service
@Profile("!bootstrap")
public class AuthenticatedPrincipalService {

    public record Principal(String role, long id, String token, Instant expiresAt) {
    }

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final EmployeeMapper employeeMapper;

    public AuthenticatedPrincipalService(JwtUtil jwtUtil, UserMapper userMapper,
            EmployeeMapper employeeMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
        this.employeeMapper = employeeMapper;
    }

    public Principal require(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("请先登录");
        }

        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new UnauthorizedException("登录凭证无效或已过期");
        }

        Instant expiresAt = claims.getExpiration() == null
                ? null : claims.getExpiration().toInstant();
        String role = claims.get("role", String.class);
        long id;
        try {
            id = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException | NullPointerException exception) {
            throw new UnauthorizedException("登录凭证无效");
        }

        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            throw new UnauthorizedException("登录凭证已过期");
        }
        if (id <= 0 || (!"admin".equals(role) && !"user".equals(role))) {
            throw new UnauthorizedException("登录凭证无效");
        }

        if ("admin".equals(role)) {
            var employee = employeeMapper.selectById(id);
            if (employee == null) throw new UnauthorizedException("登录账号不存在");
            if (!Integer.valueOf(1).equals(employee.getStatus())) {
                throw new ForbiddenException("账号已停用");
            }
            if (!EmployeeRoleConstants.ACCOUNT_ADMIN.equals(employee.getAccountRole())
                    && !EmployeeRoleConstants.OPERATOR.equals(employee.getAccountRole())) {
                throw new ForbiddenException("当前账号没有餐饮运营权限");
            }
        } else {
            var user = userMapper.selectById(id);
            if (user == null) throw new UnauthorizedException("登录账号不存在");
            if (!Integer.valueOf(1).equals(user.getStatus())) {
                throw new ForbiddenException("账号已停用");
            }
        }

        return new Principal(role, id, token, expiresAt);
    }
}
