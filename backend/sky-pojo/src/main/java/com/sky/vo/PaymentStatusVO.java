package com.sky.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentStatusVO(long orderId, String orderNumber, String channel,
                              String state, BigDecimal amount, OffsetDateTime paidAt) {}
