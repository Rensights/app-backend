package com.rensights.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArticleRequest {
    private String title;
    private String slug;
    private String excerpt;
    private String content;
    private String coverImage;
    private LocalDateTime publishedAt;
    private Boolean isActive;
}
