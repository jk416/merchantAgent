package com.shopmind.merchant.service.impl;

import com.shopmind.merchant.dao.MerchantDao;
import com.shopmind.merchant.entity.MerchantDO;
import com.shopmind.merchant.service.CustomerService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Optional;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final ChatClient chatClient;
    private final MerchantDao merchantDao;

    public CustomerServiceImpl(ChatClient chatClient, MerchantDao merchantDao) {
        this.chatClient = chatClient;
        this.merchantDao = merchantDao;
    }

    @Override
    public String chatSync(Long merchantId, String userMessage) {
        // =========================================================================
        // 【你的核心实战区域 1】：同步阻塞对话（Level 1）
        // 
        // 业务目标：
        // 1. 查询商家与店铺人设：
        //    通过 merchantDao.findById(merchantId) 查出店铺信息与 systemPrompt；
        //    若不存在，则使用兜底默认人设："你是一个专业的电商客服助手。"
        // 2. 利用 chatClient 链式调用：
        //    - .prompt() 开启提示词构建；
        //    - .system(...) 传入刚才查询到的商家人设；
        //      （核心考点 Q8：大模型严格区分 System 与 User 角色，防止恶意 Prompt 注入攻击）
        //    - .user(...) 传入买家提问 userMessage；
        //    - .call() 发起同步阻塞调用（核心考点 Q24）；
        //    - .content() 提取最终回答文本并返回。
        // =========================================================================

        // TODO: 请替换下方临时 return，写下你的核心业务实现
        return "【待实现】请在此处编写 chatSync 同步调用逻辑";
    }

    @Override
    public Flux<String> chatStream(Long merchantId, String userMessage) {
        // =========================================================================
        // 【你的核心实战区域 2】：响应式流式对话（Level 2）
        // 
        // 业务目标：
        // 1. 同样获取商家人设 systemPrompt；
        // 2. 利用 chatClient.prompt().system(...).user(...) 构建上下文；
        // 3. 将调用方式由 .call() 切换为 .stream()；
        // 4. 调用 .content() 获取响应式流 Flux<String> 并直接返回！
        // 
        // 底层原理与面试核心（Q1 & Q2）：
        // - 为什么返回 Flux<String> 时，默认 200 个 Tomcat 工作线程不会被耗尽？
        // - 相比于 call() 漫长的白屏等待，stream() 为什么能将 TTFT（首字响应时间）降到 300ms？
        // =========================================================================

        // TODO: 请替换下方临时 return，写下你的核心业务实现
        return Flux.just("【待实现】请在此处编写 chatStream 流式调用逻辑");
    }
}
