package com.sky.vo;

public record AddressVO(long id, long userId, String consignee, String phone,
                        String building, String detail, boolean isDefault) {}
