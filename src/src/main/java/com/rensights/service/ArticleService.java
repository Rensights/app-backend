package com.rensights.service;

import com.rensights.dto.ArticleDTO;
import com.rensights.model.AppSetting;
import com.rensights.model.Article;
import com.rensights.repository.AppSettingRepository;
import com.rensights.repository.ArticleRepository;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArticleService {

    public static final String ARTICLES_ENABLED_KEY = "articles.enabled";

    private final ArticleRepository articleRepository;
    private final AppSettingRepository appSettingRepository;

    @Cacheable(cacheNames = "articlesList", key = "'all'")
    @Transactional(readOnly = true)
    public List<ArticleDTO> listPublic() {
        if (!isArticlesEnabled()) {
            return List.of();
        }
        return articleRepository.findByIsActiveTrueOrderByPublishedAtDesc().stream()
            .map(this::toSummaryDTO)
            .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = "articleBySlug", key = "#slug")
    @Transactional(readOnly = true)
    public ArticleDTO getPublicBySlug(String slug) {
        if (!isArticlesEnabled()) {
            return null;
        }
        return articleRepository.findBySlugAndIsActiveTrue(slug)
            .map(this::toDTO)
            .orElse(null);
    }

    @Cacheable(cacheNames = "killSwitches", key = "'articles'")
    @Transactional(readOnly = true)
    public boolean isArticlesEnabled() {
        return appSettingRepository.findById(ARTICLES_ENABLED_KEY)
            .map(AppSetting::getSettingValue)
            .map(Boolean::parseBoolean)
            .orElse(true);
    }

    /**
     * True only when the articles feature is enabled AND at least one active article exists.
     * Uses an existence query (no row loading); the kill-switch part reuses the 60s-TTL cache.
     */
    @Transactional(readOnly = true)
    public boolean hasArticles() {
        return isArticlesEnabled() && articleRepository.existsByIsActiveTrue();
    }

    /**
     * Raw stored cover-image data URI (e.g. {@code data:image/png;base64,...}) for an active
     * article, or {@code null} if the article or its cover image is absent. Deliberately NOT
     * cached to avoid holding large base64 blobs in memory — the public cover endpoint sets a
     * long immutable Cache-Control so browsers/CDN cache the decoded bytes instead.
     */
    @Transactional(readOnly = true)
    public String getCoverImage(String slug) {
        return articleRepository.findBySlugAndIsActiveTrue(slug)
            .map(Article::getCoverImage)
            .orElse(null);
    }

    /** Full detail DTO: keeps content, but ships coverImage as a relative URL (no base64). */
    private ArticleDTO toDTO(Article article) {
        return ArticleDTO.builder()
            .id(article.getId().toString())
            .title(article.getTitle())
            .slug(article.getSlug())
            .excerpt(article.getExcerpt())
            .content(article.getContent())
            .coverImage(coverImageUrl(article))
            .publishedAt(article.getPublishedAt())
            .isActive(Boolean.TRUE.equals(article.getIsActive()))
            .build();
    }

    /** Slim list DTO: omits content and ships coverImage as a relative URL (no base64). */
    private ArticleDTO toSummaryDTO(Article article) {
        return ArticleDTO.builder()
            .id(article.getId().toString())
            .title(article.getTitle())
            .slug(article.getSlug())
            .excerpt(article.getExcerpt())
            .content(null)
            .coverImage(coverImageUrl(article))
            .publishedAt(article.getPublishedAt())
            .isActive(Boolean.TRUE.equals(article.getIsActive()))
            .build();
    }

    /**
     * Relative URL for the public cover-image endpoint, versioned by the article's updatedAt so a
     * new upload busts the immutable browser cache. Returns {@code null} when no cover exists.
     */
    private String coverImageUrl(Article article) {
        String cover = article.getCoverImage();
        if (cover == null || cover.isBlank()) {
            return null;
        }
        long version = article.getUpdatedAt() != null
            ? article.getUpdatedAt().toEpochSecond(ZoneOffset.UTC)
            : 0L;
        return "/api/articles/cover/" + article.getSlug() + "?v=" + version;
    }
}
