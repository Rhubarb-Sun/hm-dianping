package com.hmdp.interceptor;

import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author: sunxun
 * @date: 2/17/23 3:19 PM
 * @description: WebMvcConfigurer.addInterceptors()加入此拦截器，但是这个拦截器类本身并没有注入Spring容器
 */

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 1. 需要拦截
        if (UserHolder.getUser() == null) {
            //没有登录！需要拦截
            log.info("没有登录！");
            response.setStatus(401);
            return false;
        }

        return true;
    }
}
