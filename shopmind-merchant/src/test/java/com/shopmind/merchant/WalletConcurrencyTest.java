package com.shopmind.merchant;

import com.shopmind.merchant.entity.MerchantDO;
import com.shopmind.merchant.mapper.MerchantMapper;
import com.shopmind.merchant.service.MerchantWalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
public class WalletConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(WalletConcurrencyTest.class);

    @Autowired
    private MerchantWalletService merchantWalletService;

    @Autowired
    private MerchantMapper merchantMapper;

    private static final Long TEST_MERCHANT_ID = 1L;
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000.00");
    private static final BigDecimal WITHDRAW_AMOUNT = new BigDecimal("1000.00");

    @Test
    @DisplayName("商户提现并发测试：复现重复出金与双花事故")
    public void testConcurrentWithdraw() throws InterruptedException {
        // 1. 初始化商户资金余额为 1000.00
        MerchantDO merchant = merchantMapper.selectById(TEST_MERCHANT_ID);
        merchant.setBalance(INITIAL_BALANCE);
        merchantMapper.updateById(merchant);

        log.info("=================================================================");
        log.info("💳 提现并发测试准备：商户 ID={}，初始可用余额【{}】元", TEST_MERCHANT_ID, INITIAL_BALANCE);
        log.info("⚡ 模拟商户手抖双击/网络重试：2 个并发请求同时发起【{}】元全额提现！", WITHDRAW_AMOUNT);
        log.info("=================================================================");

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 1; i <= 2; i++) {
            final int requestId = i;
            executorService.submit(() -> {
                try {
                    startSignal.await(); // 同时起跑
                    log.info("🚀 请求 #{} 正在发起提现...", requestId);
                    merchantWalletService.withdraw(TEST_MERCHANT_ID, WITHDRAW_AMOUNT);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.warn("❌ 请求 #{} 被拦截/失败: {}", requestId, e.getMessage());
                    failCount.incrementAndGet();
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        // 发令枪响！
        long startTime = System.currentTimeMillis();
        startSignal.countDown();

        doneSignal.await();
        long costTime = System.currentTimeMillis() - startTime;
        executorService.shutdown();

        // 查库复盘
        MerchantDO finalMerchant = merchantMapper.selectById(TEST_MERCHANT_ID);
        BigDecimal totalWithdrawn = WITHDRAW_AMOUNT.multiply(new BigDecimal(successCount.get()));

        log.info("========================= 提现测试复盘报告 =========================");
        log.info("⏱️ 执行总耗时: {} ms", costTime);
        log.info("✅ 成功提现打款次数: {} 次", successCount.get());
        log.info("❌ 失败拦截次数: {} 次", failCount.get());
        log.info("💸 实际向银行划出总资金: {} 元", totalWithdrawn);
        log.info("🏦 数据库最终剩余商户余额: {} 元", finalMerchant.getBalance());
        log.info("====================================================================");

        if (successCount.get() > 1) {
            log.error("🔥【特大资金事故！双倍出金！】账户只有 1000 元，却向银行成功发起了 2 次打款，共划出 {} 元！公司亏损 1000 元！", totalWithdrawn);
        } else {
            log.info("🎉【验证通过！】分布式锁成功拦截重复提现，资金绝对安全！");
        }
    }
}
