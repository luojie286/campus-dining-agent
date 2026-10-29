package com.sky.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class DishVO {

    // 对外商品ID，不是dish表的原始ID。
    private Long id;

    private String kind;
    private String name;
    private Long categoryId;
    private BigDecimal price;
    private String description;
    private Integer status;
    private Integer stock;
    private List<String> tags;
    private String art;

    // 当前只返回菜品，因此固定为空数组。
    private List<Object> composition;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String image;
}