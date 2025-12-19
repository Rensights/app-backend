package com.rensights.controller;

import com.rensights.dto.TranslationDTO;
import com.rensights.dto.TranslationRequest;
import com.rensights.dto.TranslationsResponse;
import com.rensights.service.TranslationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/translations")
@RequiredArgsConstructor
public class TranslationController {
    
    private final TranslationService translationService;
    
    // Get all translations (admin only)
    @GetMapping
    public ResponseEntity<List<TranslationDTO>> getAllTranslations() {
        return ResponseEntity.ok(translationService.getAllTranslations());
    }
    
    // Get translations by language
    @GetMapping("/language/{languageCode}")
    public ResponseEntity<List<TranslationDTO>> getTranslationsByLanguage(
        @PathVariable String languageCode
    ) {
        return ResponseEntity.ok(translationService.getTranslationsByLanguage(languageCode));
    }
    
    // Get translations by language and namespace (public endpoint for frontend)
    @GetMapping("/{languageCode}/{namespace}")
    public ResponseEntity<TranslationsResponse> getTranslationsByLanguageAndNamespace(
        @PathVariable String languageCode,
        @PathVariable String namespace
    ) {
        return ResponseEntity.ok(translationService.getTranslationsByLanguageAndNamespace(languageCode, namespace));
    }
    
    // Get a specific translation
    @GetMapping("/{languageCode}/{namespace}/{translationKey}")
    public ResponseEntity<TranslationDTO> getTranslation(
        @PathVariable String languageCode,
        @PathVariable String namespace,
        @PathVariable String translationKey
    ) {
        return ResponseEntity.ok(translationService.getTranslation(languageCode, namespace, translationKey));
    }
    
    // Create a new translation (admin only)
    @PostMapping
    public ResponseEntity<TranslationDTO> createTranslation(@Valid @RequestBody TranslationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(translationService.createTranslation(request));
    }
    
    // Update a translation (admin only)
    @PutMapping("/{id}")
    public ResponseEntity<TranslationDTO> updateTranslation(
        @PathVariable UUID id,
        @Valid @RequestBody TranslationRequest request
    ) {
        return ResponseEntity.ok(translationService.updateTranslation(id, request));
    }
    
    // Delete a translation (admin only)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTranslation(@PathVariable UUID id) {
        translationService.deleteTranslation(id);
        return ResponseEntity.noContent().build();
    }
    
    // Get available languages (deprecated - use /api/languages instead)
    @GetMapping("/languages")
    public ResponseEntity<List<String>> getAvailableLanguages() {
        return ResponseEntity.ok(translationService.getAvailableLanguages());
    }
    
    // Get namespaces for a language
    @GetMapping("/language/{languageCode}/namespaces")
    public ResponseEntity<List<String>> getNamespaces(@PathVariable String languageCode) {
        return ResponseEntity.ok(translationService.getNamespaces(languageCode));
    }
}

