package com.shopmind.merchant.dto;

/**
 * 智能客服对话请求 DTO
 */
public record ChatRequest(
        Long merchantId,
        String message,
        String chatId
) {
}
