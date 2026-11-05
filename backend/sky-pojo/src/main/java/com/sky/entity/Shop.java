package com.sky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("shop")
public class Shop {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String name;

    // 数据库约定：1 营业，0 打烊。
    private Integer status;

    private String openingHours;
    private String pickupPoint;
}