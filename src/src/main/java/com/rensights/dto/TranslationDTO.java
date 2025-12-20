package com.rensights.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranslationDTO {
    private UUID id;
    private String languageCode;
    private String namespace;
    private String translationKey;
    private String translationValue;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}



