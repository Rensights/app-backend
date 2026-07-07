package com.rensights.service;

import com.rensights.model.LoginEvent;
import com.rensights.repository.LoginEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * Records login events for the admin customer-analytics dashboard (DAU/MAU,
 * per-customer login history). Best-effort: a failure here must never block
 * an actual login.
 */
@Service
public class LoginEventService {

    private static final Logger logger = LoggerFactory.getLogger(LoginEventService.class);

    @Autowired
    private LoginEventRepository loginEventRepository;

    public void recordLogin(UUID userId, HttpServletRequest request) {
        try {
            LoginEvent event = LoginEvent.builder()
                    .userId(userId)
                    .loggedInAt(LocalDateTime.now())
                    .ipAddress(getClientIpAddress(request))
                    .build();
            loginEventRepository.save(event);
        } catch (Exception e) {
            logger.warn("Failed to record login event for user {}: {}", userId, e.getMessage());
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .filter(header -> !header.isEmpty())
                .map(header -> Arrays.stream(header.split(","))
                        .findFirst()
                        .map(String::trim)
                        .orElse(header.trim()))
                .orElseGet(request::getRemoteAddr);
    }
}
