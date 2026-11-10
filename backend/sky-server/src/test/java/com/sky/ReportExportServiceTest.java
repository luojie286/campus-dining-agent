package com.sky;

import com.sky.exception.BadRequestException;
import com.sky.mapper.ReportRepository;
import com.sky.service.ReportExportService;
import com.sky.service.ReportService;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 导出必须是能被 Excel/POI 读回的真 xlsx，金额是数值单元格且不含手机号与详细地址。 */
class ReportExportServiceTest {

    private static final String BEGIN = "2026-11-01";
    private static final String END = "2026-11-02";

    @Test
    void ordersExportWritesReadableWorkbook() throws IOException {
        LocalDate begin = LocalDate.parse(BEGIN);
        LocalDate end = LocalDate.parse(END);
        ReportService.Range range = new ReportService.Range(begin, end,
                begin.atStartOfDay(), end.plusDays(1).atStartOfDay(), List.of(begin, end));

        ReportService reports = mock(ReportService.class);
        when(reports.exportRange(BEGIN, END)).thenReturn(range);
        when(reports.exportOrderRows(range)).thenReturn(List.of(new ReportRepository.ExportOrderRow(
                "NO-1", LocalDateTime.of(2026, 11, 1, 12, 5), 1, 0, "MOCK",
                new BigDecimal("29.50"), "张三", "DELIVERY", "少辣")));

        byte[] file = new ReportExportService(reports).export("orders", BEGIN, END);

        assertTrue(file.length > 0);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheet("订单明细");
            assertNotNull(sheet);
            assertEquals("订单号", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("NO-1", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("UNPAID", sheet.getRow(1).getCell(2).getStringCellValue());
            assertEquals(29.50, sheet.getRow(1).getCell(5).getNumericCellValue(), 0.001);
            assertTrue(workbook.getSheet("订单明细").getRow(0).getLastCellNum() < 12);
        }
    }

    @Test
    void unknownExportTypeIsRejected() {
        ReportService reports = mock(ReportService.class);
        ReportExportService service = new ReportExportService(reports);

        assertThrows(BadRequestException.class,
                () -> service.export("unknown", BEGIN, END));
    }
}
