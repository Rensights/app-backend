package com.rensights.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "analysis_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    @JsonIgnore // Prevent serialization issues with lazy-loaded User entity
    private User user; // Optional - can be submitted by non-authenticated users
    
    @Column(name = "email", nullable = false)
    private String email; // Contact email
    
    // Property Information
    @Column(name = "city", nullable = false)
    private String city;
    
    @Column(name = "area", nullable = false)
    private String area;
    
    @Column(name = "building_name", nullable = false)
    private String buildingName;
    
    @Column(name = "listing_url", length = 500)
    private String listingUrl;
    
    @Column(name = "property_type", nullable = false)
    private String propertyType;
    
    @Column(name = "bedrooms", nullable = false)
    private String bedrooms;
    
    @Column(name = "size")
    private String size;
    
    @Column(name = "plot_size")
    private String plotSize;
    
    @Column(name = "floor")
    private String floor;
    
    @Column(name = "total_floors")
    private String totalFloors;
    
    @Column(name = "building_status", nullable = false)
    private String buildingStatus;
    
    @Column(name = "condition", nullable = false)
    private String condition;
    
    // Location coordinates
    @Column(name = "latitude", length = 20)
    private String latitude;
    
    @Column(name = "longitude", length = 20)
    private String longitude;
    
    // Financial Information
    @Column(name = "asking_price", nullable = false)
    private String askingPrice;
    
    @Column(name = "service_charge")
    private String serviceCharge;
    
    @Column(name = "handover_date")
    private String handoverDate;
    
    @Column(name = "developer")
    private String developer;
    
    @Column(name = "payment_plan")
    private String paymentPlan;
    
    // Additional Details
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", columnDefinition = "jsonb")
    private List<String> features; // Array of feature IDs
    
    @Column(name = "view_type")
    private String view;
    
    @Column(name = "furnishing")
    private String furnishing;
    
    @Column(name = "additional_notes", columnDefinition = "TEXT")
    private String additionalNotes;
    
    // File attachments
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "file_paths", columnDefinition = "jsonb")
    private List<String> filePaths; // Array of file paths
    
    // Status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AnalysisRequestStatus status = AnalysisRequestStatus.PENDING;
    
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
    
    public enum AnalysisRequestStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED
    }
}



