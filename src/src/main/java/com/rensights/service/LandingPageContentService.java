package com.rensights.service;

import com.rensights.dto.LandingPageSectionDTO;
import com.rensights.model.LandingPageContent;
import com.rensights.repository.LandingPageContentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LandingPageContentService {
    
    private final LandingPageContentRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Transactional(readOnly = true)
    public LandingPageSectionDTO getSectionContent(String section, String languageCode) {
        List<LandingPageContent> contents = repository
            .findBySectionAndLanguageCodeAndIsActiveTrueOrderByDisplayOrderAsc(section, languageCode);
        
        Map<String, Object> contentMap = new HashMap<>();
        for (LandingPageContent content : contents) {
            Object value = parseContentValue(content.getContentValue(), content.getContentType());
            contentMap.put(content.getFieldKey(), value);
        }
        
        return LandingPageSectionDTO.builder()
            .section(section)
            .languageCode(languageCode)
            .content(contentMap)
            .build();
    }
    
    @Transactional(readOnly = true)
    public Map<String, LandingPageSectionDTO> getAllSections(String languageCode) {
        List<String> sections = List.of("hero", "why-invest", "solutions", "how-it-works", "pricing", "footer");
        Map<String, LandingPageSectionDTO> result = new HashMap<>();
        
        for (String section : sections) {
            result.put(section, getSectionContent(section, languageCode));
        }
        
        return result;
    }
    
    private Object parseContentValue(String contentValue, String contentType) {
        if (contentValue == null) {
            return null;
        }
        
        switch (contentType.toLowerCase()) {
            case "json":
                try {
                    return objectMapper.readValue(contentValue, new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    return contentValue;
                }
            case "image":
            case "video":
            case "text":
            default:
                return contentValue;
        }
    }
}



