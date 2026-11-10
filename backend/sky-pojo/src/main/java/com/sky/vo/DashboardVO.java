package com.sky.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 兼容旧工作台页面的经营概览；字段形状保持不变，口径说明写在 metricNote。 */
public record DashboardVO(BigDecimal revenue, int paidOrders, BigDecimal averageAmount,
                          int completedOrders, BigDecimal cancellationRate, int orderCount,
                          Map<String, Integer> counts, List<TrendPoint> trend,
                          List<SalesItemVO> sales, PeriodVO period, String metricNote) {

    public record TrendPoint(LocalDate date, BigDecimal revenue, int orders) {
    }
}
