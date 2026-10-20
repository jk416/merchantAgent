package com.shopmind.merchant.mq;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmind.merchant.dto.OrderMessageDTO;
import com.shopmind.merchant.entity.OrderDO;
import com.shopmind.merchant.entity.ProductDO;
import com.shopmind.merchant.mapper.OrderMapper;
import com.shopmind.merchant.mapper.ProductMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 秒杀订单异步削峰消费者
 * <p>
 * 核心设计：
 * 1. 削峰填谷：平滑拉取消息，匀速写入 MySQL，彻底消除数据库热点行锁争抢
 * 2. 消费幂等性：Redis 内存防重 + MySQL 唯一索引防重，绝不重复创建订单
 */
@Component
public class OrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);

    @Value("${rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    private static final String TOPIC = "SECKILL_ORDER_TOPIC";
    private static final String CONSUMER_GROUP = "seckill-order-consumer-group-v2";

    private DefaultMQPushConsumer consumer;

    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public OrderConsumer(ProductMapper productMapper,
                         OrderMapper orderMapper,
                         StringRedisTemplate redisTemplate,
                         ObjectMapper objectMapper) {
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() throws MQClientException {
        consumer = new DefaultMQPushConsumer(CONSUMER_GROUP);
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe(TOPIC, "*");

        // 注册并发消费监听器
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                for (MessageExt msg : msgs) {
                    try {
                        consumeSingleMessage(msg);
                    } catch (Exception e) {
                        log.error("💥 消费秒杀订单消息异常, msgId={}", msg.getMsgId(), e);
                        // 返回稍后重试，RocketMQ 会自动按退避级别重试投递
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        consumer.start();
        log.info("📥 RocketMQ 秒杀削峰消费者启动成功！监听 Topic: {}", TOPIC);
    }

    /**
     * 单条消息核心消费与幂等防重落库逻辑
     */
    private void consumeSingleMessage(MessageExt msg) throws Exception {
        // TODO: 【学员亲手实现】
        // 步骤指导：
        // 1. 将 msg.getBody() 反序列化为 OrderMessageDTO
        OrderMessageDTO dto = objectMapper.readValue(msg.getBody(), OrderMessageDTO.class);

        // 2. 【第一道防线：Redis 内存幂等防重】
        //    使用 redisTemplate.opsForValue().setIfAbsent("mq:consumed:" + dto.orderNo(), "1", Duration.ofDays(1));
        //    如果返回 false，说明是重复投递的消息，直接打印 warn 日志并 return，绝不重复消费！
        Boolean isConsumed = redisTemplate.opsForValue().setIfAbsent("mq:consumed:" + dto.orderNo(), "1", Duration.ofDays(1));
        if (!isConsumed) {
            log.warn("检测到重复消息，已消费，直接返回, msgId={}, orderNo={}", msg.getMsgId(), dto.orderNo());
            return;
        }
        // 3. 【第二道防线：MySQL 事务与唯一索引兜底】
        //    try {
        //        a. 更新 DB 库存：LambdaUpdateWrapper, stock_count = stock_count - dto.quantity()
        //        b. 插入 t_order 订单表
        //    } catch (DuplicateKeyException e) {
        //        log.warn("检测到数据库唯一键冲突，重复消息已成功被 MySQL 幂等拦截, orderNo={}", dto.orderNo());
        //        // 幂等成功，正常返回
        //    }
        try {
            // a. 更新 DB 库存：LambdaUpdateWrapper, stock_count = stock_count - dto.quantity()
            LambdaUpdateWrapper<ProductDO> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(ProductDO::getId, dto.productId())
                    .ge(ProductDO::getStockCount, dto.quantity())
                    .setSql("stock_count = stock_count - " + dto.quantity());
            int rows = productMapper.update(null, updateWrapper);
            if (rows <= 0) {
                log.error("💥 [消费异常] 数据库库存不足，阻断落库！orderNo={}", dto.orderNo());
                return;
            }
            // b. 插入 t_order 订单表
            OrderDO order = OrderDO.builder()
                    .orderNo(dto.orderNo())
                    .productId(dto.productId())
                    .quantity(dto.quantity())
                    .totalAmount(dto.totalAmount())
                    .orderStatus(1)
                    .createdAt(java.time.LocalDateTime.now())
                    .merchantId(dto.merchantId())
                    .userId(dto.userId())
                    .productName(dto.productName())
                    .build();
            orderMapper.insert(order);
            log.info("📦 [异步削峰落库成功] 订单已平稳入库, 订单号: {}", dto.orderNo());
        } catch (DuplicateKeyException e) {
            log.warn("检测到数据库唯一键冲突，重复消息已成功被 MySQL 幂等拦截, orderNo={}", dto.orderNo());
        }
    }

    @PreDestroy
    public void stop() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }
}
