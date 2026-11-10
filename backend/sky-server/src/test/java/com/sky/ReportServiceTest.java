package com.sky;

import com.sky.exception.BadRequestException;
import com.sky.mapper.ReportRepository;
import com.sky.service.EmployeePermissionService;
import com.sky.service.PaymentChannel;
import com.sky.service.ReportService;
import com.sky.service.ShopService;
import com.sky.vo.RevenueReportVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 统计口径中不依赖真实数据库的规则：日期范围、趋势补 0、净现金计算。 */
class ReportServiceTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private ReportRepository repository;
    private ReportService service;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-11-10T04:00:00Z"), BUSINESS_ZONE);
        repository = mock(ReportRepository.class);
        service = new ReportService(mock(EmployeePermissionService.class), repository,
                mock(ShopService.class), fixed, PaymentChannel.MOCK);
        when(repository.dailyPaid(any(), any())).thenReturn(List.of());
        when(repository.dailyRefund(any(), any())).thenReturn(List.of());
    }

    @Test
    void defaultRangeCoversLastSevenBusinessDays() {
        ReportService.Range range = service.range(null, null, ReportService.MAX_RANGE_DAYS, false);

        assertEquals(LocalDate.of(2026, 11, 4), range.begin());
        assertEquals(LocalDate.of(2026, 11, 10), range.end());
        assertEquals(7, range.dates().size());
        assertEquals(LocalDate.of(2026, 11, 11).atStartOfDay(), range.to());
    }

    @Test
    void oneSidedOrTooLongRangeIsRejected() {
        assertThrows(BadRequestException.class,
                () -> service.range("2026-11-01", null, ReportService.MAX_RANGE_DAYS, false));
        assertThrows(BadRequestException.class,
                () -> service.range(null, "2026-11-01", ReportService.MAX_RANGE_DAYS, false));
        assertThrows(BadRequestException.class,
                () -> service.range("2026-07-01", "2026-11-10", ReportService.MAX_RANGE_DAYS, false));
        assertThrows(BadRequestException.class,
                () -> service.exportRange(null, null));
        assertThrows(BadRequestException.class,
                () -> service.exportRange("2026-10-01", "2026-11-10"));
    }

    @Test
    void revenueUsesSeparatePaidAndRefundDatesAndFillsMissingDays() {
        when(repository.sumPaidAmount(any(), any())).thenReturn(new BigDecimal("100.00"));
        when(repository.sumRefundAmount(any(), any())).thenReturn(new BigDecimal("30.50"));
        when(repository.countPaidOrders(any(), any())).thenReturn(2);

        RevenueReportVO report = service.revenue(null, null);

        assertEquals(0, new BigDecimal("100.00").compareTo(report.grossPaidAmount()));
        assertEquals(0, new BigDecimal("30.50").compareTo(report.refundAmount()));
        assertEquals(0, new BigDecimal("69.50").compareTo(report.netCashAmount()));
        assertEquals(2, report.paidOrders());
        assertEquals(7, report.trend().size());
        for (RevenueReportVO.Trend point : report.trend()) {
            assertEquals(0, BigDecimal.ZERO.compareTo(point.grossPaidAmount()));
            assertEquals(0, BigDecimal.ZERO.compareTo(point.netCashAmount()));
            assertEquals(0, point.paidOrders());
        }
    }

    @Test
    void exportRangeRequiresBothDatesAndAtMostThirtyOneDays() {
        ReportService.Range range = service.exportRange("2026-11-01", "2026-11-02");

        assertEquals(LocalDate.of(2026, 11, 1), range.begin());
        assertEquals(LocalDate.of(2026, 11, 2), range.end());
        assertThrows(BadRequestException.class,
                () -> service.exportRange("2026-10-01", "2026-11-01"));
    }
}
