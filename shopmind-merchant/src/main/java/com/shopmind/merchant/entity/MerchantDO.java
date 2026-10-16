package com.shopmind.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商家与店铺人设实体对象（MyBatis-Plus）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_merchant")
public class MerchantDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String merchantCode;

    private String merchantName;

    private String systemPrompt;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
