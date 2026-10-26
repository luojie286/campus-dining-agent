package com.sky.controller;

import com.sky.result.Result;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import com.sky.dto.EmployeeCreateDTO;

/** 唯一已实现的自定义接口：证明环境可以启动，不代表业务可用。 */
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
            "message", "基础环境已启动；业务、鉴权、WebSocket 和 Agent 请自行实现。"
        ));
    }
}
