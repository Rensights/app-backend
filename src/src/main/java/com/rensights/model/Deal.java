package com.rensights.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "deals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Deal {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "location", nullable = false)
    private String location;
    
    @Column(name = "city", nullable = false)
    private String city;
    
    @Column(name = "area", nullable = false)
    private String area;
    
    @Column(name = "bedrooms", nullable = false)
    private String bedrooms;
    
    @Column(name = "bedroom_count")
    private String bedroomCount;
    
    @Column(name = "size", nullable = false)
    private String size;
    
    @Column(name = "listed_price", nullable = false)
    private String listedPrice;
    
    @Column(name = "price_value", nullable = false)
    private BigDecimal priceValue;
    
    @Column(name = "estimate_min")
    private BigDecimal estimateMin;
    
    @Column(name = "estimate_max")
    private BigDecimal estimateMax;
    
    @Column(name = "estimate_range")
    private String estimateRange;
    
    @Column(name = "discount")
    private String discount;
    
    @Column(name = "rental_yield")
    private String rentalYield;
    
    @Column(name = "gross_rental_yield")
    private String grossRentalYield;
    
    @Column(name = "building_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private BuildingStatus buildingStatus;
    
    @Column(name = "property_type")
    private String propertyType; // APARTMENT, VILLA, TOWNHOUSE, etc.
    
    @Column(name = "price_vs_estimations")
    private String priceVsEstimations; // Comparison text or percentage
    
    @Column(name = "price_per_sqft")
    private BigDecimal pricePerSqft;
    
    @Column(name = "price_per_sqft_vs_market")
    private BigDecimal pricePerSqftVsMarket;
    
    @Column(name = "property_description", columnDefinition = "TEXT")
    private String propertyDescription;
    
    @Column(name = "building_features", columnDefinition = "TEXT")
    private String buildingFeatures; // JSON or comma-separated features
    
    @Column(name = "service_charge")
    private String serviceCharge;
    
    @Column(name = "developer")
    private String developer;
    
    @Column(name = "property_link")
    private String propertyLink; // External link to the property
    
    @Column(name = "property_id")
    private String propertyId; // External property ID (separate from our UUID)
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private DealStatus status = DealStatus.PENDING;
    
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true; // Whether the deal is active (visible to users)
    
    @Column(name = "batch_date")
    private LocalDateTime batchDate;
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    
    @Column(name = "approved_by")
    private UUID approvedBy;
    
    @ManyToMany(mappedBy = "deals", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ListedDeal> listedDeals = new HashSet<>();
    
    @ManyToMany(mappedBy = "deals", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<RecentSale> recentSales = new HashSet<>();
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (batchDate == null) {
            batchDate = LocalDateTime.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum BuildingStatus {
        READY, OFF_PLAN
    }
    
    public enum DealStatus {
        PENDING, APPROVED, REJECTED
    }
}

