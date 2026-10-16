package com.shopmind.merchant.service.impl;

import com.shopmind.merchant.dao.MerchantDao;
import com.shopmind.merchant.entity.MerchantDO;
import com.shopmind.merchant.service.CustomerService;
import com.shopmind.merchant.tool.CustomerTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Optional;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final ChatClient chatClient;
    private final MerchantDao merchantDao;
    private final CustomerTools customerTools;

    public CustomerServiceImpl(ChatClient chatClient, MerchantDao merchantDao, CustomerTools customerTools) {
        this.chatClient = chatClient;
        this.merchantDao = merchantDao;
        this.customerTools = customerTools;
    }

    @Override
    public String chatSync(Long merchantId, String userMessage) {
        Optional<MerchantDO> merchantDO = merchantDao.findById(merchantId);
        String basePrompt = merchantDO.map(MerchantDO::getSystemPrompt).orElse("你是一个专业的电商客服助手。");
        // 注入当前会话的上下文店铺ID，大模型开单调用工具时会自动绑定该 merchantId
        String systemPrompt = basePrompt + "\n【重要上下文】当前服务店铺ID为：" + merchantId + "。当顾客询问本店商品列表或订单物流时，必须优先调用本地工具查询真实数据库。";

        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .tools(customerTools) // 👈 挂载业务工具箱（Function Calling）
                .call()
                .content();
    }

    @Override
    public Flux<String> chatStream(Long merchantId, String userMessage) {
        Optional<MerchantDO> merchantDO = merchantDao.findById(merchantId);
        String basePrompt = merchantDO.map(MerchantDO::getSystemPrompt).orElse("你是一个专业的电商客服助手。");
        String systemPrompt = basePrompt + "\n【重要上下文】当前服务店铺ID为：" + merchantId + "。当顾客询问本店商品列表或订单物流时，必须优先调用本地工具查询真实数据库。";

        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .tools(customerTools) // 👈 挂载业务工具箱（Function Calling）
                .stream()
                .content();
    }
}
