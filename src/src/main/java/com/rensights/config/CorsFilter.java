package com.rensights.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class CorsFilter implements Filter {
    
    private final String allowedOrigins;
    
    public CorsFilter(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins != null ? allowedOrigins : "*";
    }
    
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        
        String origin = request.getHeader("Origin");
        
        // Only set CORS headers if origin is present and matches allowed origins
        // Don't set "*" when credentials are enabled - let Spring Security handle CORS
        if (origin != null && !origin.isEmpty() && !origin.equals("null")) {
            // Check if origin is allowed
            boolean isAllowed = false;
            if (allowedOrigins != null && !allowedOrigins.equals("*")) {
                String[] allowed = allowedOrigins.split(",");
                for (String allowedOrigin : allowed) {
                    if (origin.equals(allowedOrigin.trim())) {
                        isAllowed = true;
                        break;
                    }
                }
            } else if (allowedOrigins != null && allowedOrigins.equals("*")) {
                // If explicitly set to "*", allow all (but can't use credentials)
                isAllowed = true;
            }
            
            if (isAllowed) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.setHeader("Access-Control-Allow-Credentials", "true");
                response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
                response.setHeader("Access-Control-Allow-Headers", "*");
                response.setHeader("Access-Control-Expose-Headers", "Authorization, Content-Type, X-Requested-With");
                response.setHeader("Access-Control-Max-Age", "3600");
            }
        }
        // Don't set CORS headers for requests without origin when credentials are enabled
        
        // Handle preflight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        
        chain.doFilter(req, res);
    }
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No initialization needed
    }
    
    @Override
    public void destroy() {
        // No cleanup needed
    }
}
