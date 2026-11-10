package com.sky.vo;

import java.time.OffsetDateTime;

/** 一次性 WebSocket 握手票据；票据本体只通过 Redis 保存，不写入响应之外的地方。 */
public record WsTicketVO(String ticket, OffsetDateTime expiresAt) {
}
