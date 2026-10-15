package com.shopmind.merchant.controller;

import com.shopmind.common.core.result.Result;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class PingController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public PingController(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        String mysqlVersion = jdbcTemplate.queryForObject("select version()", String.class);
        String redisPong = redisTemplate.execute((RedisCallback<String>) RedisConnection::ping);
        return Result.ok(Map.of(
                "mysql", mysqlVersion,
                "redis", redisPong,
                "time", LocalDateTime.now().toString()
        ));
    }
}
