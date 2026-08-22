package com.rensights.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A category as the Insights page needs it: a key to filter by, a label, and a chip colour. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleCategoryDTO {
    private String id;
    private String slug;
    private String label;
    private String color;
    private Integer sortOrder;
}
