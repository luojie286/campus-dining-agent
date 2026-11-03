package com.sky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("setmeal")
public class Setmeal {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long categoryId;

    private String name;
    private BigDecimal price;
    private Integer status;
    private String description;
    private String image;

    private Integer stock;

    private String tags;

    private String art;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createUser;
    private Long updateUser;

}
