package com.rensights.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LanguageRequest {
    @NotBlank(message = "Language code is required")
    private String code;
    
    @NotBlank(message = "Language name is required")
    private String name;
    
    private String nativeName;
    
    private String flag;
    
    @Builder.Default
    private Boolean enabled = true;
    
    @Builder.Default
    private Boolean isDefault = false;
}

