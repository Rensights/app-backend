package com.rensights.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "languages", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"code"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Language {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "code", nullable = false, unique = true, length = 10)
    private String code; // e.g., "en", "ar", "fr"
    
    @Column(name = "name", nullable = false, length = 100)
    private String name; // e.g., "English", "العربية", "Français"
    
    @Column(name = "native_name", length = 100)
    private String nativeName; // e.g., "English", "العربية", "Français"
    
    @Column(name = "flag", length = 10)
    private String flag; // Emoji flag, e.g., "🇬🇧", "🇸🇦", "🇫🇷"
    
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true; // Whether this language is enabled and available
    
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false; // Whether this is the default language
    
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

