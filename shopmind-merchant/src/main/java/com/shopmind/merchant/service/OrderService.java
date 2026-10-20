package com.shopmind.merchant.service;

import com.shopmind.merchant.dto.CreateOrderRequest;

public interface OrderService {

    /**
     * 核心下单接口
     *
     * @param request 下单请求
     * @return 成功生成的业务订单号（如: 202610190001）
     */
    String createOrder(CreateOrderRequest request);

    /**
     * 秒杀活动预热：将商品数据库库存预先同步到 Redis 中
     *
     * @param productId 商品ID
     */
    void warmUpStock(Long productId);
}
