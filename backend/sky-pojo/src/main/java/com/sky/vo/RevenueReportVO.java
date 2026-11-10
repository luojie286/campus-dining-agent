package com.sky.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 营业额：按支付成功时间统计历史实付，退款按退款成功时间统计，两者可以跨日。 */
public record RevenueReportVO(PeriodVO period, BigDecimal grossPaidAmount,
                              BigDecimal refundAmount, BigDecimal netCashAmount,
                              int paidOrders, List<Trend> trend, String metricNote) {

    public record Trend(LocalDate date, BigDecimal grossPaidAmount, BigDecimal refundAmount,
                        BigDecimal netCashAmount, int paidOrders) {
    }
}
