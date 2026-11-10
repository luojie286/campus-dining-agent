package com.sky.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 订单统计：创建数与状态按创建时间，paidOrders 按付款时间，是两个时间口径。 */
public record OrderReportVO(PeriodVO period, int createdOrders, int paidOrders,
                            int validOrders, int completedOrders, int cancelledOrders,
                            BigDecimal completionRate, Map<String, Integer> counts,
                            List<Trend> trend, String metricNote) {

    public record Trend(LocalDate date, int createdOrders, int paidOrders,
                        int validOrders, int completedOrders, int cancelledOrders) {
    }
}
