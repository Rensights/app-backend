package com.rensights.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "revoked_tokens", indexes = {
    @Index(name = "idx_revoked_token_hash", columnList = "token_hash", unique = true),
    @Index(name = "idx_revoked_expiry", columnList = "expires_at")
})
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RevokedToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
