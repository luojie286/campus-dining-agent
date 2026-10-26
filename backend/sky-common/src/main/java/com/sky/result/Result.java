package com.sky.result;

/** 与课程一致的响应形状。只提供基础设施，不包含业务实现。 */
public record Result<T>(int code, String msg, T data) {
    public static <T> Result<T> success(T data) { return new Result<>(1, "", data); }
    public static Result<Void> error(String message) { return new Result<>(0, message, null); }
}
