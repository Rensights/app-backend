package com.rensights.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class EarlyAccessRequestDTO {
    private String id;
    private String fullName;
    private String email;
    private String phone;
    private String location;
    private String experience;
    private String budget;
    private String portfolio;
    private String timeline;
    private List<String> goals;
    private List<String> propertyTypes;
    private String targetRegions;
    private String challenges;
    private String valuableServices;
    private LocalDateTime createdAt;
}
