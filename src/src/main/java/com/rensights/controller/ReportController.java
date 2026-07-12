package com.rensights.controller;

import com.rensights.dto.ReportSectionDTO;
import com.rensights.model.ReportDocument;
import com.rensights.model.User;
import com.rensights.model.UserTier;
import com.rensights.repository.ReportDocumentRepository;
import com.rensights.repository.UserRepository;
import com.rensights.service.ReportSectionService;
import com.rensights.service.ReportStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportSectionService reportSectionService;
    private final ReportDocumentRepository reportDocumentRepository;
    private final ReportStorageService reportStorageService;
    private final UserRepository userRepository;

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
    public ResponseEntity<Resource> getDocument(
        @PathVariable UUID documentId,
        Authentication authentication) {
        // Enforce the owning section's access tier so premium/enterprise PDFs can't be
        // fetched by a lower-tier (or anonymous) caller guessing a document id. This
        // mirrors the tier gate on the section listing.
        UserTier requiredTier = reportDocumentRepository.findAccessTierByDocumentId(documentId)
            .orElse(null);
        if (requiredTier == null) {
            return ResponseEntity.notFound().build();
        }
        if (!hasTierAccess(resolveUserTier(authentication), requiredTier)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ReportDocument doc = reportDocumentRepository.findById(documentId)
            .orElseThrow(() -> new RuntimeException("Document not found"));
        if (doc.getFileContentBase64() != null && !doc.getFileContentBase64().isBlank()) {
            byte[] fileBytes = java.util.Base64.getDecoder().decode(doc.getFileContentBase64());
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getOriginalFilename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(fileBytes.length)
                .body(new org.springframework.core.io.ByteArrayResource(fileBytes));
        }
        Optional<java.nio.file.Path> path = reportStorageService.resolvePath(doc.getFilePath());
        if (path.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = reportStorageService.loadAsResource(doc.getFilePath());
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getOriginalFilename() + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(resource);
    }

    private UserTier resolveUserTier(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return UserTier.FREE;
        }
        try {
            UUID userId = UUID.fromString(authentication.getName());
            return userRepository.findById(userId)
                .map(User::getUserTier)
                .orElse(UserTier.FREE);
        } catch (Exception e) {
            return UserTier.FREE;
        }
    }

    // Access granted when the caller's tier is at least the required tier
    // (FREE < PREMIUM < ENTERPRISE). Mirrors ReportSectionQueryService's allow-list.
    private boolean hasTierAccess(UserTier userTier, UserTier requiredTier) {
        if (requiredTier == UserTier.FREE) {
            return true;
        }
        if (requiredTier == UserTier.PREMIUM) {
            return userTier == UserTier.PREMIUM || userTier == UserTier.ENTERPRISE;
        }
        return userTier == UserTier.ENTERPRISE;
    }
}
