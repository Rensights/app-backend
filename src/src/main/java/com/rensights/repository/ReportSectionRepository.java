package com.rensights.repository;

import com.rensights.model.ReportSection;
import com.rensights.model.UserTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportSectionRepository extends JpaRepository<ReportSection, UUID> {
    List<ReportSection> findByLanguageCodeAndIsActiveTrueOrderByDisplayOrderAsc(String languageCode);
    List<ReportSection> findByLanguageCodeAndIsActiveTrueAndAccessTierInOrderByDisplayOrderAsc(String languageCode, List<UserTier> accessTiers);
}
