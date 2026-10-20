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
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactionTemplate; // 👈 1. 声明它


    public OrderConsumer(ProductMapper productMapper,
                         OrderMapper orderMapper,
                         StringRedisTemplate redisTemplate,
                         ObjectMapper objectMapper, TransactionTemplate transactionTemplate) {
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
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
        if (Boolean.FALSE.equals(isConsumed)) {
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
            transactionTemplate.executeWithoutResult(status -> {
                orderMapper.insert(order);
                log.info("📦 [异步削峰落库成功] 订单已平稳入库, 订单号: {}", dto.orderNo());
                // a. 更新 DB 库存：LambdaUpdateWrapper, stock_count = stock_count - dto.quantity()
                LambdaUpdateWrapper<ProductDO> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(ProductDO::getId, dto.productId())
                        .ge(ProductDO::getStockCount, dto.quantity())
                        .setSql("stock_count = stock_count - " + dto.quantity());
                int rows = productMapper.update(null, updateWrapper);
                if (rows <= 0) {
                    // 抛出运行时异常，让 TransactionTemplate 自动回滚上面插入的订单！
                    throw new RuntimeException("数据库库存不足，阻断落库！orderNo=" + dto.orderNo());
                }
            });
            log.info("📦 [异步削峰落库成功] 订单已平稳入库, 订单号: {}", dto.orderNo());

        } catch (DuplicateKeyException e) {
            // 【分支 1：幂等命中】
            // 订单已存在，属于正常幂等现象！
            // 绝对不要删除 Redis key（让它继续在内存中挡住后续重复消息）
            // 正常 return，不抛异常，MQ 会自动提交 CONSUME_SUCCESS，不再重复消费！
            log.warn("🛡️ [MySQL 唯一索引防线] 检测到重复主键冲突，幂等放行, orderNo={}", dto.orderNo());
        } catch (Exception e) {
            // 【分支 2：真正的系统故障】（数据库断网、死锁、超时等）
            log.error("💥 [消费异常] 订单落库发生未知系统异常，清除 Redis 标记并触发 MQ 重试, orderNo={}", dto.orderNo(), e);
            // 1. 补偿清理 Redis 占位，为下次重试放行铺路
            redisTemplate.delete("mq:consumed:" + dto.orderNo());
            // 2. 必须向外抛出异常！让外层监听器捕获后向 RocketMQ 返回 RECONSUME_LATER！
            throw e;
        }
    }

    @PreDestroy
    public void stop() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }
}
