package com.shopmind.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopmind.merchant.entity.ProductDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品 MyBatis Mapper 接口
 */
@Mapper
public interface ProductMapper extends BaseMapper<ProductDO> {
}
