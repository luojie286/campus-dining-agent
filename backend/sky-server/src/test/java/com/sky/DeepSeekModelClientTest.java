package com.sky;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.agent.AgentModelClient;
import com.sky.agent.AgentProperties;
import com.sky.agent.DeepSeekModelClient;
import com.sky.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 未配置密钥时明确 503，绝不伪造模型回答，也不会发出外部请求。 */
class DeepSeekModelClientTest {

    @Test
    void missingApiKeyFailsFast() {
        AgentProperties properties = mock(AgentProperties.class);
        when(properties.configured()).thenReturn(false);
        when(properties.modelTimeoutMs()).thenReturn(5000L);

        DeepSeekModelClient client = new DeepSeekModelClient(properties, new ObjectMapper());

        assertThrows(ServiceUnavailableException.class,
                () -> client.complete(List.of(AgentModelClient.ModelMessage.user("你好")), List.of()));
    }
}
