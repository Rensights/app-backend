package com.rensights.dto;

import lombok.Data;

import java.util.List;

@Data
public class EarlyAccessRequestCreate {
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
}
