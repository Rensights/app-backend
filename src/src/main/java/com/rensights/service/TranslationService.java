package com.rensights.service;

import com.rensights.dto.TranslationDTO;
import com.rensights.dto.TranslationRequest;
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
    public List<TranslationDTO> getAllTranslations() {
        return translationRepository.findAll().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<TranslationDTO> getTranslationsByLanguage(String languageCode) {
        return translationRepository.findByLanguageCode(languageCode).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
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
    
    @Transactional(readOnly = true)
    public TranslationDTO getTranslation(String languageCode, String namespace, String translationKey) {
        return translationRepository
            .findByLanguageCodeAndNamespaceAndTranslationKey(languageCode, namespace, translationKey)
            .map(this::toDTO)
            .orElseThrow(() -> new RuntimeException(
                String.format("Translation not found: %s/%s/%s", languageCode, namespace, translationKey)
            ));
    }
    
    @Transactional
    public TranslationDTO createTranslation(TranslationRequest request) {
        // Check if translation already exists
        if (translationRepository.existsByLanguageCodeAndNamespaceAndTranslationKey(
            request.getLanguageCode(), request.getNamespace(), request.getTranslationKey())) {
            throw new RuntimeException("Translation already exists");
        }
        
        Translation translation = Translation.builder()
            .languageCode(request.getLanguageCode())
            .namespace(request.getNamespace())
            .translationKey(request.getTranslationKey())
            .translationValue(request.getTranslationValue())
            .description(request.getDescription())
            .build();
        
        translation = translationRepository.save(translation);
        return toDTO(translation);
    }
    
    @Transactional
    public TranslationDTO updateTranslation(UUID id, TranslationRequest request) {
        Translation translation = translationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Translation not found"));
        
        // Check if updating to a different key that already exists
        if (!translation.getLanguageCode().equals(request.getLanguageCode()) ||
            !translation.getNamespace().equals(request.getNamespace()) ||
            !translation.getTranslationKey().equals(request.getTranslationKey())) {
            
            if (translationRepository.existsByLanguageCodeAndNamespaceAndTranslationKey(
                request.getLanguageCode(), request.getNamespace(), request.getTranslationKey())) {
                throw new RuntimeException("Translation with this key already exists");
            }
        }
        
        translation.setLanguageCode(request.getLanguageCode());
        translation.setNamespace(request.getNamespace());
        translation.setTranslationKey(request.getTranslationKey());
        translation.setTranslationValue(request.getTranslationValue());
        translation.setDescription(request.getDescription());
        
        translation = translationRepository.save(translation);
        return toDTO(translation);
    }
    
    @Transactional
    public void deleteTranslation(UUID id) {
        if (!translationRepository.existsById(id)) {
            throw new RuntimeException("Translation not found");
        }
        translationRepository.deleteById(id);
    }
    
    @Transactional(readOnly = true)
    public List<String> getAvailableLanguages() {
        return translationRepository.findDistinctLanguageCode();
    }
    
    @Transactional(readOnly = true)
    public List<String> getNamespaces(String languageCode) {
        return translationRepository.findDistinctNamespaceByLanguageCode(languageCode);
    }
    
    private TranslationDTO toDTO(Translation translation) {
        return TranslationDTO.builder()
            .id(translation.getId())
            .languageCode(translation.getLanguageCode())
            .namespace(translation.getNamespace())
            .translationKey(translation.getTranslationKey())
            .translationValue(translation.getTranslationValue())
            .description(translation.getDescription())
            .createdAt(translation.getCreatedAt())
            .updatedAt(translation.getUpdatedAt())
            .build();
    }
}

