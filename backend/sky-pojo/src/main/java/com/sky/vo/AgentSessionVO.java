package com.sky.vo;

import java.time.OffsetDateTime;

public record AgentSessionVO(String id, String title,
                             OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
