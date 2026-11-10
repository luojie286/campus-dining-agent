package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.*;
import lombok.Getter;

@Getter
public class AddressSaveDTO {
    @NotNull @Min(0) @Max(9007199254740991L)
    private Long id;
    @Min(1) @Max(9007199254740991L)
    private Long userId;
    @NotBlank @Size(max = 30)
    private String consignee;
    @NotBlank @Pattern(regexp = "1[0-9]{10}")
    private String phone;
    @NotBlank @Size(max = 50)
    private String building;
    @NotBlank @Size(max = 100)
    private String detail;
    @NotNull
    private Boolean isDefault;

    @JsonSetter("id")
    public void setId(Object value) { id = integer(value); }

    @JsonSetter(value = "userId", nulls = Nulls.FAIL)
    public void setUserId(Object value) { userId = integer(value); }

    @JsonSetter("consignee")
    public void setConsignee(Object value) { consignee = text(value); }

    @JsonSetter("phone")
    public void setPhone(Object value) { phone = text(value); }

    @JsonSetter("building")
    public void setBuilding(Object value) { building = text(value); }

    @JsonSetter("detail")
    public void setDetail(Object value) { detail = text(value); }

    @JsonSetter("isDefault")
    public void setIsDefault(Object value) {
        if (value != null && !(value instanceof Boolean)) {
            throw new IllegalArgumentException("isDefault必须为布尔值");
        }
        isDefault = (Boolean) value;
    }

    @JsonAnySetter
    public void unknown(String field, Object value) {
        throw new IllegalArgumentException("地址请求包含不允许的字段");
    }

    private Long integer(Object value) {
        if (value == null) return null;
        if (value instanceof Integer || value instanceof Long) return ((Number) value).longValue();
        throw new IllegalArgumentException("地址ID必须为整数");
    }

    private String text(Object value) {
        if (value == null) return null;
        if (value instanceof String string) return string.trim();
        throw new IllegalArgumentException("地址字段必须为字符串");
    }
}
