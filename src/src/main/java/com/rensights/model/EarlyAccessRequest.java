package com.rensights.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "early_access_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EarlyAccessRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Column
    private String phone;

    @Column(nullable = false)
    private String location;

    @Column
    private String experience;

    @Column
    private String budget;

    @Column
    private String portfolio;

    @Column
    private String timeline;

    @Column(name = "target_regions", columnDefinition = "TEXT")
    private String targetRegions;

    @Column(columnDefinition = "TEXT")
    private String challenges;

    @Column(name = "valuable_services", columnDefinition = "TEXT")
    private String valuableServices;

    @Column(name = "goals_json", columnDefinition = "TEXT")
    private String goalsJson;

    @Column(name = "property_types_json", columnDefinition = "TEXT")
    private String propertyTypesJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
