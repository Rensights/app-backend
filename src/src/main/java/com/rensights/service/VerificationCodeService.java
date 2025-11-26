package com.rensights.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VerificationCodeService {
    
    private static final int CODE_LENGTH = 6;
    private static final int CODE_EXPIRY_MINUTES = 10;
    private static final SecureRandom random = new SecureRandom();
    
    // In-memory storage: email -> (code, expiryTime)
    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();
    
    public String generateCode(String email) {
        // Generate 6-digit code
        int code = 100000 + random.nextInt(900000);
        String codeString = String.valueOf(code);
        
        // Store with expiry time
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES);
        codes.put(email, new CodeEntry(codeString, expiryTime));
        
        return codeString;
    }
    
    // Optimized: Use Optional for cleaner null handling and early returns
    public boolean verifyCode(String email, String code) {
        return java.util.Optional.ofNullable(codes.get(email))
                .filter(entry -> !LocalDateTime.now().isAfter(entry.expiryTime))
                .filter(entry -> entry.code.equals(code))
                .map(entry -> {
                    codes.remove(email);
                    return true;
                })
                .orElseGet(() -> {
                    // Remove expired entries
                    codes.remove(email);
                    return false;
                });
    }
    
    public void removeCode(String email) {
        codes.remove(email);
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

