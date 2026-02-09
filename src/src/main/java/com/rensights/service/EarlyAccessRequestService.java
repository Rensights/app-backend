package com.rensights.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rensights.dto.EarlyAccessRequestCreate;
import com.rensights.dto.EarlyAccessRequestDTO;
import com.rensights.model.EarlyAccessRequest;
import com.rensights.repository.EarlyAccessRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EarlyAccessRequestService {

    private final EarlyAccessRequestRepository earlyAccessRequestRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public EarlyAccessRequestDTO create(EarlyAccessRequestCreate request) {
        EarlyAccessRequest entity = EarlyAccessRequest.builder()
            .fullName(request.getFullName())
            .email(request.getEmail())
            .phone(request.getPhone())
            .location(request.getLocation())
            .experience(request.getExperience())
            .budget(request.getBudget())
            .portfolio(request.getPortfolio())
            .timeline(request.getTimeline())
            .targetRegions(request.getTargetRegions())
            .challenges(request.getChallenges())
            .valuableServices(request.getValuableServices())
            .goalsJson(writeJson(request.getGoals()))
            .propertyTypesJson(writeJson(request.getPropertyTypes()))
            .build();

        EarlyAccessRequest saved = earlyAccessRequestRepository.save(entity);
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public Page<EarlyAccessRequestDTO> list(Pageable pageable) {
        return earlyAccessRequestRepository.findAll(pageable).map(this::toDTO);
    }

    private EarlyAccessRequestDTO toDTO(EarlyAccessRequest entity) {
        return EarlyAccessRequestDTO.builder()
            .id(entity.getId().toString())
            .fullName(entity.getFullName())
            .email(entity.getEmail())
            .phone(entity.getPhone())
            .location(entity.getLocation())
            .experience(entity.getExperience())
            .budget(entity.getBudget())
            .portfolio(entity.getPortfolio())
            .timeline(entity.getTimeline())
            .goals(readJsonList(entity.getGoalsJson()))
            .propertyTypes(readJsonList(entity.getPropertyTypesJson()))
            .targetRegions(entity.getTargetRegions())
            .challenges(entity.getChallenges())
            .valuableServices(entity.getValuableServices())
            .createdAt(entity.getCreatedAt())
            .build();
    }

    private String writeJson(List<String> values) {
        if (values == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize list", e);
        }
    }

    private List<String> readJsonList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}
