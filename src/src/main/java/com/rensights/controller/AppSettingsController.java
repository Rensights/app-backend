package com.rensights.controller;

import com.rensights.service.GoogleAnalyticsSettingsService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class AppSettingsController {

    private final GoogleAnalyticsSettingsService googleAnalyticsSettingsService;

    @GetMapping("/google-analytics")
    public ResponseEntity<Map<String, String>> getGoogleAnalyticsMeasurementId() {
        return ResponseEntity.ok(
            Map.of("measurementId", googleAnalyticsSettingsService.getMeasurementId())
        );
    }
}
