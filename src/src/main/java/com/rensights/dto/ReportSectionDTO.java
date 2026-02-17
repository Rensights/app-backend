package com.rensights.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportSectionDTO {
    private String id;
    private String sectionKey;
    private String title;
    private String navTitle;
    private String description;
    private String accessTier;
    private Integer displayOrder;
    private String languageCode;
    private List<ReportDocumentDTO> documents;
}
