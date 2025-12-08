package com.rensights.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * SECURITY: Rate limiting filter to prevent brute force attacks and DoS
 * Implements sliding window rate limiting per IP address
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);
    
    // Rate limit configurations
    private static final int AUTH_RATE_LIMIT = 5; // 5 requests per window
    private static final int AUTH_WINDOW_SECONDS = 60; // 1 minute window
    
    private static final int GENERAL_RATE_LIMIT = 100; // 100 requests per window
    private static final int GENERAL_WINDOW_SECONDS = 60; // 1 minute window
    
    // Cache: IP address -> Request count with expiry
    private final Cache<String, Integer> authRequestCache = Caffeine.newBuilder()
            .expireAfterWrite(AUTH_WINDOW_SECONDS, TimeUnit.SECONDS)
            .maximumSize(10000)
            .build();
    
    private final Cache<String, Integer> generalRequestCache = Caffeine.newBuilder()
            .expireAfterWrite(GENERAL_WINDOW_SECONDS, TimeUnit.SECONDS)
            .maximumSize(10000)
            .build();
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Skip rate limiting for OPTIONS requests (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String clientIp = getClientIpAddress(request);
        String path = request.getRequestURI();
        
        // Apply stricter rate limiting to authentication endpoints
        if (path.startsWith("/api/auth/") || path.startsWith("/api/admin/auth/")) {
            if (!checkRateLimit(clientIp, authRequestCache, AUTH_RATE_LIMIT, "authentication")) {
                logger.warn("SECURITY ALERT: Rate limit exceeded for IP {} on path {}", clientIp, path);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
                return;
            }
        } else {
            // General rate limiting for all other endpoints
            if (!checkRateLimit(clientIp, generalRequestCache, GENERAL_RATE_LIMIT, "general")) {
                logger.warn("SECURITY ALERT: Rate limit exceeded for IP {} on path {}", clientIp, path);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
    
    private boolean checkRateLimit(String key, Cache<String, Integer> cache, int limit, String type) {
        Integer count = cache.getIfPresent(key);
        
        if (count == null) {
            cache.put(key, 1);
            return true;
        }
        
        if (count >= limit) {
            return false;
        }
        
        cache.put(key, count + 1);
        return true;
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        // Check X-Forwarded-For header first (for proxies/load balancers)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP if there's a comma-separated list
            return xForwardedFor.split(",")[0].trim();
        }
        
        // Check X-Real-IP header
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // Fallback to remote address
        return request.getRemoteAddr();
    }
}

