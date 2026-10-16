package com.shopmind.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopmind.merchant.entity.MerchantDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商家与人设 MyBatis Mapper 接口
 */
@Mapper
public interface MerchantMapper extends BaseMapper<MerchantDO> {
}
