package com.rensights.service;

import com.rensights.model.Device;
import com.rensights.repository.DeviceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class DeviceService {
    
    @Autowired
    private DeviceRepository deviceRepository;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    /**
     * Generate a device fingerprint from request headers
     * Optimized: Use StringBuilder to avoid multiple string concatenations
     */
    public String generateDeviceFingerprint(HttpServletRequest request) {
        StringBuilder fingerprint = new StringBuilder(256); // Pre-size for typical header length
        
        String userAgent = request.getHeader("User-Agent");
        String acceptLanguage = request.getHeader("Accept-Language");
        String acceptEncoding = request.getHeader("Accept-Encoding");
        
        // Use StringBuilder for efficient string building (O(n) vs O(n²) for concatenation)
        if (userAgent != null) {
            fingerprint.append(userAgent);
        }
        fingerprint.append('|');
        if (acceptLanguage != null) {
            fingerprint.append(acceptLanguage);
        }
        fingerprint.append('|');
        if (acceptEncoding != null) {
            fingerprint.append(acceptEncoding);
        }
        
        // In production, you might want to use a hash
        return String.valueOf(fingerprint.toString().hashCode());
    }
    
    /**
     * Check if device is known for user
     */
    public boolean isDeviceKnown(UUID userId, String deviceFingerprint) {
        return deviceRepository.existsByUserIdAndDeviceFingerprint(userId, deviceFingerprint);
    }
    
    /**
     * Register a new device for user
     */
    @Transactional
    public Device registerDevice(UUID userId, String deviceFingerprint, HttpServletRequest request) {
        // Check if device already exists
        return deviceRepository.findByUserIdAndDeviceFingerprint(userId, deviceFingerprint)
                .orElseGet(() -> {
                    // Create new device - DO NOT set timestamp fields in builder
                    // Let @CreationTimestamp, @UpdateTimestamp, and @PrePersist handle them
                    Device device = Device.builder()
                            .userId(userId)
                            .deviceFingerprint(deviceFingerprint)
                            .userAgent(request.getHeader("User-Agent"))
                            .ipAddress(getClientIpAddress(request))
                            // Don't set createdAt, updatedAt, or lastUsedAt here
                            // They will be set by @CreationTimestamp, @UpdateTimestamp, and @PrePersist
                            .build();
                    
                    // Set lastUsedAt explicitly since it doesn't have a Hibernate annotation
                    LocalDateTime now = LocalDateTime.now();
                    device.setLastUsedAt(now);
                    
                    // Use EntityManager.persist() to ensure Hibernate processes @CreationTimestamp/@UpdateTimestamp
                    // This ensures the annotations are properly triggered
                    entityManager.persist(device);
                    entityManager.flush(); // Force immediate INSERT to database
                    entityManager.refresh(device); // Refresh to get database-generated values
                    
                    return device;
                });
    }
    
    /**
     * Update device last used timestamp
     * Optimized: Use @Modifying query to update in single DB round trip instead of find + save
     */
    @Transactional
    public void updateDeviceLastUsed(UUID userId, String deviceFingerprint) {
        // Use direct update query to avoid loading entity into memory (faster, less memory)
        deviceRepository.updateLastUsedAt(userId, deviceFingerprint, LocalDateTime.now());
    }
    
    // Optimized: Use Optional and stream for cleaner IP extraction
    private String getClientIpAddress(HttpServletRequest request) {
        return java.util.Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .filter(header -> !header.isEmpty())
                .map(header -> java.util.Arrays.stream(header.split(","))
                        .findFirst()
                        .map(String::trim)
                        .orElse(header.trim()))
                .orElseGet(request::getRemoteAddr);
    }
}

