package com.rensights.repository;

import com.rensights.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {
    Optional<Device> findByUserIdAndDeviceFingerprint(UUID userId, String deviceFingerprint);
    boolean existsByUserIdAndDeviceFingerprint(UUID userId, String deviceFingerprint);
    
    /**
     * Optimized: Direct update query to avoid loading entity into memory
     * Reduces memory usage and improves performance (single DB round trip)
     */
    @Modifying
    @Query("UPDATE Device d SET d.lastUsedAt = :lastUsedAt WHERE d.userId = :userId AND d.deviceFingerprint = :deviceFingerprint")
    int updateLastUsedAt(@Param("userId") UUID userId, 
                        @Param("deviceFingerprint") String deviceFingerprint, 
                        @Param("lastUsedAt") LocalDateTime lastUsedAt);
}

