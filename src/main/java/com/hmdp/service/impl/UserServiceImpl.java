package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (phone == null || RegexUtils.isPhoneInvalid(phone)) {
        //如果不符合要求，返回错误信息
            return Result.fail("手机号格式不正确");
        }

        //符合要求，发送验证码
        //生成验证码
        String code = RandomUtil.randomNumbers(6);

        //保存验证码到session
//        session.setAttribute("code", code);
        //保存验证码到redis

        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY +phone,
                code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);



        //发送验证码
        log.debug("发送验证码成功：{}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 校验手机号 和 验证码
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
        // 不一致 返回错误信息
            return Result.fail("手机号格式不正确");
        }
        //从session中获取验证码
//        Object cacheCode = session.getAttribute("code");

        //从redis中获取验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);


        String code = loginForm.getCode();


        if (cacheCode ==null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        // 一致 根据手机号查询用户信息
        User user = query().eq("phone", phone).one();

        //判断用户是否存在
        if (user == null) {
            user= createUserWithPhone(phone);
        }

        //保存用户信息到session
        //BeanUtil.copyProperties(user, UserDTO.class) 将user对象转换为UserDTO对象
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        //7保存用户信息到redis
        //7.1随机生成一个token
        String token = UUID.randomUUID().toString(true);
        //7.2将UserDTO对象转换为哈希
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //这里由于userDTO中有Long类型的属性，会导致转换失败，所以需要将Long类型的属性转换为String类型
//        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        //设置字段值编辑器，将Long类型的属性转换为String类型
                                    .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
        //7.3存储
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY+token, userMap);
        //7.4设置过期时间
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
