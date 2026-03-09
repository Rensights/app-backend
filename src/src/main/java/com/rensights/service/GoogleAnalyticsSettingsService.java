package com.rensights.service;

import com.rensights.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleAnalyticsSettingsService {

    public static final String GA_MEASUREMENT_ID_KEY = "googleAnalytics.measurementId";

    private final AppSettingRepository appSettingRepository;

    @Transactional(readOnly = true)
    public String getMeasurementId() {
        return appSettingRepository.findById(GA_MEASUREMENT_ID_KEY)
            .map(setting -> setting.getSettingValue() == null ? "" : setting.getSettingValue())
            .orElse("");
    }
}
