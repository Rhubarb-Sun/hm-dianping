package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author: sunxun
 * @date: 2/17/23 3:29 PM
 * @description:
 */

@Configuration
public class MvcConfig implements WebMvcConfigurer {
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
        );
    }
}
