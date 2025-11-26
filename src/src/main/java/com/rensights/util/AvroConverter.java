package com.rensights.util;

import com.rensights.model.User;
import com.rensights.model.Subscription;
// Avro classes will be generated during Maven build from schemas/avro/*.avsc
// Uncomment after first Maven build:
// import com.rensights.schema.User;
// import com.rensights.schema.UserTier;
// import com.rensights.schema.Subscription;
// import com.rensights.schema.PlanType;
// import com.rensights.schema.SubscriptionStatus;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * Utility class to convert between JPA entities and Avro schema objects.
 * 
 * NOTE: Avro classes are generated during Maven build from schemas/avro/*.avsc files.
 * Run 'mvn generate-sources' or 'mvn compile' to generate the classes first.
 */
@Component
public class AvroConverter {
    
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    /**
     * Convert JPA User entity to Avro User schema.
     * Uncomment after Avro classes are generated.
     */
    /*
    public User toAvroUser(com.rensights.model.User user) {
        return User.newBuilder()
                .setId(user.getId().toString())
                .setEmail(user.getEmail())
                .setFirstName(user.getFirstName())
                .setLastName(user.getLastName())
                .setIsActive(user.getIsActive())
                .setEmailVerified(user.getEmailVerified())
                .setUserTier(user.getUserTier() != null 
                    ? UserTier.valueOf(user.getUserTier().name()) 
                    : UserTier.FREE)
                .setCustomerId(user.getCustomerId())
                .setStripeCustomerId(user.getStripeCustomerId())
                .setCreatedAt(user.getCreatedAt() != null 
                    ? user.getCreatedAt().format(ISO_FORMATTER) 
                    : null)
                .setUpdatedAt(user.getUpdatedAt() != null 
                    ? user.getUpdatedAt().format(ISO_FORMATTER) 
                    : null)
                .build();
    }
    */
    
    /**
     * Convert JPA Subscription entity to Avro Subscription schema.
     * Uncomment after Avro classes are generated.
     */
    /*
    public Subscription toAvroSubscription(com.rensights.model.Subscription subscription) {
        return Subscription.newBuilder()
                .setId(subscription.getId().toString())
                .setUserId(subscription.getUser().getId().toString())
                .setUserEmail(subscription.getUser().getEmail())
                .setPlanType(subscription.getPlanType() != null 
                    ? PlanType.valueOf(subscription.getPlanType().name()) 
                    : PlanType.FREE)
                .setStatus(subscription.getStatus() != null 
                    ? SubscriptionStatus.valueOf(subscription.getStatus().name()) 
                    : SubscriptionStatus.ACTIVE)
                .setStartDate(subscription.getStartDate() != null 
                    ? subscription.getStartDate().format(ISO_FORMATTER) 
                    : null)
                .setEndDate(subscription.getEndDate() != null 
                    ? subscription.getEndDate().format(ISO_FORMATTER) 
                    : null)
                .setStripeCustomerId(subscription.getStripeCustomerId())
                .setStripeSubscriptionId(subscription.getStripeSubscriptionId())
                .setStripePaymentMethodId(subscription.getStripePaymentMethodId())
                .setCreatedAt(subscription.getCreatedAt() != null 
                    ? subscription.getCreatedAt().format(ISO_FORMATTER) 
                    : null)
                .setUpdatedAt(subscription.getUpdatedAt() != null 
                    ? subscription.getUpdatedAt().format(ISO_FORMATTER) 
                    : null)
                .build();
    }
    */
}

