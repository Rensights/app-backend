package com.rensights.service;

import com.rensights.dto.ReportDocumentDTO;
import com.rensights.dto.ReportSectionDTO;
import com.rensights.model.ReportSection;
import com.rensights.model.UserTier;
import com.rensights.repository.ReportDocumentRepository;
import com.rensights.repository.ReportSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Holds the tier-scoped report-section query + DTO mapping.
 *
 * <p>Extracted into its own bean so {@link ReportSectionService#getSectionsForUser} can invoke the
 * cached {@link #getSectionsForTier} through a real Spring proxy. If this method lived in the same
 * class as {@code getSectionsForUser}, the internal {@code this.} call would bypass the caching
 * proxy entirely.
 *
 * <p>The cache key is {@code languageCode + ':' + tier.name()} — deliberately NOT the userId. The
 * result depends only on (language, resolved tier), so keying on tier keeps a FREE request pinned
 * to the FREE entry and makes it impossible to serve PREMIUM/ENTERPRISE sections to a FREE user
 * from a warm cache. The live userId→tier resolution stays uncached in {@code getSectionsForUser},
 * so a tier change takes effect immediately (the very next request keys into a different tier
 * bucket).
 */
@Service
@RequiredArgsConstructor
public class ReportSectionQueryService {

    private final ReportSectionRepository sectionRepository;
    private final ReportDocumentRepository documentRepository;

    @Cacheable(cacheNames = "reportSections", key = "#languageCode + ':' + #tier.name()")
    public List<ReportSectionDTO> getSectionsForTier(String languageCode, UserTier tier) {
        List<UserTier> allowed = new ArrayList<>();
        allowed.add(UserTier.FREE);
        if (tier == UserTier.PREMIUM || tier == UserTier.ENTERPRISE) {
            allowed.add(UserTier.PREMIUM);
        }
        if (tier == UserTier.ENTERPRISE) {
            allowed.add(UserTier.ENTERPRISE);
        }

        // Return ALL active sections so the client can render the paid ones behind an
        // upgrade shadow. Sections the caller's tier can't access are returned as locked
        // stubs (metadata only, no documents) so no file URLs/ids leak to the browser.
        List<ReportSection> sections = sectionRepository
            .findByLanguageCodeAndIsActiveTrueOrderByDisplayOrderAsc(languageCode);

        if (sections.isEmpty() && !"en".equalsIgnoreCase(languageCode)) {
            sections = sectionRepository
                .findByLanguageCodeAndIsActiveTrueOrderByDisplayOrderAsc("en");
        }

        return sections.stream()
            // Enterprise sections stay fully hidden until the caller is Enterprise —
            // not even a locked stub. Premium sections are still returned (stubbed) so
            // lower tiers see the upgrade shadow.
            .filter(section -> section.getAccessTier() != UserTier.ENTERPRISE
                || tier == UserTier.ENTERPRISE)
            .map(section -> toSectionDTO(section, allowed.contains(section.getAccessTier())))
            .collect(Collectors.toList());
    }

    private ReportSectionDTO toSectionDTO(ReportSection section, boolean unlocked) {
        // Locked sections expose no documents at all: no file ids/urls reach the client,
        // so the ungated /documents/{id}/file endpoint can't be hit for premium content.
        List<ReportDocumentDTO> docs = unlocked
            ? documentRepository
                .findBySectionIdAndIsActiveTrueOrderByDisplayOrderAsc(section.getId())
                .stream()
                .map(doc -> ReportDocumentDTO.builder()
                    .id(doc.getId().toString())
                    .title(doc.getTitle())
                    .description(doc.getDescription())
                    .fileUrl("/api/reports/documents/" + doc.getId() + "/file")
                    .displayOrder(doc.getDisplayOrder())
                    .languageCode(doc.getLanguageCode())
                    .updatedAt(doc.getUpdatedAt())
                    .build())
                .collect(Collectors.toList())
            : List.of();

        return ReportSectionDTO.builder()
            .id(section.getId().toString())
            .sectionKey(section.getSectionKey())
            .title(section.getTitle())
            .navTitle(section.getNavTitle())
            .description(section.getDescription())
            .accessTier(section.getAccessTier().name())
            .displayOrder(section.getDisplayOrder())
            .languageCode(section.getLanguageCode())
            .documents(docs)
            .build();
    }
}
