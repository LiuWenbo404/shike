package io.liuwenbo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import io.liuwenbo.entity.User;

public interface UserService extends IService<User> {
    String login(String phone, String password);
}
