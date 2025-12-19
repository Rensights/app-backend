package com.rensights.repository;

import com.rensights.model.Translation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
    
    // Find all languages available
    List<String> findDistinctLanguageCode();
    
    // Find all namespaces for a language
    List<String> findDistinctNamespaceByLanguageCode(String languageCode);
    
    // Check if translation exists
    boolean existsByLanguageCodeAndNamespaceAndTranslationKey(
        String languageCode, String namespace, String translationKey
    );
}

