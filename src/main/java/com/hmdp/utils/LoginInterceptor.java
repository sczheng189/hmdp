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
        // threadLocal是否有用户信息
        if (UserHolder.getUser()==null) {
            //用户不存在，;拦截
            response.setStatus(401);
            return false;
        }
        //8.用户存在，放行
        return true;
    }

}
