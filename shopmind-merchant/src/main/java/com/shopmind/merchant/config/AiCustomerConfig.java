package com.shopmind.merchant.config;

import com.shopmind.merchant.memory.RedisChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiCustomerConfig {

    /**
     * 构建核心 ChatClient Bean
     * 绑定 DashScope (通义千问) ChatModel
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel, RedisChatMemory redisChatMemory) {
        return ChatClient.builder(chatModel).defaultAdvisors(new MessageChatMemoryAdvisor(redisChatMemory)).build();
    }
}
