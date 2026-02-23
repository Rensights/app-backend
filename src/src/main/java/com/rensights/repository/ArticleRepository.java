package com.rensights.repository;

import com.rensights.model.Article;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleRepository extends JpaRepository<Article, UUID> {
    Optional<Article> findBySlugAndIsActiveTrue(String slug);
    List<Article> findByIsActiveTrueOrderByPublishedAtDesc();
}
