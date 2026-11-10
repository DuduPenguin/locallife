package com.gao.locallife.interceptor;

import cn.hutool.json.JSONUtil;
import com.gao.locallife.common.Result;
import com.gao.locallife.common.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 第二层拦截器：<b>强制校验</b>，ThreadLocal 里没用户就返回 401。
 *
 * <p>依赖第一层把用户放进 ThreadLocal，所以 order 必须比它大。
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (UserHolder.getUser() != null) {
            return true;
        }
        // 注意：拦截器里抛出异常不会被 @RestControllerAdvice 处理（那时还没进 Handler），
        // 所以这里手动写响应
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSONUtil.toJsonStr(Result.fail(401, "请先登录")));
        return false;
    }
}
