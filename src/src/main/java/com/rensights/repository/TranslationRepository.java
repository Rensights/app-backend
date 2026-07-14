package com.rensights.repository;

import com.rensights.model.Translation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TranslationRepository extends JpaRepository<Translation, UUID> {
    
    // Find all translations for a specific language
    List<Translation> findByLanguageCode(String languageCode);
    
    // Find translations by language and namespace
    List<Translation> findByLanguageCodeAndNamespace(String languageCode, String namespace);
    
    // Find a specific translation
    Optional<Translation> findByLanguageCodeAndNamespaceAndTranslationKey(
        String languageCode, String namespace, String translationKey
    );
    
    // Check if translation exists
    boolean existsByLanguageCodeAndNamespaceAndTranslationKey(
        String languageCode, String namespace, String translationKey
    );

    // Latest updated_at for a namespace. The admin save path bumps updated_at on
    // every change, so this is the real "last updated" date for content pages
    // (e.g. privacy-terms).
    @Query("SELECT MAX(t.updatedAt) FROM Translation t " +
           "WHERE t.languageCode = :languageCode AND t.namespace = :namespace")
    LocalDateTime findLatestUpdatedAt(
        @Param("languageCode") String languageCode,
        @Param("namespace") String namespace
    );
}

