package com.shopmind.merchant.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiCustomerConfig {

    /**
     * 构建核心 ChatClient Bean
     * 采用 Fluent API 建造者模式，方便后续挂载 Advisors（如 ChatMemory 记忆、审计、安全等）
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
