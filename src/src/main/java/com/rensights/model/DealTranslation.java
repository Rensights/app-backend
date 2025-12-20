package com.rensights.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "deal_translations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealTranslation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "deal_id", nullable = false)
    private UUID dealId;
    
    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;
    
    @Column(name = "field_name", nullable = false, length = 50)
    private String fieldName;
    
    @Column(name = "translated_value", nullable = false, columnDefinition = "TEXT")
    private String translatedValue;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}



