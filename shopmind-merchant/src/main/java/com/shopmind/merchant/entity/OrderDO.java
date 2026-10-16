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
 * 订单实体类（MyBatis-Plus）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_order")
public class OrderDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long merchantId;

    private Long userId;

    private Long productId;

    private String productName;

    private Integer quantity;

    private BigDecimal totalAmount;

    private Integer orderStatus;

    private String expressNo;

    private LocalDateTime createdAt;
}
