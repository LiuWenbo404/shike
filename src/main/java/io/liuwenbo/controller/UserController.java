package io.liuwenbo.controller;

import io.liuwenbo.common.BaseContext;
import io.liuwenbo.common.Result;
import io.liuwenbo.dto.UserLoginDTO;
import io.liuwenbo.entity.User;
import io.liuwenbo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@Slf4j
@Tag(name = "用户接口")
@RestController
@RequestMapping("/api/user/")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户登录
     * @return
     */
    @Operation(summary = "用户登录接口")
    @PostMapping("/login")
    public Result login(@RequestBody UserLoginDTO dto){
        log.info("用户登录:{}",dto.getPhone());
        String phone = dto.getPhone();
        String password = dto.getPassword();
        String token = userService.login(phone, password);
        return Result.ok(token);
    }


    /**
     * 获取当前用户信息
     * @return
     */
    @Operation(summary = "获取当前用户信息")
    @GetMapping("/info")
    public Result<User> me(){
        User user = userService.getById(BaseContext.getUserId());
        user.setPassword("");
        return Result.ok(user);
    }
}
