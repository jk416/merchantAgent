package com.shopmind.merchant.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shopmind.merchant.entity.MerchantDO;
import com.shopmind.merchant.mapper.MerchantMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MerchantDao {

    private final MerchantMapper merchantMapper;

    public MerchantDao(MerchantMapper merchantMapper) {
        this.merchantMapper = merchantMapper;
    }

    /**
     * 根据商家ID查询店铺信息及AI人设（基于 MyBatis-Plus 实现）
     */
    public Optional<MerchantDO> findById(Long id) {
        MerchantDO merchant = merchantMapper.selectOne(
                new LambdaQueryWrapper<MerchantDO>()
                        .eq(MerchantDO::getId, id)
                        .eq(MerchantDO::getStatus, 1)
        );
        return Optional.ofNullable(merchant);
    }
}
