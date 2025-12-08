package com.rensights.controller;

import com.rensights.dto.SubscriptionResponse;
import com.rensights.model.Subscription;
import com.rensights.model.SubscriptionStatus;
import com.rensights.model.User;
import com.rensights.model.UserTier;
import com.rensights.repository.SubscriptionRepository;
import com.rensights.repository.UserRepository;
import com.rensights.service.StripeService;
import com.rensights.service.SubscriptionService;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {
    
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionController.class);
    
    @Autowired
    private SubscriptionService subscriptionService;
    
    @Autowired
    private StripeService stripeService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    
    @Value("${stripe.premium-price-id:}")
    private String premiumPriceId;
    
    @Value("${stripe.enterprise-price-id:}")
    private String enterprisePriceId;
    
    @Value("${app.frontend-url:http://dev.72.62.40.154.nip.io:31416}")
    private String frontendUrl;
    
    /**
     * Create Stripe Checkout Session for subscription
     */
    @PostMapping("/create-checkout-session")
    public ResponseEntity<?> createCheckoutSession(@RequestBody CreateCheckoutRequest request) {
        try {
            UUID userId = getCurrentUserId();
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Optimized: Use Map for O(1) lookup instead of switch statement
            String priceId = java.util.Map.of(
                    UserTier.PREMIUM, premiumPriceId,
                    UserTier.ENTERPRISE, enterprisePriceId
            ).get(request.getPlanType());
            
            if (priceId == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("Invalid plan type for checkout"));
            }
            
            if (priceId == null || priceId.isEmpty()) {
                logger.error("Stripe price ID not configured for plan: {}. Product ID: prod_TTZPr5yGZso2iI. Please create a price for this product in Stripe dashboard.", request.getPlanType());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("Stripe price ID not configured for plan: " + request.getPlanType() + ". Please create a price for product prod_TTZPr5yGZso2iI in Stripe dashboard."));
            }
            
            // Optimized: Check existing subscription for Stripe customer ID, or create new
            String customerId = subscriptionRepository.findByUserId(user.getId())
                    .stream()
                    .filter(sub -> sub.getStripeCustomerId() != null && !sub.getStripeCustomerId().isEmpty())
                    .findFirst()
                    .map(Subscription::getStripeCustomerId)
                    .orElseGet(() -> {
                        // Optimized: Use String.join for cleaner concatenation
                        String fullName = String.join(" ",
                                java.util.Optional.ofNullable(user.getFirstName()).orElse(""),
                                java.util.Optional.ofNullable(user.getLastName()).orElse(""))
                                .trim();
                        try {
                            com.stripe.model.Customer stripeCustomer = stripeService.createCustomer(
                                    user.getEmail(),
                                    fullName.isEmpty() ? user.getEmail() : fullName);
                            return stripeCustomer.getId();
                        } catch (StripeException e) {
                            logger.error("Error creating Stripe customer: {}", e.getMessage());
                            throw new RuntimeException("Failed to create Stripe customer", e);
                        }
                    });
            
            // Create checkout session
            String successUrl = frontendUrl + "/account?session_id={CHECKOUT_SESSION_ID}";
            String cancelUrl = frontendUrl + "/account?canceled=true";
            
            Session session = stripeService.createCheckoutSession(
                    customerId,
                    priceId,
                    successUrl,
                    cancelUrl,
                    user.getCustomerId() != null ? user.getCustomerId() : "UNKNOWN"
            );
            
            return ResponseEntity.ok(new CheckoutSessionResponse(session.getUrl()));
        } catch (StripeException e) {
            logger.error("Error creating checkout session: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to create checkout session. Please try again later."));
        } catch (Exception e) {
            logger.error("Error creating checkout session: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to create checkout session. Please try again later."));
        }
    }
    
    /**
     * Handle Stripe checkout success - update subscription
     */
    @GetMapping("/checkout-success")
    public ResponseEntity<?> checkoutSuccess(@RequestParam("session_id") String sessionId) {
        try {
            UUID userId = getCurrentUserId();
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            logger.info("Processing checkout success for user {} with session {}", userId, sessionId);
            
            // Retrieve the checkout session from Stripe
            Session session = Session.retrieve(sessionId);
            logger.info("Retrieved session status: {}", session.getStatus());
            
            if (!"complete".equals(session.getStatus())) {
                logger.warn("Session not complete. Status: {}", session.getStatus());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("Checkout session not completed. Status: " + session.getStatus()));
            }
            
            // Get subscription from Stripe
            String stripeSubscriptionId = session.getSubscription();
            if (stripeSubscriptionId == null) {
                logger.error("No subscription found in checkout session");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("No subscription found in checkout session"));
            }
            
            logger.info("Retrieving Stripe subscription: {}", stripeSubscriptionId);
            com.stripe.model.Subscription stripeSubscription = com.stripe.model.Subscription.retrieve(stripeSubscriptionId);
            
            // Optimized: Use stream to get price ID safely and Map for O(1) lookup
            String priceId = stripeSubscription.getItems().getData().stream()
                    .findFirst()
                    .map(item -> item.getPrice().getId())
                    .orElseThrow(() -> new RuntimeException("No price found in subscription"));
            
            logger.info("Price ID from Stripe: {}", priceId);
            
            // Optimized: Use stream with filter for O(1) lookup pattern
            UserTier planType = java.util.stream.Stream.of(
                    java.util.Map.entry(premiumPriceId, UserTier.PREMIUM),
                    java.util.Map.entry(enterprisePriceId, UserTier.ENTERPRISE)
            )
            .filter(entry -> entry.getKey() != null && entry.getKey().equals(priceId))
            .map(java.util.Map.Entry::getValue)
            .findFirst()
            .orElseThrow(() -> {
                logger.error("Unknown price ID: {}. Expected premium: {} or enterprise: {}", priceId, premiumPriceId, enterprisePriceId);
                return new RuntimeException("Unknown price ID: " + priceId);
            });
            
            logger.info("Plan type determined: {}", planType);
            
            // Cancel existing active subscription
            Subscription existing = subscriptionService.getCurrentSubscription(userId);
            if (existing != null) {
                logger.info("Cancelling existing subscription: {}", existing.getId());
                existing.setStatus(SubscriptionStatus.CANCELLED);
                existing.setEndDate(java.time.LocalDateTime.now());
                subscriptionRepository.save(existing);
            }
            
            // Create new subscription record
            Subscription subscription = Subscription.builder()
                    .user(user)
                    .planType(planType)
                    .status(SubscriptionStatus.ACTIVE)
                    .startDate(java.time.LocalDateTime.now())
                    .endDate(java.time.LocalDateTime.now().plusMonths(1))
                    .stripeCustomerId(session.getCustomer())
                    .stripeSubscriptionId(stripeSubscriptionId)
                    .build();
            
            subscription = subscriptionRepository.save(subscription);
            logger.info("Created subscription: {} for user: {}", subscription.getId(), userId);
            
            // Update user tier
            user.setUserTier(planType);
            userRepository.save(user);
            logger.info("Updated user tier to: {} for user: {}", planType, userId);
            
            return ResponseEntity.ok(toResponse(subscription));
        } catch (StripeException e) {
            logger.error("Stripe error processing checkout success: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to process checkout. Please try again later."));
        } catch (Exception e) {
            logger.error("Error processing checkout success: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to process checkout. Please try again later."));
        }
    }
    
    /**
     * Get available plans
     */
    @GetMapping("/plans")
    public ResponseEntity<List<String>> getAvailablePlans() {
        return ResponseEntity.ok(List.of("FREE", "PREMIUM", "ENTERPRISE"));
    }
    
    /**
     * Get current subscription
     */
    @GetMapping
    public ResponseEntity<?> getCurrentSubscription() {
        try {
            UUID userId = getCurrentUserId();
            Subscription subscription = subscriptionService.getCurrentSubscription(userId);
            
            if (subscription == null) {
                return ResponseEntity.ok(SubscriptionResponse.builder()
                        .planType(UserTier.FREE)
                        .status(com.rensights.model.SubscriptionStatus.ACTIVE)
                        .build());
            }
            
            return ResponseEntity.ok(toResponse(subscription));
        } catch (Exception e) {
            logger.error("Error getting subscription: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to get subscription"));
        }
    }
    
    /**
     * Purchase/Subscribe to a plan
     */
    @PostMapping("/purchase")
    public ResponseEntity<?> purchasePlan(@RequestBody PurchaseRequest request) {
        logger.info("Purchase request for plan: {}", request.getPlanType());
        
        try {
            UUID userId = getCurrentUserId();
            
            if (request.getPlanType() == UserTier.FREE) {
                // Free plan doesn't require payment
                return ResponseEntity.ok(new MessageResponse("Free plan activated"));
            }
            
            if (request.getPaymentMethodId() == null || request.getPaymentMethodId().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("Payment method ID is required"));
            }
            
            Subscription subscription = subscriptionService.createSubscription(
                    userId,
                    request.getPlanType(),
                    request.getPaymentMethodId()
            );
            
            logger.info("Subscription created successfully: {}", subscription.getId());
            return ResponseEntity.ok(toResponse(subscription));
        } catch (RuntimeException e) {
            logger.error("Error purchasing plan: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error purchasing plan: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to process payment"));
        }
    }
    
    /**
     * Cancel subscription
     */
    @PutMapping("/cancel")
    public ResponseEntity<?> cancelSubscription() {
        try {
            UUID userId = getCurrentUserId();
            Subscription subscription = subscriptionService.cancelSubscription(userId);
            return ResponseEntity.ok(toResponse(subscription));
        } catch (RuntimeException e) {
            logger.error("Error cancelling subscription: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error cancelling subscription: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to cancel subscription"));
        }
    }
    
    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || 
            "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("User not authenticated");
        }
        return UUID.fromString(authentication.getName());
    }
    
    private SubscriptionResponse toResponse(Subscription subscription) {
        return SubscriptionResponse.builder()
                .id(subscription.getId().toString())
                .planType(subscription.getPlanType())
                .status(subscription.getStatus())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .createdAt(subscription.getCreatedAt())
                .build();
    }
    
    private static class PurchaseRequest {
        private UserTier planType;
        private String paymentMethodId;
        
        public UserTier getPlanType() { return planType; }
        public void setPlanType(UserTier planType) { this.planType = planType; }
        
        public String getPaymentMethodId() { return paymentMethodId; }
        public void setPaymentMethodId(String paymentMethodId) { this.paymentMethodId = paymentMethodId; }
    }
    
    private static class ErrorResponse {
        private String error;
        public ErrorResponse(String error) { this.error = error; }
        public String getError() { return error; }
    }
    
    private static class MessageResponse {
        private String message;
        public MessageResponse(String message) { this.message = message; }
        public String getMessage() { return message; }
    }
    
    private static class CreateCheckoutRequest {
        private UserTier planType;
        public UserTier getPlanType() { return planType; }
        public void setPlanType(UserTier planType) { this.planType = planType; }
    }
    
    private static class CheckoutSessionResponse {
        private String url;
        public CheckoutSessionResponse(String url) { this.url = url; }
        public String getUrl() { return url; }
    }
}

