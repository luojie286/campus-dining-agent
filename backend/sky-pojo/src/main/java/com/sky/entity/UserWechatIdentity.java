package com.sky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_wechat_identity")
public class UserWechatIdentity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String appId;
    private String openId;
}