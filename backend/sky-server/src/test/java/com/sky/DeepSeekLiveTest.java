package com.sky;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.agent.AgentModelClient;
import com.sky.agent.AgentProperties;
import com.sky.agent.DeepSeekModelClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实模型联调：默认跳过，只有显式设置 RUN_DEEPSEEK_TEST=true 且存在 DEEPSEEK_API_KEY 时才运行。
 * 这样默认测试不会消耗额度；模拟测试永远不能替代这次真实 Function Calling 验收。
 */
class DeepSeekLiveTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_DEEPSEEK_TEST", matches = "true")
    void realModelAcceptsToolDefinitions() {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "缺少 DEEPSEEK_API_KEY");

        AgentProperties properties = new AgentProperties(apiKey,
                "https://api.deepseek.com", "deepseek-chat", true,
                15000L, 15000L, 2, 2, 32000, 8000, 20, 24, 30, 5);
        DeepSeekModelClient client = new DeepSeekModelClient(properties, new ObjectMapper());
        AgentModelClient.ToolDefinition tool = new AgentModelClient.ToolDefinition(
                "searchDish", "搜索当前可售菜品", "{\"type\":\"object\",\"properties\":{}}");

        AgentModelClient.ModelResponse response = client.complete(
                List.of(AgentModelClient.ModelMessage.user("请用 searchDish 工具查询可售菜品，或直接说明不需要工具。")),
                List.of(tool));

        assertTrue(!response.content().isBlank() || !response.toolCalls().isEmpty(),
                "模型应返回文本或工具调用");
    }
}
