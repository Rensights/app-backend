package com.rensights.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class AdminJwtService {

    @Value("${admin.jwt.secret:${jwt.secret}}")
    private String secret;

    private volatile SecretKey cachedSigningKey;
    private volatile String cachedSecret;

    private SecretKey getSigningKey() {
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

    public UUID getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();

        return UUID.fromString(claims.getSubject());
    }
}
