package com.rensights.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingInterceptor.class);
    private static final String START_TIME_ATTR = "requestStartTimeMs";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());

        String handlerName = handler instanceof HandlerMethod
            ? ((HandlerMethod) handler).getBeanType().getSimpleName() + "#" + ((HandlerMethod) handler).getMethod().getName()
            : handler.getClass().getSimpleName();
        String traceId = MDC.get("traceId");

        logger.info("Flow start: {} {} -> {} traceId={}", request.getMethod(), request.getRequestURI(), handlerName, traceId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object startedAt = request.getAttribute(START_TIME_ATTR);
        long durationMs = startedAt instanceof Long ? (System.currentTimeMillis() - (Long) startedAt) : -1L;

        String handlerName = handler instanceof HandlerMethod
            ? ((HandlerMethod) handler).getBeanType().getSimpleName() + "#" + ((HandlerMethod) handler).getMethod().getName()
            : handler.getClass().getSimpleName();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String principal = (auth != null && auth.isAuthenticated()) ? String.valueOf(auth.getName()) : "anonymous";
        String traceId = MDC.get("traceId");

        if (ex != null) {
            logger.error("Flow error: {} {} -> {} ({}ms) user={} traceId={} error={}",
                request.getMethod(), request.getRequestURI(), handlerName, durationMs, principal, traceId, ex.getClass().getSimpleName());
        } else {
            logger.info("Flow end: {} {} -> {} ({}ms) status={} user={} traceId={}",
                request.getMethod(), request.getRequestURI(), handlerName, durationMs, response.getStatus(), principal, traceId);
        }
    }
}
