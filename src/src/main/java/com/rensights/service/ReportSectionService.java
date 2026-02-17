package com.rensights.service;

import com.rensights.dto.ReportDocumentDTO;
import com.rensights.dto.ReportSectionDTO;
import com.rensights.model.ReportDocument;
import com.rensights.model.ReportSection;
import com.rensights.model.User;
import com.rensights.model.UserTier;
import com.rensights.repository.ReportDocumentRepository;
import com.rensights.repository.ReportSectionRepository;
import com.rensights.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportSectionService {

    private final ReportSectionRepository sectionRepository;
    private final ReportDocumentRepository documentRepository;
    private final UserRepository userRepository;

    public List<ReportSectionDTO> getSectionsForUser(String languageCode, UUID userId) {
        UserTier tier = UserTier.FREE;
        if (userId != null) {
            Optional<User> user = userRepository.findById(userId);
            if (user.isPresent() && user.get().getUserTier() != null) {
                tier = user.get().getUserTier();
            }
        }

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
