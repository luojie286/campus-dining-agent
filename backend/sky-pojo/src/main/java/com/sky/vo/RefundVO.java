package com.sky.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RefundVO(String refundNo, long orderId, String channel, String state,
                       BigDecimal amount, String reason, OffsetDateTime requestedAt,
                       OffsetDateTime refundedAt) {}
