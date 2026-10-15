package com.shopmind.merchant.entity;

/**
 * 商家与店铺人设实体对象
 */
public record MerchantDO(
        Long id,
        String merchantCode,
        String merchantName,
        String systemPrompt,
        Integer status
) {
}
