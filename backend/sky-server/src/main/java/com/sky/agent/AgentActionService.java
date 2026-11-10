package com.sky.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.context.BaseContext;
import com.sky.exception.ConflictException;
import com.sky.exception.ForbiddenException;
import com.sky.exception.NotFoundException;
import com.sky.mapper.AgentActionRepository;
import com.sky.mapper.OrderRepository;
import com.sky.service.AdminOrderService;
import com.sky.service.AuthenticatedPrincipalService;
import com.sky.service.OrderLifecycleService;
import com.sky.service.SetmealService;
import com.sky.vo.ActionConfirmVO;
import com.sky.vo.AgentActionVO;
import com.sky.vo.OrderVO;
import com.sky.vo.PendingActionVO;
import com.sky.vo.SetmealVO;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;

/**
 * 写操作两阶段：模型只能准备待确认卡片，真正的业务执行在确认接口里。
 * 并发闸门是数据库条件更新（markExecuting），不是 Redis 标记，也不是模型的一句话。
 */
@Service
@Profile("!bootstrap")
public class AgentActionService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final AgentActionRepository actions;
    private final OrderRepository orders;
    private final OrderLifecycleService lifecycle;
    private final AdminOrderService adminOrders;
    private final SetmealService setmeals;
    private final AgentProperties properties;
    private final ObjectMapper json;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AgentActionService(AgentActionRepository actions, OrderRepository orders,
            OrderLifecycleService lifecycle, AdminOrderService adminOrders,
            SetmealService setmeals, AgentProperties properties,
            ObjectMapper json, Clock clock) {
        this.actions = actions;
        this.orders = orders;
        this.lifecycle = lifecycle;
        this.adminOrders = adminOrders;
        this.setmeals = setmeals;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    public AgentToolResult prepareCancelOrder(JsonNode args,
            AuthenticatedPrincipalService.Principal principal, String sessionId) {
        long orderId = longArgument(args, "orderId");
        boolean isUser = "user".equals(principal.role());
        OrderVO order = (isUser ? orders.findOwned(principal.id(), orderId) : orders.findAny(orderId))
                .orElseThrow(() -> new NotFoundException("订单不存在"));
        if (!"UNPAID".equals(order.status()) || !"UNPAID".equals(order.payStatus())) {
            throw new ConflictException("助手目前只能准备取消待支付订单");
        }
        if (!"MOCK".equals(order.paymentChannel())) {
            throw new ConflictException("微信订单需先确认关单结果，不能通过助手取消");
        }

        ObjectNode params = json.createObjectNode();
        params.put("toolName", "prepareCancelOrder");
        params.put("orderId", orderId);
        params.put("expectedStatus", order.status());
        params.put("expectedPayStatus", order.payStatus());

        String label = "取消订单 " + order.number();
        String detail = "订单号 " + order.number() + "，当前状态 " + order.status()
                + "，金额 ¥" + order.amount() + "。未支付订单取消会释放预占库存；"
                + "确认时仍会复查归属、支付与状态。";
        PendingActionVO card = createCard("prepareCancelOrder", params, label, detail,
                principal, sessionId);
        String observation = "已生成待确认卡片（" + card.id() + "）：" + label
                + "。此步骤没有执行取消，必须等待用户确认。";
        return AgentToolResult.success("prepareCancelOrder", "已生成待确认卡片，未执行业务",
                observation, java.util.List.of(), java.util.List.of(), null, card);
    }

    public AgentToolResult prepareSetmealStatus(JsonNode args,
            AuthenticatedPrincipalService.Principal principal, String sessionId) {
        if (!"admin".equals(principal.role())) {
            throw new ForbiddenException("仅运营账号可以准备套餐上下架");
        }
        long setmealId = longArgument(args, "setmealId");
        int targetStatus = intArgument(args, "targetStatus", 0, 1);
        SetmealVO meal = setmeals.getById(setmealId);

        ObjectNode params = json.createObjectNode();
        params.put("toolName", "prepareSetmealStatus");
        params.put("setmealId", setmealId);
        params.put("targetStatus", targetStatus);
        params.put("expectedStatus", meal.getStatus() == null ? -1 : meal.getStatus());

        String label = (targetStatus == 1 ? "上架套餐 " : "下架套餐 ") + meal.getName();
        String detail = "套餐 " + meal.getName() + "（ID " + meal.getId() + "），当前状态 "
                + meal.getStatus() + "，目标状态 " + targetStatus
                + "。确认时会复查分类、组成与目标状态。";
        PendingActionVO card = createCard("prepareSetmealStatus", params, label, detail,
                principal, sessionId);
        String observation = "已生成待确认卡片（" + card.id() + "）：" + label
                + "。此步骤没有修改套餐，必须等待用户确认。";
        return AgentToolResult.success("prepareSetmealStatus", "已生成待确认卡片，未执行业务",
                observation, java.util.List.of(), java.util.List.of(), null, card);
    }

    /** 重复确认返回首次结果；撤销、过期或目标状态变化明确 409。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ActionConfirmVO confirm(String actionId,
            AuthenticatedPrincipalService.Principal principal) {
        AgentActionRepository.Row row = owned(actionId, principal);
        LocalDateTime now = LocalDateTime.now(clock);

        if (row.status() == AgentActionRepository.SUCCESS) {
            return new ActionConfirmVO(row.result() == null ? "操作已完成" : row.result());
        }
        if (row.status() != AgentActionRepository.PENDING) {
            throw new ConflictException("该确认记录已撤销、已过期或已处理");
        }
        if (!row.expireTime().isAfter(now)) {
            throw new ConflictException("确认卡片已过期，请重新发起操作");
        }
        if (!actions.markExecuting(actionId, principal.id(), principal.role(), now)) {
            throw new ConflictException("确认状态已变化，请刷新后重试");
        }

        String content = execute(row, principal);
        actions.markSuccess(actionId, content, LocalDateTime.now(clock));
        return new ActionConfirmVO(content);
    }

    public void cancel(String actionId, AuthenticatedPrincipalService.Principal principal) {
        AgentActionRepository.Row row = owned(actionId, principal);
        if (row.status() == AgentActionRepository.CANCELLED) return;
        if (row.status() != AgentActionRepository.PENDING) {
            throw new ConflictException("该记录已执行或已过期，不能撤销");
        }
        if (!actions.cancel(actionId, principal.id(), principal.role(), row.sessionId())) {
            throw new ConflictException("确认状态已变化，请刷新后重试");
        }
    }

    public AgentActionVO get(String actionId,
            AuthenticatedPrincipalService.Principal principal) {
        AgentActionRepository.Row row = owned(actionId, principal);
        return new AgentActionVO(row.actionId(), row.label(), row.detail(),
                offset(row.expireTime()), stateName(row.status()), row.result());
    }

    public void cancelBySession(String sessionId,
            AuthenticatedPrincipalService.Principal principal) {
        actions.cancelBySession(sessionId, principal.id(), principal.role());
    }

    private PendingActionVO createCard(String toolName, ObjectNode params, String label,
            String detail, AuthenticatedPrincipalService.Principal principal, String sessionId) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String actionId = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime expireTime = LocalDateTime.now(clock).plusMinutes(properties.actionTtlMinutes());
        actions.insert(actionId, principal.id(), principal.role(), sessionId, toolName,
                encode(params), label, detail, expireTime);
        return new PendingActionVO(actionId, label, detail, offset(expireTime));
    }

    private String execute(AgentActionRepository.Row row,
            AuthenticatedPrincipalService.Principal principal) {
        JsonNode params = readParams(row.params());
        String toolName = row.toolName();

        if ("prepareCancelOrder".equals(toolName)) {
            long orderId = params.path("orderId").asLong();
            OrderVO latest = orders.findAny(orderId)
                    .orElseThrow(() -> new ConflictException("订单不存在或已被清理"));
            boolean isUser = "user".equals(principal.role());
            if (isUser && latest.userId() != principal.id()) {
                throw new ConflictException("订单归属已变化");
            }
            if (!params.path("expectedStatus").asText("").equals(latest.status())
                    || !params.path("expectedPayStatus").asText("").equals(latest.payStatus())) {
                throw new ConflictException("订单状态已变化，请重新预览并确认");
            }
            // Agent 请求不经过用户/运营 JWT 拦截器，业务 Service 需要可信身份来自上下文。
            BaseContext.setCurrentId(principal.id());
            try {
                if (isUser) {
                    lifecycle.cancel(orderId);
                } else {
                    adminOrders.cancelUnpaidForAgent(orderId, "运营助手确认取消订单");
                }
            } finally {
                BaseContext.removeCurrentId();
            }
            return "订单 " + latest.number() + " 已取消。";
        }

        if ("prepareSetmealStatus".equals(toolName)) {
            long setmealId = params.path("setmealId").asLong();
            int targetStatus = params.path("targetStatus").asInt(-1);
            int expectedStatus = params.path("expectedStatus").asInt(-1);
            BaseContext.setCurrentId(principal.id());
            try {
                SetmealVO current = setmeals.updateStatusIfCurrent(setmealId, targetStatus, expectedStatus);
                return "套餐 " + current.getName() + " 已" + (targetStatus == 1 ? "上架" : "下架") + "。";
            } finally {
                BaseContext.removeCurrentId();
            }
        }

        throw new ConflictException("确认记录的工具不在白名单内");
    }

    private AgentActionRepository.Row owned(String actionId,
            AuthenticatedPrincipalService.Principal principal) {
        AgentActionRepository.Row row = actions.find(actionId)
                .orElseThrow(() -> new NotFoundException("确认记录不存在"));
        if (row.actorId() != principal.id() || !principal.role().equals(row.actorRole())) {
            // 他人资源按不存在处理，不泄漏是否存在。
            throw new NotFoundException("确认记录不存在");
        }
        return row;
    }

    private String stateName(int status) {
        return switch (status) {
            case AgentActionRepository.PENDING -> "PENDING";
            case AgentActionRepository.SUCCESS -> "SUCCESS";
            case AgentActionRepository.CANCELLED -> "CANCELLED";
            case AgentActionRepository.EXPIRED -> "EXPIRED";
            case AgentActionRepository.FAILED -> "FAILED";
            case AgentActionRepository.EXECUTING -> "EXECUTING";
            default -> "UNKNOWN";
        };
    }

    private String encode(ObjectNode node) {
        try {
            return json.writeValueAsString(node);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("确认参数无法序列化", exception);
        }
    }

    private JsonNode readParams(String value) {
        try {
            return json.readTree(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ConflictException("确认参数无法读取，请重新发起操作");
        }
    }

    private long longArgument(JsonNode args, String field) {
        if (args == null || !args.has(field) || (!args.path(field).isIntegralNumber() || !args.path(field).canConvertToLong())) {
            throw new com.sky.exception.BadRequestException(field + " 参数不合法");
        }
        long value = args.path(field).asLong();
        if (value < 1 || value > 9007199254740991L) {
            throw new com.sky.exception.BadRequestException(field + " 参数不合法");
        }
        return value;
    }

    private int intArgument(JsonNode args, String field, int min, int max) {
        if (args == null || !args.has(field) || (!args.path(field).isIntegralNumber() || !args.path(field).canConvertToInt())) {
            throw new com.sky.exception.BadRequestException(field + " 参数不合法");
        }
        int value = args.path(field).asInt();
        if (value < min || value > max) {
            throw new com.sky.exception.BadRequestException(field + " 参数超出范围");
        }
        return value;
    }

    private OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atZone(BUSINESS_ZONE).toOffsetDateTime();
    }
}
