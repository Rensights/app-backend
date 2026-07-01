package com.rensights.service;

import com.rensights.model.VerificationCode;
import com.rensights.repository.VerificationCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class VerificationCodeService {

    private static final Logger logger = LoggerFactory.getLogger(VerificationCodeService.class);
    private static final int CODE_LENGTH = 6;
    private static final int CODE_EXPIRY_MINUTES = 5;
    private static final SecureRandom random = new SecureRandom();
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;
    private static final int MAX_CODE_GENERATION_PER_EMAIL = 5; // Max 5 codes per email per hour

    @Autowired
    private VerificationCodeRepository repository;

    @Transactional
    public String generateCode(String email) {
        LocalDateTime now = LocalDateTime.now();

        Optional<VerificationCode> existing = repository.findByEmail(email);

        if (existing.isPresent()) {
            VerificationCode vc = existing.get();
            // SECURITY: Rate limiting - prevent code generation DoS
            if (vc.getGenerationResetTime() != null && now.isBefore(vc.getGenerationResetTime())) {
                if (vc.getGenerationCount() >= MAX_CODE_GENERATION_PER_EMAIL) {
                    logger.warn("SECURITY ALERT: Too many code generation requests for email: {}", email);
                    throw new IllegalStateException("Too many code generation requests. Please try again later.");
                }
                vc.setGenerationCount(vc.getGenerationCount() + 1);
            } else {
                // Reset counter after 1 hour window expires
                vc.setGenerationCount(1);
                vc.setGenerationResetTime(now.plusHours(1));
            }

            // Generate new code, replacing the old one
            int codeInt = 100000 + random.nextInt(900000);
            String codeString = String.valueOf(codeInt);
            vc.setCode(codeString);
            vc.setExpiryTime(now.plusMinutes(CODE_EXPIRY_MINUTES));
            vc.setAttempts(0);

            // JPA dirty checking persists the update
            logger.debug("Regenerated verification code for {} with expiry at {}", email, vc.getExpiryTime());
            return codeString;
        }

        // No existing row — create a fresh one
        int codeInt = 100000 + random.nextInt(900000);
        String codeString = String.valueOf(codeInt);
        LocalDateTime expiryTime = now.plusMinutes(CODE_EXPIRY_MINUTES);

        VerificationCode vc = VerificationCode.builder()
                .email(email)
                .code(codeString)
                .expiryTime(expiryTime)
                .attempts(0)
                .generationCount(1)
                .generationResetTime(now.plusHours(1))
                .build();

        repository.save(vc);
        logger.debug("Generated verification code for {} with expiry at {}", email, expiryTime);
        return codeString;
    }

    // SECURITY FIX: Rate limiting and constant-time comparison
    @Transactional
    public boolean verifyCode(String email, String code) {
        LocalDateTime now = LocalDateTime.now();

        Optional<VerificationCode> optVc = repository.findByEmail(email);

        if (optVc.isEmpty()) {
            logger.debug("Verification failed for {}: no code found", email);
            return false;
        }

        VerificationCode vc = optVc.get();

        // SECURITY: Check brute force attempts
        if (vc.getAttempts() >= MAX_VERIFICATION_ATTEMPTS) {
            logger.warn("SECURITY ALERT: Too many verification attempts for email: {}", email);
            throw new IllegalStateException("Too many failed verification attempts. Please request a new code.");
        }

        // Check if code has expired
        if (now.isAfter(vc.getExpiryTime())) {
            repository.deleteByEmail(email);
            logger.debug("Verification failed for {}: code expired at {}, current time {}", email, vc.getExpiryTime(), now);
            return false;
        }

        // SECURITY: Use constant-time comparison to prevent timing attacks
        boolean isValid = constantTimeEquals(vc.getCode(), code);

        if (isValid) {
            // Successful verification - delete the row entirely
            repository.deleteByEmail(email);
            logger.debug("Verification successful for {}", email);
            return true;
        } else {
            // Failed verification - increment attempts (dirty-check persists)
            vc.setAttempts(vc.getAttempts() + 1);
            logger.debug("Verification failed for {}: code mismatch", email);
            return false;
        }
    }

    /**
     * Verify code without consuming it (for password reset flow where code needs
     * to be verified first, then used again to confirm the reset).
     */
    @Transactional(readOnly = true)
    public boolean verifyCodeWithoutConsuming(String email, String code) {
        LocalDateTime now = LocalDateTime.now();

        Optional<VerificationCode> optVc = repository.findByEmail(email);

        if (optVc.isEmpty()) {
            return false;
        }

        VerificationCode vc = optVc.get();

        // Check if code has expired
        if (now.isAfter(vc.getExpiryTime())) {
            return false;
        }

        // SECURITY: Use constant-time comparison to prevent timing attacks
        return constantTimeEquals(vc.getCode(), code);
    }

    @Transactional
    public void removeCode(String email) {
        repository.deleteByEmail(email);
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
}
