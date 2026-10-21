package com.shopmind.merchant.service.impl;

import com.shopmind.merchant.dao.MerchantDao;
import com.shopmind.merchant.entity.MerchantDO;
import com.shopmind.merchant.service.CustomerService;
import com.shopmind.merchant.tool.CustomerTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Optional;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

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
    public String chatSync(Long merchantId, String chatId, String userMessage) {
        Optional<MerchantDO> merchantDO = merchantDao.findById(merchantId);
        String basePrompt = merchantDO.map(MerchantDO::getSystemPrompt).orElse("你是一个专业的电商客服助手。");
        String systemPrompt = basePrompt + "\n【重要上下文与行为准则】" +
                "\n1. 当前服务店铺ID为：" + merchantId + "。" +
                "\n2. 当顾客询问本店商品列表或订单物流时，优先调用本地查询工具获取真实数据。" +
                "\n3. 当顾客表达想要抢购、秒杀或下单商品时：若顾客未提供用户ID，请礼貌提示顾客提供用户ID；若顾客已说明用户ID（或在会话中提供了数字ID），请自主提取参数并调用 seckillProduct 工具为顾客执行秒杀开单！开单成功后，请热情播报商品名称、订单号、金额及30分钟内完成支付的提醒。";

        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .tools(customerTools) // 👈 挂载业务工具箱（Function Calling）
                .call()
                .content();
    }

    @Override
    public Flux<String> chatStream(Long merchantId, String chatId, String userMessage) {
        Optional<MerchantDO> merchantDO = merchantDao.findById(merchantId);
        String basePrompt = merchantDO.map(MerchantDO::getSystemPrompt).orElse("你是一个专业的电商客服助手。");
        String systemPrompt = basePrompt + "\n【重要上下文与行为准则】" +
                "\n1. 当前服务店铺ID为：" + merchantId + "。" +
                "\n2. 当顾客询问本店商品列表或订单物流时，优先调用本地查询工具获取真实数据。" +
                "\n3. 当顾客表达想要抢购、秒杀或下单商品时：若顾客未提供用户ID，请礼貌提示顾客提供用户ID；若顾客已说明用户ID（或在会话中提供了数字ID），请自主提取参数并调用 seckillProduct 工具为顾客执行秒杀开单！开单成功后，请热情播报商品名称、订单号、金额及30分钟内完成支付的提醒。";

        return chatClient.prompt()
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .system(systemPrompt)
                .user(userMessage)
                .tools(customerTools) // 👈 挂载业务工具箱（Function Calling）
                .stream()
                .content();
    }
}
