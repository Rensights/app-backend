package com.rensights.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "translations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"language_code", "translation_key", "namespace"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Translation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode; // e.g., "en", "ar", "fr"
    
    @Column(name = "namespace", nullable = false, length = 100)
    @Builder.Default
    private String namespace = "common"; // e.g., "common", "dashboard", "deals", "auth"
    
    @Column(name = "translation_key", nullable = false, length = 255)
    private String translationKey; // e.g., "welcome.message", "button.submit"
    
    @Column(name = "translation_value", nullable = false, columnDefinition = "TEXT")
    private String translationValue; // The actual translated text
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // Optional description for context
    
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


