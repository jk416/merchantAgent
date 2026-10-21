package com.shopmind.merchant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shopmind.merchant.entity.OrderDO;
import com.shopmind.merchant.entity.ProductDO;
import com.shopmind.merchant.mapper.OrderMapper;
import com.shopmind.merchant.mapper.ProductMapper;
import com.shopmind.merchant.service.CustomerService;
import com.shopmind.merchant.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AiAgentSeckillIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AiAgentSeckillIntegrationTest.class);

    @Autowired
    private CustomerService customerService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final Long MERCHANT_ID = 1L;
    private static final Long PRODUCT_ID = 1L;

    @Test
    @DisplayName("测试：AI 客服意图识别与自主决策反射调用高并发秒杀开单全链路")
    public void testAiAgentSeckillClosedLoop() throws InterruptedException {
        // 1. 初始化测试库存环境（将商品 1 库存重置为 100 并预热到 Redis）
        ProductDO product = productMapper.selectById(PRODUCT_ID);
        assertNotNull(product, "商品 ID=1 必须在数据库中存在");
        product.setStockCount(100);
        productMapper.updateById(product);

        orderService.warmUpStock(PRODUCT_ID);

        Long initialOrders = orderMapper.selectCount(new LambdaQueryWrapper<OrderDO>().eq(OrderDO::getProductId, PRODUCT_ID));
        String initialRedisStock = redisTemplate.opsForValue().get("seckill:stock:" + PRODUCT_ID);
        log.info("🛒 [测试前置状态] 数据库初始库存: 100, Redis预热库存: {}, 历史订单数: {}", initialRedisStock, initialOrders);

        // 2. 模拟顾客自然语言提问（包含购买意图与关键参数）
        String userPrompt = "你好客服，我是周子龙，我的用户ID是 10086。听说你们店里的极客三模机械键盘（商品ID为 1）在做秒杀，请帮我抢购下单 1 台！";
        log.info("🗣️ [顾客自然语言提问] \"{}\"", userPrompt);

        // 3. 呼叫大模型智能客服（大模型需自主决策意图，提取参数并反射调用 seckillProduct）
        String reply = customerService.chatSync(MERCHANT_ID, "test-seckill-chat-" + System.currentTimeMillis(), userPrompt);
        log.info("🤖 [AI 智能客服回复话术]:\n{}", reply);

        assertNotNull(reply, "AI 客服回复不应为空");
        assertFalse(reply.isBlank(), "AI 客服回复不应为空白");

        // 4. 等待 2 秒供后台 RocketMQ 消费者平稳将消息持久化到 MySQL
        Thread.sleep(2000);

        // 5. 校验底层高并发引擎执行结果
        String afterRedisStock = redisTemplate.opsForValue().get("seckill:stock:" + PRODUCT_ID);
        Long afterOrders = orderMapper.selectCount(new LambdaQueryWrapper<OrderDO>().eq(OrderDO::getProductId, PRODUCT_ID));

        log.info("📦 [交易引擎状态复盘] 数据库订单数: {} -> {}, Redis 内存库存: {} -> {}",
                initialOrders, afterOrders, initialRedisStock, afterRedisStock);

        // 验证 Redis 库存是否成功原子预扣 1 件（从 100 变成 99）
        assertEquals("99", afterRedisStock, "Redis 库存应由 Lua 脚本原子扣减 1 件，剩余 99");

        // 验证 MySQL 订单记录是否成功新增 1 单
        assertEquals(initialOrders + 1, afterOrders, "MySQL 数据库应由 RocketMQ 消费者平稳新增 1 笔落库订单");

        log.info("🎉【验证大圆满！】AI 意图识别 ➔ Java Tool 触发 ➔ Redis 预扣 ➔ RocketMQ 异步削峰落库全链路彻底打通！");
    }
}
