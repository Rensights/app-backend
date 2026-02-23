package com.rensights.controller;

import com.rensights.dto.ArticleDTO;
import com.rensights.service.ArticleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping("/articles")
    public ResponseEntity<List<ArticleDTO>> listPublic() {
        List<ArticleDTO> articles = articleService.listPublic();
        if (articles.isEmpty() && !articleService.isArticlesEnabled()) {
            return ResponseEntity.status(404).build();
        }
        return ResponseEntity.ok(articles);
    }

    @GetMapping("/articles/slug/{slug}")
    public ResponseEntity<ArticleDTO> getPublicBySlug(@PathVariable String slug) {
        ArticleDTO article = articleService.getPublicBySlug(slug);
        if (article == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(article);
    }
}
