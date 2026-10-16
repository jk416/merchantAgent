package com.shopmind.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体类（MyBatis-Plus）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_product")
public class ProductDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    private String productCode;

    private String productName;

    private String category;

    private BigDecimal price;

    private Integer stockCount;

    private String description;

    private Integer status;

    private LocalDateTime createdAt;
}
