package com.rensights.service;

import com.rensights.dto.LanguageDTO;
import com.rensights.dto.LanguageRequest;
import com.rensights.model.Language;
import com.rensights.repository.LanguageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LanguageService {
    
    private final LanguageRepository languageRepository;
    
    @Transactional(readOnly = true)
    public List<LanguageDTO> getAllLanguages() {
        return languageRepository.findAllByOrderByNameAsc().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<LanguageDTO> getEnabledLanguages() {
        return languageRepository.findByEnabledTrueOrderByNameAsc().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public LanguageDTO getLanguageByCode(String code) {
        return languageRepository.findByCode(code)
            .map(this::toDTO)
            .orElseThrow(() -> new RuntimeException("Language not found: " + code));
    }
    
    @Transactional
    public LanguageDTO createLanguage(LanguageRequest request) {
        // Check if language code already exists
        if (languageRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Language with code " + request.getCode() + " already exists");
        }
        
        // If this is set as default, unset other defaults
        if (request.getIsDefault()) {
            languageRepository.findByIsDefaultTrue().ifPresent(existingDefault -> {
                existingDefault.setIsDefault(false);
                languageRepository.save(existingDefault);
            });
        }
        
        Language language = Language.builder()
            .code(request.getCode())
            .name(request.getName())
            .nativeName(request.getNativeName())
            .flag(request.getFlag())
            .enabled(request.getEnabled())
            .isDefault(request.getIsDefault())
            .build();
        
        language = languageRepository.save(language);
        return toDTO(language);
    }
    
    @Transactional
    public LanguageDTO updateLanguage(UUID id, LanguageRequest request) {
        Language language = languageRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Language not found"));
        
        // Check if code is being changed and if it conflicts with another language
        if (!language.getCode().equals(request.getCode())) {
            if (languageRepository.existsByCode(request.getCode())) {
                throw new RuntimeException("Language with code " + request.getCode() + " already exists");
            }
        }
        
        // If this is being set as default, unset other defaults
        if (request.getIsDefault() && !language.getIsDefault()) {
            languageRepository.findByIsDefaultTrue().ifPresent(existingDefault -> {
                if (!existingDefault.getId().equals(id)) {
                    existingDefault.setIsDefault(false);
                    languageRepository.save(existingDefault);
                }
            });
        }
        
        language.setCode(request.getCode());
        language.setName(request.getName());
        language.setNativeName(request.getNativeName());
        language.setFlag(request.getFlag());
        language.setEnabled(request.getEnabled());
        language.setIsDefault(request.getIsDefault());
        
        language = languageRepository.save(language);
        return toDTO(language);
    }
    
    @Transactional
    public LanguageDTO toggleLanguage(UUID id) {
        Language language = languageRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Language not found"));
        
        language.setEnabled(!language.getEnabled());
        
        // Prevent disabling the default language
        if (!language.getEnabled() && language.getIsDefault()) {
            throw new RuntimeException("Cannot disable the default language");
        }
        
        language = languageRepository.save(language);
        return toDTO(language);
    }
    
    @Transactional
    public LanguageDTO setAsDefault(UUID id) {
        Language language = languageRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Language not found"));
        
        // Ensure language is enabled before setting as default
        if (!language.getEnabled()) {
            throw new RuntimeException("Cannot set a disabled language as default");
        }
        
        // Unset other defaults
        languageRepository.findByIsDefaultTrue().ifPresent(existingDefault -> {
            if (!existingDefault.getId().equals(id)) {
                existingDefault.setIsDefault(false);
                languageRepository.save(existingDefault);
            }
        });
        
        language.setIsDefault(true);
        language = languageRepository.save(language);
        return toDTO(language);
    }
    
    @Transactional
    public void deleteLanguage(UUID id) {
        Language language = languageRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Language not found"));
        
        // Prevent deleting default language
        if (language.getIsDefault()) {
            throw new RuntimeException("Cannot delete the default language");
        }
        
        languageRepository.deleteById(id);
    }
    
    private LanguageDTO toDTO(Language language) {
        return LanguageDTO.builder()
            .id(language.getId())
            .code(language.getCode())
            .name(language.getName())
            .nativeName(language.getNativeName())
            .flag(language.getFlag())
            .enabled(language.getEnabled())
            .isDefault(language.getIsDefault())
            .createdAt(language.getCreatedAt())
            .updatedAt(language.getUpdatedAt())
            .build();
    }
}

