package com.rensights.service;

import com.rensights.dto.TranslationsResponse;
import com.rensights.model.Translation;
import com.rensights.repository.TranslationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TranslationService {
    
    private final TranslationRepository translationRepository;
    
    @Transactional(readOnly = true)
    public TranslationsResponse getTranslationsByLanguageAndNamespace(String languageCode, String namespace) {
        List<Translation> translations = translationRepository.findByLanguageCodeAndNamespace(languageCode, namespace);
        
        Map<String, String> translationMap = translations.stream()
            .collect(Collectors.toMap(
                Translation::getTranslationKey,
                Translation::getTranslationValue
            ));
        
        return TranslationsResponse.builder()
            .languageCode(languageCode)
            .namespace(namespace)
            .translations(translationMap)
            .build();
    }
}
