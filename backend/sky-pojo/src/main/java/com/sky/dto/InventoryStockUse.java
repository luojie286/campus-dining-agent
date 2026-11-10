package com.sky.dto;

/** 下单扣减快照使用原始数据库ID，取消时按原记录释放。 */
public record InventoryStockUse(String kind, long id, int quantity) {}
