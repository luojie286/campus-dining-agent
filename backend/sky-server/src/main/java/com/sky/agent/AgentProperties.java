package com.sky.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Agent 的所有上限集中在这里，模型不能通过文本修改。
 * 密钥只从环境变量 DEEPSEEK_API_KEY 注入，不写日志、不下发前端。
 */
@Component
@Profile("!bootstrap")
public class AgentProperties {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final boolean enabled;
    private final long modelTimeoutMs;
    private final long totalBudgetMs;
    private final int maxModelCalls;
    private final int maxToolCalls;
    private final int maxContextChars;
    private final int maxToolResultChars;
    private final int maxToolResultRows;
    private final long sessionTtlHours;
    private final int maxVisibleMessages;
    private final long actionTtlMinutes;

    public AgentProperties(
            @Value("${sky.agent.api-key:}") String apiKey,
            @Value("${sky.agent.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${sky.agent.model:deepseek-chat}") String model,
            @Value("${sky.agent.enabled:true}") boolean enabled,
            @Value("${sky.agent.model-timeout-ms:5000}") long modelTimeoutMs,
            @Value("${sky.agent.total-budget-ms:15000}") long totalBudgetMs,
            @Value("${sky.agent.max-model-calls:5}") int maxModelCalls,
            @Value("${sky.agent.max-tool-calls:8}") int maxToolCalls,
            @Value("${sky.agent.max-context-chars:32000}") int maxContextChars,
            @Value("${sky.agent.max-tool-result-chars:8000}") int maxToolResultChars,
            @Value("${sky.agent.max-tool-result-rows:20}") int maxToolResultRows,
            @Value("${sky.agent.session-ttl-hours:24}") long sessionTtlHours,
            @Value("${sky.agent.max-visible-messages:30}") int maxVisibleMessages,
            @Value("${sky.agent.action-ttl-minutes:5}") long actionTtlMinutes) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.enabled = enabled;
        this.modelTimeoutMs = modelTimeoutMs;
        this.totalBudgetMs = totalBudgetMs;
        this.maxModelCalls = maxModelCalls;
        this.maxToolCalls = maxToolCalls;
        this.maxContextChars = maxContextChars;
        this.maxToolResultChars = maxToolResultChars;
        this.maxToolResultRows = maxToolResultRows;
        this.sessionTtlHours = sessionTtlHours;
        this.maxVisibleMessages = maxVisibleMessages;
        this.actionTtlMinutes = actionTtlMinutes;
    }

    public boolean configured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public String apiKey() { return apiKey; }
    public String baseUrl() { return baseUrl; }
    public String model() { return model; }
    public long modelTimeoutMs() { return modelTimeoutMs; }
    public long totalBudgetMs() { return totalBudgetMs; }
    public int maxModelCalls() { return maxModelCalls; }
    public int maxToolCalls() { return maxToolCalls; }
    public int maxContextChars() { return maxContextChars; }
    public int maxToolResultChars() { return maxToolResultChars; }
    public int maxToolResultRows() { return maxToolResultRows; }
    public long sessionTtlHours() { return sessionTtlHours; }
    public int maxVisibleMessages() { return maxVisibleMessages; }
    public long actionTtlMinutes() { return actionTtlMinutes; }
}
