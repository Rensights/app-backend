package com.rensights.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per tracked customer action: an automatic page view, or a manually
 * instrumented business event (e.g. "DEAL_VIEWED", "ANALYSIS_REQUEST_SUBMITTED").
 * Every column has an explicit name - see the AppSetting settingKey/setting_key
 * incident for why implicit naming strategy must never be relied on here.
 */
@Entity
@Table(name = "activity_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "page_path", length = 500)
    private String pagePath;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }
}
