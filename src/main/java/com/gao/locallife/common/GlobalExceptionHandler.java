package com.gao.locallife.common;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 *
 * <p>三类：业务异常、参数校验失败、兜底异常。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** @Valid 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.fail(400, msg);
    }

    /** 兜底：不把堆栈暴露给前端 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "系统繁忙，请稍后再试");
    }

    /** 请求方法不支持（比如用 GET 访问 POST 接口） */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.fail(405, "请求方法不支持：" + e.getMethod());
    }

    /** 路径不存在 */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        return Result.fail(404, "接口不存在：" + e.getResourcePath());
    }
}
