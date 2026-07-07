package com.rensights.dto;

import lombok.Data;

@Data
public class TrackEventRequest {
    private String eventType;
    private String pagePath;
    private String metadata;
}
