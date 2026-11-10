package com.sky.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.dto.DishPageQueryDTO;
import com.sky.dto.OrderPageQueryDTO;
import com.sky.exception.BadRequestException;
import com.sky.result.PageResult;
import com.sky.service.AdminOrderService;
import com.sky.service.AuthenticatedPrincipalService;
import com.sky.service.MenuService;
import com.sky.service.ReportService;
import com.sky.service.UserOrderService;
import com.sky.vo.DashboardVO;
import com.sky.vo.MenuItemVO;
import com.sky.vo.OrderVO;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 查询工具：只调用已经验收的业务 Service，参数先做硬过滤再交给模型。
 * 传给模型的 observation 是裁剪后的文本，不包含收餐电话、详细地址和完整订单实体。
 */
@Component
@Profile("!bootstrap")
public class AgentQueryTools {

    private final MenuService menu;
    private final UserOrderService userOrders;
    private final AdminOrderService adminOrders;
    private final ReportService reports;
    private final AgentProperties properties;
    private final ObjectMapper json;

    public AgentQueryTools(MenuService menu, UserOrderService userOrders,
            AdminOrderService adminOrders, ReportService reports,
            AgentProperties properties, ObjectMapper json) {
        this.menu = menu;
        this.userOrders = userOrders;
        this.adminOrders = adminOrders;
        this.reports = reports;
        this.properties = properties;
        this.json = json;
    }

    public AgentToolResult searchDish(JsonNode args) {
        String keyword = text(args, "keyword", 50);
        BigDecimal maxPrice = price(args, "maxPrice");
        String taste = text(args, "taste", 20);
        int limit = limit(args, "limit", 20);

        List<MenuItemVO> candidates = menuCandidates(keyword, "dish", maxPrice, taste, limit, null);
        return AgentToolResult.success("searchDish",
                "返回 " + candidates.size() + " 个可售菜品候选",
                itemObservation(candidates, maxPrice, taste), candidates, List.of(), null, null);
    }

    public AgentToolResult querySetmeal(JsonNode args) {
        String keyword = text(args, "keyword", 50);
        BigDecimal maxPrice = price(args, "maxPrice");
        int limit = limit(args, "limit", 20);
        Long setmealId = args.has("setmealId") && args.path("setmealId").canConvertToLong()
                ? args.path("setmealId").asLong() : null;

        List<MenuItemVO> candidates = menuCandidates(keyword, "setmeal", maxPrice, null, limit, setmealId);
        return AgentToolResult.success("querySetmeal",
                "返回 " + candidates.size() + " 个可售套餐候选",
                itemObservation(candidates, maxPrice, null), candidates, List.of(), null, null);
    }

    public AgentToolResult queryOrder(JsonNode args,
            AuthenticatedPrincipalService.Principal principal) {
        OrderPageQueryDTO query = new OrderPageQueryDTO();
        query.setPage(pageNumber(args));
        query.setPageSize(Math.min(limit(args, "pageSize", 20), 20));
        query.setName(nullIfBlank(text(args, "number", 50)));
        query.setBegin(nullIfBlank(text(args, "begin", 10)));
        query.setEnd(nullIfBlank(text(args, "end", 10)));

        String status = text(args, "status", 30);
        if (!status.isEmpty()) {
            try {
                query.setStatus(OrderPageQueryDTO.State.valueOf(status));
            } catch (IllegalArgumentException exception) {
                throw new BadRequestException("订单状态不合法");
            }
        }

        boolean isUser = "user".equals(principal.role());
        PageResult<OrderVO> page = isUser ? userOrders.page(query) : adminOrders.page(query);
        List<OrderVO> orders = page.records();

        return AgentToolResult.success("queryOrder",
                "返回 " + orders.size() + " 条订单，共 " + page.total() + " 条",
                orderObservation(orders, page.total(), query.getPageSize(), principal.role()),
                List.of(), orders, null, null);
    }

    public AgentToolResult queryBusinessMetrics(JsonNode args) {
        String begin = nullIfBlank(text(args, "begin", 10));
        String end = nullIfBlank(text(args, "end", 10));
        var revenue = reports.revenue(begin, end);
        var orders = reports.orders(begin, end);
        var users = reports.users(begin, end);
        DashboardVO metrics = reports.overview(begin, end);

        StringBuilder observation = new StringBuilder();
        observation.append("统计范围 ").append(metrics.period().begin())
                .append(" 至 ").append(metrics.period().end()).append("，单位人民币元。\n");
        observation.append("历史实付总额 ").append(revenue.grossPaidAmount())
                .append("，退款 ").append(revenue.refundAmount())
                .append("，净现金 ").append(revenue.netCashAmount())
                .append("，支付订单 ").append(revenue.paidOrders()).append(" 笔。\n");
        observation.append("创建订单 ").append(orders.createdOrders())
                .append("，有效订单 ").append(orders.validOrders())
                .append("，完成 ").append(orders.completedOrders())
                .append("，完成率 ").append(orders.completionRate()).append("%。\n");
        observation.append("新增用户 ").append(users.newUsers())
                .append("，累计用户 ").append(users.totalUsers())
                .append("，下单用户 ").append(users.orderingUsers())
                .append("，付款用户 ").append(users.payingUsers()).append("。\n");
        observation.append("口径：").append(revenue.metricNote()).append("\n");
        observation.append("口径：").append(orders.metricNote());

        return AgentToolResult.success("queryBusinessMetrics", "返回聚合指标与口径",
                trim(observation.toString()), List.of(), List.of(), metrics, null);
    }

    public AgentToolResult queryDishSales(JsonNode args) {
        String begin = nullIfBlank(text(args, "begin", 10));
        String end = nullIfBlank(text(args, "end", 10));
        String kind = nullIfBlank(text(args, "kind", 10));
        Integer topN = args.has("topN") && !args.path("topN").isNull()
                ? limit(args, "topN", 100) : null;
        var sales = reports.sales(begin, end, kind, topN);

        StringBuilder observation = new StringBuilder();
        observation.append("统计范围 ").append(sales.period().begin())
                .append(" 至 ").append(sales.period().end()).append("，单位人民币元。\n");
        int returned = Math.min(sales.items().size(), properties.maxToolResultRows());
        for (int index = 0; index < returned; index++) {
            var item = sales.items().get(index);
            observation.append(index + 1).append(". [ID:").append(item.id()).append("] ")
                    .append(item.name()).append("，销量 ").append(item.quantity())
                    .append(" 份，销售额 ").append(item.amount()).append("\n");
        }
        if (sales.items().isEmpty()) observation.append("该范围没有有效成交明细。\n");
        observation.append("共返回 ").append(returned).append(" 条，完整结果 ")
                .append(sales.items().size()).append(" 条；结果是否完整：")
                .append(sales.items().size() <= returned ? "是" : "否（已截断）").append("。\n");
        observation.append("口径：").append(sales.metricNote());

        return AgentToolResult.success("queryDishSales", "返回销量排名",
                trim(observation.toString()), List.of(), List.of(), null, null);
    }

    private List<MenuItemVO> menuCandidates(String keyword, String kind, BigDecimal maxPrice,
            String taste, int limit, Long exactId) {
        DishPageQueryDTO query = new DishPageQueryDTO();
        query.setPage(1);
        query.setPageSize(100);
        query.setName(nullIfBlank(keyword));
        query.setStatus(1);

        PageResult<MenuItemVO> page = menu.pageForUser(query);
        List<MenuItemVO> candidates = new ArrayList<>();
        for (MenuItemVO item : page.records()) {
            if (!kind.equals(item.kind())) continue;
            if (item.status() == null || item.status() != 1) continue;
            if (item.availableStock() <= 0) continue;
            if (exactId != null && !exactId.equals(item.id())) continue;
            if (maxPrice != null && item.price() != null && item.price().compareTo(maxPrice) > 0) continue;
            if (taste != null && !taste.isEmpty()
                    && (item.tags() == null || !item.tags().contains(taste))) continue;
            candidates.add(item);
            if (candidates.size() >= limit) break;
        }
        return candidates;
    }

    private String itemObservation(List<MenuItemVO> candidates, BigDecimal maxPrice, String taste) {
        StringBuilder observation = new StringBuilder();
        observation.append("候选商品共返回 ").append(candidates.size())
                .append(" 条；金额单位人民币元。\n");
        for (MenuItemVO item : candidates) {
            List<String> tags = item.tags() == null ? List.of() : item.tags();
            observation.append("[ID:").append(item.id()).append("] ").append(item.name())
                    .append("，¥").append(item.price()).append("，可售 ").append(item.availableStock())
                    .append(" 份，标签 ").append(String.join("/", tags))
                    .append("\n");
        }
        if (candidates.isEmpty()) observation.append("没有符合硬条件的候选，不能编造商品或放宽预算。\n");
        if (maxPrice != null) observation.append("已应用预算上限 ").append(maxPrice).append(" 元。\n");
        if (taste != null && !taste.isEmpty()) observation.append("已应用标签条件 ").append(taste).append("。\n");
        observation.append("结果为候选快照，下单时以数据库实时价格与库存校验为准。");
        return trim(observation.toString());
    }

    private String orderObservation(List<OrderVO> orders, long total, int pageSize, String role) {
        StringBuilder observation = new StringBuilder();
        observation.append("身份范围：").append("user".equals(role) ? "仅本人订单" : "运营授权范围")
                .append("；返回 ").append(orders.size()).append(" 条，匹配总数 ").append(total)
                .append("，每页上限 ").append(pageSize).append(" 条。\n");
        for (OrderVO order : orders) {
            observation.append("订单号 ").append(order.number()).append("，状态 ").append(order.status())
                    .append("，支付状态 ").append(order.payStatus())
                    .append("，金额 ¥").append(order.amount())
                    .append("，下单时间 ").append(order.createdAt()).append("\n");
            for (OrderVO.Line line : order.lines()) {
                observation.append("  - ").append(line.name()).append(" ×").append(line.quantity()).append("\n");
            }
        }
        if (orders.isEmpty()) observation.append("该范围没有订单，不要编造订单号或送达时间。\n");
        observation.append("已裁剪：不提供收餐电话与详细地址。");
        return trim(observation.toString());
    }

    private String trim(String value) {
        int max = properties.maxToolResultChars();
        if (value.length() <= max) return value;
        return value.substring(0, max) + "\n[结果已截断，完整结果请缩小查询范围]";
    }

    public String encodeParams(ObjectNode node) {
        try {
            return json.writeValueAsString(node);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("确认参数无法序列化", exception);
        }
    }

    private String text(JsonNode args, String field, int maxLength) {
        if (args == null || !args.has(field) || args.path(field).isNull()) return "";
        String value = args.path(field).asText("").trim();
        if (value.length() > maxLength) throw new BadRequestException(field + " 参数过长");
        return value;
    }

    private BigDecimal price(JsonNode args, String field) {
        String value = text(args, field, 20);
        if (value.isEmpty()) return null;
        try {
            BigDecimal price = new BigDecimal(value);
            if (price.signum() < 0 || price.compareTo(new BigDecimal("9999")) > 0) {
                throw new BadRequestException("预算金额不合法");
            }
            return price;
        } catch (NumberFormatException exception) {
            throw new BadRequestException("预算金额格式不合法");
        }
    }

    private int limit(JsonNode args, String field, int max) {
        if (args == null || !args.has(field) || args.path(field).isNull()) return Math.min(10, max);
        int value = args.path(field).asInt(0);
        if (value < 1 || value > max) throw new BadRequestException(field + " 超出允许范围");
        return value;
    }

    private int pageNumber(JsonNode args) {
        if (args == null || !args.has("page") || args.path("page").isNull()) return 1;
        int value = args.path("page").asInt(0);
        if (value < 1 || value > 1000) throw new BadRequestException("页码不合法");
        return value;
    }

    private String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
