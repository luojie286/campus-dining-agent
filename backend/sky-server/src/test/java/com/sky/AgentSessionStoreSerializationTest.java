package com.sky;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.agent.AgentSessionStore;
import com.sky.vo.AgentReplyVO;
import com.sky.vo.ToolTraceVO;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Redis 里保存的是整段会话 JSON；验证 record 与 OffsetDateTime 能完整读回。 */
class AgentSessionStoreSerializationTest {

    @Test
    void sessionRoundTripKeepsReplyAndTraces() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        OffsetDateTime now = OffsetDateTime.parse("2026-11-10T12:00:00+08:00");
        AgentReplyVO reply = new AgentReplyVO("session-1", "推荐结果",
                List.of(), List.of(), null,
                List.of(new ToolTraceVO("searchDish", "返回候选", "success")), null);
        AgentSessionStore.StoredMessage message = new AgentSessionStore.StoredMessage(
                "message-1", "assistant", "推荐结果", now, reply);
        AgentSessionStore.StoredSession session = new AgentSessionStore.StoredSession(
                "session-1", "推荐午餐", now, now, List.of(message));

        String stored = mapper.writeValueAsString(session);
        AgentSessionStore.StoredSession restored =
                mapper.readValue(stored, AgentSessionStore.StoredSession.class);

        assertEquals("session-1", restored.id());
        assertEquals(1, restored.messages().size());
        assertEquals("推荐结果", restored.messages().getFirst().content());
        assertEquals("searchDish",
                restored.messages().getFirst().reply().traces().getFirst().name());
    }
}
