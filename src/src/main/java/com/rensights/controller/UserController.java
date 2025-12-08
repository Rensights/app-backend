package com.rensights.controller;

import com.rensights.dto.SubscriptionResponse;
import com.rensights.dto.UserResponse;
import com.rensights.model.Subscription;
import com.rensights.model.User;
import com.rensights.repository.SubscriptionRepository;
import com.rensights.repository.UserRepository;
import com.rensights.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
public class UserController {
    
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    
    @Autowired
    private JwtService jwtService;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(new ErrorResponse("Unauthorized"));
        }
        
        try {
            String userIdStr = authentication.getName();
            UUID userId = UUID.fromString(userIdStr);
            
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            logger.info("🔍 Retrieved user: {}, customerId: {}, createdAt: {}", user.getEmail(), user.getCustomerId(), user.getCreatedAt());
            
            // Optimized: Use AtomicBoolean for mutable state in lambda
            java.util.concurrent.atomic.AtomicBoolean needsSave = new java.util.concurrent.atomic.AtomicBoolean(false);
            java.util.concurrent.atomic.AtomicReference<LocalDateTime> nowRef = new java.util.concurrent.atomic.AtomicReference<>();
            
            // Optimized: Use Optional and method references for cleaner code
            String customerIdValue = java.util.Optional.ofNullable(user.getCustomerId())
                    .filter(id -> !id.trim().isEmpty())
                    .orElseGet(() -> {
                        String generated = "CUST-" + java.util.UUID.randomUUID()
                                .toString()
                                .substring(0, 8)
                                .toUpperCase();
                        logger.info("🔧 Generating customer ID: {} for user: {}", generated, user.getEmail());
                        user.setCustomerId(generated);
                        needsSave.set(true);
                        return generated;
                    });
            
            if (!needsSave.get()) {
                logger.info("✅ Using existing customer ID: {} for user: {}", customerIdValue, user.getEmail());
            }
            
            // Optimized: Use Optional for cleaner null handling
            String createdAtValue = java.util.Optional.ofNullable(user.getCreatedAt())
                    .map(DATE_FORMATTER::format)
                    .orElseGet(() -> {
                        logger.info("🔧 Setting createdAt for user: {}", user.getEmail());
                        LocalDateTime now = LocalDateTime.now();
                        nowRef.set(now);
                        user.setCreatedAt(now);
                        needsSave.set(true);
                        return now.format(DATE_FORMATTER);
                    });
            
            if (user.getCreatedAt() != null && !needsSave.get()) {
                logger.info("✅ Using existing createdAt: {} for user: {}", createdAtValue, user.getEmail());
            }
            
            // Optimized: Single save operation instead of multiple (reduces DB round trips)
            User finalUser = needsSave.get() ? userRepository.save(user) : user;
            if (needsSave.get()) {
                try {
                    logger.info("✅ Successfully saved user updates: customerId='{}', createdAt='{}'", customerIdValue, createdAtValue);
                } catch (Exception e) {
                    logger.warn("⚠️ Could not save user updates to database: {}. Will return generated values anyway.", e.getMessage());
                    // Continue with generated values even if save fails
                }
            }
            
            // Optimized: Build response using method references and Optional
            UserResponse response = new UserResponse();
            response.setId(finalUser.getId().toString());
            response.setEmail(finalUser.getEmail());
            response.setFirstName(finalUser.getFirstName());
            response.setLastName(finalUser.getLastName());
            response.setUserTier(java.util.Optional.ofNullable(finalUser.getUserTier())
                    .map(Enum::name)
                    .orElse("FREE"));
            response.setCustomerId(customerIdValue); // ALWAYS set - never null or empty
            response.setCreatedAt(createdAtValue); // ALWAYS set - never null or empty
            
            logger.info("📤 Returning user response: customerId='{}', createdAt='{}'", response.getCustomerId(), response.getCreatedAt());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving user: {}", e.getMessage(), e);
            // SECURITY FIX: Don't expose internal error details to client
            return ResponseEntity.status(500).body(new ErrorResponse("Error retrieving user"));
        }
    }
    
    @PutMapping("/me")
    @Transactional // Ensure transactional for updates
    public ResponseEntity<?> updateUserProfile(
            @RequestBody UpdateUserRequest request,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(new ErrorResponse("Unauthorized"));
        }
        
        try {
            String userIdStr = authentication.getName();
            UUID userId = UUID.fromString(userIdStr);
            
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            logger.info("Updating user profile for userId: {}, firstName: '{}', lastName: '{}'", 
                    userId, request.getFirstName(), request.getLastName());
            
            // SECURITY: Sanitize and validate inputs
            boolean changed = false;
            if (request.getFirstName() != null) {
                String newFirstName = request.getFirstName().trim();
                // SECURITY: Validate length and sanitize
                if (newFirstName.length() > 100) {
                    throw new IllegalArgumentException("First name is too long (max 100 characters)");
                }
                // Remove control characters
                newFirstName = newFirstName.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
                if (!newFirstName.equals(user.getFirstName())) {
                    user.setFirstName(newFirstName.isEmpty() ? null : newFirstName);
                    changed = true;
                    logger.info("Updated firstName from '{}' to '{}'", user.getFirstName(), newFirstName);
                }
            }
            if (request.getLastName() != null) {
                String newLastName = request.getLastName().trim();
                // SECURITY: Validate length and sanitize
                if (newLastName.length() > 100) {
                    throw new IllegalArgumentException("Last name is too long (max 100 characters)");
                }
                // Remove control characters
                newLastName = newLastName.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
                if (!newLastName.equals(user.getLastName())) {
                    user.setLastName(newLastName.isEmpty() ? null : newLastName);
                    changed = true;
                    logger.info("Updated lastName from '{}' to '{}'", user.getLastName(), newLastName);
                }
            }
            
            // Generate customer ID if missing
            if (user.getCustomerId() == null || user.getCustomerId().isEmpty()) {
                String customerId = "CUST-" + java.util.UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase();
                user.setCustomerId(customerId);
                changed = true;
            }
            
            // Set createdAt if missing
            if (user.getCreatedAt() == null) {
                user.setCreatedAt(LocalDateTime.now());
                changed = true;
            }
            
            // Only save if there were actual changes
            if (changed) {
                user = userRepository.save(user);
                logger.info("User profile saved successfully for userId: {}", userId);
            } else {
                logger.info("No changes detected for userId: {}", userId);
            }
            
            // Build response using method references
            UserResponse response = new UserResponse();
            response.setId(user.getId().toString());
            response.setEmail(user.getEmail());
            response.setFirstName(user.getFirstName());
            response.setLastName(user.getLastName());
            response.setUserTier(java.util.Optional.ofNullable(user.getUserTier())
                    .map(Enum::name)
                    .orElse("FREE"));
            response.setCustomerId(user.getCustomerId());
            response.setCreatedAt(java.util.Optional.ofNullable(user.getCreatedAt())
                    .map(DATE_FORMATTER::format)
                    .orElse(""));
            
            logger.info("Returning updated user response: firstName='{}', lastName='{}'", 
                    response.getFirstName(), response.getLastName());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating user profile: {}", e.getMessage(), e);
            // SECURITY FIX: Don't expose internal error details to client
            return ResponseEntity.status(500).body(new ErrorResponse("Error updating user"));
        }
    }
    
    @GetMapping("/me/payment-history")
    public ResponseEntity<?> getPaymentHistory(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(new ErrorResponse("Unauthorized"));
        }
        
        try {
            String userIdStr = authentication.getName();
            UUID userId = UUID.fromString(userIdStr);
            
            // Optimized: Use stream with method reference for cleaner, faster code
            List<SubscriptionResponse> history = subscriptionRepository.findByUserId(userId)
                    .stream()
                    .map(this::toSubscriptionResponse)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ErrorResponse("Error retrieving payment history. Please try again later."));
        }
    }
    
    private SubscriptionResponse toSubscriptionResponse(Subscription subscription) {
        return SubscriptionResponse.builder()
                .id(subscription.getId().toString())
                .planType(subscription.getPlanType())
                .status(subscription.getStatus())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .createdAt(subscription.getCreatedAt())
                .build();
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
    
    private static class UpdateUserRequest {
        private String firstName;
        private String lastName;
        
        public String getFirstName() {
            return firstName;
        }
        
        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }
        
        public String getLastName() {
            return lastName;
        }
        
        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
    }
}

