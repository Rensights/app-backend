package com.rensights.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleLoginRequest {

    /**
     * Google Identity Services credential (JWT).
     */
    @NotBlank
    private String credential;

    private String deviceFingerprint;
}
