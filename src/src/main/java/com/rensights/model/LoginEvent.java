package com.rensights.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per successful login (password, Google, or known-device silent
 * auth). Every column has an explicit name - do not rely on implicit naming
 * strategy here, since app-backend and admin-backend have been observed to
 * resolve implicit column names differently for the same entity (see the
 * AppSetting settingKey/setting_key incident).
 */
@Entity
@Table(name = "login_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "logged_in_at", nullable = false)
    private LocalDateTime loggedInAt;

    @Column(name = "ip_address")
    private String ipAddress;

    @PrePersist
    protected void onCreate() {
        if (loggedInAt == null) {
            loggedInAt = LocalDateTime.now();
        }
    }
}
