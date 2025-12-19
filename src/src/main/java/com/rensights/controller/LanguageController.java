package com.rensights.controller;

import com.rensights.dto.LanguageDTO;
import com.rensights.dto.LanguageRequest;
import com.rensights.service.LanguageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/languages")
@RequiredArgsConstructor
public class LanguageController {
    
    private final LanguageService languageService;
    
    // Get all languages (admin only)
    @GetMapping("/all")
    public ResponseEntity<List<LanguageDTO>> getAllLanguages() {
        return ResponseEntity.ok(languageService.getAllLanguages());
    }
    
    // Get enabled languages only (public endpoint for frontend)
    @GetMapping
    public ResponseEntity<List<LanguageDTO>> getEnabledLanguages() {
        return ResponseEntity.ok(languageService.getEnabledLanguages());
    }
    
    // Get language by code
    @GetMapping("/{code}")
    public ResponseEntity<LanguageDTO> getLanguageByCode(@PathVariable String code) {
        return ResponseEntity.ok(languageService.getLanguageByCode(code));
    }
    
    // Create a new language (admin only)
    @PostMapping
    public ResponseEntity<LanguageDTO> createLanguage(@Valid @RequestBody LanguageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(languageService.createLanguage(request));
    }
    
    // Update a language (admin only)
    @PutMapping("/{id}")
    public ResponseEntity<LanguageDTO> updateLanguage(
        @PathVariable UUID id,
        @Valid @RequestBody LanguageRequest request
    ) {
        return ResponseEntity.ok(languageService.updateLanguage(id, request));
    }
    
    // Toggle language enabled/disabled (admin only)
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<LanguageDTO> toggleLanguage(@PathVariable UUID id) {
        return ResponseEntity.ok(languageService.toggleLanguage(id));
    }
    
    // Set language as default (admin only)
    @PatchMapping("/{id}/set-default")
    public ResponseEntity<LanguageDTO> setAsDefault(@PathVariable UUID id) {
        return ResponseEntity.ok(languageService.setAsDefault(id));
    }
    
    // Delete a language (admin only)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLanguage(@PathVariable UUID id) {
        languageService.deleteLanguage(id);
        return ResponseEntity.noContent().build();
    }
}

