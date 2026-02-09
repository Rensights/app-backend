package com.rensights.service;

import com.rensights.dto.ArticleDTO;
import com.rensights.dto.ArticleRequest;
import com.rensights.model.AppSetting;
import com.rensights.model.Article;
import com.rensights.repository.AppSettingRepository;
import com.rensights.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleService {

    public static final String ARTICLES_ENABLED_KEY = "articles.enabled";

    private final ArticleRepository articleRepository;
    private final AppSettingRepository appSettingRepository;

    @Transactional(readOnly = true)
    public List<ArticleDTO> listPublic() {
        if (!isArticlesEnabled()) {
            return List.of();
        }
        return articleRepository.findByIsActiveTrueOrderByPublishedAtDesc().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ArticleDTO getPublicBySlug(String slug) {
        if (!isArticlesEnabled()) {
            return null;
        }
        return articleRepository.findBySlugAndIsActiveTrue(slug)
            .map(this::toDTO)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ArticleDTO> listAdmin() {
        return articleRepository.findAllByOrderByPublishedAtDesc().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    @Transactional
    public ArticleDTO create(ArticleRequest request) {
        Article article = Article.builder()
            .title(request.getTitle())
            .slug(request.getSlug())
            .excerpt(request.getExcerpt())
            .content(request.getContent())
            .coverImage(request.getCoverImage())
            .publishedAt(request.getPublishedAt())
            .isActive(Optional.ofNullable(request.getIsActive()).orElse(true))
            .build();
        Article saved = articleRepository.save(article);
        return toDTO(saved);
    }

    @Transactional
    public ArticleDTO update(UUID id, ArticleRequest request) {
        Article article = articleRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Article not found"));

        article.setTitle(request.getTitle());
        article.setSlug(request.getSlug());
        article.setExcerpt(request.getExcerpt());
        article.setContent(request.getContent());
        article.setCoverImage(request.getCoverImage());
        article.setPublishedAt(request.getPublishedAt());
        if (request.getIsActive() != null) {
            article.setIsActive(request.getIsActive());
        }

        Article saved = articleRepository.save(article);
        return toDTO(saved);
    }

    @Transactional
    public void delete(UUID id) {
        articleRepository.deleteById(id);
    }

    @Transactional
    public ArticleDTO toggle(UUID id, boolean isActive) {
        Article article = articleRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Article not found"));
        article.setIsActive(isActive);
        return toDTO(articleRepository.save(article));
    }

    @Transactional
    public boolean setArticlesEnabled(boolean enabled) {
        AppSetting setting = appSettingRepository.findById(ARTICLES_ENABLED_KEY)
            .orElseGet(() -> AppSetting.builder().settingKey(ARTICLES_ENABLED_KEY).build());
        setting.setSettingValue(Boolean.toString(enabled));
        appSettingRepository.save(setting);
        return enabled;
    }

    @Transactional(readOnly = true)
    public boolean isArticlesEnabled() {
        return appSettingRepository.findById(ARTICLES_ENABLED_KEY)
            .map(setting -> Boolean.parseBoolean(setting.getSettingValue()))
            .orElse(true);
    }

    private ArticleDTO toDTO(Article article) {
        return ArticleDTO.builder()
            .id(article.getId().toString())
            .title(article.getTitle())
            .slug(article.getSlug())
            .excerpt(article.getExcerpt())
            .content(article.getContent())
            .coverImage(article.getCoverImage())
            .publishedAt(article.getPublishedAt())
            .isActive(Boolean.TRUE.equals(article.getIsActive()))
            .build();
    }
}
