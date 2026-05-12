package com.rensights.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class UserResponse {
    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private String userTier; // FREE, PREMIUM, ENTERPRISE
    private String customerId; // Our internal customer ID
    private String createdAt;
    /** False until budget, portfolio, goals, and registration plan are set (e.g. after Google sign-up). */
    private boolean registrationProfileComplete;
    private String phone;
    private String budget;
    private String portfolio;
    private String registrationPlan;
    private List<String> goals;
}

