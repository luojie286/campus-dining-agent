package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.*;
import lombok.Getter;

@Getter
public class SetmealCompositionDTO {
    @NotNull @Min(2) @Max(9007199254740991L)
    private Long dishId;
    @NotNull @Min(1) @Max(99)
    private Integer quantity;

    @JsonSetter("dishId")
    public void setDishId(Object value) {
        if (value != null && !(value instanceof Integer) && !(value instanceof Long)) {
            throw new IllegalArgumentException("dishId必须是整数");
        }
        dishId = value == null ? null : ((Number) value).longValue();
    }

    @JsonSetter("quantity")
    public void setQuantity(Object value) {
        if (value != null && !(value instanceof Integer)) {
            throw new IllegalArgumentException("quantity必须是整数");
        }
        quantity = (Integer) value;
    }

    @JsonAnySetter
    public void rejectUnknown(String field, Object value) {
        throw new IllegalArgumentException("套餐组成包含不允许的字段");
    }
}
