package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.*;
import lombok.Getter;

@Getter
public class OrderSubmitDTO {
    @NotNull @Pattern(regexp = "DELIVERY|PICKUP")
    private String fulfillment;
    @Min(1) @Max(9007199254740991L)
    private Long addressId;
    @NotNull @Size(max = 300)
    private String remark;

    @JsonSetter("fulfillment")
    public void setFulfillment(Object value) { fulfillment = text(value); }

    @JsonSetter("remark")
    public void setRemark(Object value) { remark = text(value); }

    @JsonSetter(value = "addressId", nulls = Nulls.FAIL)
    public void setAddressId(Object value) {
        if (!(value instanceof Integer || value instanceof Long)) {
            throw new IllegalArgumentException("地址ID必须为整数");
        }
        addressId = ((Number) value).longValue();
    }

    @JsonAnySetter
    public void unknown(String field, Object value) {
        throw new IllegalArgumentException("下单请求包含不允许的字段");
    }

    private String text(Object value) {
        if (value == null || value instanceof String) return (String) value;
        throw new IllegalArgumentException("下单字段必须为字符串");
    }
}
