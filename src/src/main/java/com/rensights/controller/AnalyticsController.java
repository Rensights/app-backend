package com.rensights.controller;

import com.rensights.dto.TrackEventRequest;
import com.rensights.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Presence heartbeat + activity-event ingestion for the customer used by
 * app-frontend. Not under /api/auth/** on purpose - that prefix is permitAll,
 * and both of these endpoints must be authenticated (they need to know which
 * user to attribute the event to).
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsController.class);

    @Autowired
    private AnalyticsService analyticsService;

    @PostMapping("/heartbeat")
    public ResponseEntity<?> heartbeat() {
        try {
            analyticsService.recordHeartbeat(getCurrentUserId());
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
            logger.warn("Heartbeat failed: {}", e.getMessage());
            // Best-effort - never let a heartbeat failure surface as an error to the user.
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }
    }

    @PostMapping("/events")
    public ResponseEntity<?> trackEvents(@RequestBody List<TrackEventRequest> events) {
        try {
            analyticsService.recordEvents(getCurrentUserId(), events);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
            logger.warn("Event tracking failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("User not authenticated");
        }
        return UUID.fromString(authentication.getName());
    }
}
