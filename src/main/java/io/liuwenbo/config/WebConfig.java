package io.liuwenbo.config;

import io.liuwenbo.common.JwtInterceptor;
import io.liuwenbo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public void addInterceptors(InterceptorRegistry registry){
        registry.addInterceptor(new JwtInterceptor(jwtUtil))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/user/login",
                        "/doc.html",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/swagger-resources/**",
                        "/favicon.ico"
                );

    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // knife4j静态资源
        registry.addResourceHandler("doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
