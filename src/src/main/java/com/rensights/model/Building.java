package com.rensights.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A building the user can pick from when requesting a property analysis.
 *
 * <p>Names only, on purpose: the form needs a name to suggest and nothing else, so there is no
 * area, city or developer to keep accurate. Maintained by admins (CSV import or typed in) and
 * read by the analysis request form as type-ahead suggestions. It is a suggestion list, not a
 * constraint: the form still accepts a building that is not here.
 *
 * <p>Lives in the shared database, written by the admin service and read by this one.
 */
@Entity
@Table(name = "buildings", indexes = {
    @Index(name = "idx_buildings_name", columnList = "name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 300)
    private String name;

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
