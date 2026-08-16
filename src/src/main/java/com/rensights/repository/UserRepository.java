package com.rensights.repository;

import com.rensights.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByStripeCustomerId(String stripeCustomerId);

    // Direct UPDATE (not entity save) so this never touches updatedAt.
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.lastSeenAt = :seenAt WHERE u.id = :userId")
    void updateLastSeenAt(@Param("userId") UUID userId, @Param("seenAt") LocalDateTime seenAt);

    /**
     * Accounts whose welcome email is due: email verified, created long enough ago to clear the
     * delay, but not so long ago that a first deploy (or a long outage) would mail the whole
     * back catalogue.
     *
     * <p>The verified check is written as {@code = TRUE} rather than {@code IS TRUE} so a null
     * flag on an older row counts as unverified instead of matching.
     */
    @Query("SELECT u FROM User u WHERE u.welcomeEmailSentAt IS NULL "
        + "AND u.emailVerified = TRUE "
        + "AND u.createdAt <= :sendBefore AND u.createdAt >= :sendAfter "
        + "ORDER BY u.createdAt ASC")
    java.util.List<User> findWelcomeEmailDue(@Param("sendBefore") LocalDateTime sendBefore,
                                             @Param("sendAfter") LocalDateTime sendAfter,
                                             org.springframework.data.domain.Pageable pageable);

    // Direct UPDATE so stamping the welcome email never bumps updatedAt.
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.welcomeEmailSentAt = :sentAt WHERE u.id = :userId")
    void markWelcomeEmailSent(@Param("userId") UUID userId, @Param("sentAt") LocalDateTime sentAt);
}
