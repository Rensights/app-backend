package com.rensights.service;

import com.rensights.dto.LanguageDTO;
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


