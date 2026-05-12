package com.rensights.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Collections;
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

    @Autowired
    private ObjectMapper objectMapper;
    
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
            fillRegistrationProfile(response, finalUser);
            
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
            
            logger.info("Updating user profile for userId: {}", userId);
            
            boolean changed = false;
            if (request.getFirstName() != null) {
                String newFirstName = request.getFirstName().trim();
                if (newFirstName.length() > 100) {
                    throw new IllegalArgumentException("First name is too long (max 100 characters)");
                }
                newFirstName = newFirstName.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
                if (!newFirstName.equals(java.util.Optional.ofNullable(user.getFirstName()).orElse(""))) {
                    user.setFirstName(newFirstName.isEmpty() ? null : newFirstName);
                    changed = true;
                }
            }
            if (request.getLastName() != null) {
                String newLastName = request.getLastName().trim();
                if (newLastName.length() > 100) {
                    throw new IllegalArgumentException("Last name is too long (max 100 characters)");
                }
                newLastName = newLastName.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
                if (!newLastName.equals(java.util.Optional.ofNullable(user.getLastName()).orElse(""))) {
                    user.setLastName(newLastName.isEmpty() ? null : newLastName);
                    changed = true;
                }
            }
            if (request.getPhone() != null) {
                String phone = request.getPhone().trim();
                if (phone.length() > 40) {
                    throw new IllegalArgumentException("Phone is too long");
                }
                phone = phone.replaceAll("[^\\d+\\-() ]", "");
                if (!phone.equals(java.util.Optional.ofNullable(user.getPhone()).orElse(""))) {
                    user.setPhone(phone.isEmpty() ? null : phone);
                    changed = true;
                }
            }
            if (request.getBudget() != null) {
                String budget = request.getBudget().trim();
                if (budget.length() > 64) {
                    throw new IllegalArgumentException("Invalid budget");
                }
                if (!budget.equals(java.util.Optional.ofNullable(user.getBudget()).orElse(""))) {
                    user.setBudget(budget.isEmpty() ? null : budget);
                    changed = true;
                }
            }
            if (request.getPortfolio() != null) {
                String portfolio = request.getPortfolio().trim();
                if (portfolio.length() > 64) {
                    throw new IllegalArgumentException("Invalid portfolio");
                }
                if (!portfolio.equals(java.util.Optional.ofNullable(user.getPortfolio()).orElse(""))) {
                    user.setPortfolio(portfolio.isEmpty() ? null : portfolio);
                    changed = true;
                }
            }
            if (request.getPlan() != null) {
                String plan = request.getPlan().trim().toLowerCase();
                if (!plan.equals("free") && !plan.equals("premium")) {
                    throw new IllegalArgumentException("Plan must be free or premium");
                }
                if (!plan.equals(java.util.Optional.ofNullable(user.getRegistrationPlan()).orElse(""))) {
                    user.setRegistrationPlan(plan);
                    changed = true;
                }
            }
            if (request.getGoals() != null) {
                if (request.getGoals().isEmpty()) {
                    throw new IllegalArgumentException("Select at least one goal");
                }
                for (String g : request.getGoals()) {
                    if (g != null && g.length() > 80) {
                        throw new IllegalArgumentException("Invalid goal value");
                    }
                }
                String goalsJson = objectMapper.writeValueAsString(
                        request.getGoals().stream().filter(java.util.Objects::nonNull).map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toList()));
                if (!goalsJson.equals(java.util.Optional.ofNullable(user.getGoalsJson()).orElse(""))) {
                    user.setGoalsJson(goalsJson);
                    changed = true;
                }
            }
            
            if (user.getCustomerId() == null || user.getCustomerId().isEmpty()) {
                String customerId = "CUST-" + java.util.UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase();
                user.setCustomerId(customerId);
                changed = true;
            }
            
            if (user.getCreatedAt() == null) {
                user.setCreatedAt(LocalDateTime.now());
                changed = true;
            }
            
            if (changed) {
                user = userRepository.save(user);
                logger.info("User profile saved successfully for userId: {}", userId);
            }
            
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
            fillRegistrationProfile(response, user);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating user profile: {}", e.getMessage(), e);
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
                .cancelAtPeriodEnd(subscription.getCancelAtPeriodEnd())
                .createdAt(subscription.getCreatedAt())
                .build();
    }
    
    private void fillRegistrationProfile(UserResponse response, User user) {
        response.setRegistrationProfileComplete(user.isRegistrationProfileComplete());
        response.setPhone(user.getPhone());
        response.setBudget(user.getBudget());
        response.setPortfolio(user.getPortfolio());
        response.setRegistrationPlan(user.getRegistrationPlan());
        response.setGoals(parseGoalsList(user.getGoalsJson()));
    }

    private List<String> parseGoalsList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() { });
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
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
    
    private static class UpdateUserRequest {
        private String firstName;
        private String lastName;
        private String phone;
        private String budget;
        private String portfolio;
        private String plan;
        private List<String> goals;

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

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getBudget() {
            return budget;
        }

        public void setBudget(String budget) {
            this.budget = budget;
        }

        public String getPortfolio() {
            return portfolio;
        }

        public void setPortfolio(String portfolio) {
            this.portfolio = portfolio;
        }

        public String getPlan() {
            return plan;
        }

        public void setPlan(String plan) {
            this.plan = plan;
        }

        public List<String> getGoals() {
            return goals;
        }

        public void setGoals(List<String> goals) {
            this.goals = goals;
        }
    }
}

