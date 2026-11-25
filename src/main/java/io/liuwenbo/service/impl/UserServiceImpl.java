package io.liuwenbo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.liuwenbo.common.BizException;
import io.liuwenbo.entity.User;
import io.liuwenbo.mapper.UserMapper;
import io.liuwenbo.service.UserService;
import io.liuwenbo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public String login(String phone, String password) {
        if (phone == null || phone.isBlank() || password == null || password.isBlank()) {
            throw new BizException("手机号和密码不能为空");
        }
        // 密码加密后比对
        password = DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
        User user = lambdaQuery().eq(User::getPhone, phone)
                .eq(User::getPassword, password)
                .one();
        if (user == null) {
            throw new BizException("用户名或密码错误！");
        }

        // 登录接口不走 JWT 拦截器，这里不要写 BaseContext：
        // 请求结束后没有 afterCompletion 负责清理，会污染线程池里的下一个请求。
        return jwtUtil.generateToken(user.getId());
    }
}
