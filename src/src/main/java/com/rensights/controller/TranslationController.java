package com.rensights.controller;

import com.rensights.dto.TranslationsResponse;
import com.rensights.service.TranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/translations")
@RequiredArgsConstructor
public class TranslationController {
    
    private final TranslationService translationService;
    
    // Get translations by language and namespace (public endpoint for frontend)
    @GetMapping("/{languageCode}/{namespace}")
    public ResponseEntity<TranslationsResponse> getTranslationsByLanguageAndNamespace(
        @PathVariable String languageCode,
        @PathVariable String namespace
    ) {
        return ResponseEntity.ok(translationService.getTranslationsByLanguageAndNamespace(languageCode, namespace));
    }
}
