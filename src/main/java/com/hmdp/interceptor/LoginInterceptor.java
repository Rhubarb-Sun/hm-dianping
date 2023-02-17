package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author: sunxun
 * @date: 2/17/23 3:19 PM
 * @description: WebMvcConfigurer.addInterceptors()加入此拦截器，但是这个拦截器类本身并没有注入Spring容器
 */

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    // LoginInterceptor类并没有交给Spring托管，不能用注解注入。可以通过构造器注入
    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 执行handle前，校验登录状态，加入ThreadLocal本地缓存，并刷新Redis缓存。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 由前端获取token并存在请求头的authorization里。
        String token = request.getHeader("authorization");

        // 2. 校验非空
        if (StrUtil.isBlank(token)) {
            log.info("401: user not login");
            response.setStatus(401);
            return false;
        }

        // 3. 从redis获取User
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if (userMap.isEmpty()) {
            log.info("401: user not login");
            response.setStatus(401);
            return false;
        }

        // 4. Hash类型转化为Java对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        // 5. 存入ThreadLocal
        UserHolder.saveUser(userDTO);

        // 6. 每次获取User，刷新过期时间（用session的时候已经被实现了）
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        return true;
    }

    /**
     * remove the threadLocal after the completion of handler to avoid memory leak.
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
