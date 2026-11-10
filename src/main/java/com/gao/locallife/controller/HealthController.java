package com.gao.locallife.controller;

import com.gao.locallife.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * M0 健康检查：用来验收「工程骨架 + 中间件连通」。
 *
 * <ul>
 *   <li>GET /ping   —— 应用是否起来</li>
 *   <li>GET /health —— MySQL / Redis 是否连通</li>
 * </ul>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.ok("pong");
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> status = new LinkedHashMap<>();

        try {
            Integer one = jdbcTemplate.queryForObject("select 1", Integer.class);
            status.put("mysql", (one != null && one == 1) ? "ok" : "unexpected");
        } catch (Exception e) {
            status.put("mysql", "fail: " + e.getMessage());
        }

        try {
            stringRedisTemplate.opsForValue().set("health:ping", "1", Duration.ofSeconds(10));
            String v = stringRedisTemplate.opsForValue().get("health:ping");
            status.put("redis", "1".equals(v) ? "ok" : "unexpected");
        } catch (Exception e) {
            status.put("redis", "fail: " + e.getMessage());
        }

        log.debug("health check: {}", status);
        return Result.ok(status);
    }
}
