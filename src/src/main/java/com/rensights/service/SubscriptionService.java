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
     * Cancel subscription
     */
    @Transactional
    public Subscription cancelSubscription(UUID userId) {
        Subscription subscription = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("No active subscription found"));
        
        // Optimized: Use Optional for cleaner null checks
        java.util.Optional.ofNullable(subscription.getStripeSubscriptionId())
                .ifPresent(subId -> {
                    try {
                        stripeService.cancelSubscription(subId);
                    } catch (StripeException e) {
                        logger.error("Error cancelling Stripe subscription: {}", e.getMessage());
                        throw new RuntimeException("Failed to cancel subscription", e);
                    }
                });
        
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setEndDate(LocalDateTime.now());
        subscription = subscriptionRepository.save(subscription);
        
        // Update user tier to FREE
        User user = subscription.getUser();
        user.setUserTier(UserTier.FREE);
        userRepository.save(user);
        
        return subscription;
    }
    
    /**
     * Get all subscriptions for user
     */
    public List<Subscription> getUserSubscriptions(UUID userId) {
        return subscriptionRepository.findByUserId(userId);
    }
}

