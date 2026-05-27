package com.ttnn.flashsale.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * 【阶段二 · AOP 接口性能监控】
 * <p>
 * 通用切面，拦截所有 Controller 方法，自动记录：<br/>
 * 1. 请求入参<br/>
 * 2. 响应出参<br/>
 * 3. 接口执行耗时
 */
@Aspect
@Component
@Slf4j
public class ApiLogAspect {

    @Autowired
    private ObjectMapper objectMapper;

    @Pointcut("execution(* com.ttnn.flashsale.controller..*.*(..))")
    public void controllerPointcut() {
    }

    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();

        // 过滤不可序列化的参数（如 HttpServletRequest / HttpServletResponse）
        Object[] filteredArgs = filterArgs(joinPoint.getArgs());
        String argsJson = safeToJson(filteredArgs);
        log.info(">>> 请求开始 | 方法: {} | 入参: {}", methodName, argsJson);

        long startTime = System.currentTimeMillis();
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            long cost = System.currentTimeMillis() - startTime;
            log.error("<<< 请求异常 | 方法: {} | 耗时: {}ms | 异常: {}", methodName, cost, t.getMessage());
            throw t;
        }

        long cost = System.currentTimeMillis() - startTime;
        String resultJson = safeToJson(result);
        log.info("<<< 请求结束 | 方法: {} | 耗时: {}ms | 出参: {}", methodName, cost, resultJson);

        return result;
    }

    /** 过滤 Servlet 原生对象，避免序列化异常 */
    private Object[] filterArgs(Object[] args) {
        if (args == null) {
            return new Object[0];
        }
        List<Object> filtered = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse) {
                continue;
            }
            filtered.add(arg);
        }
        return filtered.toArray();
    }

    private String safeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[序列化失败]";
        }
    }
}
