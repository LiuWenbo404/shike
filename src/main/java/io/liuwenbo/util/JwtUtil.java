package io.liuwenbo.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;        // jwt秘钥
    private final Long expireHours;     // jwt过期时间(小时)

    public JwtUtil(
            @Value("${jwt.key}") String secret,
            @Value("${jwt.expire_hours}") Long expireHours
    ){
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); // 初始化key
        this.expireHours = expireHours;                                         // 初始化过期时间
    }

    // 生成jwt
    public String generateToken(Long userId){
        return Jwts.builder()
                .claim("userId",userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expireHours * 3600_000L)) // 转为毫秒
                .signWith(key, SignatureAlgorithm.HS256)    // 加密算法：HS256
                .compact();
    }

    // 解析jwt
    public Long parseToken(String token){
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("userId",Long.class);
    }

}
