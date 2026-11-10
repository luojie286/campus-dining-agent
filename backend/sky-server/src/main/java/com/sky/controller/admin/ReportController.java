package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.ReportService;
import com.sky.vo.DashboardVO;
import com.sky.vo.OrderReportVO;
import com.sky.vo.RevenueReportVO;
import com.sky.vo.SalesReportVO;
import com.sky.vo.UserReportVO;
import com.sky.vo.WorkbenchVO;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 经营统计入口；权限由 ReportService 在每次请求时按数据库当前角色校验。 */
@RestController
@Profile("!bootstrap")
@RequestMapping("/api/admin")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/report/overview")
    public Result<DashboardVO> overview(
            @RequestParam(required = false) String begin,
            @RequestParam(required = false) String end) {
        return Result.success(reports.overview(begin, end));
    }

    @GetMapping("/report/revenue")
    public Result<RevenueReportVO> revenue(
            @RequestParam(required = false) String begin,
            @RequestParam(required = false) String end) {
        return Result.success(reports.revenue(begin, end));
    }

    @GetMapping("/report/users")
    public Result<UserReportVO> users(
            @RequestParam(required = false) String begin,
            @RequestParam(required = false) String end) {
        return Result.success(reports.users(begin, end));
    }

    @GetMapping("/report/orders")
    public Result<OrderReportVO> orders(
            @RequestParam(required = false) String begin,
            @RequestParam(required = false) String end) {
        return Result.success(reports.orders(begin, end));
    }

    @GetMapping("/report/sales")
    public Result<SalesReportVO> sales(
            @RequestParam(required = false) String begin,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) Integer topN) {
        return Result.success(reports.sales(begin, end, kind, topN));
    }

    @GetMapping("/workbench")
    public Result<WorkbenchVO> workbench(@RequestParam(required = false) String date) {
        return Result.success(reports.workbench(date));
    }
}
