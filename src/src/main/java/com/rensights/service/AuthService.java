package com.rensights.service;

import com.rensights.dto.AuthResponse;
import com.rensights.dto.LoginRequest;
import com.rensights.dto.RegisterRequest;
import com.rensights.model.User;
import com.rensights.service.EmailAlreadyRegisteredException;
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
    private GoogleTokenVerifierService googleTokenVerifierService;

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
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            // Don't throw - just return null to indicate verification required (which will send code)
            // This prevents revealing that email already exists
            logger.warn("Registration attempted for existing email: {} - treating as verification request", request.getEmail());
            userRepository.findByEmailIgnoreCase(request.getEmail()).ifPresent(existing -> {
                if (Boolean.TRUE.equals(existing.getEmailVerified())) {
                    throw new EmailAlreadyRegisteredException("Email already registered");
                }
                // If user isn't verified yet, update missing profile fields from this registration attempt
                if (!Boolean.TRUE.equals(existing.getEmailVerified())) {
                    boolean updated = false;
                    if (isNonBlank(request.getFirstName()) && isBlank(existing.getFirstName())) {
                        existing.setFirstName(request.getFirstName().trim());
                        updated = true;
                    }
                    if (isNonBlank(request.getLastName()) && isBlank(existing.getLastName())) {
                        existing.setLastName(request.getLastName().trim());
                        updated = true;
                    }
                    if (isNonBlank(request.getPhone()) && isBlank(existing.getPhone())) {
                        existing.setPhone(request.getPhone().trim());
                        updated = true;
                    }
                    if (isNonBlank(request.getBudget()) && isBlank(existing.getBudget())) {
                        existing.setBudget(request.getBudget().trim());
                        updated = true;
                    }
                    if (isNonBlank(request.getPortfolio()) && isBlank(existing.getPortfolio())) {
                        existing.setPortfolio(request.getPortfolio().trim());
                        updated = true;
                    }
                    if (request.getGoals() != null && !request.getGoals().isEmpty() && isBlank(existing.getGoalsJson())) {
                        existing.setGoalsJson(writeJson(request.getGoals()));
                        updated = true;
                    }
                    if (isNonBlank(request.getPlan()) && isBlank(existing.getRegistrationPlan())) {
                        existing.setRegistrationPlan(request.getPlan().trim());
                        updated = true;
                    }
                    if (updated) {
                        userRepository.save(existing);
                    }
                }
            });
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
            com.stripe.model.Customer stripeCustomer = stripeService.findOrCreateCustomerByEmail(request.getEmail(), fullName);
            stripeCustomerId = stripeCustomer.getId();
            logger.info("Linked Stripe customer {} for new user: {}", stripeCustomerId, request.getEmail());
        } catch (Exception e) {
            // Log error but don't fail registration if Stripe fails
            logger.error("Failed to link/create Stripe customer for user {}: {}", request.getEmail(), e.getMessage(), e);
            // Continue with registration even if Stripe customer lookup/creation fails
        }
        
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(normalize(request.getFirstName()))
                .lastName(normalize(request.getLastName()))
                .phone(normalize(request.getPhone()))
                .budget(normalize(request.getBudget()))
                .portfolio(normalize(request.getPortfolio()))
                .goalsJson(writeJson(request.getGoals()))
                .registrationPlan(normalize(request.getPlan()))
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
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (!verificationCodeService.verifyCode(user.getEmail(), code)) {
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
        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));
        
        String rawPassword = request.getPassword() == null ? "" : request.getPassword().strip();
        // Optimized: Validate password early to fail fast (before any other operations)
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        return completeLoginAfterIdentityVerified(user, deviceFingerprint, httpRequest);
    }

    /**
     * Google Sign-In: verify ID token, create user if needed, then same device/session flow as password login.
     */
    @Transactional
    public LoginResponse loginWithGoogle(String credential, String deviceFingerprint, jakarta.servlet.http.HttpServletRequest httpRequest) {
        GoogleTokenVerifierService.GoogleUserInfo info;
        try {
            info = googleTokenVerifierService.verify(credential);
        } catch (IllegalStateException e) {
            throw new RuntimeException("Google Sign-In is not configured");
        } catch (Exception e) {
            logger.warn("Google token verification failed: {}", e.getMessage());
            throw new RuntimeException("Invalid Google sign-in");
        }

        User user = userRepository.findByEmailIgnoreCase(info.email())
                .map(existing -> mergeGoogleProfileIfNeeded(existing, info))
                .orElseGet(() -> createUserFromGoogle(info));

        return completeLoginAfterIdentityVerified(user, deviceFingerprint, httpRequest);
    }

    private User mergeGoogleProfileIfNeeded(User user, GoogleTokenVerifierService.GoogleUserInfo info) {
        boolean changed = false;
        if (isBlank(user.getFirstName()) && isNonBlank(info.givenName())) {
            user.setFirstName(normalize(info.givenName()));
            changed = true;
        }
        if (isBlank(user.getLastName()) && isNonBlank(info.familyName())) {
            user.setLastName(normalize(info.familyName()));
            changed = true;
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            user.setEmailVerified(true);
            changed = true;
        }
        if (changed) {
            return userRepository.save(user);
        }
        return user;
    }

    private User createUserFromGoogle(GoogleTokenVerifierService.GoogleUserInfo info) {
        String customerId = "CUST-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String randomPassword = java.util.UUID.randomUUID() + java.util.UUID.randomUUID().toString();

        String stripeCustomerId = null;
        try {
            String fullName = (isNonBlank(info.givenName()) ? info.givenName() : "")
                    + (isNonBlank(info.familyName()) ? " " + info.familyName() : "");
            fullName = fullName.trim();
            if (fullName.isEmpty()) {
                fullName = info.email();
            }
            com.stripe.model.Customer stripeCustomer = stripeService.findOrCreateCustomerByEmail(info.email(), fullName);
            stripeCustomerId = stripeCustomer.getId();
        } catch (Exception e) {
            logger.error("Failed to link/create Stripe customer for Google user {}: {}", info.email(), e.getMessage(), e);
        }

        User user = User.builder()
                .email(info.email())
                .passwordHash(passwordEncoder.encode(randomPassword))
                .firstName(normalize(info.givenName()))
                .lastName(normalize(info.familyName()))
                .customerId(customerId)
                .stripeCustomerId(stripeCustomerId)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .emailVerified(true)
                .build();

        return userRepository.save(user);
    }

    private LoginResponse completeLoginAfterIdentityVerified(User user, String deviceFingerprint, jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (!user.getIsActive()) {
            throw new RuntimeException("Account is deactivated");
        }

        boolean isKnownDevice = java.util.Optional.ofNullable(deviceFingerprint)
                .filter(fp -> !fp.isEmpty())
                .map(fp -> deviceService.isDeviceKnown(user.getId(), fp))
                .orElse(false);

        User finalUser = user;
        if (!user.getEmailVerified()) {
            if (!emailVerificationRequired) {
                logger.info("Email verification disabled - auto-verifying user: {}", user.getEmail());
                user.setEmailVerified(true);
                finalUser = userRepository.save(user);
            } else {
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
            String code = verificationCodeService.generateCode(finalUser.getEmail());
            emailService.sendVerificationCode(finalUser.getEmail(), code);

            return LoginResponse.builder()
                    .requiresVerification(true)
                    .email(finalUser.getEmail())
                    .deviceFingerprint(deviceFingerprint)
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

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isNonBlank(String value) {
        return !isBlank(value);
    }
    
    /**
     * Verify code for login (new device)
     */
    @Transactional
    public AuthResponse verifyDeviceAndLogin(String email, String code, String deviceFingerprint, jakarta.servlet.http.HttpServletRequest httpRequest) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (!verificationCodeService.verifyCode(user.getEmail(), code)) {
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
        if (!userRepository.existsByEmailIgnoreCase(email)) {
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
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        
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
        
        // Generate reset code using verification code service (key must match row email for verify/reset)
        String code = verificationCodeService.generateCode("reset:" + user.getEmail());
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
        return userRepository.findByEmailIgnoreCase(email)
                .map(u -> verificationCodeService.verifyCodeWithoutConsuming("reset:" + u.getEmail(), code))
                .orElse(false);
    }
    
    /**
     * Reset password with code
     */
    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (!user.getIsActive()) {
            throw new RuntimeException("Account is deactivated");
        }
        
        // Verify the reset code
        if (!verificationCodeService.verifyCode("reset:" + user.getEmail(), code)) {
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
