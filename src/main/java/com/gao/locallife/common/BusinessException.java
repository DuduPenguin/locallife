package com.gao.locallife.common;

/**
 * 业务异常：全局异常处理器会把它转成 Result.fail(code, msg)。
 *
 * <p>用法：throw new BusinessException("库存不足");
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(500, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
