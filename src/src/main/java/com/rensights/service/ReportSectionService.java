package com.rensights.service;

import com.rensights.dto.ReportSectionDTO;
import com.rensights.model.User;
import com.rensights.model.UserTier;
import com.rensights.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportSectionService {

    private final UserRepository userRepository;
    private final ReportSectionQueryService reportSectionQueryService;

    /**
     * Resolves the caller's live tier from the database (UNCACHED, so a tier change takes effect
     * immediately) and delegates the actual content lookup to the tier-keyed cache in
     * {@link ReportSectionQueryService}. The result is identical to the previous single-method
     * implementation for any given (languageCode, tier).
     */
    public List<ReportSectionDTO> getSectionsForUser(String languageCode, UUID userId) {
        UserTier tier = UserTier.FREE;
        if (userId != null) {
            Optional<User> user = userRepository.findById(userId);
            if (user.isPresent() && user.get().getUserTier() != null) {
                tier = user.get().getUserTier();
            }
        }

        return reportSectionQueryService.getSectionsForTier(languageCode, tier);
    }
}
