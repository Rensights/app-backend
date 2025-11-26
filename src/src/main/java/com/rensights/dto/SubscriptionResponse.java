package com.rensights.dto;

import com.rensights.model.SubscriptionStatus;
import com.rensights.model.UserTier;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SubscriptionResponse {
    private String id;
    private UserTier planType;
    private SubscriptionStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime createdAt;
}

