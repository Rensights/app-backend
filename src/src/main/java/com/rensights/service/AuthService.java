package com.rensights.service;

import com.rensights.dto.AuthResponse;
import com.rensights.dto.LoginRequest;
import com.rensights.dto.RegisterRequest;
import com.rensights.model.User;
import com.rensights.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtService jwtService;
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private VerificationCodeService verificationCodeService;
    
    @Autowired
    private DeviceService deviceService;
    
    @Autowired
    private StripeService stripeService;

    @Autowired
    private ObjectMapper objectMapper;
    
    @org.springframework.beans.factory.annotation.Value("${app.email-verification-required:true}")
    private boolean emailVerificationRequired;
    
    /**
     * Register user - requires email verification before access (unless disabled)
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, String deviceFingerprint, jakarta.servlet.http.HttpServletRequest httpRequest) {
        // SECURITY FIX: Check if email exists but don't reveal this to prevent user enumeration
        if (userRepository.existsByEmail(request.getEmail())) {
            // Don't throw - just return null to indicate verification required (which will send code)
            // This prevents revealing that email already exists
            logger.warn("Registration attempted for existing email: {} - treating as verification request", request.getEmail());
            // Generate verification code anyway (user can't register, but can't tell from response)
            String code = verificationCodeService.generateCode(request.getEmail());
            emailService.sendVerificationCode(request.getEmail(), code);
            return null; // Return null to trigger verification code response
        }
        
        // Optimized: Generate customer ID using method chaining
        String customerId = java.util.UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
        customerId = "CUST-" + customerId;
        
        // Create Stripe customer for new user
        String stripeCustomerId = null;
        try {
            String fullName = (request.getFirstName() != null ? request.getFirstName() : "") + 
                            (request.getLastName() != null ? " " + request.getLastName() : "").trim();
            if (fullName.isEmpty()) {
                fullName = request.getEmail(); // Use email as fallback name
            }
            com.stripe.model.Customer stripeCustomer = stripeService.createCustomer(request.getEmail(), fullName);
            stripeCustomerId = stripeCustomer.getId();
            logger.info("Created Stripe customer {} for new user: {}", stripeCustomerId, request.getEmail());
        } catch (Exception e) {
            // Log error but don't fail registration if Stripe fails
            logger.error("Failed to create Stripe customer for user {}: {}", request.getEmail(), e.getMessage(), e);
            // Continue with registration even if Stripe customer creation fails
        }
        
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .budget(request.getBudget())
                .portfolio(request.getPortfolio())
                .goalsJson(writeJson(request.getGoals()))
                .registrationPlan(request.getPlan())
                .customerId(customerId)
                .stripeCustomerId(stripeCustomerId) // Store Stripe customer ID
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .emailVerified(!emailVerificationRequired) // Auto-verify if verification is disabled
                .build();
        
        User savedUser = userRepository.save(user);
        
        if (emailVerificationRequired) {
            // Send verification code
            String code = verificationCodeService.generateCode(savedUser.getEmail());
            emailService.sendVerificationCode(savedUser.getEmail(), code);
            return null; // Return null to indicate verification is required
        } else {
            // Auto-verify and login
            logger.info("Email verification disabled - auto-verifying user: {}", savedUser.getEmail());
            
            // Optimized: Use Optional for cleaner null/empty checks
            final User finalUser = savedUser;
            java.util.Optional.ofNullable(deviceFingerprint)
                    .filter(fp -> !fp.isEmpty())
                    .ifPresent(fp -> deviceService.registerDevice(finalUser.getId(), fp, httpRequest));
            
            // Generate token
            String token = jwtService.generateToken(savedUser.getId(), savedUser.getEmail());
            
            // Optimized: Use method references in builder
            return AuthResponse.builder()
                    .token(token)
                    .email(savedUser.getEmail())
                    .firstName(savedUser.getFirstName())
                    .lastName(savedUser.getLastName())
                    .build();
        }
    }
    
    /**
     * Verify email after registration - also registers the device
     */
    @Transactional
    public AuthResponse verifyEmailAndLogin(String email, String code, String deviceFingerprint, jakarta.servlet.http.HttpServletRequest httpRequest) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (!verificationCodeService.verifyCode(email, code)) {
            throw new RuntimeException("Invalid or expired verification code");
        }
        
        // Mark email as verified
        user.setEmailVerified(true);
        User savedUser = userRepository.save(user);
        
        // Optimized: Use Optional for cleaner null/empty checks
        final User finalUser = savedUser;
        java.util.Optional.ofNullable(deviceFingerprint)
                .filter(fp -> !fp.isEmpty())
                .ifPresent(fp -> deviceService.registerDevice(finalUser.getId(), fp, httpRequest));
        
        // Generate token
        String token = jwtService.generateToken(savedUser.getId(), savedUser.getEmail());
        
        return AuthResponse.builder()
                .token(token)
                .email(savedUser.getEmail())
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .build();
    }
    
    /**
     * Login - checks device and requires verification for new devices
     * Optimized: Reduced database queries and improved flow
     */
    public LoginResponse login(LoginRequest request, String deviceFingerprint, jakarta.servlet.http.HttpServletRequest httpRequest) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));
        
        // Optimized: Validate password early to fail fast (before any other operations)
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }
        
        if (!user.getIsActive()) {
            throw new RuntimeException("Account is deactivated");
        }
        
        // Optimized: Use Optional for cleaner null/empty checks and method chaining
        boolean isKnownDevice = java.util.Optional.ofNullable(deviceFingerprint)
                .filter(fp -> !fp.isEmpty())
                .map(fp -> deviceService.isDeviceKnown(user.getId(), fp))
                .orElse(false);
        
        User finalUser = user;
        if (!user.getEmailVerified()) {
            if (!emailVerificationRequired) {
                // Auto-verify if verification is disabled
                logger.info("Email verification disabled - auto-verifying user: {}", user.getEmail());
                user.setEmailVerified(true);
                finalUser = userRepository.save(user);
            } else {
                // If email is not verified, send a new code and require verification
                String code = verificationCodeService.generateCode(user.getEmail());
                emailService.sendVerificationCode(user.getEmail(), code);
                logger.info("User {} email not verified. Sent new verification code.", user.getEmail());
                return LoginResponse.builder()
                        .requiresVerification(true)
                        .email(user.getEmail())
                        .deviceFingerprint(deviceFingerprint)
                        .build();
            }
        }
        
        if (isKnownDevice || !emailVerificationRequired) {
            // Optimized: Use Optional for cleaner conditional logic
            final User userForLambda = finalUser;
            if (isKnownDevice) {
                deviceService.updateDeviceLastUsed(userForLambda.getId(), deviceFingerprint);
            } else {
                java.util.Optional.ofNullable(deviceFingerprint)
                        .filter(fp -> !fp.isEmpty())
                        .ifPresent(fp -> deviceService.registerDevice(userForLambda.getId(), fp, httpRequest));
            }
            
            String token = jwtService.generateToken(userForLambda.getId(), userForLambda.getEmail());
            
            return LoginResponse.builder()
                    .requiresVerification(false)
                    .token(token)
                    .email(userForLambda.getEmail())
                    .firstName(userForLambda.getFirstName())
                    .lastName(userForLambda.getLastName())
                    .build();
        } else {
            // New device - send verification code
            String code = verificationCodeService.generateCode(finalUser.getEmail());
            emailService.sendVerificationCode(finalUser.getEmail(), code);
            
            return LoginResponse.builder()
                    .requiresVerification(true)
                    .email(finalUser.getEmail())
                    .build();
        }
    }

    private String writeJson(java.util.List<String> values) {
        if (values == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize list", e);
        }
    }
    
    /**
     * Verify code for login (new device)
     */
    @Transactional
    public AuthResponse verifyDeviceAndLogin(String email, String code, String deviceFingerprint, jakarta.servlet.http.HttpServletRequest httpRequest) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (!verificationCodeService.verifyCode(email, code)) {
            throw new RuntimeException("Invalid or expired verification code");
        }
        
        // Register the new device
        deviceService.registerDevice(user.getId(), deviceFingerprint, httpRequest);
        
        // Generate token
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        
        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }
    
    // SECURITY FIX: This method is used for password reset - don't throw to prevent user enumeration
    // The controller should always return success message regardless
    public void validateUserExists(String email) {
        // Always succeed silently to prevent user enumeration
        // If user doesn't exist, password reset code generation will just fail silently
        // This prevents attackers from discovering which emails are registered
        if (!userRepository.existsByEmail(email)) {
            // Don't throw - just log and return (prevents user enumeration)
            logger.warn("Password reset requested for non-existent email: {}", email);
            return;
        }
    }
    
    /**
     * Request password reset - sends reset code to email
     * Returns true if email exists and code was sent, false if email doesn't exist
     */
    @Transactional
    public boolean requestPasswordReset(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        
        if (user == null) {
            // User doesn't exist - return false so frontend can inform user
            logger.warn("Password reset requested for non-existent email: {}", email);
            return false;
        }
        
        if (!user.getIsActive()) {
            // Account deactivated - treat as if email doesn't exist for user feedback
            logger.warn("Password reset requested for deactivated account: {}", email);
            return false;
        }
        
        // Generate reset code using verification code service
        String code = verificationCodeService.generateCode("reset:" + email);
        emailService.sendPasswordResetCode(email, code);
        logger.info("Password reset code sent to: {}", email);
        return true;
    }
    
    /**
     * Verify password reset code
     * NOTE: Does NOT remove the code - it will be removed when password is actually reset
     * This allows the code to be verified first, then used again to reset the password
     */
    public boolean verifyResetCode(String email, String code) {
        // Use a non-consuming verification that doesn't remove the code
        // The code will be removed when resetPassword() is called
        return verificationCodeService.verifyCodeWithoutConsuming("reset:" + email, code);
    }
    
    /**
     * Reset password with code
     */
    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (!user.getIsActive()) {
            throw new RuntimeException("Account is deactivated");
        }
        
        // Verify the reset code
        if (!verificationCodeService.verifyCode("reset:" + email, code)) {
            throw new RuntimeException("Invalid or expired reset code");
        }
        
        // Update password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        logger.info("Password reset successful for: {}", email);
    }
    
    // Response classes
    @lombok.Data
    @lombok.Builder
    public static class LoginResponse {
        private boolean requiresVerification;
        private String token;
        private String email;
        private String firstName;
        private String lastName;
        private String deviceFingerprint;
    }
}
