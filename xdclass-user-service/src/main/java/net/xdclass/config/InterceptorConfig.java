package net.xdclass.config;

import lombok.extern.slf4j.Slf4j;
import net.xdclass.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 每个微服务都配置自己的拦截器
@Configuration
@Slf4j
public class InterceptorConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(new LoginInterceptor())
                //拦截的路径
                .addPathPatterns("/api/user/*/**","/api/address/*/**")

                //不拦截的路径：图形验证码、邮箱验证码、注册、登录、文件上传
                .excludePathPatterns("/api/user/*/captcha","/api/user/*/send_code",
                        "/api/user/*/register","/api/user/*/login","/api/user/*/upload");

    }
}
