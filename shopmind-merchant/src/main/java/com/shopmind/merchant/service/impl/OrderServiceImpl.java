package com.shopmind.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shopmind.merchant.dto.CreateOrderRequest;
import com.shopmind.merchant.entity.OrderDO;
import com.shopmind.merchant.entity.ProductDO;
import com.shopmind.merchant.mapper.OrderMapper;
import com.shopmind.merchant.mapper.ProductMapper;
import com.shopmind.merchant.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单与秒杀服务实现类
 * <p>
 * 演进阶段：
 * Level 1: 脆弱版本（无并发防护，存在典型的"丢失更新"与"超卖"缺陷）
 * Level 2: 数据库乐观锁 / 行锁防超卖（行锁排队严重，DB CPU 100% 瓶颈）
 * Level 3: Redis + Lua 脚本内存原子预扣减终极方案（零行锁排队，10万 QPS 极速拦截）
 */
@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";

    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> stockDeductScript;
    private final RedisScript<Long> stockRollbackScript;

    public OrderServiceImpl(ProductMapper productMapper,
                            OrderMapper orderMapper,
                            StringRedisTemplate redisTemplate,
                            @Qualifier("stockDeductScript") RedisScript<Long> stockDeductScript,
                            @Qualifier("stockRollbackScript") RedisScript<Long> stockRollbackScript) {
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.redisTemplate = redisTemplate;
        this.stockDeductScript = stockDeductScript;
        this.stockRollbackScript = stockRollbackScript;
    }

    @Override
    public void warmUpStock(Long productId) {
        ProductDO product = productMapper.selectById(productId);
        if (product != null) {
            String stockKey = STOCK_KEY_PREFIX + productId;
            redisTemplate.opsForValue().set(stockKey, String.valueOf(product.getStockCount()));
            log.info("🔥 [秒杀预热] 商品 ID={}，库存已成功预热至 Redis: {} 件", productId, product.getStockCount());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(CreateOrderRequest request) {
        // TODO: 【阶段 3 - Redis + Lua 原子预扣减终极实战】
        // 核心理念：
        // 1. 将 99% 的无效流量用 Lua 脚本挡在 Redis 内存层（毫秒级响应，零 DB 压力）
        // 2. 只有拿到预扣减入场券的幸运儿，才允许继续往后走写数据库！
        //
        // 步骤指导：
        // 1. 组装 Redis 库存 Key: String stockKey = STOCK_KEY_PREFIX + request.productId();
        String stockKey = STOCK_KEY_PREFIX + request.productId();
        // 2. 执行 Lua 脚本扣减：
        long result = redisTemplate.execute(
                stockDeductScript,
                Collections.singletonList(stockKey),
                String.valueOf(request.quantity())
        );
        // 3. 判断 result 结果：
        //    - if (result == null || result == 0L) -> throw new RuntimeException("秒杀已售罄，手慢无！");
        //    - if (result == -1L) -> throw new RuntimeException("活动未开始或库存未预热！");
        if (result == 0L) {
            throw new RuntimeException("秒杀已售罄，手慢无！");
        } else if (result == -1L) {
            throw new RuntimeException("活动未开始或库存未预热！");
        }
        // 4. 拿到入场券（result == 1L）：
        //    查询商品基础信息（用于获取价格、商家ID等），更新 DB 库存，并插入 t_order 订单表！
        String orderNo = generateOrderNo();
        ProductDO product = productMapper.selectById(request.productId());
        if (product == null) {
            // 补偿归还 Redis
            redisTemplate.execute(stockRollbackScript, Collections.singletonList(stockKey), String.valueOf(request.quantity()));
            throw new RuntimeException("商品不存在");
        }
        try {
            // 更新 DB 库存
            LambdaUpdateWrapper<ProductDO> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(ProductDO::getId, request.productId());
            updateWrapper.setSql("stock_count = stock_count - " + request.quantity());
            productMapper.update(null, updateWrapper);

            // 插入 t_order 订单表（补齐非空字段）
            OrderDO order = OrderDO.builder()
                    .orderNo(orderNo)
                    .merchantId(product.getMerchantId()) // 👈 补上
                    .userId(request.userId())            // 👈 补上
                    .productId(request.productId())
                    .productName(product.getProductName()) // 👈 补上
                    .quantity(request.quantity())
                    .totalAmount(product.getPrice().multiply(BigDecimal.valueOf(request.quantity())))
                    .orderStatus(10)                     // 待支付
                    .createdAt(LocalDateTime.now())
                    .build();
            orderMapper.insert(order);

            log.info("🔥 [秒杀下单] 用户 ID={}，下单成功，订单号: {}", request.userId(), orderNo);
        } catch (Exception e) {
            log.error("🔥 [秒杀下单] 用户 ID={}，下单失败，订单号: {}", request.userId(), orderNo, e);
            // 5. 【高可用保障】如果落库过程中发生未知异常，建议调用 stockRollbackScript 回滚 Redis 库存！
            redisTemplate.execute(
                    stockRollbackScript,
                    Collections.singletonList(stockKey),
                    String.valueOf(request.quantity())
            );
            throw new RuntimeException("下单失败，库存已回滚: " + e.getMessage());
        }
        return orderNo;
    }

    /**
     * 生成模拟业务订单号：yyyyMMddHHmmss + 4位随机数
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return timestamp + random;
    }
}
