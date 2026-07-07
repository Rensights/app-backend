package com.rensights.service;

import com.rensights.dto.TrackEventRequest;
import com.rensights.model.ActivityEvent;
import com.rensights.repository.ActivityEventRepository;
import com.rensights.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Records presence heartbeats and activity events for the admin customer
 * analytics dashboard. Both are best-effort: failures here must never break
 * the page/action the customer is actually trying to use.
 */
@Service
public class AnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);
    private static final int MAX_EVENTS_PER_BATCH = 50;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityEventRepository activityEventRepository;

    public void recordHeartbeat(UUID userId) {
        try {
            userRepository.updateLastSeenAt(userId, LocalDateTime.now());
        } catch (Exception e) {
            logger.warn("Failed to record heartbeat for user {}: {}", userId, e.getMessage());
        }
    }

    /** For server-side events (e.g. Stripe webhook subscription changes) where there's no HTTP request to batch. */
    public void recordEvent(UUID userId, String eventType, String metadata) {
        try {
            ActivityEvent entity = ActivityEvent.builder()
                    .userId(userId)
                    .eventType(eventType)
                    .metadata(metadata)
                    .occurredAt(LocalDateTime.now())
                    .build();
            activityEventRepository.save(entity);
        } catch (Exception e) {
            logger.warn("Failed to record activity event '{}' for user {}: {}", eventType, userId, e.getMessage());
        }
    }

    public void recordEvents(UUID userId, List<TrackEventRequest> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        // Cap batch size - this is an ingestion endpoint the client controls,
        // don't let a runaway client flood the table in one request.
        int limit = Math.min(events.size(), MAX_EVENTS_PER_BATCH);
        for (int i = 0; i < limit; i++) {
            TrackEventRequest event = events.get(i);
            if (event.getEventType() == null || event.getEventType().isBlank()) {
                continue;
            }
            try {
                ActivityEvent entity = ActivityEvent.builder()
                        .userId(userId)
                        .eventType(event.getEventType())
                        .pagePath(event.getPagePath())
                        .metadata(event.getMetadata())
                        .occurredAt(LocalDateTime.now())
                        .build();
                activityEventRepository.save(entity);
            } catch (Exception e) {
                logger.warn("Failed to record activity event '{}' for user {}: {}",
                        event.getEventType(), userId, e.getMessage());
            }
        }
    }
}
