package com.sky.dto;

import lombok.Data;

@Data
public class MenuPartRow {

    private Long setmealId;
    private Long dishId;
    private String dishName;
    private Integer copies;
    private Integer dishStock;
    private Integer dishStatus;
    private Integer dishCategoryStatus;
    private Integer dishCategoryType;
}