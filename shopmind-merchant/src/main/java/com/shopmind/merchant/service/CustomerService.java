package com.shopmind.merchant.service;

import reactor.core.publisher.Flux;

public interface CustomerService {

    /**
     * Level 1: 同步客服对话接口（阻塞式调用，一次性返回大模型完整回答）
     * 对应面试考点：Q24 同步 call() 底层机制与 Tomcat 阻塞模型
     *
     * @param merchantId  商家店铺ID（用于获取该店铺的专属 AI 人设）
     * @param userMessage 买家咨询内容
     * @return 大模型生成的完整文本回答
     */
    String chatSync(Long merchantId, String userMessage);

    /**
     * Level 2: 响应式流式客服对话接口（SSE 打字机实时推流）
     * 对应面试考点：Q1 Tomcat 线程释放原理、Q2 SSE 协议、Q24 stream() 响应式模型
     *
     * @param merchantId  商家店铺ID
     * @param userMessage 买家咨询内容
     * @return 响应式文本流 Flux<String>
     */
    Flux<String> chatStream(Long merchantId, String userMessage);
}
