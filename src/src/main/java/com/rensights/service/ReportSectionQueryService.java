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

        List<ReportSection> sections = sectionRepository
            .findByLanguageCodeAndIsActiveTrueAndAccessTierInOrderByDisplayOrderAsc(languageCode, allowed);

        if (sections.isEmpty() && !"en".equalsIgnoreCase(languageCode)) {
            sections = sectionRepository
                .findByLanguageCodeAndIsActiveTrueAndAccessTierInOrderByDisplayOrderAsc("en", allowed);
        }

        return sections.stream().map(this::toSectionDTO).collect(Collectors.toList());
    }

    private ReportSectionDTO toSectionDTO(ReportSection section) {
        List<ReportDocumentDTO> docs = documentRepository
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
            .collect(Collectors.toList());

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
