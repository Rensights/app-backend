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
 * <p>The list is maintained by admins (CSV import) and read by the analysis request form as
 * type-ahead suggestions. It is a suggestion list, not a constraint: the form still accepts a
 * building that is not here, so a missing row never blocks a request.
 *
 * <p>Lives in the shared database, written by the admin service and read by this one.
 */
@Entity
@Table(name = "buildings", indexes = {
    @Index(name = "idx_buildings_name", columnList = "name"),
    @Index(name = "idx_buildings_area", columnList = "area")
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

    /** Community / district, when the import provides one. Used to disambiguate same-name towers. */
    @Column(name = "area", length = 200)
    private String area;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "developer", length = 200)
    private String developer;

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
