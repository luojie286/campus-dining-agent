package com.sky.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** 实时工作台：当日经营 + 全部日期未完结订单待办 + 当前商品上下架数量。 */
public record WorkbenchVO(LocalDate businessDate, ShopVO shop, BigDecimal revenue,
                          int paidOrders, int createdOrders, int completedOrders,
                          BigDecimal averageAmount, Map<String, Integer> pending,
                          StockCounts dishCounts, StockCounts setmealCounts,
                          String metricNote) {

    public record StockCounts(int onSale, int offSale) {
    }
}
