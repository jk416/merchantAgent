package com.shopmind.merchant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shopmind.merchant.dto.CreateOrderRequest;
import com.shopmind.merchant.entity.OrderDO;
import com.shopmind.merchant.entity.ProductDO;
import com.shopmind.merchant.mapper.OrderMapper;
import com.shopmind.merchant.mapper.ProductMapper;
import com.shopmind.merchant.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
public class OrderConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(OrderConcurrencyTest.class);

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private static final Long TEST_PRODUCT_ID = 1L;
    private static final int INITIAL_STOCK = 100;
    private static final int CONCURRENT_USERS = 200;

    @Test
    @DisplayName("高并发抢购压测：复现超卖与线程安全问题")
    public void testConcurrentCreateOrder() throws InterruptedException {
        // 1. 初始化测试环境：重置商品 1 的库存为 100
        ProductDO product = productMapper.selectById(TEST_PRODUCT_ID);
        if (product == null) {
            log.error("测试商品 ID=1 不存在，请先检查数据库");
            return;
        }
        product.setStockCount(INITIAL_STOCK);
        productMapper.updateById(product);

        // 秒杀预热：将库存同步到 Redis
        orderService.warmUpStock(TEST_PRODUCT_ID);

        // 统计压测前已有的订单数
        Long initialOrderCount = orderMapper.selectCount(new LambdaQueryWrapper<OrderDO>().eq(OrderDO::getProductId, TEST_PRODUCT_ID));

        log.info("=================================================================");
        log.info("🚀 秒杀压测准备就绪：商品【{}】，初始库存【{}】件，模拟【{}】个并发用户同时抢购！",
                product.getProductName(), INITIAL_STOCK, CONCURRENT_USERS);
        log.info("=================================================================");

        // 2. 线程池与发令枪控制
        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch startSignal = new CountDownLatch(1); // 发令枪（确保所有线程同时起跑）
        CountDownLatch doneSignal = new CountDownLatch(CONCURRENT_USERS); // 终点线

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // 3. 提交 200 个并发抢购任务
        for (int i = 1; i <= CONCURRENT_USERS; i++) {
            final long userId = 10000L + i;
            executorService.submit(() -> {
                try {
                    startSignal.await(); // 等待发令枪响
                    orderService.createOrder(new CreateOrderRequest(product.getMerchantId(), userId, TEST_PRODUCT_ID, 1));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        // 4. 鸣枪开跑！
        long startTime = System.currentTimeMillis();
        startSignal.countDown();

        // 5. 等待所有线程执行完毕
        doneSignal.await();
        long costTime = System.currentTimeMillis() - startTime;
        executorService.shutdown();

        // 6. 查库复盘
        ProductDO finalProduct = productMapper.selectById(TEST_PRODUCT_ID);
        Long finalOrderCount = orderMapper.selectCount(new LambdaQueryWrapper<OrderDO>().eq(OrderDO::getProductId, TEST_PRODUCT_ID));
        long actualCreatedOrders = finalOrderCount - initialOrderCount;
        String redisRemainingStock = redisTemplate.opsForValue().get("seckill:stock:" + TEST_PRODUCT_ID);

        log.info("========================= 压测复盘报告 =========================");
        log.info("⏱️ 并发执行总耗时: {} ms", costTime);
        log.info("✅ Java 捕获成功下单数: {}", successCount.get());
        log.info("❌ Java 捕获失败阻断数: {}", failCount.get());
        log.info("📦 数据库中实际新增订单记录: {} 单", actualCreatedOrders);
        log.info("🏷️ 数据库中最终剩余商品库存: {} 件", finalProduct.getStockCount());
        log.info("⚡ Redis 内存中最终剩余预热库存: {} 件", redisRemainingStock);
        log.info("================================================================");

        // 7. 判定是否发生超卖或数据不一致（对标 Q20 / Q21）
        if (actualCreatedOrders > INITIAL_STOCK) {
            log.error("🔥【致命事故：严重超卖！】初始库存仅 {} 件，数据库实际却卖出了 {} 单！超卖了 {} 件！",
                    INITIAL_STOCK, actualCreatedOrders, (actualCreatedOrders - INITIAL_STOCK));
        } else if (actualCreatedOrders + finalProduct.getStockCount() != INITIAL_STOCK) {
            log.error("🔥【数据不一致/丢失更新！】实际卖出 {} 单 + 剩余库存 {} 件 != 初始库存 {} 件！",
                    actualCreatedOrders, finalProduct.getStockCount(), INITIAL_STOCK);
        } else {
            log.info("🎉【恭喜！并发安全验证通过！】未发生超卖，库存与订单完全守恒！");
        }
    }
}
