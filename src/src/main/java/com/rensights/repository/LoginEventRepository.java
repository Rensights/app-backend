package com.rensights.repository;

import com.rensights.model.LoginEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LoginEventRepository extends JpaRepository<LoginEvent, UUID> {

    /** Erasure: sign-in history carries IPs and timestamps tied to a person. */
    @Modifying
    @Query("DELETE FROM LoginEvent e WHERE e.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
