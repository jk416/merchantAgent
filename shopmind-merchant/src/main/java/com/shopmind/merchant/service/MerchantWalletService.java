package com.shopmind.merchant.service;

import java.math.BigDecimal;

/**
 * 商家资金与钱包服务
 */
public interface MerchantWalletService {

    /**
     * 商家账户资金提现到银行卡/微信
     *
     * @param merchantId 商家ID
     * @param amount     提现金额
     * @return 提现出金流水号
     */
    String withdraw(Long merchantId, BigDecimal amount);
}
