package com.rensights.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDTO {
    private String id;
    private String title;
    private String slug;
    private String excerpt;
    private String content;
    private String coverImage;
    private LocalDateTime publishedAt;
    private boolean isActive;
}
