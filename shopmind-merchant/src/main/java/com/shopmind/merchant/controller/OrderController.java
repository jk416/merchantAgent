package com.shopmind.merchant.controller;

import com.shopmind.common.core.result.CommonErrorCode;
import com.shopmind.common.core.result.Result;
import com.shopmind.merchant.dto.CreateOrderRequest;
import com.shopmind.merchant.service.OrderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 基础下单接口
     */
    @PostMapping("/create")
    public Result<String> createOrder(@RequestBody CreateOrderRequest request) {
        if (request.productId() == null || request.quantity() == null || request.quantity() <= 0) {
            return Result.fail(CommonErrorCode.PARAM_ERROR);
        }
        try {
            String orderNo = orderService.createOrder(request);
            return Result.ok(orderNo);
        } catch (Exception e) {
            return Result.fail(CommonErrorCode.SYSTEM_ERROR.code(), e.getMessage());
        }
    }
}
