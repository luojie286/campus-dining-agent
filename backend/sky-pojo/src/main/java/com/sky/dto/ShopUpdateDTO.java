package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ShopUpdateDTO {

    @NotNull(message = "营业状态不能为空")
    private Boolean open;

    @JsonSetter("open")
    public void setOpen(Object value) {
        if (value == null) {
            this.open = null;
            return;
        }

        if (!(value instanceof Boolean openValue)) {
            throw new IllegalArgumentException("open 必须是布尔值");
        }

        this.open = openValue;
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("请求包含不允许的字段");
    }
}