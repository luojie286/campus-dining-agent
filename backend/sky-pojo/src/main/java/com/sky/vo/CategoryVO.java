package com.sky.vo;

public record CategoryVO(
        Long id,
        String name,
        Integer type,
        Integer sort,
        Integer status
) {
}