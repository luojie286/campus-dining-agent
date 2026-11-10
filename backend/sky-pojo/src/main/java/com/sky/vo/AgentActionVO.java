package com.sky.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentActionVO(String id, String label, String detail,
                            OffsetDateTime expiresAt, String state, String result) {
}
