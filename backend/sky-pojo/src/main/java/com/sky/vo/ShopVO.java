package com.sky.vo;

public record ShopVO(
        String name,
        boolean open,
        String openingHours,
        String pickupPoint
) {
}