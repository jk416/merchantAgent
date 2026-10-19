package com.shopmind.merchant.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于 Redis 的分布式生产级会话记忆存储
 * 实现 Spring AI 标准的 ChatMemory 接口
 * <p>
 * 核心技术规范：
 * 1. 使用 Redis List 结构，Key 格式为: chat:memory:{conversationId}
 * 2. 采用滑动窗口设计，仅保留最近 MAX_MESSAGES 条消息，防止 Token 爆炸与 OOM
 * 3. 每次存入新消息，自动滑动刷新 TTL（默认 30 分钟）
 */
@Component
public class RedisChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemory.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 单个会话最大保留的历史消息条数（滑动窗口大小，例如 10 条 = 5 轮一问一答）
     */
    private static final int MAX_MESSAGES = 10;

    /**
     * 会话闲置过期时间：30 分钟无交互自动释放 Redis 内存
     */
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);

    private static final String KEY_PREFIX = "chat:memory:";

    public RedisChatMemory(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 内部消息持久化传输对象（解决 Spring AI Message 多态序列化）
     */
    public record MessageDTO(MessageType messageType, String content) {
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        // TODO 1: 【学员实现】
        // 1. 判空校验
        if (messages == null || messages.isEmpty()) {
            return;
        }
        if (conversationId == null) {
            log.warn("conversationId is null");
            return;
        }
        // 2. 构造 Redis Key: KEY_PREFIX + conversationId
        String key = KEY_PREFIX + conversationId;
        // 3. 将 messages 转换为 MessageDTO，利用 objectMapper 序列化为 JSON 字符串
        List<String> messageDTOs = new ArrayList<>();
        for (Message message : messages) {
            MessageDTO messageDTO = new MessageDTO(message.getMessageType(), message.getText());
            try {
                messageDTOs.add(objectMapper.writeValueAsString(messageDTO));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize message: {}", message, e);
            }
        }
        // 4. 调用 redisTemplate.opsForList().rightPushAll(...) 追加到 List 尾部
        if (!messageDTOs.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(key, messageDTOs);
            redisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1);
            redisTemplate.expire(key, SESSION_TTL);
        }
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        if (lastN <= 0) {
            return Collections.emptyList();
        }
        // 1. 构造 Redis Key
        String key = KEY_PREFIX + conversationId;
        // 2. 从 List 尾部向前截取指定数量的消息：
        //    提示：比如要取最后 lastN 条，在 Redis 中范围是 -lastN 到 -1
        List<String> messageDTOs = redisTemplate.opsForList().range(key, -lastN, -1);
        if (messageDTOs == null || messageDTOs.isEmpty()) {
            return Collections.emptyList();
        }
        // 3. 将查出的 JSON 列表依次反序列化为 MessageDTO
        List<Message> messages = new ArrayList<>();
        for (String messageDTO : messageDTOs) {
            try {
                MessageDTO dto = objectMapper.readValue(messageDTO, MessageDTO.class);
                if (dto.messageType() == MessageType.USER) {
                    messages.add(new UserMessage(dto.content()));
                } else if (dto.messageType() == MessageType.ASSISTANT) {
                    messages.add(new AssistantMessage(dto.content()));
                } else if (dto.messageType() == MessageType.SYSTEM) {
                    messages.add(new SystemMessage(dto.content()));
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize message: {}", messageDTO, e);
            }
        }
        // 4. 根据 MessageDTO 中的 messageType 转换为 Spring AI 具体的 Message 实现类：
        //    - USER -> new UserMessage(dto.content())
        //    - ASSISTANT -> new AssistantMessage(dto.content())
        //    - SYSTEM -> new SystemMessage(dto.content())
        // 5. 返回还原好的 List<Message>
        return messages;
    }

    @Override
    public void clear(String conversationId) {
        // TODO 3: 【学员实现】
        // 1. 直接删除对应的 Redis Key，释放内存
        redisTemplate.delete(KEY_PREFIX + conversationId);
    }
}
