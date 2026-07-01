package com.rensights.repository;

import com.rensights.model.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {
    Optional<VerificationCode> findByEmail(String email);

    @Modifying @Transactional
    @Query("DELETE FROM VerificationCode v WHERE v.expiryTime < :now")
    int deleteExpired(LocalDateTime now);

    @Modifying @Transactional
    void deleteByEmail(String email);
}
