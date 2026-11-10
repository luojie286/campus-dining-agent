package com.sky.controller;

import com.sky.agent.AgentActionService;
import com.sky.agent.AgentOrchestrator;
import com.sky.agent.AgentSessionStore;
import com.sky.dto.AgentChatDTO;
import com.sky.exception.BadRequestException;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.AuthenticatedPrincipalService;
import com.sky.vo.ActionConfirmVO;
import com.sky.vo.AgentActionVO;
import com.sky.vo.AgentMessageVO;
import com.sky.vo.AgentReplyVO;
import com.sky.vo.AgentSessionVO;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/agent/** 不经过 /api/admin 或 /api/user 的 MVC 拦截器，
 * 因此这里每个入口都用同一个可信身份服务复验 token 与账号状态。
 */
@RestController
@Profile("!bootstrap")
@RequestMapping("/api/agent")
public class AgentController {

    private final AuthenticatedPrincipalService principals;
    private final AgentOrchestrator orchestrator;
    private final AgentSessionStore sessions;
    private final AgentActionService actions;

    public AgentController(AuthenticatedPrincipalService principals,
            AgentOrchestrator orchestrator, AgentSessionStore sessions,
            AgentActionService actions) {
        this.principals = principals;
        this.orchestrator = orchestrator;
        this.sessions = sessions;
        this.actions = actions;
    }

    @PostMapping("/chat")
    public Result<AgentReplyVO> chat(
            @RequestHeader(value = "token", required = false) String token,
            @Valid @RequestBody AgentChatDTO body) {
        var principal = principals.require(token);
        return Result.success(orchestrator.chat(principal, body.getSessionId(), body.getMessage()));
    }

    @GetMapping("/sessions/page")
    public Result<PageResult<AgentSessionVO>> sessions(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int pageSize) {
        var principal = principals.require(token);
        validatePage(page, pageSize);
        return Result.success(sessions.page(principal, page, pageSize));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<PageResult<AgentMessageVO>> messages(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable("sessionId") String sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int pageSize) {
        var principal = principals.require(token);
        validatePage(page, pageSize);
        return Result.success(sessions.messages(principal, sessionId, page, pageSize));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable("sessionId") String sessionId) {
        var principal = principals.require(token);
        sessions.delete(principal, sessionId);
        // 会话删除后，它下面待确认的写操作立即失效；已执行成功的业务不回滚。
        actions.cancelBySession(sessionId, principal);
        return Result.success(null);
    }

    @GetMapping("/actions/{actionId}")
    public Result<AgentActionVO> action(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable("actionId") String actionId) {
        return Result.success(actions.get(actionId, principals.require(token)));
    }

    @PostMapping("/actions/{actionId}/confirm")
    public Result<ActionConfirmVO> confirm(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable("actionId") String actionId) {
        return Result.success(actions.confirm(actionId, principals.require(token)));
    }

    @PostMapping("/actions/{actionId}/cancel")
    public Result<Void> cancel(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable("actionId") String actionId) {
        actions.cancel(actionId, principals.require(token));
        return Result.success(null);
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || page > 1000000 || pageSize < 1 || pageSize > 100) {
            throw new BadRequestException("分页参数不合法");
        }
    }
}
