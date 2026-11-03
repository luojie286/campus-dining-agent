package com.sky.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetmealPageQueryDTO {
    @NotNull @Min(1) @Max(1000000)
    private Integer page = 1;

    @NotNull @Min(1) @Max(100)
    private Integer pageSize = 8;

    @Size(max = 100)
    private String name;

    @Min(1) @Max(9007199254740991L)
    private Long categoryId;

    @Min(0) @Max(1)
    private Integer status;

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }
}
