package com.gao.locallife.interceptor;

import cn.hutool.core.util.StrUtil;
import com.gao.locallife.common.SystemConstants;
import com.gao.locallife.common.UserHolder;
import com.gao.locallife.dto.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.gao.locallife.common.RedisConstants.LOGIN_TOKEN_KEY;
import static com.gao.locallife.common.RedisConstants.LOGIN_TOKEN_TTL;

/**
 * 第一层拦截器：<b>永不拦截</b>，只做「识别当前用户 + 滑动续期」。
 *
 * <p>为什么不在这里拦：公开接口（商户列表、热门笔记）也要知道"我是谁"，
 * 比如未登录也要显示"我有没有点过赞"，但不能因为没登录就拒绝访问。
 *
 * <p>注意必须配 {@code /**}，否则续期链路会断。
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader(SystemConstants.TOKEN_HEADER);
        if (StrUtil.isBlank(token)) {
            return true;                       // 没 token 也放行，交给第二层判断
        }

        String key = LOGIN_TOKEN_KEY + token;
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);
        if (map.isEmpty()) {
            return true;                       // token 无效或已过期，同样放行
        }

        UserDTO user = new UserDTO();
        user.setId(Long.valueOf(String.valueOf(map.get("id"))));
        user.setNickName(StrUtil.blankToDefault(String.valueOf(map.get("nickName")), ""));
        user.setIcon(StrUtil.blankToDefault(String.valueOf(map.get("icon")), ""));
        UserHolder.saveUser(user);

        // 滑动续期：只在「剩余不足一半」时写 Redis，避免每个请求都产生无效写
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl != null && ttl < LOGIN_TOKEN_TTL / 2) {
            stringRedisTemplate.expire(key, LOGIN_TOKEN_TTL, TimeUnit.SECONDS);
        }
        return true;
    }

    /** 必须清理：Tomcat 线程复用，不清会串号 + 内存泄漏。 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserHolder.removeUser();
    }
}
