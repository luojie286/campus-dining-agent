package com.sky.exception;

/** HTTP 接口之外的鉴权入口（WebSocket 票据、Agent 等）使用，统一映射 401。 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
