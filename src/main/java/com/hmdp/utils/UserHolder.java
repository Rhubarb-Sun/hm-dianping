package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

/**
 * 拦截器校验状态，放行后，存入ThreadLocal本地缓存，方便当前线程的后续操作。
 * 调用拦截的handler完成后，删除此ThreadLocal。
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
