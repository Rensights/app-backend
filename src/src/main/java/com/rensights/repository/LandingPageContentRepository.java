package com.rensights.repository;

import com.rensights.model.LandingPageContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LandingPageContentRepository extends JpaRepository<LandingPageContent, UUID> {
    List<LandingPageContent> findBySectionAndLanguageCodeAndIsActiveTrueOrderByDisplayOrderAsc(
        String section, String languageCode
    );
}


