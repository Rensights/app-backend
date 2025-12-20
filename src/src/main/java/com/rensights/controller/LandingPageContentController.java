package com.rensights.controller;

import com.rensights.dto.LandingPageSectionDTO;
import com.rensights.service.LandingPageContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/landing-page")
@RequiredArgsConstructor
public class LandingPageContentController {
    
    private final LandingPageContentService landingPageContentService;
    
    @GetMapping("/section/{section}")
    public ResponseEntity<LandingPageSectionDTO> getSectionContent(
        @PathVariable String section,
        @RequestParam(defaultValue = "en") String language
    ) {
        return ResponseEntity.ok(landingPageContentService.getSectionContent(section, language));
    }
    
    @GetMapping
    public ResponseEntity<Map<String, LandingPageSectionDTO>> getAllSections(
        @RequestParam(defaultValue = "en") String language
    ) {
        return ResponseEntity.ok(landingPageContentService.getAllSections(language));
    }
}



