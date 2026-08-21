package com.rensights.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    @Column(name = "first_name")
    private String firstName;
    
    @Column(name = "last_name")
    private String lastName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "budget")
    private String budget;

    @Column(name = "portfolio")
    private String portfolio;

    @Column(name = "goals_json", columnDefinition = "TEXT")
    private String goalsJson;

    @Column(name = "registration_plan")
    private String registrationPlan;
    
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "user_tier", nullable = false)
    @Builder.Default
    private UserTier userTier = UserTier.FREE;
    
    @Column(name = "customer_id", unique = true, nullable = true)
    private String customerId; // Our internal customer ID (random)
    
    @Column(name = "stripe_customer_id")
    private String stripeCustomerId; // Stripe's customer ID
        
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Bumped only by the heartbeat endpoint via a direct UPDATE query (see
    // UserRepository.updateLastSeenAt) - never via entity save, so it doesn't
    // touch updatedAt (which should only reflect real profile changes).
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    // Stamped once the welcome email goes out, a few minutes after sign-up
    // (see WelcomeEmailScheduler). Null means it is still pending or was never due.
    @Column(name = "welcome_email_sent_at")
    private LocalDateTime welcomeEmailSentAt;

    // Stamped once the getting-started email goes out, a day after sign-up
    // (see GettingStartedEmailScheduler). Null means it is still pending or was never due.
    @Column(name = "getting_started_email_sent_at")
    private LocalDateTime gettingStartedEmailSentAt;

    // Stamped once the feedback check-in goes out, ~10 days after sign-up
    // (see FeedbackEmailScheduler). Null means it is still pending or was never due.
    @Column(name = "feedback_email_sent_at")
    private LocalDateTime feedbackEmailSentAt;

    // Set when the account is erased on request (see AccountDeletionService). The row survives
    // because invoices reference it and have to be kept for accounting, but everything
    // identifying is stripped. A non-null value means "this is a tombstone, not a person".
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Same required investment fields as email/password registration (phone optional).
     */
    public boolean isRegistrationProfileComplete() {
        if (isBlank(budget) || isBlank(portfolio) || isBlank(registrationPlan)) {
            return false;
        }
        if (goalsJson == null) {
            return false;
        }
        String g = goalsJson.trim();
        if (g.length() < 3 || "[]".equals(g)) {
            return false;
        }
        return g.startsWith("[");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
