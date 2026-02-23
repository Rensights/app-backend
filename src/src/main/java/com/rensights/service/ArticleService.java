package com.rensights.service;

import com.rensights.dto.ArticleDTO;
import com.rensights.model.AppSetting;
import com.rensights.model.Article;
import com.rensights.repository.AppSettingRepository;
import com.rensights.repository.ArticleRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public boolean isArticlesEnabled() {
        return appSettingRepository.findById(ARTICLES_ENABLED_KEY)
            .map(AppSetting::getSettingValue)
            .map(Boolean::parseBoolean)
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
