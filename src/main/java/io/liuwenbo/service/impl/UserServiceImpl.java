package io.liuwenbo.service.impl;

import ch.qos.logback.core.util.MD5Util;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.liuwenbo.common.BaseContext;
import io.liuwenbo.common.BizException;
import io.liuwenbo.entity.User;
import io.liuwenbo.mapper.UserMapper;
import io.liuwenbo.service.UserService;
import io.liuwenbo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.security.NoSuchAlgorithmException;
import java.util.List;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public String login(String phone, String password) {
        // 密码加密后比对
        password = DigestUtils.md5DigestAsHex(password.getBytes());
        User user = lambdaQuery().eq(User::getPhone, phone)
                .eq(User::getPassword, password)
                .one();
        if(user == null){
            throw new BizException(){{setMsg("用户名或密码错误！");}};
        }

        Long userId = user.getId();
        BaseContext.setUserId(userId);

        String token = jwtUtil.generateToken(userId);
        return token;
    }
}
