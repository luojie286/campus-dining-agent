package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class EmployeeStatusUpdateDTO {

    @NotNull(message = "账号状态不能为空")
    @Min(value = 0, message = "账号状态只能为0或1")
    @Max(value = 1, message = "账号状态只能为0或1")
    private Integer status;

    @JsonSetter("status")
    public void setStatus(Object value) {
        if (value == null) {
            this.status = null;
            return;
        }

        if (!(value instanceof Integer integer)) {
            throw new IllegalArgumentException(
                    "账号状态必须是整数0或1"
            );
        }

        this.status = integer;
    }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException(
                "状态修改请求包含不允许的字段"
        );
    }
}