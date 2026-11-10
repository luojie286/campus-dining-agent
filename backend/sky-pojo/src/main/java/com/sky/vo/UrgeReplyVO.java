package com.sky.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

/** 第五次催单后已经没有下一次可催时间，因此 nextAllowedAt 允许为空。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UrgeReplyVO(long orderId, OffsetDateTime acceptedAt, OffsetDateTime nextAllowedAt,
                          int urgeCount, int remaining) {
}
