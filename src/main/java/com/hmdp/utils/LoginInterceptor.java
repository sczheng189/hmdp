package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        //获取session
////        HttpSession session = request.getSession();
////        //获取session的用户信息
////        Object user = session.getAttribute("user");
////        //判断用户是否存在
////        if (user == null) {
////            //用户不存在，;拦截
////            response.setStatus(401);
////            return false;
////        }
////        //把用户信息存入ThreadLocal
////        UserHolder.saveUser((UserDTO) user);
////        //用户存在，放行
////        return true;
        //1.获取请求投中的token
        String token = request.getHeader("authorization");
        log.info("token:{}", token);

        if (StrUtil.isBlank(token)) {
            //用户不存在，;拦截
//            response.setStatus(401);
            return true;
        }
        //2.基于token获取redis中的用户信息
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
        //3.判断用户是否存在
        if (userMap.isEmpty()) {
            //4.用户不存在，;拦截
//            response.setStatus(401);
            return true;
        }

        //5.将查询到的Hash数据转为UserDto对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        //6.将UserDto对象存入ThreadLocal
        UserHolder.saveUser(userDTO);

        //7.刷新token的过期时间
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        //8.用户存在，放行
        return true;

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //清除ThreadLocal中的用户信息
        UserHolder.removeUser();
    }
}
