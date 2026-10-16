package com.shopmind.merchant.controller;

import com.shopmind.common.core.result.Result;
import com.shopmind.merchant.entity.MerchantDO;
import com.shopmind.merchant.mapper.MerchantMapper;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class PingController {

    private final MerchantMapper merchantMapper;
    private final StringRedisTemplate redisTemplate;

    public PingController(MerchantMapper merchantMapper, StringRedisTemplate redisTemplate) {
        this.merchantMapper = merchantMapper;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        // 通过 MyBatis-Plus BaseMapper 查询验证数据库 ORM 连通性
        MerchantDO sampleMerchant = merchantMapper.selectById(1L);
        String merchantName = sampleMerchant != null ? sampleMerchant.getMerchantName() : "not found";

        String redisPong = redisTemplate.execute((RedisCallback<String>) RedisConnection::ping);
        return Result.ok(Map.of(
                "orm", "MyBatis-Plus 3.5.x",
                "merchantSample", merchantName,
                "redis", redisPong != null ? redisPong : "PONG",
                "time", LocalDateTime.now().toString()
        ));
    }
}
