package com.sky.vo;

import java.time.OffsetDateTime;

/** 待确认卡片：参数由后端生成后不可由客户端覆盖。 */
public record PendingActionVO(String id, String label, String detail, OffsetDateTime expiresAt) {
}
