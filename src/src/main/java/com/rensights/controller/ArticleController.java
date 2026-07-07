package com.rensights.controller;

import com.rensights.dto.ArticleDTO;
import com.rensights.service.ArticleImageStorageService;
import com.rensights.service.ArticleService;
import java.net.URLConnection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
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
    private final ArticleImageStorageService articleImageStorageService;

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

    @GetMapping("/articles/images/{filename}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        // Reject anything that could escape the article-images directory.
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return ResponseEntity.badRequest().build();
        }
        Resource resource = articleImageStorageService.loadAsResource(filename);
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }
        String contentType = URLConnection.guessContentTypeFromName(filename);
        return ResponseEntity.ok()
            .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
            .body(resource);
    }
}
