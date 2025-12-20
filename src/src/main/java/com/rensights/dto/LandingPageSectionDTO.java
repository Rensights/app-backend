package com.rensights.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandingPageSectionDTO {
    private String section;
    private String languageCode;
    private Map<String, Object> content;
}



