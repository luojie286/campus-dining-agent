package com.sky.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class SetmealVO {

    // 对外商品ID，不是setmeal表的原始ID。
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

    // 组成中使用对外菜品ID。
    private List<CompositionVO> composition;

    public record CompositionVO(Long dishId, Integer quantity) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String image;
}