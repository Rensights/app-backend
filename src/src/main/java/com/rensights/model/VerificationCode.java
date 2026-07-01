package com.rensights.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_codes", indexes = {
    @Index(name = "idx_verification_email", columnList = "email"),
    @Index(name = "idx_verification_expiry", columnList = "expiry_time")
})
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class VerificationCode {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String code;

    @Column(name = "expiry_time", nullable = false)
    private LocalDateTime expiryTime;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "generation_count", nullable = false)
    private int generationCount;

    @Column(name = "generation_reset_time")
    private LocalDateTime generationResetTime;
}
