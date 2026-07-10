package com.rensights.controller;

import com.rensights.dto.ArticleDTO;
import com.rensights.service.ArticleImageStorageService;
import com.rensights.service.ArticleService;
import java.net.URLConnection;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
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

    @GetMapping("/articles/exists")
    public ResponseEntity<Map<String, Boolean>> hasArticles() {
        return ResponseEntity.ok(Map.of("hasArticles", articleService.hasArticles()));
    }

    /**
     * Serves an article's cover image as raw bytes, decoded from the base64 data URI stored in the
     * DB, so the public list/detail JSON never has to ship base64. Cached hard by the browser/CDN.
     */
    @GetMapping("/articles/cover/{slug}")
    public ResponseEntity<byte[]> getCoverImage(@PathVariable String slug) {
        if (!articleService.isArticlesEnabled()) {
            return ResponseEntity.notFound().build();
        }
        String dataUri = articleService.getCoverImage(slug);
        if (dataUri == null || dataUri.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        // Expected form: data:<mime>;base64,<payload>
        if (!dataUri.startsWith("data:")) {
            return ResponseEntity.badRequest().build();
        }
        int semi = dataUri.indexOf(';');
        int comma = dataUri.indexOf(',');
        if (semi < 5 || comma < 0 || comma <= semi) {
            return ResponseEntity.badRequest().build();
        }
        String mime = dataUri.substring(5, semi);
        String encoding = dataUri.substring(semi + 1, comma);
        if (!"base64".equalsIgnoreCase(encoding.trim())) {
            return ResponseEntity.badRequest().build();
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUri.substring(comma + 1));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        MediaType mediaType;
        try {
            mediaType = mime.isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(mime);
        } catch (RuntimeException e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
            .body(bytes);
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
