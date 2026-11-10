package com.sky.controller;

import com.sky.result.Result;
import com.sky.service.AuthenticatedPrincipalService;
import com.sky.vo.WsTicketVO;
import com.sky.websocket.WsTicketService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 浏览器握手无法带 token 头，这里用当前登录凭证换取 30 秒单次票据。 */
@RestController
@Profile("!bootstrap")
@RequestMapping("/api/auth")
public class AuthTicketController {

    private final AuthenticatedPrincipalService principals;
    private final WsTicketService tickets;

    public AuthTicketController(AuthenticatedPrincipalService principals, WsTicketService tickets) {
        this.principals = principals;
        this.tickets = tickets;
    }

    @PostMapping("/ws-ticket")
    public Result<WsTicketVO> ticket(@RequestHeader(value = "token", required = false) String token) {
        return Result.success(tickets.issue(principals.require(token)));
    }
}
