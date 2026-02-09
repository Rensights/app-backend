package com.rensights.controller;

import com.rensights.dto.ArticleDTO;
import com.rensights.dto.ArticleRequest;
import com.rensights.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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

    @GetMapping("/articles/{slug}")
    public ResponseEntity<ArticleDTO> getPublic(@PathVariable String slug) {
        ArticleDTO article = articleService.getPublicBySlug(slug);
        if (article == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(article);
    }

    @GetMapping("/articles/settings")
    public ResponseEntity<?> getSettings() {
        return ResponseEntity.ok(java.util.Map.of("enabled", articleService.isArticlesEnabled()));
    }

    @GetMapping("/admin/articles")
    public ResponseEntity<List<ArticleDTO>> listAdmin() {
        return ResponseEntity.ok(articleService.listAdmin());
    }

    @PostMapping("/admin/articles")
    public ResponseEntity<ArticleDTO> create(@RequestBody ArticleRequest request) {
        return ResponseEntity.ok(articleService.create(request));
    }

    @PutMapping("/admin/articles/{id}")
    public ResponseEntity<ArticleDTO> update(@PathVariable UUID id, @RequestBody ArticleRequest request) {
        return ResponseEntity.ok(articleService.update(id, request));
    }

    @DeleteMapping("/admin/articles/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        articleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/admin/articles/{id}/active")
    public ResponseEntity<ArticleDTO> toggleActive(@PathVariable UUID id, @RequestParam boolean active) {
        return ResponseEntity.ok(articleService.toggle(id, active));
    }

    @PutMapping("/admin/articles/settings")
    public ResponseEntity<?> updateSettings(@RequestParam boolean enabled) {
        return ResponseEntity.ok(java.util.Map.of("enabled", articleService.setArticlesEnabled(enabled)));
    }
}
