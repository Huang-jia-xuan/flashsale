package com.ttnn.flashsale.config;

import com.ttnn.flashsale.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 【阶段二 · 拦截器注册】
 * <p>
 * 将 AuthInterceptor 注册到 Spring MVC 拦截链中，
 * 拦截 /api/orders/** 下的所有请求（商品查询接口不需要登录）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/orders/**");
    }
}
