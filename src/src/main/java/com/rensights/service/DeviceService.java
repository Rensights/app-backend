package com.rensights.service;

import com.rensights.model.Device;
import com.rensights.repository.DeviceRepository;
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
                    // Create new device - set all timestamp fields explicitly to ensure they're not null
                    // @PrePersist should also set them, but we set them here as a safety measure
                    LocalDateTime now = LocalDateTime.now();
                    Device device = Device.builder()
                            .userId(userId)
                            .deviceFingerprint(deviceFingerprint)
                            .userAgent(request.getHeader("User-Agent"))
                            .ipAddress(getClientIpAddress(request))
                            .createdAt(now)
                            .updatedAt(now)
                            .lastUsedAt(now)
                            .build();
                    return deviceRepository.save(device);
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

