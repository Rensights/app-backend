package com.rensights.controller;

import com.rensights.dto.LanguageDTO;
import com.rensights.service.LanguageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/languages")
@RequiredArgsConstructor
public class LanguageController {
    
    private final LanguageService languageService;
    
    // Get enabled languages only (public endpoint for frontend)
    @GetMapping
    public ResponseEntity<List<LanguageDTO>> getEnabledLanguages() {
        return ResponseEntity.ok(languageService.getEnabledLanguages());
    }
    
    // Get language by code (public endpoint for frontend)
    @GetMapping("/{code}")
    public ResponseEntity<LanguageDTO> getLanguageByCode(@PathVariable String code) {
        return ResponseEntity.ok(languageService.getLanguageByCode(code));
    }
}


