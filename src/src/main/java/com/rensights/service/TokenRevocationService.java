package com.rensights.service;

import com.rensights.model.RevokedToken;
import com.rensights.repository.RevokedTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * SECURITY: Token revocation service for blacklisting JWT tokens.
 *
 * Tokens are stored by SHA-256 hash in the revoked_tokens table with an
 * expiry timestamp equal to the JWT's own expiry. The scheduled cleanup
 * (cleanupExpiredTokens) removes rows that are past expiry so the table
 * stays small.
 */
@Service
public class TokenRevocationService {

    private static final Logger logger = LoggerFactory.getLogger(TokenRevocationService.class);

    @Autowired
    private RevokedTokenRepository repository;

    /**
     * Revoke a token by persisting its hash to the database.
     *
     * @param token                JWT string to revoke
     * @param expirationTimeMillis epoch-millis of the JWT's own expiry
     */
    @Transactional
    public void revokeToken(String token, long expirationTimeMillis) {
        String tokenHash = hashToken(token);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(expirationTimeMillis),
                ZoneId.systemDefault());

        RevokedToken revokedToken = RevokedToken.builder()
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .build();

        repository.save(revokedToken);
        logger.info("Token revoked: hash={}", tokenHash);
    }

    /**
     * Check if a token has been revoked.
     *
     * @param token JWT string to check
     * @return true if the token is in the revocation table
     */
    public boolean isTokenRevoked(String token) {
        String tokenHash = hashToken(token);
        boolean revoked = repository.existsByTokenHash(tokenHash);

        if (revoked) {
            logger.warn("Revoked token access attempt detected: hash={}", tokenHash);
        }

        return revoked;
    }

    /**
     * Remove expired revocation records. Call this from a scheduled task to
     * keep the table compact.
     */
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = repository.deleteExpired(LocalDateTime.now());
        logger.debug("Cleaned up {} expired revoked tokens", deleted);
    }

    /**
     * Hash token for storage (SHA-256).
     */
    private String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            // Fallback — SHA-256 is always available in standard JVMs
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
}
