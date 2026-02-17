package com.rensights.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportDocumentDTO {
    private String id;
    private String title;
    private String description;
    private String fileUrl;
    private Integer displayOrder;
    private String languageCode;
}
