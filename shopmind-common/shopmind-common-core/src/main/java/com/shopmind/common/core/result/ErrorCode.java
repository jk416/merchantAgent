package com.shopmind.common.core.result;

/**
 * 错误码契约接口：
 * 规范：1xxxx 通用  2xxxx 商家  3xxxx 商品/库存  4xxxx 订单/支付  5xxxx AI
 */
public interface ErrorCode {
    int code();
    String msg();
}
