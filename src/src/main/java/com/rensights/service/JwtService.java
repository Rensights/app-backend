package com.rensights.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    
    @Value("${jwt.secret:dev-secret-key-change-in-production-minimum-32-characters-long}")
    private String secret;
    
    @Value("${jwt.expiration:86400000}")
    private Long expiration;
    
    // Cache the signing key to avoid recreation on every call (memory optimization)
    private volatile SecretKey cachedSigningKey;
    private volatile String cachedSecret;
    
    private SecretKey getSigningKey() {
        // Double-check locking pattern for thread-safe lazy initialization
        if (cachedSigningKey == null || !secret.equals(cachedSecret)) {
            synchronized (this) {
                if (cachedSigningKey == null || !secret.equals(cachedSecret)) {
                    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
                    cachedSigningKey = Keys.hmacShaKeyFor(keyBytes);
                    cachedSecret = secret;
                }
            }
        }
        return cachedSigningKey;
    }
    
    public String generateToken(UUID userId, String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
    
    public UUID getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        return UUID.fromString(claims.getSubject());
    }
    
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

