package com.rensights.controller;

import com.rensights.dto.AuthResponse;
import com.rensights.dto.ForgotPasswordRequest;
import com.rensights.dto.LoginRequest;
import com.rensights.dto.RegisterRequest;
import com.rensights.dto.ResetPasswordRequest;
import com.rensights.dto.SendVerificationCodeRequest;
import com.rensights.dto.VerifyCodeRequest;
import com.rensights.dto.VerifyDeviceRequest;
import com.rensights.dto.VerifyEmailRequest;
import com.rensights.dto.VerifyResetCodeRequest;
import com.rensights.service.AuthService;
import com.rensights.service.DeviceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    @Autowired
    private AuthService authService;
    
    @Autowired
    private DeviceService deviceService;
    
    @Autowired
    private com.rensights.service.VerificationCodeService verificationCodeService;
    
    @Autowired
    private com.rensights.service.EmailService emailService;
    
    /**
     * Register - sends verification code if required, or returns token if verification disabled
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        logger.info("=== Register called for: {}", request.getEmail());
        try {
            // Generate device fingerprint if not provided
            String deviceFingerprint = request.getDeviceFingerprint();
            if (deviceFingerprint == null || deviceFingerprint.isEmpty()) {
                deviceFingerprint = deviceService.generateDeviceFingerprint(httpRequest);
            }
            
            AuthResponse authResponse = authService.register(request, deviceFingerprint, httpRequest);
            
            if (authResponse != null) {
                // Email verification disabled - return token directly
                logger.info("✅ Registration successful (verification disabled), token generated for: {}", request.getEmail());
                return ResponseEntity.ok(authResponse);
            } else {
                // Email verification required - send code
                logger.info("✅ Registration successful, verification code sent to: {}", request.getEmail());
                return ResponseEntity.ok(new MessageResponse("Registration successful. Please check your email for verification code."));
            }
        } catch (RuntimeException e) {
            logger.error("❌ Registration failed: {}", e.getMessage());
            // SECURITY FIX: Use generic error message to prevent information disclosure
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Registration failed. Please check your input and try again."));
        }
    }
    
    /**
     * Verify email after registration - also registers device
     */
    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailRequest request, HttpServletRequest httpRequest) {
        logger.info("=== Verify email called for: {}", request.getEmail());
        
        // Generate device fingerprint if not provided
        String deviceFingerprint = request.getDeviceFingerprint();
        if (deviceFingerprint == null || deviceFingerprint.isEmpty()) {
            deviceFingerprint = deviceService.generateDeviceFingerprint(httpRequest);
            logger.info("Generated device fingerprint for registration: {}", deviceFingerprint);
        }
        
        try {
            AuthResponse response = authService.verifyEmailAndLogin(
                    request.getEmail(), 
                    request.getCode(), 
                    deviceFingerprint,
                    httpRequest
            );
            logger.info("✅ Email verified and device registered for: {}", request.getEmail());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            logger.error("❌ Email verification failed: {}", e.getMessage());
            // SECURITY FIX: Use generic error message
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Invalid or expired verification code. Please request a new code."));
        }
    }
    
    /**
     * Login - checks device, may require verification
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        logger.info("=== Login called for: {}", request.getEmail());
        
        // Generate device fingerprint if not provided
        String deviceFingerprint = request.getDeviceFingerprint();
        if (deviceFingerprint == null || deviceFingerprint.isEmpty()) {
            deviceFingerprint = deviceService.generateDeviceFingerprint(httpRequest);
            logger.info("Generated device fingerprint: {}", deviceFingerprint);
        }
        
        try {
            AuthService.LoginResponse response = authService.login(request, deviceFingerprint, httpRequest);
            
            if (response.isRequiresVerification()) {
                logger.info("⚠️ New device detected, verification required for: {}", request.getEmail());
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new LoginResponse(true, null, request.getEmail(), null, null, deviceFingerprint));
            } else {
                logger.info("✅ Login successful (known device) for: {}", request.getEmail());
                return ResponseEntity.ok(new LoginResponse(false, response.getToken(), 
                        response.getEmail(), response.getFirstName(), response.getLastName(), deviceFingerprint));
            }
        } catch (RuntimeException e) {
            logger.error("❌ Login failed: {}", e.getMessage());
            // SECURITY FIX: Use generic error message to prevent user enumeration
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid email or password"));
        }
    }
    
    /**
     * Verify device for login (new device)
     */
    @PostMapping("/verify-device")
    public ResponseEntity<?> verifyDevice(@Valid @RequestBody VerifyDeviceRequest request, HttpServletRequest httpRequest) {
        logger.info("=== Verify device called for: {}", request.getEmail());
        
        try {
            AuthResponse response = authService.verifyDeviceAndLogin(
                    request.getEmail(), 
                    request.getCode(), 
                    request.getDeviceFingerprint(),
                    httpRequest
            );
            logger.info("✅ Device verified successfully for: {}", request.getEmail());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            logger.error("❌ Device verification failed: {}", e.getMessage());
            // SECURITY FIX: Use generic error message
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Invalid or expired verification code. Please request a new code."));
        }
    }
    
    /**
     * Resend verification code (for registration or login)
     */
    @PostMapping("/resend-verification-code")
    public ResponseEntity<?> resendVerificationCode(@Valid @RequestBody SendVerificationCodeRequest request) {
        logger.info("=== Resend verification code called for: {}", request.getEmail());
        
        try {
            authService.validateUserExists(request.getEmail());
            
            // Generate and send new verification code
            String code = verificationCodeService.generateCode(request.getEmail());
            logger.info("New code generated, sending email...");
            emailService.sendVerificationCode(request.getEmail(), code);
            logger.info("✅ Verification code resent to: {}", request.getEmail());
            
            return ResponseEntity.ok(new MessageResponse("Verification code sent to your email"));
        } catch (RuntimeException e) {
            logger.error("❌ Error resending verification code: {}", e.getMessage());
            // SECURITY FIX: Don't reveal if user exists - always return success message
            return ResponseEntity.ok(new MessageResponse("If the email exists, a verification code has been sent."));
        } catch (Exception e) {
            logger.error("❌ Unexpected error resending verification code: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to resend verification code. Please try again later."));
        }
    }
    
    /**
     * Request password reset - sends reset code to email
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        logger.info("=== Forgot password called for: {}", request.getEmail());
        
        try {
            authService.requestPasswordReset(request.getEmail());
            logger.info("✅ Password reset code sent to: {}", request.getEmail());
            return ResponseEntity.ok(new MessageResponse("Password reset code sent to your email"));
        } catch (RuntimeException e) {
            logger.error("❌ Error requesting password reset: {}", e.getMessage());
            // SECURITY FIX: Don't reveal if user exists - always return success message to prevent user enumeration
            return ResponseEntity.ok(new MessageResponse("If the email exists, a password reset code has been sent."));
        } catch (Exception e) {
            logger.error("❌ Unexpected error requesting password reset: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to send password reset code. Please try again later."));
        }
    }
    
    /**
     * Verify password reset code
     */
    @PostMapping("/verify-reset-code")
    public ResponseEntity<?> verifyResetCode(@Valid @RequestBody VerifyResetCodeRequest request) {
        logger.info("=== Verify reset code called for: {}", request.getEmail());
        
        try {
            boolean isValid = authService.verifyResetCode(request.getEmail(), request.getCode());
            if (isValid) {
                logger.info("✅ Reset code verified for: {}", request.getEmail());
                return ResponseEntity.ok(new MessageResponse("Reset code verified successfully"));
            } else {
                logger.warn("❌ Invalid reset code for: {}", request.getEmail());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("Invalid or expired reset code"));
            }
        } catch (Exception e) {
            logger.error("❌ Unexpected error verifying reset code: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to verify reset code. Please try again later."));
        }
    }
    
    /**
     * Reset password with code
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        logger.info("=== Reset password called for: {}", request.getEmail());
        
        try {
            authService.resetPassword(request.getEmail(), request.getCode(), request.getNewPassword());
            logger.info("✅ Password reset successful for: {}", request.getEmail());
            return ResponseEntity.ok(new MessageResponse("Password reset successfully"));
        } catch (RuntimeException e) {
            logger.error("❌ Error resetting password: {}", e.getMessage());
            // SECURITY FIX: Use generic error message
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Invalid or expired reset code. Please request a new code."));
        } catch (Exception e) {
            logger.error("❌ Unexpected error resetting password: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to reset password. Please try again later."));
        }
    }
    
    private static class ErrorResponse {
        private String error;
        
        public ErrorResponse(String error) {
            this.error = error;
        }
        
        public String getError() {
            return error;
        }
    }
    
    private static class MessageResponse {
        private String message;
        
        public MessageResponse(String message) {
            this.message = message;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    private static class LoginResponse {
        private boolean requiresVerification;
        private String token;
        private String email;
        private String firstName;
        private String lastName;
        private String deviceFingerprint;
        
        public LoginResponse(boolean requiresVerification, String token, String email, 
                           String firstName, String lastName, String deviceFingerprint) {
            this.requiresVerification = requiresVerification;
            this.token = token;
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.deviceFingerprint = deviceFingerprint;
        }
        
        public boolean isRequiresVerification() { return requiresVerification; }
        public String getToken() { return token; }
        public String getEmail() { return email; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getDeviceFingerprint() { return deviceFingerprint; }
    }
}
