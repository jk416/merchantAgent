package com.shopmind.merchant.controller;

import com.shopmind.common.core.result.CommonErrorCode;
import com.shopmind.common.core.result.Result;
import com.shopmind.merchant.dto.ChatRequest;
import com.shopmind.merchant.service.CustomerService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/customer-service")
public class CustomerServiceController {

    private final CustomerService customerService;

    public CustomerServiceController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /**
     * Level 1: 同步阻塞对话接口（直接返回完整字符串 JSON）
     */
    @PostMapping("/chat/sync")
    public Result<String> chatSync(@RequestBody ChatRequest request) {
        if (request.merchantId() == null || request.message() == null || request.message().isBlank()) {
            return Result.fail(CommonErrorCode.PARAM_ERROR);
        }
        String chatId = (request.chatId() != null && !request.chatId().isBlank())
                ? request.chatId()
                : java.util.UUID.randomUUID().toString();
        String reply = customerService.chatSync(request.merchantId(), chatId, request.message());
        return Result.ok(reply);
    }

    /**
     * Level 2: 响应式流式对话接口（SSE，产生 text/event-stream 打字机数据流）
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        if (request.merchantId() == null || request.message() == null || request.message().isBlank()) {
            return Flux.just("参数错误：merchantId 和 message 不能为空");
        }
        String chatId = (request.chatId() != null && !request.chatId().isBlank())
                ? request.chatId()
                : java.util.UUID.randomUUID().toString();
        return customerService.chatStream(request.merchantId(), chatId, request.message());
    }
}
