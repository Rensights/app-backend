package com.rensights.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastUsedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastUsedAt = LocalDateTime.now();
    }
}

