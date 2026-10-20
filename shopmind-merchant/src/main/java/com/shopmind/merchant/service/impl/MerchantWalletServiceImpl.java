package com.shopmind.merchant.service.impl;

import com.shopmind.merchant.entity.MerchantDO;
import com.shopmind.merchant.mapper.MerchantMapper;
import com.shopmind.merchant.service.MerchantWalletService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 商家钱包资金服务实现
 * <p>
 * 演进阶段：
 * Level 1: 脆弱版本（无分布式锁保护，并发下发生重复提现/多倍出金重大资金事故）
 * Level 2: 接入 Redisson 分布式锁（掌握 tryLock 防重复提交与看门狗自动续期机制）
 */
@Service
public class MerchantWalletServiceImpl implements MerchantWalletService {

    private static final Logger log = LoggerFactory.getLogger(MerchantWalletServiceImpl.class);

    private final MerchantMapper merchantMapper;
    private final RedissonClient redissonClient;

    public MerchantWalletServiceImpl(MerchantMapper merchantMapper, RedissonClient redissonClient) {
        this.merchantMapper = merchantMapper;
        this.redissonClient = redissonClient;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String withdraw(Long merchantId, BigDecimal amount) {
        String localKey = "lock:wallet:withdraw:" + merchantId;
        RLock lock = redissonClient.getLock(localKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, -1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取分布式锁失败", e);
        }
        if (!acquired) {
            // 核心拦截：同一时间只允许一笔提现在飞！
            throw new RuntimeException("提现业务正在处理中，请勿重复提交！");
        }
        try {
            // 1. 查询商家当前账户余额
            MerchantDO merchant = merchantMapper.selectById(merchantId);
            if (merchant == null) {
                throw new RuntimeException("商家不存在");
            }

            // 2. 校验余额是否充足
            if (merchant.getBalance().compareTo(amount) < 0) {
                throw new RuntimeException("账户余额不足，提现失败！当前余额: " + merchant.getBalance());
            }

            // 3. 【核心慢调用模拟】：调用第三方银行/微信支付代付接口（耗时约 500ms）
            // 在真实生产中，这个调用极其危险，绝不能在未加互斥锁的情况下并发进入！
            mockCallBankPaymentGateway(merchantId, amount);

            // 4. 扣减数据库余额并写回
            BigDecimal newBalance = merchant.getBalance().subtract(amount);
            merchant.setBalance(newBalance);
            merchantMapper.updateById(merchant);

            String serialNo = "W-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + ThreadLocalRandom.current().nextInt(1000, 9999);
            log.info("💰 提现成功！商家 ID={}, 提现金额={}, 剩余余额={}, 流水号={}",
                    merchantId, amount, newBalance, serialNo);

            return serialNo;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 模拟调用第三方银行支付通道（慢网络 IO）
     */
    private void mockCallBankPaymentGateway(Long merchantId, BigDecimal amount) {
        try {
            log.info("🏦 正在调用第三方银行打款通道，商家 ID={}, 正在向银行转出金额: {} 元...", merchantId, amount);
            Thread.sleep(500); // 模拟网络延迟
            log.info("✅ 银行打款接口调用完成！资金已划出: {} 元！", amount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
