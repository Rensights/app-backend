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
}
