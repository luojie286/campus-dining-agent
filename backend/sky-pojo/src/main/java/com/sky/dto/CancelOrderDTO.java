package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class CancelOrderDTO {
    @NotBlank @Size(max = 200)
    private String reason;

    @JsonSetter("reason")
    public void setReason(Object value) {
        if (value != null && !(value instanceof String)) throw new IllegalArgumentException("取消原因必须是字符串");
        reason = value == null ? null : ((String) value).trim();
    }

    @JsonAnySetter
    public void unknown(String field, Object value) {
        throw new IllegalArgumentException("取消订单请求包含不允许的字段");
    }
}
