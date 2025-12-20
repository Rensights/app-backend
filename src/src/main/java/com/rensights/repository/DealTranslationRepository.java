package com.rensights.repository;

import com.rensights.model.DealTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DealTranslationRepository extends JpaRepository<DealTranslation, UUID> {
    List<DealTranslation> findByDealIdAndLanguageCode(UUID dealId, String languageCode);
}


