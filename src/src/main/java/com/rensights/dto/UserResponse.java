package com.rensights.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

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
}

