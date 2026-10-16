package com.shopmind.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopmind.merchant.entity.OrderDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单 MyBatis Mapper 接口
 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderDO> {
}
