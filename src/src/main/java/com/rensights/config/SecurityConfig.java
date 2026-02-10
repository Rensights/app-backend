package com.rensights.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Autowired
    private CorsConfig corsConfig;
    
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // SECURITY NOTE: CSRF is disabled because we use stateless JWT authentication
            // Stateless REST APIs with JWT tokens are not vulnerable to traditional CSRF attacks
            // because:
            // 1. JWT tokens are stored in localStorage/HttpOnly cookies, not browser cookies
            // 2. Each request requires explicit Authorization header with Bearer token
            // 3. Same-origin policy prevents malicious sites from reading tokens
            // For additional security, consider validating Origin header for state-changing requests
            .csrf(csrf -> csrf.disable())
            // CORS must be configured before authentication
            .cors(cors -> cors.configurationSource(corsConfig.corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // SECURITY FIX: Add security headers
            .headers(headers -> headers
                .contentTypeOptions(contentTypeOptions -> {})
                .frameOptions(frameOptions -> frameOptions.deny())
                .xssProtection(xss -> xss
                    .headerValue(org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)
                )
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000) // 1 year
                    .includeSubDomains(true)
                    .preload(true)
                )
                .referrerPolicy(referrer -> referrer
                    .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                )
            )
            // SECURITY: Add filters in correct order
            // First add JWT filter before Spring Security's authentication filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // OPTIONS requests must be permitted first for CORS preflight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/landing-page/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/translations/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/languages").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/early-access").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/early-access").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/analysis-requests").permitAll() // Allow submission without auth
                // SECURITY: Webhook endpoint must be public for Stripe to call it, but signature verification is performed
                // Allow both with and without trailing slash
                .requestMatchers(HttpMethod.POST, "/api/webhooks/stripe").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/webhooks/stripe/").permitAll()
                // SECURITY FIX: Require authentication for file access to prevent unauthorized access
                .requestMatchers("/api/analysis-requests/files/**").authenticated()
                .requestMatchers("/api/analysis-requests/my-requests").authenticated() // User's own requests require auth
                .requestMatchers("/api/subscriptions/**").authenticated()
                .requestMatchers("/users/**").authenticated()
                .requestMatchers("/actuator/health").permitAll() // Only health endpoint public
                .requestMatchers("/actuator/**").authenticated() // Other actuator endpoints require auth
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            );
        
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
