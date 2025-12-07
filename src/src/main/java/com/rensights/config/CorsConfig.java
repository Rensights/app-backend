package com.rensights.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Parse allowed origins
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
        
        // Build patterns list - include exact matches and wildcard patterns
        List<String> patterns = new ArrayList<>();
        for (String origin : origins) {
            patterns.add(origin); // Add exact match
            // Also add wildcard patterns for domain flexibility
            try {
                java.net.URL url = new java.net.URL(origin);
                String protocol = url.getProtocol();
                String host = url.getHost();
                int port = url.getPort();
                
                // Add pattern with wildcard port
                if (port != -1) {
                    patterns.add(protocol + "://" + host + ":*");
                }
                // Add pattern without port
                patterns.add(protocol + "://" + host);
            } catch (Exception e) {
                // If URL parsing fails, just use the origin as-is
            }
        }
        
        String[] originPatterns = patterns.toArray(new String[0]);
        
        System.out.println("CORS WebMvcConfigurer - Allowed Origin Patterns: " + Arrays.toString(originPatterns));
        
        registry.addMapping("/**")
            .allowedOriginPatterns(originPatterns)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("Authorization", "Content-Type", "X-Requested-With")
            .allowCredentials(true)
            .maxAge(3600);
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Parse allowed origins, trim whitespace
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
        
        // Ensure we have at least one origin (don't allow empty list which might default to "*")
        if (origins.isEmpty()) {
            throw new IllegalStateException("CORS allowed origins cannot be empty. Set cors.allowed-origins property.");
        }
        
        // Build patterns list - include exact matches and wildcard patterns for flexibility
        List<String> patterns = new ArrayList<>();
        for (String origin : origins) {
            patterns.add(origin); // Add exact match
            // Also add wildcard patterns for domain flexibility
            try {
                java.net.URL url = new java.net.URL(origin);
                String protocol = url.getProtocol();
                String host = url.getHost();
                int port = url.getPort();
                
                // Add pattern with wildcard port
                if (port != -1) {
                    patterns.add(protocol + "://" + host + ":*");
                }
                // Add pattern without port
                patterns.add(protocol + "://" + host);
            } catch (Exception e) {
                // If URL parsing fails, just use the origin as-is
                System.out.println("Warning: Could not parse CORS origin as URL: " + origin);
            }
        }
        
        // IMPORTANT: Only use allowedOriginPatterns, never set allowedOrigins
        // Setting allowedOrigins with "*" when allowCredentials is true causes the error
        configuration.setAllowedOriginPatterns(patterns);
        
        // Explicitly clear allowedOrigins to ensure it's not set to "*" by default
        configuration.setAllowedOrigins(null);
        
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // Cache preflight for 1 hour
        
        System.out.println("CORS Configuration - Allowed Origin Patterns: " + patterns);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

