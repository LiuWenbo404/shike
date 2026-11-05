package io.liuwenbo.common;

import io.jsonwebtoken.Claims;
import io.liuwenbo.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    public JwtInterceptor(JwtUtil jwtUtil){
        this.jwtUtil = jwtUtil;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if(token == null || !token.startsWith("Bearer ")){
            // 没有token
            response.setStatus(401);
            response.getWriter().write("no token");
            return false;
        }
        log.info("开始jwt令牌校验：{}",token);
        try {
            Long userId = jwtUtil.parseToken(token.substring(7));// 裁剪开头的 Bearer
            BaseContext.setUserId(userId); // 保存到threadlocal
            log.info("当前用户id:{}",BaseContext.getUserId());
            return true;
        } catch (Exception e)
        {
            response.setStatus(401);
            return false;
        }


    }
}
