package com.rensights.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "devices", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "device_fingerprint"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "device_fingerprint", nullable = false)
    private String deviceFingerprint;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        // Always set timestamps in @PrePersist as final safety net
        // This ensures they're never null even if builder didn't set them
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (lastUsedAt == null) {
            lastUsedAt = now;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        // @UpdateTimestamp handles updatedAt automatically
        // But we update it here as backup and also update lastUsedAt
        updatedAt = LocalDateTime.now();
        if (lastUsedAt == null) {
            lastUsedAt = LocalDateTime.now();
        } else {
            lastUsedAt = LocalDateTime.now();
        }
    }
}

