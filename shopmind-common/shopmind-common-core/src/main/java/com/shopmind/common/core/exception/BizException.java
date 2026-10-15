package com.shopmind.common.core.exception;

import com.shopmind.common.core.result.ErrorCode;

/**
 * 通用业务异常基类
 */
public class BizException extends RuntimeException {
    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.msg());
        this.code = errorCode.code();
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(detail);
        this.code = errorCode.code();
    }

    public int getCode() {
        return code;
    }
}
