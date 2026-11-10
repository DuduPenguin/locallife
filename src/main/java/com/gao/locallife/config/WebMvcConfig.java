package com.gao.locallife.config;

import com.gao.locallife.interceptor.LoginInterceptor;
import com.gao.locallife.interceptor.RefreshTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 拦截器注册。
 *
 * <p>顺序很重要：order 小的先执行 preHandle（afterCompletion 反过来）。
 * 必须是「识别续期(0) -> 强制校验(1)」，否则第二层拿不到用户。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 第一层：全量放行，只识别 + 续期
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);

        // 第二层：只拦需要登录的接口
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 登录相关
                        "/user/code", "/user/login", "/user/logout",
                        // 运维
                        "/ping", "/health", "/db/**", "/error",
                        // 公开浏览
                        "/shop/**", "/shop-type/**", "/voucher/**",
                        "/blog/hot", "/upload/**"
                )
                .order(1);
    }
}
