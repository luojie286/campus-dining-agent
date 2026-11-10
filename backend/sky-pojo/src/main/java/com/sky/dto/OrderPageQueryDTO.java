package com.sky.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderPageQueryDTO {
    @NotNull @Min(1) @Max(1000000)
    private Integer page = 1;

    @NotNull @Min(1) @Max(100)
    private Integer pageSize = 8;

    @Size(max = 100)
    private String name;

    private State status;

    @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private String begin;

    @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private String end;

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }

    public enum State {
        UNPAID(1), PREPARING(2), READY_DELIVERY(3), DELIVERING(4),
        COMPLETED(5), CANCELLED(6), READY_PICKUP(7);

        private final int code;

        State(int code) { this.code = code; }

        public int code() { return code; }

        public static State fromCode(int code) {
            for (State state : values()) {
                if (state.code == code) return state;
            }
            throw new IllegalStateException("订单状态不合法");
        }
    }
}
