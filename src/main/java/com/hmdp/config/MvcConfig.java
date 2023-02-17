package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author: sunxun
 * @date: 2/17/23 3:29 PM
 * @description:
 */

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * RefreshTokenInterceptor拦截所有请求，能从前端获取到用户信息，则刷新。
     * LoginInterceptor在之后，拦截部分请求，做已登录验证。
     * 用Order控制执行顺序。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor()).excludePathPatterns(
                "/user/login",
                "/user/code",
                "/blog/hot",
                "/shop/**",
                "/shop-type/**",
                "/voucher/**",
                "/upload/**"
        ).order(1);

        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);
    }
}
