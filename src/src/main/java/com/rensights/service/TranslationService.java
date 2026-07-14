package com.rensights.service;

import com.rensights.dto.TranslationsResponse;
import com.rensights.model.Translation;
import com.rensights.repository.TranslationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TranslationService {

    private final TranslationRepository translationRepository;

    @Cacheable(cacheNames = "translations", key = "#languageCode + ':' + #namespace")
    @Transactional(readOnly = true)
    public TranslationsResponse getTranslationsByLanguageAndNamespace(String languageCode, String namespace) {
        List<Translation> translations = translationRepository.findByLanguageCodeAndNamespace(languageCode, namespace);
        
        Map<String, String> translationMap = translations.stream()
            .collect(Collectors.toMap(
                Translation::getTranslationKey,
                Translation::getTranslationValue
            ));

        // Real "last updated" = the updated_at column, bumped by the admin save
        // path on every change (read straight from the DB).
        LocalDateTime latestUpdatedAt = translationRepository.findLatestUpdatedAt(languageCode, namespace);

        return TranslationsResponse.builder()
            .languageCode(languageCode)
            .namespace(namespace)
            .translations(translationMap)
            .updatedAt(latestUpdatedAt != null ? latestUpdatedAt.toString() : null)
            .build();
    }
}
