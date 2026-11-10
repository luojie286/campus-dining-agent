package com.sky.vo;

import java.time.LocalDate;

/** 统计日期范围，按 Asia/Shanghai 业务日，含首尾两天。 */
public record PeriodVO(LocalDate begin, LocalDate end) {
}
