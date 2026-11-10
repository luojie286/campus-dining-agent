package com.sky.vo;

import java.util.List;

/** 销量排行：按付款时间的有效成交明细聚合，套餐不拆成组成菜品。 */
public record SalesReportVO(PeriodVO period, List<SalesItemVO> items, String metricNote) {
}
