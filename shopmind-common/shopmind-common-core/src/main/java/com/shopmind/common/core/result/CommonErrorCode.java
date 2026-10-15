package com.shopmind.common.core.result;

public enum CommonErrorCode implements ErrorCode {
    PARAM_ERROR(10001, "参数错误"),
    UNAUTHORIZED(10002, "未登录"),
    FORBIDDEN(10003, "无权限"),
    TOO_MANY_REQUESTS(10004, "请求过于频繁"),
    SYSTEM_ERROR(19999, "系统繁忙，请稍后重试");

    private final int code;
    private final String msg;

    CommonErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String msg() {
        return msg;
    }
}
