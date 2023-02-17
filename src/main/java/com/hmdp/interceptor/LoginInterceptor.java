package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * @author: sunxun
 * @date: 2/17/23 3:19 PM
 * @description:
 */
public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 执行handle前，校验登录状态
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession();
        Object user = session.getAttribute("user");
        if (user == null) {
            // 401: is not login
            response.setStatus(401);
            return false;
        }

        UserHolder.saveUser((UserDTO) user);

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
