package com.sky.service;

import com.sky.dto.OrderPageQueryDTO;
import com.sky.exception.BadRequestException;
import com.sky.exception.ConflictException;
import com.sky.mapper.ReportRepository;
import com.sky.vo.SalesItemVO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 服务端真 xlsx 导出。数据与报表页面、工作台共用 ReportService 的统计口径，
 * 不把浏览器 CSV 改后缀冒充 Excel。
 */
@Service
@Profile("!bootstrap")
public class ReportExportService {

    private static final Set<String> TYPES = Set.of("summary", "orders", "sales", "users");
    private static final int COLUMN_WIDTH = 20 * 256;

    private final ReportService reports;

    public ReportExportService(ReportService reports) {
        this.reports = reports;
    }

    public byte[] export(String type, String begin, String end) {
        if (!TYPES.contains(type)) {
            throw new BadRequestException("导出类型只能是 summary、orders、sales 或 users");
        }
        // 权限、必填日期、最多 31 日都在统一 Service 里校验。
        ReportService.Range range = reports.exportRange(begin, end);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            switch (type) {
                case "summary" -> writeSummary(workbook, range);
                case "orders" -> writeOrders(workbook, range);
                case "sales" -> writeSales(workbook, range);
                case "users" -> writeUsers(workbook, range);
                default -> throw new BadRequestException("导出类型不合法");
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Excel 报表生成失败", exception);
        }
    }

    public String filename(String type, String begin, String end) {
        return "园味运营报表_" + type + "_" + begin + "_" + end + ".xlsx";
    }

    private void writeSummary(XSSFWorkbook workbook, ReportService.Range range) {
        String begin = range.begin().toString();
        String end = range.end().toString();
        var revenue = reports.revenue(begin, end);
        var orders = reports.orders(begin, end);
        var users = reports.users(begin, end);
        var sales = reports.sales(begin, end, null, 100);

        Sheet summary = newSheet(workbook, "营业汇总", "指标", "数值");
        addRow(summary, "统计开始日期", begin);
        addRow(summary, "统计结束日期", end);
        addRow(summary, "历史支付总额（元）", revenue.grossPaidAmount());
        addRow(summary, "退款金额（元）", revenue.refundAmount());
        addRow(summary, "净现金金额（元）", revenue.netCashAmount());
        addRow(summary, "支付订单数（按付款时间）", revenue.paidOrders());
        addRow(summary, "创建订单数（按创建时间）", orders.createdOrders());
        addRow(summary, "有效订单数", orders.validOrders());
        addRow(summary, "完成订单数", orders.completedOrders());
        addRow(summary, "取消订单数", orders.cancelledOrders());
        addRow(summary, "订单完成率（%）", orders.completionRate());
        addRow(summary, "新增用户数", users.newUsers());
        addRow(summary, "累计注册用户数", users.totalUsers());
        addRow(summary, "下单用户数（去重）", users.orderingUsers());
        addRow(summary, "付款用户数（去重）", users.payingUsers());
        addRow(summary, "营业额口径说明", revenue.metricNote());
        addRow(summary, "订单口径说明", orders.metricNote());
        addRow(summary, "用户口径说明", users.metricNote());
        addRow(summary, "销量口径说明", sales.metricNote());

        Sheet revenueTrend = newSheet(workbook, "营业额趋势", "日期", "历史支付总额（元）",
                "退款金额（元）", "净现金金额（元）", "支付订单数");
        for (var point : revenue.trend()) {
            addRow(revenueTrend, point.date(), point.grossPaidAmount(), point.refundAmount(),
                    point.netCashAmount(), point.paidOrders());
        }

        Sheet orderTrend = newSheet(workbook, "订单趋势", "日期", "创建订单数", "支付订单数",
                "有效订单数", "完成订单数", "取消订单数");
        for (var point : orders.trend()) {
            addRow(orderTrend, point.date(), point.createdOrders(), point.paidOrders(),
                    point.validOrders(), point.completedOrders(), point.cancelledOrders());
        }

        Sheet userTrend = newSheet(workbook, "用户趋势", "日期", "新增用户", "累计用户",
                "下单用户", "付款用户");
        for (var point : users.trend()) {
            addRow(userTrend, point.date(), point.newUsers(), point.totalUsers(),
                    point.orderingUsers(), point.payingUsers());
        }

        Sheet salesSheet = newSheet(workbook, "销量排行", "商品ID", "商品名称", "销量（份）", "销售额（元）");
        for (SalesItemVO item : sales.items()) {
            addRow(salesSheet, item.id(), item.name(), item.quantity(), item.amount());
        }
    }

    private void writeOrders(XSSFWorkbook workbook, ReportService.Range range) {
        Sheet sheet = newSheet(workbook, "订单明细", "订单号", "下单时间", "订单状态",
                "支付状态", "支付渠道", "成交金额（元）", "员工姓名", "履约方式", "订单备注");
        for (ReportRepository.ExportOrderRow order : reports.exportOrderRows(range)) {
            addRow(sheet, order.number(), order.createdAt(), statusName(order.status()),
                    payStatusName(order.payStatus()), order.channel(), order.amount(),
                    order.userName(), order.fulfillment(), order.remark());
        }
    }

    private void writeSales(XSSFWorkbook workbook, ReportService.Range range) {
        List<SalesItemVO> items = reports.salesItems(range, null, ReportService.MAX_EXPORT_ROWS + 1);
        if (items.size() > ReportService.MAX_EXPORT_ROWS) {
            throw new ConflictException("导出数据超过 10000 行，请缩小日期范围");
        }
        Sheet sheet = newSheet(workbook, "销量统计", "商品ID", "商品名称", "销量（份）", "销售额（元）");
        for (SalesItemVO item : items) {
            addRow(sheet, item.id(), item.name(), item.quantity(), item.amount());
        }
    }

    private void writeUsers(XSSFWorkbook workbook, ReportService.Range range) {
        var report = reports.users(range.begin().toString(), range.end().toString());
        Sheet sheet = newSheet(workbook, "用户按日统计", "日期", "新增用户", "累计用户",
                "下单用户", "付款用户");
        for (var point : report.trend()) {
            addRow(sheet, point.date(), point.newUsers(), point.totalUsers(),
                    point.orderingUsers(), point.payingUsers());
        }
    }

    private Sheet newSheet(XSSFWorkbook workbook, String name, String... headers) {
        Sheet sheet = workbook.createSheet(name);
        Row header = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            header.createCell(index).setCellValue(headers[index]);
            sheet.setColumnWidth(index, COLUMN_WIDTH);
        }
        sheet.createFreezePane(0, 1);
        return sheet;
    }

    private void addRow(Sheet sheet, Object... values) {
        Row row = sheet.createRow(sheet.getLastRowNum() + 1);
        for (int index = 0; index < values.length; index++) {
            writeCell(row.createCell(index), values[index]);
        }
    }

    /**
     * 金额写数值单元格，其余一律写字符串单元格。
     * POI 的 setCellValue(String) 是文本类型，Excel 不会把 "=..." 当成公式执行。
     */
    private void writeCell(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
            return;
        }
        if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        if (value instanceof LocalDate date) {
            cell.setCellValue(date.toString());
            return;
        }
        if (value instanceof LocalDateTime time) {
            cell.setCellValue(time.toString());
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    private String statusName(int code) {
        return OrderPageQueryDTO.State.fromCode(code).name();
    }

    private String payStatusName(int code) {
        return switch (code) {
            case 0 -> "UNPAID";
            case 1 -> "PAID";
            case 2 -> "REFUNDED";
            default -> "UNKNOWN";
        };
    }
}
