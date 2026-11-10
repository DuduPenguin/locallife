package com.gao.locallife.common;

/** 秒杀 Lua 脚本的返回码，避免业务代码里写魔法数字。 */
public final class SeckillCode {

    private SeckillCode() {
    }

    public static final long SUCCESS = 0L;
    public static final long SOLD_OUT = 1L;
    public static final long DUPLICATE = 2L;
    public static final long NOT_PREHEATED = 3L;
}
