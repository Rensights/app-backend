package com.rensights.repository;

import com.rensights.model.ActivityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {

    /** Erasure: behavioural history is personal data and is removed with the account. */
    @Modifying
    @Query("DELETE FROM ActivityEvent e WHERE e.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
