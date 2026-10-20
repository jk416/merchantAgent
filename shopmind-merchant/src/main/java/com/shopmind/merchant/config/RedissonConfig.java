package com.shopmind.merchant.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 分布式锁配置类
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = "redis://" + host + ":" + port;
        var serverConfig = config.useSingleServer()
                .setAddress(address)
                .setTimeout(3000)
                .setConnectionPoolSize(32)
                .setConnectionMinimumIdleSize(8);

        if (password != null && !password.isBlank()) {
            serverConfig.setPassword(password);
        }

        return Redisson.create(config);
    }
}
