package com.sky.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.context.BaseContext;
import com.sky.service.AuthenticatedPrincipalService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具白名单：模型只能看到并调用这里登记的工具，不能执行任意类名、方法名或 SQL。
 * 身份与数据范围由执行器注入，参数 Schema 之外的字段由各工具自己再校验。
 */
@Component
@Profile("!bootstrap")
public class AgentToolRegistry {

    private record Tool(String name, String description, String parametersSchema,
                        Set<String> roles, boolean writes) {
    }

    private static final Set<String> ALL = Set.of("admin", "user");
    private static final Set<String> ADMIN = Set.of("admin");

    private static final String SEARCH_DISH_SCHEMA = """
            {"type":"object","properties":{
              "keyword":{"type":"string","maxLength":50,"description":"菜品名称关键词"},
              "maxPrice":{"type":"number","minimum":0,"maximum":9999,"description":"预算上限，人民币元"},
              "taste":{"type":"string","maxLength":20,"description":"已维护的固定标签，如不辣"},
              "limit":{"type":"integer","minimum":1,"maximum":20,"description":"最多返回的候选数"}
            }}
            """;

    private static final String QUERY_SETMEAL_SCHEMA = """
            {"type":"object","properties":{
              "keyword":{"type":"string","maxLength":50,"description":"套餐名称关键词"},
              "maxPrice":{"type":"number","minimum":0,"maximum":9999,"description":"预算上限，人民币元"},
              "setmealId":{"type":"integer","description":"指定套餐的对外商品ID"},
              "limit":{"type":"integer","minimum":1,"maximum":20,"description":"最多返回的候选数"}
            }}
            """;

    private static final String QUERY_ORDER_SCHEMA = """
            {"type":"object","properties":{
              "number":{"type":"string","maxLength":50,"description":"订单号关键词"},
              "status":{"type":"string","enum":["UNPAID","PREPARING","READY_DELIVERY","DELIVERING","READY_PICKUP","COMPLETED","CANCELLED"]},
              "begin":{"type":"string","description":"开始日期 YYYY-MM-DD"},
              "end":{"type":"string","description":"结束日期 YYYY-MM-DD"},
              "page":{"type":"integer","minimum":1,"maximum":1000},
              "pageSize":{"type":"integer","minimum":1,"maximum":20}
            }}
            """;

    private static final String METRICS_SCHEMA = """
            {"type":"object","properties":{
              "begin":{"type":"string","description":"开始日期 YYYY-MM-DD，缺省最近7天"},
              "end":{"type":"string","description":"结束日期 YYYY-MM-DD，缺省最近7天"}
            }}
            """;

    private static final String SALES_SCHEMA = """
            {"type":"object","properties":{
              "begin":{"type":"string","description":"开始日期 YYYY-MM-DD"},
              "end":{"type":"string","description":"结束日期 YYYY-MM-DD"},
              "kind":{"type":"string","enum":["dish","setmeal"]},
              "topN":{"type":"integer","minimum":1,"maximum":100}
            }}
            """;

    private static final String CANCEL_SCHEMA = """
            {"type":"object","properties":{
              "orderId":{"type":"integer","description":"要取消的订单ID"}
            },"required":["orderId"]}
            """;

    private static final String SETMEAL_STATUS_SCHEMA = """
            {"type":"object","properties":{
              "setmealId":{"type":"integer","description":"套餐对外商品ID"},
              "targetStatus":{"type":"integer","enum":[0,1],"description":"0下架，1上架"}
            },"required":["setmealId","targetStatus"]}
            """;

    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final AgentQueryTools queries;
    private final AgentActionService writes;
    private final ObjectMapper json;

    public AgentToolRegistry(AgentQueryTools queries, AgentActionService writes,
            ObjectMapper json) {
        this.queries = queries;
        this.writes = writes;
        this.json = json;

        register(new Tool("searchDish",
                "搜索当前可售菜品，可按名称、预算上限与固定标签硬过滤；返回候选ID、价格、可售数量。",
                SEARCH_DISH_SCHEMA, ALL, false));
        register(new Tool("querySetmeal",
                "查询当前可售套餐及组成，可按名称、预算与套餐ID硬过滤；套餐按整体计，不拆组成菜品。",
                QUERY_SETMEAL_SCHEMA, ALL, false));
        register(new Tool("queryOrder",
                "查询订单：点餐用户只能查本人订单，运营按授权范围查询；不返回电话与详细地址。",
                QUERY_ORDER_SCHEMA, ALL, false));
        register(new Tool("queryBusinessMetrics",
                "仅运营可用：查询营业额、退款、订单与用户的聚合指标及统一口径。",
                METRICS_SCHEMA, ADMIN, false));
        register(new Tool("queryDishSales",
                "仅运营可用：按有效成交明细查询菜品/套餐销量排名。",
                SALES_SCHEMA, ADMIN, false));
        register(new Tool("prepareCancelOrder",
                "仅准备取消本人或运营授权范围内的待支付订单，生成待确认卡片，不立即执行。",
                CANCEL_SCHEMA, ALL, true));
        register(new Tool("prepareSetmealStatus",
                "仅运营可用：准备套餐上下架，生成待确认卡片，不立即修改状态。",
                SETMEAL_STATUS_SCHEMA, ADMIN, true));
    }

    public List<AgentModelClient.ToolDefinition> definitionsFor(String role) {
        List<AgentModelClient.ToolDefinition> definitions = new ArrayList<>();
        for (Tool tool : tools.values()) {
            if (tool.roles().contains(role)) {
                definitions.add(new AgentModelClient.ToolDefinition(
                        tool.name(), tool.description(), tool.parametersSchema()));
            }
        }
        return definitions;
    }

    public AgentToolResult execute(String name, String argumentsJson,
            AuthenticatedPrincipalService.Principal principal, String sessionId) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return AgentToolResult.failure(name, "工具不在白名单内，已拒绝",
                    "工具调用被拒绝：只能使用后端提供的白名单工具。");
        }
        if (!tool.roles().contains(principal.role())) {
            return AgentToolResult.failure(name, "当前身份无权使用该工具",
                    "工具调用被拒绝：当前登录身份没有使用该工具的权限。");
        }

        JsonNode args;
        try {
            String source = argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson;
            args = json.readTree(source);
            if (args == null || !args.isObject()) {
                throw new IllegalArgumentException("参数必须是 JSON 对象");
            }
        } catch (Exception exception) {
            return AgentToolResult.failure(name, "工具参数不是合法 JSON",
                    "工具参数解析失败，请用登记的参数 Schema 重新调用。");
        }

        // 查询工具与写工具复用的业务 Service 从 BaseContext 读取可信身份。
        BaseContext.setCurrentId(principal.id());
        try {
            return dispatch(tool, args, principal, sessionId);
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null ? "工具执行失败" : exception.getMessage();
            return AgentToolResult.failure(name, message, "工具执行失败：" + message);
        } finally {
            BaseContext.removeCurrentId();
        }
    }

    private AgentToolResult dispatch(Tool tool, JsonNode args,
            AuthenticatedPrincipalService.Principal principal, String sessionId) {
        return switch (tool.name()) {
            case "searchDish" -> queries.searchDish(args);
            case "querySetmeal" -> queries.querySetmeal(args);
            case "queryOrder" -> queries.queryOrder(args, principal);
            case "queryBusinessMetrics" -> queries.queryBusinessMetrics(args);
            case "queryDishSales" -> queries.queryDishSales(args);
            case "prepareCancelOrder" -> writes.prepareCancelOrder(args, principal, sessionId);
            case "prepareSetmealStatus" -> writes.prepareSetmealStatus(args, principal, sessionId);
            default -> AgentToolResult.failure(tool.name(), "工具没有执行入口", "工具未实现。");
        };
    }

    private void register(Tool tool) {
        tools.put(tool.name(), tool);
    }
}
