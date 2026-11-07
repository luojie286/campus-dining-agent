package com.sky.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MenuRow {

    private Long rawId;
    private Long itemId;
    private String kind;
    private Long categoryId;
    private String name;
    private BigDecimal price;
    private String description;
    private Integer status;
    private Integer stock;
    private String tags;
    private String art;
    private String image;
}