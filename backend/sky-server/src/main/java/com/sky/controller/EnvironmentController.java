package com.sky.controller;

import com.sky.result.Result;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/**
 * 运行环境健康检查。响应里的 businessImplemented 是早期兼容字段，固定为 false，
 * 只表示这个接口本身不探测业务状态，不能据此判断订单、统计等业务是否实现。
 */
@RestController
public class EnvironmentController {
    private final Environment environment;
    public EnvironmentController(Environment environment) { this.environment = environment; }

    @GetMapping("/api/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of(
            "application", "campus-dining",
            "javaVersion", System.getProperty("java.version"),
            "springBootVersion", SpringBootVersion.getVersion(),
            "profiles", environment.getActiveProfiles(),
            "businessImplemented", false,
            "message", "基础环境健康检查；本接口不探测业务实现状态，业务能力以源码与接口合同为准。"
        ));
    }
}
