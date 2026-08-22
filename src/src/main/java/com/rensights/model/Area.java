package com.rensights.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An area / district offered in the analysis request form's dropdown.
 *
 * <p>Names only. Unlike the building catalogue this one <em>is</em> a closed list — the form
 * renders it as a select, and the chosen name is sent on to the analysis module, so it has to
 * match the community naming that module expects.
 *
 * <p>Maintained by admins (CSV import or typed in), replacing a 301-entry list that used to be
 * hardcoded in the frontend and could not be corrected without a deploy.
 *
 * <p>Lives in the shared database, written by the admin service and read by this one.
 */
@Entity
@Table(name = "areas", indexes = {
    @Index(name = "idx_areas_name", columnList = "name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Area {

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
