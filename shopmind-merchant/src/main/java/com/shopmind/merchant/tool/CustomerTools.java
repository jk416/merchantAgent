package com.shopmind.merchant.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shopmind.merchant.entity.OrderDO;
import com.shopmind.merchant.entity.ProductDO;
import com.shopmind.merchant.mapper.OrderMapper;
import com.shopmind.merchant.mapper.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 智能客服专用业务工具箱（Function Calling）
 * 供大模型在推理过程中自主决策并反射调用
 */
@Component
public class CustomerTools {

    private static final Logger log = LoggerFactory.getLogger(CustomerTools.class);

    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;

    public CustomerTools(ProductMapper productMapper, OrderMapper orderMapper) {
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
    }

    /**
     * 商品出参数据结构
     */
    public record ProductItemVO(
            String productCode,
            String productName,
            String category,
            BigDecimal price,
            Integer stock,
            String description
    ) {}

    /**
     * 订单出参数据结构
     */
    public record OrderItemVO(
            String orderNo,
            String productName,
            BigDecimal totalAmount,
            String statusDesc,
            String expressNo
    ) {}

    /**
     * 工具 1：根据商家店铺ID查询在售商品列表
     */
    @Tool(description = "根据商家店铺ID查询该店铺当前所有在售的商品列表、类目、价格及库存信息")
    public List<ProductItemVO> queryProductsByMerchantId(
            @ToolParam(description = "商家店铺ID，默认通常为当前店铺ID") Long merchantId
    ) {
        log.info("🔥 [Agent Tool 触发] 大模型自主开单调用 Java 方法: queryProductsByMerchantId, 入参 merchantId={}", merchantId);
        List<ProductDO> list = productMapper.selectList(
                new LambdaQueryWrapper<ProductDO>()
                        .eq(ProductDO::getMerchantId, merchantId)
                        .eq(ProductDO::getStatus, 1)
        );
        log.info("🔥 [Agent Tool 查库完成] 本地 MySQL 数据库返回在售商品记录数: {}", list.size());
        return list.stream()
                .map(p -> new ProductItemVO(
                        p.getProductCode(),
                        p.getProductName(),
                        p.getCategory(),
                        p.getPrice(),
                        p.getStockCount(),
                        p.getDescription()
                ))
                .toList();
    }

    /**
     * 工具 2：根据订单号查询订单物流与发货状态
     */
    @Tool(description = "根据订单编号查询订单详情、支付状态及顺丰物流单号")
    public OrderItemVO queryOrderByOrderNo(
            @ToolParam(description = "订单编号，例如 ORD-20261015-001") String orderNo
    ) {
        log.info("🔥 [Agent Tool 触发] 大模型自主开单调用 Java 方法: queryOrderByOrderNo, 入参 orderNo={}", orderNo);
        OrderDO order = orderMapper.selectOne(
                new LambdaQueryWrapper<OrderDO>().eq(OrderDO::getOrderNo, orderNo)
        );
        if (order == null) {
            log.warn("🔥 [Agent Tool] 未查询到对应订单号: {}", orderNo);
            return null;
        }
        String statusDesc = switch (order.getOrderStatus()) {
            case 1 -> "已付款待发货";
            case 2 -> "已发货（顺丰速运）";
            case 3 -> "已签收完成";
            case 4 -> "已退款";
            default -> "未知状态";
        };
        log.info("🔥 [Agent Tool 查库完成] 订单查询成功: 商品={}, 状态={}, 物流号={}", order.getProductName(), statusDesc, order.getExpressNo());
        return new OrderItemVO(order.getOrderNo(), order.getProductName(), order.getTotalAmount(), statusDesc, order.getExpressNo());
    }
}
