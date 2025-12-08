package com.rensights.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * SECURITY: Token revocation service for blacklisting JWT tokens
 * 
 * NOTE: This is an in-memory implementation. For production, use Redis with TTL.
 * 
 * Current limitations:
 * - Tokens are only revoked in-memory (lost on restart)
 * - Not suitable for multi-instance deployments
 * - No expiration cleanup
 * 
 * Production implementation should:
 * 1. Use Redis with token blacklist
 * 2. Set TTL equal to JWT expiration time
 * 3. Check blacklist in JwtAuthenticationFilter before validating token
 */
@Service
public class TokenRevocationService {
    
    private static final Logger logger = LoggerFactory.getLogger(TokenRevocationService.class);
    
    // In-memory blacklist: token hash -> expiration timestamp
    // In production, use Redis: SETEX token_hash expiration_seconds ""
    private final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();
    
    /**
     * Revoke a token by adding its hash to the blacklist
     * 
     * @param token The JWT token to revoke
     * @param expirationTimeMillis Expiration time in milliseconds (for cleanup)
     */
    public void revokeToken(String token, long expirationTimeMillis) {
        // Use hash to save memory (SHA-256 of token)
        String tokenHash = hashToken(token);
        revokedTokens.add(tokenHash);
        
        logger.info("Token revoked: hash={}", tokenHash);
        
        // TODO: In production, use Redis:
        // redisTemplate.opsForValue().set(
        //     "revoked:" + tokenHash, 
        //     "", 
        //     Duration.ofMillis(expirationTimeMillis - System.currentTimeMillis())
        // );
    }
    
    /**
     * Check if a token has been revoked
     * 
     * @param token The JWT token to check
     * @return true if token is revoked, false otherwise
     */
    public boolean isTokenRevoked(String token) {
        String tokenHash = hashToken(token);
        boolean revoked = revokedTokens.contains(tokenHash);
        
        if (revoked) {
            logger.warn("Revoked token access attempt detected: hash={}", tokenHash);
        }
        
        return revoked;
    }
    
    /**
     * Hash token for storage (SHA-256)
     */
    private String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            // Fallback to simple hash if SHA-256 not available (shouldn't happen)
            return String.valueOf(token.hashCode());
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * Cleanup expired tokens (call periodically in production)
     */
    public void cleanupExpiredTokens() {
        // In-memory implementation: tokens stay until restart
        // In production with Redis, TTL handles this automatically
        logger.debug("Token cleanup: {} tokens in blacklist", revokedTokens.size());
    }
}



