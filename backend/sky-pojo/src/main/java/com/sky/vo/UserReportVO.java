package com.sky.vo;

import java.time.LocalDate;
import java.util.List;

/** 用户统计：区间人数是去重结果，不能把每日人数相加；totalUsers 是截至当日的累计注册数。 */
public record UserReportVO(PeriodVO period, int newUsers, int totalUsers,
                           int orderingUsers, int payingUsers,
                           List<Trend> trend, String metricNote) {

    public record Trend(LocalDate date, int newUsers, int totalUsers,
                        int orderingUsers, int payingUsers) {
    }
}
