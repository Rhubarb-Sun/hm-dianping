package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // validate phone format
        if (RegexUtils.isPhoneInvalid(phone)) {
            log.info("The format of phone {} is wrong!", phone);
            return Result.fail("the format of phone is wrong!");
        }

        // generate random numbers and save it in session.
        String code = RandomUtil.randomNumbers(6);
        session.setAttribute("code", code);

        // TODO 发送短信验证码
        log.info("The code of phone is {}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        // 校验手机号（再次校验）
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            log.info("The format of phone {} is wrong!", phone);
            return Result.fail("the format of phone is wrong!");
        }
        // 校验验证码
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if (StrUtil.isBlankIfStr(cacheCode) || !cacheCode.equals(code)) {
            log.info("The code doesn't exist or has expired");
            return Result.fail("The code doesn't exist or has expired");
        }

        // 根据手机号从库中查询用户
        User user = query().eq("phone", phone).one();
        // 用户不存在，创建用户并入库
        if (user == null) {
            user = createUser(phone);
        }

        // 最后保存用户到Session中。**但是应该控制User可以传输的内容，此处使用UserDTO类**
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok();
    }

    private User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

        save(user);
        return user;
    }
}
