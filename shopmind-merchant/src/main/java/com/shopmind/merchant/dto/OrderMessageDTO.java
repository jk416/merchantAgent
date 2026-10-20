package com.shopmind.merchant.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单异步削峰消息传输载荷 (RocketMQ 消息体)
 */
public record OrderMessageDTO(
        String orderNo,
        Long merchantId,
        Long userId,
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal totalAmount,
        LocalDateTime createTime
) implements Serializable {
}
