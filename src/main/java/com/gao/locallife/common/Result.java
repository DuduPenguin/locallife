package com.gao.locallife.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结构：{ code, msg, data }。
 *
 * <p>约定：200 成功；400 参数错误；401 未登录；403 无权限；500 业务/系统异常。
 */
@Data
public class Result<T> implements Serializable {

    private int code;
    private String msg;
    private T data;

    public boolean isSuccess() {
        return code == 200;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.msg = "ok";
        r.data = data;
        return r;
    }

    public static <T> Result<T> ok(String msg, T data) {
        Result<T> r = ok(data);
        r.msg = msg;
        return r;
    }

    public static <T> Result<T> fail(String msg) {
        return fail(500, msg);
    }

    public static <T> Result<T> fail(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        return r;
    }
}
