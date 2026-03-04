package com.rensights.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        String method = request.getMethod();
        String path = request.getRequestURI();
        String traceId = MDC.get("traceId");

        try {
            filterChain.doFilter(request, response);
            long durationMs = System.currentTimeMillis() - startedAt;
            logger.info("HTTP {} {} -> {} ({}ms) traceId={}", method, path, response.getStatus(), durationMs, traceId);
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startedAt;
            logger.error("HTTP {} {} -> 500 ({}ms) traceId={} - {}", method, path, durationMs, traceId, ex.getMessage());
            throw ex;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator");
    }
}
