package com.sky.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * DeepSeek OpenAI 兼容接口：POST {baseUrl}/chat/completions，tools 走 function calling。
 * 未配置密钥或调用失败时明确返回 503，不伪造模型回答。
 */
@Service
@Profile("!bootstrap")
public class DeepSeekModelClient implements AgentModelClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekModelClient.class);

    private final AgentProperties properties;
    private final ObjectMapper json;
    private final HttpClient http;

    public DeepSeekModelClient(AgentProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(properties.modelTimeoutMs(), 3000)))
                .build();
    }

    @Override
    public ModelResponse complete(List<ModelMessage> messages, List<ToolDefinition> tools) {
        if (!properties.configured()) {
            throw new ServiceUnavailableException("DeepSeek 模型未配置：请设置 DEEPSEEK_API_KEY");
        }

        String requestBody = requestBody(messages, tools);
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofMillis(properties.modelTimeoutMs()))
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        long started = System.currentTimeMillis();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("模型请求被中断，请重试");
        } catch (IOException exception) {
            log.warn("DeepSeek 请求失败，耗时 {} ms", System.currentTimeMillis() - started);
            throw new ServiceUnavailableException("模型请求超时或服务不可用，请稍后重试");
        }

        if (response.statusCode() != 200) {
            log.warn("DeepSeek 返回 HTTP {}，耗时 {} ms", response.statusCode(),
                    System.currentTimeMillis() - started);
            throw new ServiceUnavailableException("模型服务暂不可用（HTTP " + response.statusCode() + "）");
        }

        return parse(response.body());
    }

    private String requestBody(List<ModelMessage> messages, List<ToolDefinition> tools) {
        ObjectNode root = json.createObjectNode();
        root.put("model", properties.model());
        root.put("temperature", 0.2);
        root.put("max_tokens", 1024);
        root.put("stream", false);

        ArrayNode messageArray = root.putArray("messages");
        for (ModelMessage message : messages) {
            ObjectNode node = messageArray.addObject();
            node.put("role", message.role());
            if (message.content() == null) {
                node.putNull("content");
            } else {
                node.put("content", message.content());
            }
            if (message.toolCallId() != null) {
                node.put("tool_call_id", message.toolCallId());
            }
            if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                ArrayNode calls = node.putArray("tool_calls");
                for (ToolCall call : message.toolCalls()) {
                    ObjectNode callNode = calls.addObject();
                    callNode.put("id", call.id());
                    callNode.put("type", "function");
                    ObjectNode function = callNode.putObject("function");
                    function.put("name", call.name());
                    function.put("arguments", call.arguments());
                }
            }
        }

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolArray = root.putArray("tools");
            for (ToolDefinition tool : tools) {
                ObjectNode toolNode = toolArray.addObject();
                toolNode.put("type", "function");
                ObjectNode function = toolNode.putObject("function");
                function.put("name", tool.name());
                function.put("description", tool.description());
                try {
                    function.set("parameters", json.readTree(tool.parametersSchema()));
                } catch (IOException exception) {
                    throw new IllegalStateException("工具参数 Schema 不合法：" + tool.name(), exception);
                }
            }
            root.put("tool_choice", "auto");
        }

        try {
            return json.writeValueAsString(root);
        } catch (IOException exception) {
            throw new IllegalStateException("模型请求无法序列化", exception);
        }
    }

    private ModelResponse parse(String body) {
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (IOException exception) {
            throw new ServiceUnavailableException("模型响应格式无法解析");
        }

        JsonNode message = root.path("choices").path(0).path("message");
        String content = message.path("content").isTextual() ? message.path("content").asText() : "";
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode call : message.path("tool_calls")) {
            JsonNode function = call.path("function");
            String name = function.path("name").asText("");
            if (name.isBlank()) continue;
            String arguments = function.path("arguments").asText("{}");
            calls.add(new ToolCall(call.path("id").asText(""), name, arguments));
        }
        return new ModelResponse(content, calls);
    }
}
