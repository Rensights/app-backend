package com.rensights.service;

import com.rensights.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WeeklyDealsSettingsService {

    public static final String WEEKLY_DEALS_ENABLED_KEY = "weeklyDeals.enabled";

    private final AppSettingRepository appSettingRepository;

    @Transactional(readOnly = true)
    public boolean isWeeklyDealsEnabled() {
        return appSettingRepository.findById(WEEKLY_DEALS_ENABLED_KEY)
            .map(setting -> Boolean.parseBoolean(setting.getSettingValue()))
            .orElse(true);
    }
}
