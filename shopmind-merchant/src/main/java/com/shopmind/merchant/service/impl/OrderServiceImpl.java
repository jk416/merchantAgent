package com.shopmind.merchant.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmind.merchant.dto.CreateOrderRequest;
import com.shopmind.merchant.dto.OrderMessageDTO;
import com.shopmind.merchant.entity.ProductDO;
import com.shopmind.merchant.mapper.ProductMapper;
import com.shopmind.merchant.service.OrderService;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单与秒杀服务实现类
 *
 * 演进阶段：
 * Level 1: 脆弱版本（无并发防护，存在典型的"丢失更新"与"超卖"缺陷）
 * Level 2: 数据库乐观锁 / 行锁防超卖（行锁排队严重，DB CPU 100% 瓶颈）
 * Level 3: Redis + Lua 脚本内存原子预扣减（零行锁排队，10万 QPS 极速拦截）
 * Level 4: RocketMQ 异步削峰解耦终极落库方案（耗时 2ms，前台极速放行，后台平稳消化）
 */
@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String TOPIC = "SECKILL_ORDER_TOPIC";

    private final ProductMapper productMapper;
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> stockDeductScript;
    private final RedisScript<Long> stockRollbackScript;
    private final DefaultMQProducer defaultMQProducer;
    private final ObjectMapper objectMapper;

    public OrderServiceImpl(ProductMapper productMapper,
                            StringRedisTemplate redisTemplate,
                            @Qualifier("stockDeductScript") RedisScript<Long> stockDeductScript,
                            @Qualifier("stockRollbackScript") RedisScript<Long> stockRollbackScript,
                            DefaultMQProducer defaultMQProducer,
                            ObjectMapper objectMapper) {
        this.productMapper = productMapper;
        this.redisTemplate = redisTemplate;
        this.stockDeductScript = stockDeductScript;
        this.stockRollbackScript = stockRollbackScript;
        this.defaultMQProducer = defaultMQProducer;
        this.objectMapper = objectMapper;
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
    public String createOrder(CreateOrderRequest request) {
        // 1. 组装 Redis 库存 Key
        String stockKey = STOCK_KEY_PREFIX + request.productId();

        // 2. 执行 Lua 脚本在内存中原子扣减
        long result = redisTemplate.execute(
                stockDeductScript,
                Collections.singletonList(stockKey),
                String.valueOf(request.quantity())
        );

        // 3. 判断扣减结果：如果售罄，微秒级直接秒拒，对下游产生 0 压力！
        if (result == 0L) {
            throw new RuntimeException("秒杀已售罄，手慢无！");
        } else if (result == -1L) {
            throw new RuntimeException("活动未开始或库存未预热！");
        }

        // 4. 拿到抢购入场券（result == 1L）：
        String orderNo = generateOrderNo();
        ProductDO product = productMapper.selectById(request.productId());
        if (product == null) {
            redisTemplate.execute(stockRollbackScript, Collections.singletonList(stockKey), String.valueOf(request.quantity()));
            throw new RuntimeException("商品不存在");
        }

        try {
            // ===================== 【阶段 4：RocketMQ 异步削峰投递】 =====================
            // 组装消息体
            OrderMessageDTO orderMessage = new OrderMessageDTO(
                    orderNo,
                    product.getMerchantId(),
                    request.userId(),
                    product.getId(),
                    product.getProductName(),
                    request.quantity(),
                    product.getPrice().multiply(BigDecimal.valueOf(request.quantity())),
                    LocalDateTime.now()
            );

            // 序列化并投递 RocketMQ 消息
            String messageJson = objectMapper.writeValueAsString(orderMessage);
            Message msg = new Message(TOPIC, "create", orderNo, messageJson.getBytes(StandardCharsets.UTF_8));
            
            // 同步发送消息到 Broker（耗时仅约 1~2ms）
            defaultMQProducer.send(msg);

            log.info("⚡ [MQ 异步削峰] 用户 ID={}，订单消息投递成功，订单号: {}", request.userId(), orderNo);
            
            // 核心演进：此时不再同步写数据库！直接向用户返回订单号！
            return orderNo;

        } catch (Exception e) {
            log.error("💥 [MQ 投递异常] 用户 ID={}，投递消息失败，触发 Redis 库存补偿回滚！", request.userId(), e);
            // 异常兜底补偿：若发 MQ 失败，立即归还 Redis 库存
            redisTemplate.execute(
                    stockRollbackScript,
                    Collections.singletonList(stockKey),
                    String.valueOf(request.quantity())
            );
            throw new RuntimeException("系统繁忙，请稍后重试: " + e.getMessage());
        }
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
