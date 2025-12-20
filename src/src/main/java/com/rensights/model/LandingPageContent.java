package com.rensights.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "landing_page_content")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandingPageContent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "section", nullable = false, length = 50)
    private String section;
    
    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;
    
    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;
    
    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;
    
    @Column(name = "content_value", nullable = false, columnDefinition = "TEXT")
    private String contentValue;
    
    @Column(name = "display_order")
    private Integer displayOrder;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}


