package com.shopmind.merchant.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shopmind.merchant.dto.CreateOrderRequest;
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
    private final com.shopmind.merchant.service.OrderService orderService;

    public CustomerTools(ProductMapper productMapper,
                         OrderMapper orderMapper,
                         com.shopmind.merchant.service.OrderService orderService) {
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.orderService = orderService;
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
    ) {
    }

    /**
     * 订单出参数据结构
     */
    public record OrderItemVO(
            String orderNo,
            String productName,
            BigDecimal totalAmount,
            String statusDesc,
            String expressNo
    ) {
    }

    /**
     * 秒杀下单出参数据结构（供大模型理解开单结果并生成导购话术）
     */
    public record SeckillOrderResultVO(
            boolean success,
            String orderNo,
            String productName,
            Integer quantity,
            BigDecimal totalAmount,
            String message
    ) {
    }

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

    /**
     * 工具 3：智能导购秒杀下单（写操作 / Action Tool）
     * 供大模型在识别出用户抢购秒杀意图后，自主决策反射调用
     */
    @Tool(description = "根据店铺ID、用户ID、商品ID及购买数量，执行高并发秒杀下单。成功则返回订单号与金额，失败则返回具体原因")
    public SeckillOrderResultVO seckillProduct(
            @ToolParam(description = "商家店铺ID，通常为当前店铺ID") Long merchantId,
            @ToolParam(description = "顾客用户ID，必须为数字") Long userId,
            @ToolParam(description = "抢购商品ID，例如 1") Long productId,
            @ToolParam(description = "抢购数量，默认通常为 1") Integer quantity
    ) {
        // 1. 参数防御校验（防大模型参数幻觉）
        if (userId == null || productId == null) {
            return new SeckillOrderResultVO(false, null, null, null, null, "用户ID或商品ID不能为空，请先向顾客索取");
        }
        int buyQty = (quantity == null || quantity <= 0) ? 1 : quantity;

        log.info("🔥 [Agent Tool 触发] 大模型自主开单调用 Java 秒杀工具: seckillProduct, merchantId={}, userId={}, productId={}, quantity={}",
                merchantId, userId, productId, buyQty);

        // 2. 校验商品基本信息
        ProductDO product = productMapper.selectById(productId);
        if (product == null) {
            return new SeckillOrderResultVO(false, null, null, null, null, "商品不存在，请核对商品编号");
        }

        // 3. 调度底层高并发秒杀引擎（Redis Lua 预扣 + RocketMQ 异步削峰）
        try {
            String orderNo = orderService.createOrder(new CreateOrderRequest(merchantId, userId, productId, buyQty));
            BigDecimal totalAmount = product.getPrice().multiply(BigDecimal.valueOf(buyQty));
            log.info("🔥 [Agent Tool 成功] 秒杀开单完成, 订单号: {}, 商品: {}, 总金额: {}", orderNo, product.getProductName(), totalAmount);
            return new SeckillOrderResultVO(true, orderNo, product.getProductName(), buyQty, totalAmount, "秒杀抢购开单成功！");
        } catch (Exception e) {
            log.warn("🔥 [Agent Tool 业务拦截/异常] 秒杀开单未成功: {}", e.getMessage());
            // 异常友好降级（如售罄、系统繁忙），返回 success=false 并把商品名与错误原因传给大模型
            return new SeckillOrderResultVO(false, null, product.getProductName(), buyQty, null, e.getMessage());
        }
    }
}

