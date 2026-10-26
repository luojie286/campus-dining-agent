package com.sky.vo;

public record UserLoginVO(
        Long id,
        String name,
        String role,
        String token
) {}