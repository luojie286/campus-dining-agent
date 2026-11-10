package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

/** 购物车请求只接受商品和数量，价格与用户由服务端确定。 */
public final class CartRequest {
    private CartRequest() {}

    public static class StrictFields {
        @JsonAnySetter
        public void unknown(String name, Object value) {
            throw new IllegalArgumentException("购物车请求包含不允许的字段");
        }
    }

    @Getter
    public static class Quantity extends StrictFields {
        @NotNull @Min(1) @Max(99)
        private Integer quantity;

        @JsonSetter("quantity")
        public void setQuantity(Object value) {
            if (value != null && !(value instanceof Integer)) {
                throw new IllegalArgumentException("quantity必须为整数");
            }
            quantity = (Integer) value;
        }
    }

    @Getter
    public static class Item extends Quantity {
        @NotNull @Min(2) @Max(9007199254740991L)
        private Long itemId;

        @JsonSetter("itemId")
        public void setItemId(Object value) {
            if (value == null) {
                itemId = null;
            } else if (value instanceof Long || value instanceof Integer) {
                itemId = ((Number) value).longValue();
            } else {
                throw new IllegalArgumentException("itemId必须为整数");
            }
        }
    }

    @Getter
    public static class Replace extends StrictFields {
        @NotNull @Size(max = 100) @Valid
        private List<@NotNull Item> lines;

        @JsonSetter("lines")
        public void setLines(List<Item> lines) {
            this.lines = lines;
        }
    }
}
