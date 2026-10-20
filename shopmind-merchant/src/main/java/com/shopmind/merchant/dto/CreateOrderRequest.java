package com.shopmind.merchant.dto;

import java.io.Serializable;

/**
 * 创建订单/秒杀下单请求 DTO
 */
public record CreateOrderRequest(Long merchantId, Long userId, Long productId,
                                 Integer quantity) implements Serializable {
}
