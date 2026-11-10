package com.sky.controller.admin;

import com.sky.service.ReportExportService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 成功响应是真正的 xlsx 二进制，不套 Result；参数或业务错误仍由全局异常处理返回 JSON。 */
@RestController
@Profile("!bootstrap")
@RequestMapping("/api/admin/report")
public class ReportExportController {

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ReportExportService exports;

    public ReportExportController(ReportExportService exports) {
        this.exports = exports;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String begin,
            @RequestParam(required = false) String end) {
        byte[] file = exports.export(type, begin, end);
        String filename = exports.filename(type, begin, end);
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX_MIME))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"report.xlsx\"; filename*=UTF-8''" + encoded)
                .body(file);
    }
}
