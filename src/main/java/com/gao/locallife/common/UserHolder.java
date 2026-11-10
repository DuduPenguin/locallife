package com.gao.locallife.common;

import com.gao.locallife.dto.UserDTO;

/**
 * 当前登录用户上下文（ThreadLocal）。
 *
 * <p>两个纪律：
 * 1. 拦截器 preHandle 里 save，afterCompletion 里 <b>必须 remove</b>（线程池会复用线程，否则串号 + 内存泄漏）；
 * 2. 异步线程拿不到，需要用 TransmittableThreadLocal 或显式传参。
 */
public class UserHolder {

    private static final ThreadLocal<UserDTO> TL = new ThreadLocal<>();

    public static void saveUser(UserDTO user) {
        TL.set(user);
    }

    public static UserDTO getUser() {
        return TL.get();
    }

    public static Long getUserId() {
        UserDTO u = TL.get();
        return u == null ? null : u.getId();
    }

    public static void removeUser() {
        TL.remove();
    }
}
