package com.sky;

import com.sky.entity.User;
import com.sky.mapper.UserMapper;
import com.sky.exception.ForbiddenException;
import com.sky.exception.UnauthorizedException;
import com.sky.service.AuthenticatedPrincipalService;
import com.sky.util.JwtUtil;
import com.sky.websocket.WsTicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_WS_DB_TEST", matches = "true")
class WsTicketDatabaseTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtUtil jwt;
    @Autowired private UserMapper users;
    @Autowired private AuthenticatedPrincipalService principals;
    @Autowired private WsTicketService tickets;

    @Test
    void ticketIsSingleUseAndDisabledUserCannotFinishHandshake() {
        jdbc.update("INSERT INTO `user`(username,password,name,phone,status) VALUES (?,'unused','测试','',1)",
                "ticket-" + UUID.randomUUID());
        long userId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        String token = jwt.createToken(userId, "user");
        var principal = principals.require(token);

        var first = tickets.issue(principal);
        assertThat(tickets.consume(first.ticket()).id()).isEqualTo(userId);
        assertThatThrownBy(() -> tickets.consume(first.ticket())).isInstanceOf(UnauthorizedException.class);

        var second = tickets.issue(principal);
        User disabled = new User();
        disabled.setId(userId);
        disabled.setStatus(0);
        users.updateById(disabled);
        assertThatThrownBy(() -> tickets.consume(second.ticket())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> tickets.consume(second.ticket())).isInstanceOf(UnauthorizedException.class);
    }
}
