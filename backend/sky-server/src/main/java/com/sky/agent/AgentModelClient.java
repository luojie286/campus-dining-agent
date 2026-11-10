package com.sky.agent;

import java.util.List;

/**
 * 模型适配层：供应商差异（消息格式、工具 Schema、流式）都留在实现类里。
 * 编排器只处理统一的消息与工具定义，不接触供应商协议。
 */
public interface AgentModelClient {

    ModelResponse complete(List<ModelMessage> messages, List<ToolDefinition> tools);

    record ModelMessage(String role, String content, String toolCallId, List<ToolCall> toolCalls) {
        public static ModelMessage system(String content) {
            return new ModelMessage("system", content, null, List.of());
        }

        public static ModelMessage user(String content) {
            return new ModelMessage("user", content, null, List.of());
        }

        public static ModelMessage assistant(String content, List<ToolCall> toolCalls) {
            return new ModelMessage("assistant", content, null, toolCalls);
        }

        public static ModelMessage tool(String callId, String content) {
            return new ModelMessage("tool", content, callId, List.of());
        }
    }

    record ToolCall(String id, String name, String arguments) {
    }

    /** parametersSchema 是 JSON Schema 字符串，由后端工具注册表生成。 */
    record ToolDefinition(String name, String description, String parametersSchema) {
    }

    record ModelResponse(String content, List<ToolCall> toolCalls) {
    }
}
