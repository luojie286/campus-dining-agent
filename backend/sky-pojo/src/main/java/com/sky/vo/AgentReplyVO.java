package com.sky.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** 卡片数据由后端按当前身份另行组装，不把模型自由文本当成结构化事实。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentReplyVO(String sessionId, String content,
                           List<MenuItemVO> items, List<OrderVO> orders,
                           DashboardVO metrics, List<ToolTraceVO> traces,
                           PendingActionVO action) {
}
