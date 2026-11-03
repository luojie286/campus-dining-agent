package com.sky;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** 随机HTTP端口+独立Vite代理+真实前端api模块；会提交且清理本次临时业务数据。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "RUN_SETMEAL_FRONTEND_TEST", matches = "true")
class SetmealFrontendIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;
    @Autowired ObjectMapper json;

    @Test
    void frontendThroughViteProxy() throws Exception {
        long actor = jdbc.queryForObject("SELECT id FROM employee WHERE status=1 ORDER BY id LIMIT 1", Long.class);
        String prefix = "套餐联调_" + UUID.randomUUID().toString().substring(0, 8);
        Path session = Files.createTempFile("setmeal-session-", ".json");
        Path viteLog = Files.createTempFile("setmeal-vite-", ".log");
        Path testLog = Files.createTempFile("setmeal-frontend-", ".log");
        Path frontend = Path.of(System.getProperty("user.dir")).resolve("../../frontend").normalize();
        int proxyPort;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            proxyPort = socket.getLocalPort();
        }
        Process vite = null;
        Process tests = null;
        try {
            Files.writeString(session, json.writeValueAsString(Map.of("id", actor, "role", "admin",
                    "name", "临时接口测试", "token", jwt.createToken(actor, "admin"))));
            ProcessBuilder proxy = new ProcessBuilder("node", "node_modules/vite/bin/vite.js",
                    "--host", "127.0.0.1", "--port", String.valueOf(proxyPort), "--strictPort");
            proxy.directory(frontend.toFile());
            proxy.environment().put("VITE_PROXY_TARGET", "http://127.0.0.1:" + port);
            vite = proxy.redirectErrorStream(true).redirectOutput(viteLog.toFile()).start();
            String base = "http://127.0.0.1:" + proxyPort;
            boolean ready = false;
            for (int attempt = 0; attempt < 100 && vite.isAlive(); attempt++) {
                try {
                    HttpURLConnection connection = (HttpURLConnection) URI.create(base).toURL().openConnection(Proxy.NO_PROXY);
                    connection.setConnectTimeout(1000);
                    connection.setReadTimeout(1000);
                    try {
                        if (connection.getResponseCode() == 200) { ready = true; break; }
                    } finally {
                        connection.disconnect();
                    }
                } catch (java.io.IOException ignored) {
                    // Vite尚未监听，短暂等待后再次探测。
                }
                Thread.sleep(100);
            }
            assertThat(ready).as("Vite启动：%s", Files.readString(viteLog)).isTrue();
            ProcessBuilder runner = new ProcessBuilder("node", "node_modules/vitest/vitest.mjs", "run",
                    "src/api/setmeal.integration.test.ts");
            runner.directory(frontend.toFile());
            runner.environment().put("SETMEAL_INTEGRATION_SESSION", session.toString());
            runner.environment().put("SETMEAL_INTEGRATION_BASE", base + "/api");
            runner.environment().put("SETMEAL_INTEGRATION_PREFIX", prefix);
            tests = runner.redirectErrorStream(true).redirectOutput(testLog.toFile()).start();
            assertThat(tests.waitFor(90, TimeUnit.SECONDS)).as("前端联调超时").isTrue();
            assertThat(tests.exitValue()).as("前端联调：%s", Files.readString(testLog)).isZero();
            for (String table : List.of("setmeal", "dish", "category")) {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE name LIKE ?",
                        Long.class, prefix + "%")).as("临时数据清理：%s", table).isZero();
            }
        } finally {
            stop(tests);
            stop(vite);
            Files.deleteIfExists(session);
            deleteLog(viteLog);
            deleteLog(testLog);
        }
    }
    private void stop(Process process) throws InterruptedException {
        if (process == null) return;
        // Windows上esbuild子进程可能继承日志句柄，先终止本测试启动的子进程。
        var children = process.descendants().toList();
        for (ProcessHandle child : children) child.destroyForcibly();
        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
    }

    private void deleteLog(Path path) throws InterruptedException {
        for (int attempt = 0; attempt < 20; attempt++) {
            try { Files.deleteIfExists(path); return; }
            catch (java.io.IOException exception) { Thread.sleep(50); }
        }
        // 日志不含凭证；不得因Windows延迟释放日志句柄覆盖原始测试结果。
        path.toFile().deleteOnExit();
    }
}
