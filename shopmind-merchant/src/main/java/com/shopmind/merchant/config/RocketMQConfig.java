package com.shopmind.merchant.config;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 核心生产者配置类
 * 
 * 对应面试考点：DefaultMQProducer 的启动生命周期与参数调优
 */
@Configuration
public class RocketMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RocketMQConfig.class);

    @Value("${rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    @Value("${rocketmq.producer.group:seckill-order-producer-group}")
    private String producerGroup;

    @Bean(destroyMethod = "shutdown")
    public DefaultMQProducer defaultMQProducer() throws MQClientException {
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(3000);
        producer.setRetryTimesWhenSendFailed(2);
        producer.start();
        log.info("🚀 RocketMQ 生产者启动成功！NameServer: {}, Group: {}", nameServer, producerGroup);
        return producer;
    }
}
