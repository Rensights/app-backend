package com.rensights.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class CorsFilterConfig {
    // CorsFilter disabled - Spring Security's CORS configuration handles CORS
    // Having both causes conflicts with allowedOrigins "*" when allowCredentials is true
}

