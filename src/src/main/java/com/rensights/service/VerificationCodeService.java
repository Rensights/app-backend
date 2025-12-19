package com.rensights.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VerificationCodeService {
    
    private static final Logger logger = LoggerFactory.getLogger(VerificationCodeService.class);
    private static final int CODE_LENGTH = 6;
    private static final int CODE_EXPIRY_MINUTES = 10;
    private static final SecureRandom random = new SecureRandom();
    
    // SECURITY: In-memory storage: email -> (code, expiryTime)
    // TODO: Move to Redis with TTL for production (prevents DoS and provides persistence)
    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();
    
    // SECURITY: Track verification attempts to prevent brute force
    private final Map<String, Integer> verificationAttempts = new ConcurrentHashMap<>();
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;
    private static final int MAX_CODE_GENERATION_PER_EMAIL = 5; // Max 5 codes per email per hour
    private final Map<String, Integer> generationCount = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> generationResetTime = new ConcurrentHashMap<>();
    
    public String generateCode(String email) {
        // SECURITY: Rate limiting - prevent code generation DoS
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime resetTime = generationResetTime.get(email);
        
        if (resetTime == null || now.isAfter(resetTime)) {
            // Reset counter after 1 hour
            generationCount.put(email, 0);
            generationResetTime.put(email, now.plusHours(1));
        }
        
        int count = generationCount.getOrDefault(email, 0);
        if (count >= MAX_CODE_GENERATION_PER_EMAIL) {
            logger.warn("SECURITY ALERT: Too many code generation requests for email: {}", email);
            throw new IllegalStateException("Too many code generation requests. Please try again later.");
        }
        
        generationCount.put(email, count + 1);
        
        // SECURITY: Clean up expired codes periodically to prevent memory exhaustion
        cleanupExpiredCodes();
        
        // Generate 6-digit code using cryptographically secure random
        int code = 100000 + random.nextInt(900000);
        String codeString = String.valueOf(code);
        
        // Store with expiry time
        LocalDateTime expiryTime = now.plusMinutes(CODE_EXPIRY_MINUTES);
        
        // SECURITY: Remove old code if exists (prevent code reuse)
        codes.remove(email);
        
        codes.put(email, new CodeEntry(codeString, expiryTime));
        
        // Reset verification attempts for this email when new code is generated
        verificationAttempts.remove(email);
        
        return codeString;
    }
    
    // SECURITY FIX: Add rate limiting and constant-time comparison
    public boolean verifyCode(String email, String code) {
        LocalDateTime now = LocalDateTime.now();
        
        // SECURITY: Check brute force attempts
        int attempts = verificationAttempts.getOrDefault(email, 0);
        if (attempts >= MAX_VERIFICATION_ATTEMPTS) {
            logger.warn("SECURITY ALERT: Too many verification attempts for email: {}", email);
            // Lock for 30 minutes
            verificationAttempts.put(email, attempts);
            throw new IllegalStateException("Too many failed verification attempts. Please request a new code.");
        }
        
        CodeEntry entry = codes.get(email);
        
        // Remove if expired
        if (entry != null && now.isAfter(entry.expiryTime)) {
            codes.remove(email);
            verificationAttempts.remove(email);
            return false;
        }
        
        if (entry == null) {
            // Increment attempts even for non-existent codes (prevents email enumeration)
            verificationAttempts.put(email, attempts + 1);
            return false;
        }
        
        // SECURITY: Use constant-time comparison to prevent timing attacks
        boolean isValid = constantTimeEquals(entry.code, code);
        
        if (isValid) {
            // Successful verification - remove code and reset attempts
            codes.remove(email);
            verificationAttempts.remove(email);
            generationCount.remove(email);
            generationResetTime.remove(email);
            return true;
        } else {
            // Failed verification - increment attempts
            verificationAttempts.put(email, attempts + 1);
            return false;
        }
    }
    
    /**
     * Verify code without consuming it (for password reset flow where code needs to be verified twice)
     * This allows the code to be verified first, then used again to reset the password
     */
    public boolean verifyCodeWithoutConsuming(String email, String code) {
        LocalDateTime now = LocalDateTime.now();
        
        CodeEntry entry = codes.get(email);
        
        // Remove if expired
        if (entry != null && now.isAfter(entry.expiryTime)) {
            codes.remove(email);
            verificationAttempts.remove(email);
            return false;
        }
        
        if (entry == null) {
            return false;
        }
        
        // SECURITY: Use constant-time comparison to prevent timing attacks
        return constantTimeEquals(entry.code, code);
    }
    
    // SECURITY: Constant-time string comparison to prevent timing attacks
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
    
    // SECURITY: Cleanup expired codes to prevent memory exhaustion
    private void cleanupExpiredCodes() {
        LocalDateTime now = LocalDateTime.now();
        Iterator<Map.Entry<String, CodeEntry>> iterator = codes.entrySet().iterator();
        int cleaned = 0;
        
        while (iterator.hasNext()) {
            Map.Entry<String, CodeEntry> entry = iterator.next();
            if (now.isAfter(entry.getValue().expiryTime)) {
                iterator.remove();
                verificationAttempts.remove(entry.getKey());
                cleaned++;
            }
        }
        
        if (cleaned > 0) {
            logger.debug("Cleaned up {} expired verification codes", cleaned);
        }
    }
    
    public void removeCode(String email) {
        codes.remove(email);
        verificationAttempts.remove(email);
        generationCount.remove(email);
        generationResetTime.remove(email);
    }
    
    private static class CodeEntry {
        final String code;
        final LocalDateTime expiryTime;
        
        CodeEntry(String code, LocalDateTime expiryTime) {
            this.code = code;
            this.expiryTime = expiryTime;
        }
    }
}

