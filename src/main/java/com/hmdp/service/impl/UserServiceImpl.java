package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone) {
        // 1. validate phone format
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2. 校验失败
            log.info("The format of phone {} is wrong!", phone);
            return Result.fail("the format of phone is wrong!");
        }

        // 3. generate random numbers and save it in redis with Expiration time.
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.HOURS);

        // TODO 发送短信验证码
        log.info("The code of phone is {}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        // 1. 校验手机号（再次校验）
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            log.info("The format of phone {} is wrong!", phone);
            return Result.fail("the format of phone is wrong!");
        }
        // 2. 校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (StrUtil.isBlank(cacheCode) || !cacheCode.equals(code)) {
            log.info("The code doesn't exist or has expired");
            return Result.fail("The code doesn't exist or has expired");
        }
        // todo 校验成功后，应该把这个验证码删除
        // 3. 根据手机号从库中查询用户或生成新用户
        User user = query().eq("phone", phone).one();
        // 用户不存在，创建用户并入库
        if (user == null) {
            user = createUser(phone);
        }

        // 4. 最后保存用户到Redis中
        // 4.1 生成随机令牌
        String token = UUID.randomUUID().toString(true);
        // 4.2 转换为Hash形式 **应该控制User可以传输的内容，此处使用UserDTO类**
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 4.3 存入redis
        String tokenKey = LOGIN_USER_KEY + token;

        /*
        java.lang.ClassCastException: java.lang.Long cannot be cast to java.lang.String
        at org.springframework.data.redis.serializer.StringRedisSerializer.serialize

        Hash类型中Value为Long时，和序列化器相冲突。在上面BeanUtil.beanToMap的属性拷贝选项CopyOptions中设置
         */
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 4.4 set expiration time as 30 min
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.SECONDS);

        // 5 将token传给前端，前端保存在authorization中。
        return Result.ok(token);
    }

    private User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

        save(user);
        return user;
    }
}
