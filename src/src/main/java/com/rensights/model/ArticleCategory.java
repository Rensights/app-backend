package com.rensights.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A category articles can be filed under, maintained by admins.
 *
 * <p>Drives the filter pills on the public Insights page. An article can carry several, so the
 * relationship lives in the {@code article_categories} join table (see {@link Article}).
 */
@Entity
@Table(name = "article_categories_catalog", indexes = {
    @Index(name = "idx_article_categories_slug", columnList = "slug", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Stable key used in the filter deep-link (/articles#market). Lowercase, no spaces. */
    @Column(name = "slug", nullable = false, length = 80, unique = true)
    private String slug;

    @Column(name = "label", nullable = false, length = 160)
    private String label;

    /** Hex colour for the tag chip, e.g. {@code #7C3AED}. */
    @Column(name = "color", length = 20)
    private String color;

    /** Controls the order of the filter pills; lower comes first. */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

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
