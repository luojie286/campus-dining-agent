package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class WechatLoginDTO {

    @NotBlank(message = "微信登录凭证不能为空")
    @Size(max = 256, message = "微信登录凭证过长")
    private String code;

    @JsonSetter("code")
    public void setCode(Object value) {
        if (value != null && !(value instanceof String)) {
            throw new IllegalArgumentException("code 必须是字符串");
        }
        this.code = (String) value;
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("登录请求包含不允许的字段");
    }
}