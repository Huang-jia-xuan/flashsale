package com.ttnn.flashsale.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttnn.flashsale.common.Result;
import com.ttnn.flashsale.entity.User;
import com.ttnn.flashsale.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 【阶段二 · 用户身份拦截器】
 * <p>
 * 拦截所有 /api/orders/** 请求，要求请求头携带 X-User-Id。<br/>
 * 校验用户存在后，将 userId 存入 request attribute 供后续 Controller 使用。
 */
@Component
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String userIdHeader = request.getHeader("X-User-Id");

        if (userIdHeader == null || userIdHeader.trim().isEmpty()) {
            log.warn("拦截请求: 缺少 X-User-Id 请求头, uri={}", request.getRequestURI());
            writeJson(response, 401, "未登录，请在请求头中携带 X-User-Id");
            return false;
        }

        try {
            Long userId = Long.parseLong(userIdHeader.trim());
            User user = userMapper.selectById(userId);
            if (user == null) {
                writeJson(response, 401, "用户不存在");
                return false;
            }
            request.setAttribute("userId", userId);
            return true;
        } catch (NumberFormatException e) {
            writeJson(response, 401, "X-User-Id 格式不正确");
            return false;
        }
    }

    private void writeJson(HttpServletResponse response, int code, String msg) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code);
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, msg)));
    }
}
