package com.sky.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentMessageVO(String id, String role, String content,
                             OffsetDateTime createdAt, AgentReplyVO reply) {
}
