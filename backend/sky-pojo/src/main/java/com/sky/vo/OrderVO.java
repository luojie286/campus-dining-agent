package com.sky.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderVO(long id, String number, long userId, String userName,
                      String status, String payStatus, String fulfillment, BigDecimal amount,
                      OffsetDateTime createdAt, OffsetDateTime paidAt, OffsetDateTime paymentDeadline,
                      String recipient, String phone, String address, String remark,
                      List<Line> lines, List<Event> events, String paymentChannel,
                      String refundStatus, String refundNo) {
    public OrderVO(long id, String number, long userId, String userName,
                   String status, String payStatus, String fulfillment, BigDecimal amount,
                   OffsetDateTime createdAt, OffsetDateTime paidAt, OffsetDateTime paymentDeadline,
                   String recipient, String phone, String address, String remark,
                   List<Line> lines, List<Event> events, String paymentChannel) {
        this(id, number, userId, userName, status, payStatus, fulfillment, amount, createdAt,
                paidAt, paymentDeadline, recipient, phone, address, remark, lines, events,
                paymentChannel, null, null);
    }

    public record Line(long itemId, String name, String kind, BigDecimal price, int quantity) {}
    public record Event(String status, OffsetDateTime time, String note) {}
}
