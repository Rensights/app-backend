package com.rensights.service;

import com.rensights.model.Subscription;
import com.rensights.model.SubscriptionStatus;
import com.rensights.model.User;
import com.rensights.model.UserTier;
import com.rensights.repository.SubscriptionRepository;
import com.rensights.repository.UserRepository;
import com.stripe.exception.StripeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SubscriptionService {
    
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);
    
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private StripeService stripeService;
    
    @Value("${stripe.premium-price-id:}")
    private String premiumPriceId;
    
    @Value("${stripe.enterprise-price-id:}")
    private String enterprisePriceId;
    
    // Cache price map to avoid recreation on every call (memory optimization)
    private volatile Map<UserTier, String> cachedPriceMap;
    private volatile String cachedPremiumPriceId;
    private volatile String cachedEnterprisePriceId;
    
    // Price mapping - cached for performance
    private Map<UserTier, String> getPriceIdMap() {
        // Check if cache is still valid
        if (cachedPriceMap == null || 
            !premiumPriceId.equals(cachedPremiumPriceId) || 
            !enterprisePriceId.equals(cachedEnterprisePriceId)) {
            synchronized (this) {
                if (cachedPriceMap == null || 
                    !premiumPriceId.equals(cachedPremiumPriceId) || 
                    !enterprisePriceId.equals(cachedEnterprisePriceId)) {
                    Map<UserTier, String> priceMap = new HashMap<>(2); // Pre-size for 2 elements
                    if (premiumPriceId != null && !premiumPriceId.isEmpty()) {
                        priceMap.put(UserTier.PREMIUM, premiumPriceId);
                    }
                    if (enterprisePriceId != null && !enterprisePriceId.isEmpty()) {
                        priceMap.put(UserTier.ENTERPRISE, enterprisePriceId);
                    }
                    cachedPriceMap = priceMap;
                    cachedPremiumPriceId = premiumPriceId;
                    cachedEnterprisePriceId = enterprisePriceId;
                }
            }
        }
        return cachedPriceMap;
    }
    
    /**
     * Create subscription with Stripe payment
     */
    @Transactional
    public Subscription createSubscription(UUID userId, UserTier planType, String paymentMethodId) throws StripeException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Optimized: Use Optional chaining for cleaner code
        subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .ifPresent(existing -> {
                    java.util.Optional.ofNullable(existing.getStripeSubscriptionId())
                            .ifPresent(subId -> {
                                try {
                                    stripeService.cancelSubscription(subId);
                                } catch (StripeException e) {
                                    logger.error("Error cancelling existing subscription: {}", e.getMessage());
                                }
                            });
                    existing.setStatus(SubscriptionStatus.CANCELLED);
                    existing.setEndDate(LocalDateTime.now());
                    subscriptionRepository.save(existing);
                });
        
        // Get price ID for plan
        Map<UserTier, String> priceMap = getPriceIdMap();
        String priceId = priceMap.get(planType);
        
        if (priceId == null) {
            throw new RuntimeException("Price ID not configured for plan: " + planType);
        }
        
        // Optimized: Use Optional and method references for cleaner string building
        String customerId = java.util.Optional.ofNullable(user.getStripeCustomerId())
                .filter(id -> !id.isEmpty())
                .orElseGet(() -> {
                    try {
                        // Optimized: Use String.join for cleaner concatenation
                        String fullName = String.join(" ", 
                                java.util.Optional.ofNullable(user.getFirstName()).orElse(""),
                                java.util.Optional.ofNullable(user.getLastName()).orElse(""))
                                .trim();
                        com.stripe.model.Customer stripeCustomer = stripeService.createCustomer(
                                user.getEmail(), 
                                fullName.isEmpty() ? user.getEmail() : fullName);
                        String stripeId = stripeCustomer.getId();
                        // Update user with Stripe customer ID for future use
                        user.setStripeCustomerId(stripeId);
                        userRepository.save(user);
                        return stripeId;
                    } catch (StripeException e) {
                        logger.error("Error creating Stripe customer: {}", e.getMessage());
                        throw new RuntimeException("Failed to create payment customer", e);
                    }
                });
        
        // Attach payment method
        try {
            stripeService.attachPaymentMethod(paymentMethodId, customerId);
        } catch (StripeException e) {
            logger.error("Error attaching payment method: {}", e.getMessage());
            throw new RuntimeException("Failed to attach payment method", e);
        }
        
        // Create Stripe subscription
        com.stripe.model.Subscription stripeSubscription;
        try {
            stripeSubscription = stripeService.createSubscription(customerId, priceId, paymentMethodId);
        } catch (StripeException e) {
            logger.error("Error creating Stripe subscription: {}", e.getMessage());
            throw new RuntimeException("Failed to create subscription", e);
        }
        
        // Create local subscription record
        Subscription subscription = Subscription.builder()
                .user(user)
                .planType(planType)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusMonths(1)) // Monthly subscription
                .stripeCustomerId(customerId)
                .stripeSubscriptionId(stripeSubscription.getId())
                .stripePaymentMethodId(paymentMethodId)
                .build();
        
        subscription = subscriptionRepository.save(subscription);
        
        // Update user tier
        user.setUserTier(planType);
        userRepository.save(user);
        
        logger.info("Created subscription {} for user {}", subscription.getId(), userId);
        return subscription;
    }
    
    /**
     * Get current subscription for user
     */
    public Subscription getCurrentSubscription(UUID userId) {
        return subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElse(null);
    }
    
    /**
     * Cancel subscription - schedules cancellation at period end
     * User maintains access until the end of their billing period
     */
    @Transactional
    public Subscription cancelSubscription(UUID userId) {
        Subscription subscription = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("No active subscription found"));

        // Schedule cancellation at period end in Stripe
        java.util.Optional.ofNullable(subscription.getStripeSubscriptionId())
                .ifPresent(subId -> {
                    try {
                        stripeService.cancelSubscription(subId);
                    } catch (StripeException e) {
                        logger.error("Error cancelling Stripe subscription: {}", e.getMessage());
                        throw new RuntimeException("Failed to cancel subscription", e);
                    }
                });

        // Mark subscription as scheduled for cancellation
        subscription.setCancelAtPeriodEnd(true);
        subscription = subscriptionRepository.save(subscription);

        // Keep subscription ACTIVE until period end
        // Don't change status or endDate - let it expire naturally
        // The Stripe webhook will handle the actual cancellation when the period ends
        logger.info("Subscription {} scheduled to cancel at period end: {}",
                   subscription.getId(), subscription.getEndDate());

        // Don't downgrade user immediately - they keep access until endDate
        // The scheduled job or webhook will downgrade them when subscription expires

        return subscription;
    }
    
    /**
     * Get all subscriptions for user
     */
    public List<Subscription> getUserSubscriptions(UUID userId) {
        return subscriptionRepository.findByUserId(userId);
    }
    
    /**
     * Handle payment failure - downgrade user to FREE tier
     * Called when invoice payment fails or subscription is cancelled due to payment failure
     */
    @Transactional
    public void handlePaymentFailure(String stripeSubscriptionId) {
        logger.warn("Handling payment failure for Stripe subscription: {}", stripeSubscriptionId);
        
        // Find subscription by Stripe subscription ID
        Subscription subscription = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                .orElse(null);
        
        if (subscription == null) {
            logger.warn("Subscription not found for Stripe subscription ID: {}", stripeSubscriptionId);
            return;
        }
        
        // Only process if subscription is still active
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            logger.info("Subscription {} is already cancelled/expired, skipping payment failure handling", 
                       subscription.getId());
            return;
        }
        
        // Mark subscription as cancelled
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setEndDate(LocalDateTime.now());
        subscription = subscriptionRepository.save(subscription);
        
        // Downgrade user to FREE tier
        User user = subscription.getUser();
        user.setUserTier(UserTier.FREE);
        userRepository.save(user);
        
        logger.info("Payment failure handled: User {} downgraded to FREE tier, subscription {} cancelled", 
                   user.getId(), subscription.getId());
    }
    
    /**
     * Handle payment failure by Stripe customer ID (when subscription ID is not available)
     */
    @Transactional
    public void handlePaymentFailureByCustomer(String stripeCustomerId) {
        logger.warn("Handling payment failure for Stripe customer: {}", stripeCustomerId);
        
        // Find user by Stripe customer ID
        User user = userRepository.findByStripeCustomerId(stripeCustomerId)
                .orElse(null);
        
        if (user == null) {
            logger.warn("User not found for Stripe customer ID: {}", stripeCustomerId);
            return;
        }
        
        // Find active subscription for this user
        Subscription subscription = subscriptionRepository
                .findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE)
                .orElse(null);
        
        if (subscription == null) {
            logger.warn("No active subscription found for user {}", user.getId());
            // Still downgrade user tier if it's not already FREE
            if (user.getUserTier() != UserTier.FREE) {
                user.setUserTier(UserTier.FREE);
                userRepository.save(user);
                logger.info("User {} downgraded to FREE tier (no active subscription found)", user.getId());
            }
            return;
        }
        
        // Mark subscription as cancelled
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setEndDate(LocalDateTime.now());
        subscription = subscriptionRepository.save(subscription);
        
        // Downgrade user to FREE tier
        user.setUserTier(UserTier.FREE);
        userRepository.save(user);
        
        logger.info("Payment failure handled: User {} downgraded to FREE tier, subscription {} cancelled", 
                   user.getId(), subscription.getId());
    }
    
    /**
     * Handle payment success - upgrade user tier and activate subscription
     * Called when invoice payment succeeds
     */
    @Transactional
    public void handlePaymentSuccess(String stripeCustomerId, String stripeSubscriptionId, String stripeInvoiceId) {
        logger.info("Handling payment success for Stripe customer: {}, subscription: {}", 
                   stripeCustomerId, stripeSubscriptionId);
        
        try {
            // Find user by Stripe customer ID
            User user = userRepository.findByStripeCustomerId(stripeCustomerId)
                    .orElse(null);
            
            if (user == null) {
                logger.error("User not found for Stripe customer ID: {}", stripeCustomerId);
                return;
            }
            
            // Get subscription from Stripe to determine plan type
            com.stripe.model.Subscription stripeSubscription = null;
            UserTier planType = null;
            
            if (stripeSubscriptionId != null) {
                try {
                    stripeSubscription = stripeService.getSubscription(stripeSubscriptionId);
                    
                    // Get price ID from subscription
                    String priceId = stripeSubscription.getItems().getData().stream()
                            .findFirst()
                            .map(item -> item.getPrice().getId())
                            .orElse(null);
                    
                    if (priceId != null) {
                        // Determine plan type from price ID
                        if (priceId.equals(premiumPriceId)) {
                            planType = UserTier.PREMIUM;
                        } else if (priceId.equals(enterprisePriceId)) {
                            planType = UserTier.ENTERPRISE;
                        }
                    }
                } catch (StripeException e) {
                    logger.error("Error retrieving Stripe subscription {}: {}", stripeSubscriptionId, e.getMessage());
                }
            }
            
            // If we couldn't determine plan type from subscription, try to get from invoice
            if (planType == null && stripeInvoiceId != null) {
                try {
                    com.stripe.model.Invoice invoice = stripeService.getInvoice(stripeInvoiceId);
                    String subscriptionId = invoice.getSubscription();
                    if (subscriptionId != null) {
                        com.stripe.model.Subscription sub = stripeService.getSubscription(subscriptionId);
                        String priceId = sub.getItems().getData().stream()
                                .findFirst()
                                .map(item -> item.getPrice().getId())
                                .orElse(null);
                        
                        if (priceId != null) {
                            if (priceId.equals(premiumPriceId)) {
                                planType = UserTier.PREMIUM;
                            } else if (priceId.equals(enterprisePriceId)) {
                                planType = UserTier.ENTERPRISE;
                            }
                        }
                    }
                } catch (StripeException e) {
                    logger.error("Error retrieving invoice {}: {}", stripeInvoiceId, e.getMessage());
                }
            }
            
            if (planType == null) {
                logger.warn("Could not determine plan type for customer {}. Defaulting to PREMIUM.", stripeCustomerId);
                planType = UserTier.PREMIUM; // Default to PREMIUM if we can't determine
            }
            
            // Cancel existing active subscription
            Subscription existing = getCurrentSubscription(user.getId());
            if (existing != null && existing.getStatus() == SubscriptionStatus.ACTIVE) {
                logger.info("Cancelling existing subscription: {}", existing.getId());
                existing.setStatus(SubscriptionStatus.CANCELLED);
                existing.setEndDate(LocalDateTime.now());
                subscriptionRepository.save(existing);
            }
            
            // Create or update subscription
            Subscription subscription = null;
            if (stripeSubscriptionId != null) {
                subscription = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                        .orElse(null);
            }
            
            if (subscription == null) {
                // Create new subscription
                subscription = Subscription.builder()
                        .user(user)
                        .planType(planType)
                        .status(SubscriptionStatus.ACTIVE)
                        .startDate(LocalDateTime.now())
                        .endDate(LocalDateTime.now().plusMonths(1))
                        .stripeCustomerId(stripeCustomerId)
                        .stripeSubscriptionId(stripeSubscriptionId)
                        .build();
                subscription = subscriptionRepository.save(subscription);
                logger.info("Created new subscription: {} for user: {}", subscription.getId(), user.getId());
            } else {
                // Update existing subscription
                subscription.setPlanType(planType);
                subscription.setStatus(SubscriptionStatus.ACTIVE);
                subscription.setStartDate(LocalDateTime.now());
                subscription.setEndDate(LocalDateTime.now().plusMonths(1));
                subscription = subscriptionRepository.save(subscription);
                logger.info("Updated subscription: {} for user: {}", subscription.getId(), user.getId());
            }
            
            // Update user tier
            user.setUserTier(planType);
            userRepository.save(user);
            
            logger.info("✅ Payment success handled: User {} upgraded to {} tier, subscription {} activated", 
                       user.getId(), planType, subscription.getId());
        } catch (Exception e) {
            logger.error("Error handling payment success: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to handle payment success", e);
        }
    }

    /**
     * Force sync subscription status with Stripe for the current user.
     * This is used on login to ensure local state reflects Stripe truth.
     */
    @Transactional
    public Subscription syncCurrentSubscription(UUID userId) {
        Subscription subscription = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElse(null);
        
        if (subscription == null) {
            return null;
        }
        
        String stripeSubscriptionId = subscription.getStripeSubscriptionId();
        if (stripeSubscriptionId == null || stripeSubscriptionId.isEmpty()) {
            return subscription;
        }
        
        try {
            com.stripe.model.Subscription stripeSub = stripeService.getSubscription(stripeSubscriptionId);
            String status = stripeSub.getStatus();
            
            if ("active".equals(status) || "trialing".equals(status)) {
                if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
                    subscription.setStatus(SubscriptionStatus.ACTIVE);
                }
                User user = subscription.getUser();
                if (user != null && user.getUserTier() != subscription.getPlanType()) {
                    user.setUserTier(subscription.getPlanType());
                    userRepository.save(user);
                }
                return subscriptionRepository.save(subscription);
            }
            
            // Any non-active status is treated as downgrade
            subscription.setStatus(SubscriptionStatus.CANCELLED);
            subscription.setEndDate(LocalDateTime.now());
            subscription = subscriptionRepository.save(subscription);
            
            User user = subscription.getUser();
            if (user != null && user.getUserTier() != UserTier.FREE) {
                user.setUserTier(UserTier.FREE);
                userRepository.save(user);
            }
            
            logger.warn("Synced Stripe subscription {} status {} -> downgraded to FREE", stripeSubscriptionId, status);
            return subscription;
        } catch (StripeException e) {
            logger.error("Failed to sync Stripe subscription {}: {}", stripeSubscriptionId, e.getMessage());
            return subscription;
        }
    }
}
