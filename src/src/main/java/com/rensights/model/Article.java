package com.rensights.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "articles", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"slug"})
}, indexes = {
    @Index(name = "idx_articles_active_published", columnList = "is_active, published_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 255)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String excerpt;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "cover_image", columnDefinition = "TEXT")
    private String coverImage;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /**
     * Categories this article is filed under — an article can belong to several, so the filter
     * pills on the Insights page are not mutually exclusive.
     *
     * <p>Eagerly fetched because every place that reads an article also renders its tags, and a
     * lazy set here would mean an N+1 on the list endpoint. Excluded from Lombok's toString and
     * equals: with @Data they would otherwise walk the association on every log line.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "article_categories",
        joinColumns = @JoinColumn(name = "article_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @OrderBy("sortOrder ASC, label ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private Set<ArticleCategory> categories = new LinkedHashSet<>();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (publishedAt == null) {
            publishedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
