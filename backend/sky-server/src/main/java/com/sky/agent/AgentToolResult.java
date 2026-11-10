package com.sky.agent;

import com.sky.vo.DashboardVO;
import com.sky.vo.MenuItemVO;
import com.sky.vo.OrderVO;
import com.sky.vo.PendingActionVO;
import com.sky.vo.ToolTraceVO;

import java.util.List;

/** 工具结果分两路：observation 是裁剪后给模型的文本，其余是按当前身份组装的前端卡片。 */
public record AgentToolResult(String observation, ToolTraceVO trace,
                              List<MenuItemVO> items, List<OrderVO> orders,
                              DashboardVO metrics, PendingActionVO action) {

    public static AgentToolResult success(String name, String summary, String observation) {
        return new AgentToolResult(observation, new ToolTraceVO(name, summary, "success"),
                List.of(), List.of(), null, null);
    }

    public static AgentToolResult success(String name, String summary, String observation,
            List<MenuItemVO> items, List<OrderVO> orders, DashboardVO metrics,
            PendingActionVO action) {
        return new AgentToolResult(observation, new ToolTraceVO(name, summary, "success"),
                items, orders, metrics, action);
    }

    public static AgentToolResult failure(String name, String summary, String observation) {
        return new AgentToolResult(observation, new ToolTraceVO(name, summary, "failed"),
                List.of(), List.of(), null, null);
    }
}
