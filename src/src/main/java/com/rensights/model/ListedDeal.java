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
@Table(name = "listed_deals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListedDeal {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "building_name", nullable = false)
    private String buildingName;
    
    @Column(name = "property_type")
    private String propertyType;
    
    @Column(name = "size_sqft")
    private String sizeSqft;
    
    @Column(name = "view")
    private String view;
    
    @Column(name = "listed_price_aed")
    private BigDecimal listedPriceAed;
    
    @Column(name = "price_per_sqft")
    private BigDecimal pricePerSqft;
    
    @Column(name = "property_id")
    private String propertyId;
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "deal_listed_deals",
        joinColumns = @JoinColumn(name = "listed_deal_id"),
        inverseJoinColumns = @JoinColumn(name = "deal_id")
    )
    @Builder.Default
    private Set<Deal> deals = new HashSet<>();
    
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

