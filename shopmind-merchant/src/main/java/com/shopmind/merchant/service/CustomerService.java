package com.shopmind.merchant.service;

import reactor.core.publisher.Flux;

public interface CustomerService {

    /**
     * Level 1: 同步客服对话接口（阻塞式调用，支持多轮会话记忆）
     *
     * @param merchantId  商家店铺ID（用于获取该店铺的专属 AI 人设）
     * @param chatId      会话ID（对应 Redis 会话记忆 Key，区分不同用户/不同窗口）
     * @param userMessage 买家咨询内容
     * @return 大模型生成的完整文本回答
     */
    String chatSync(Long merchantId, String chatId, String userMessage);

    /**
     * Level 2: 响应式流式客服对话接口（SSE 打字机实时推流，支持多轮会话记忆）
     *
     * @param merchantId  商家店铺ID
     * @param chatId      会话ID（对应 Redis 会话记忆 Key）
     * @param userMessage 买家咨询内容
     * @return 响应式文本流 Flux<String>
     */
    Flux<String> chatStream(Long merchantId, String chatId, String userMessage);
}
