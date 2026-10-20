package com.shopmind.merchant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis Lua 脚本配置类
 * 
 * 核心考点：
 * 利用 Redis 单线程模型，确保一段 Lua 脚本在执行过程中具备绝对的原子性（不会被其他客户端命令插队）
 */
@Configuration
public class RedisScriptConfig {

    private static final String STOCK_DEDUCT_LUA = """
            -- KEYS[1]: 商品库存 Key, 例如 seckill:stock:1
            -- ARGV[1]: 本次扣减数量, 例如 1
            local stock = tonumber(redis.call('get', KEYS[1]))
            if not stock then
                return -1
            end
            local quantity = tonumber(ARGV[1])
            if stock < quantity then
                return 0
            end
            redis.call('decrby', KEYS[1], quantity)
            return 1
            """;

    private static final String STOCK_ROLLBACK_LUA = """
            -- KEYS[1]: 商品库存 Key, 例如 seckill:stock:1
            -- ARGV[1]: 需要归还/补偿的数量, 例如 1
            redis.call('incrby', KEYS[1], tonumber(ARGV[1]))
            return 1
            """;

    /**
     * 扣减库存 Lua 脚本
     * 返回值定义：
     *  1: 扣减成功
     *  0: 库存不足
     * -1: Key 不存在（未预热）
     */
    @Bean
    public RedisScript<Long> stockDeductScript() {
        return new DefaultRedisScript<>(STOCK_DEDUCT_LUA, Long.class);
    }

    /**
     * 回滚/归还库存 Lua 脚本（用于后续订单落库异常时的补偿机制）
     */
    @Bean
    public RedisScript<Long> stockRollbackScript() {
        return new DefaultRedisScript<>(STOCK_ROLLBACK_LUA, Long.class);
    }
}
