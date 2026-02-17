package com.rensights.controller;

import com.rensights.dto.ReportSectionDTO;
import com.rensights.model.ReportDocument;
import com.rensights.repository.ReportDocumentRepository;
import com.rensights.service.ReportSectionService;
import com.rensights.service.ReportStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportSectionService reportSectionService;
    private final ReportDocumentRepository reportDocumentRepository;
    private final ReportStorageService reportStorageService;

    @GetMapping("/sections")
    public ResponseEntity<List<ReportSectionDTO>> getSections(
        @RequestParam(defaultValue = "en") String lang,
        Authentication authentication) {
        UUID userId = null;
        if (authentication != null && authentication.isAuthenticated()) {
            try {
                userId = UUID.fromString(authentication.getName());
            } catch (Exception ignored) {
            }
        }
        return ResponseEntity.ok(reportSectionService.getSectionsForUser(lang, userId));
    }

    @GetMapping("/documents/{documentId}/file")
    public ResponseEntity<Resource> getDocument(@PathVariable UUID documentId) {
        ReportDocument doc = reportDocumentRepository.findById(documentId)
            .orElseThrow(() -> new RuntimeException("Document not found"));
        Resource resource = reportStorageService.loadAsResource(doc.getFilePath());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getOriginalFilename() + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(resource);
    }
}
