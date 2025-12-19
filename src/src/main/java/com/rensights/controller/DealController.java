package com.rensights.controller;

import com.rensights.model.Deal;
import com.rensights.model.User;
import com.rensights.model.UserTier;
import com.rensights.repository.DealRepository;
import com.rensights.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/deals")
public class DealController {
    
    private static final Logger logger = LoggerFactory.getLogger(DealController.class);
    
    @Autowired
    private DealRepository dealRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * Check if user has access to deals (not FREE tier)
     */
    private ResponseEntity<?> checkUserAccess(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required to access deals."));
        }
        
        try {
            UUID userId = UUID.fromString(authentication.getName());
            User user = userRepository.findById(userId).orElse(null);
            
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not found."));
            }
            
            if (user.getUserTier() == UserTier.FREE) {
                logger.warn("Free tier user {} attempted to access deals", user.getEmail());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Deals are only available for Standard Package and above. Please upgrade your account."));
            }
            
            return null; // Access granted
        } catch (Exception e) {
            logger.error("Error checking user access: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to verify user access."));
        }
    }
    
    /**
     * Get approved deals for app-frontend
     */
    @GetMapping
    public ResponseEntity<?> getApprovedDeals(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String bedroomCount,
            @RequestParam(required = false) String buildingStatus) {
        // Check if user has access (not FREE tier)
        ResponseEntity<?> accessCheck = checkUserAccess(authentication);
        if (accessCheck != null) {
            return accessCheck;
        }
        
        try {
            Sort sort = Sort.by("createdAt").descending();
            Pageable pageable = PageRequest.of(page, size, sort);
            
            Page<Deal> deals;
            
            if (city != null && !city.isEmpty()) {
                Deal.BuildingStatus statusEnum = null;
                if (buildingStatus != null && !buildingStatus.isEmpty()) {
                    try {
                        statusEnum = Deal.BuildingStatus.valueOf(buildingStatus.toUpperCase().replace("-", "_"));
                    } catch (IllegalArgumentException e) {
                        // Invalid status, ignore
                    }
                }
                // Normalize city: handle "dubai" -> "Dubai" and "abudhabi" -> "Abu Dhabi"
                String normalizedCity;
                String cityLower = city.toLowerCase().trim();
                if (cityLower.equals("dubai")) {
                    normalizedCity = "Dubai";
                } else if (cityLower.equals("abudhabi") || cityLower.equals("abu dhabi")) {
                    normalizedCity = "Abu Dhabi";
                } else {
                    // Fallback: capitalize first letter
                    normalizedCity = city.length() > 0 
                        ? city.substring(0, 1).toUpperCase() + (city.length() > 1 ? city.substring(1).toLowerCase() : "")
                        : city;
                }
                deals = dealRepository.findApprovedDealsWithFilters(normalizedCity, area, bedroomCount, statusEnum, pageable);
            } else {
                // Only return approved AND active deals
                deals = dealRepository.findApprovedAndActiveDeals(pageable);
            }
            
            Page<Map<String, Object>> dealDTOs = deals.map(deal -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id", deal.getId().toString());
                dto.put("name", deal.getName());
                dto.put("location", deal.getLocation());
                dto.put("city", deal.getCity());
                dto.put("area", deal.getArea());
                dto.put("bedrooms", deal.getBedrooms());
                dto.put("bedroomCount", deal.getBedroomCount());
                dto.put("size", deal.getSize());
                dto.put("listedPrice", deal.getListedPrice());
                dto.put("priceValue", deal.getPriceValue());
                dto.put("estimateRange", deal.getEstimateRange());
                dto.put("discount", deal.getDiscount());
                dto.put("rentalYield", deal.getRentalYield());
                dto.put("buildingStatus", deal.getBuildingStatus().name().toLowerCase().replace("_", "-"));
                return dto;
            });
            
            Map<String, Object> response = new HashMap<>();
            response.put("content", dealDTOs.getContent());
            response.put("totalElements", dealDTOs.getTotalElements());
            response.put("totalPages", dealDTOs.getTotalPages());
            response.put("size", dealDTOs.getSize());
            response.put("number", dealDTOs.getNumber());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching approved deals: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch deals. Please try again later."));
        }
    }
    
    /**
     * Get deal by ID
     */
    @GetMapping("/{dealId}")
    public ResponseEntity<?> getDealById(
            Authentication authentication,
            @PathVariable UUID dealId) {
        // Check if user has access (not FREE tier)
        ResponseEntity<?> accessCheck = checkUserAccess(authentication);
        if (accessCheck != null) {
            return accessCheck;
        }
        
        try {
            Deal deal = dealRepository.findById(dealId)
                    .orElseThrow(() -> new RuntimeException("Deal not found"));
            
            if (deal.getStatus() != Deal.DealStatus.APPROVED || !deal.getActive()) {
                return ResponseEntity.status(404).body(Map.of("error", "Deal not found"));
            }
            
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", deal.getId().toString());
            dto.put("name", deal.getName());
            dto.put("location", deal.getLocation());
            dto.put("city", deal.getCity());
            dto.put("area", deal.getArea());
            dto.put("bedrooms", deal.getBedrooms());
            dto.put("bedroomCount", deal.getBedroomCount());
            dto.put("size", deal.getSize());
            dto.put("listedPrice", deal.getListedPrice());
            dto.put("priceValue", deal.getPriceValue());
            dto.put("estimateMin", deal.getEstimateMin());
            dto.put("estimateMax", deal.getEstimateMax());
            dto.put("estimateRange", deal.getEstimateRange());
            dto.put("discount", deal.getDiscount());
            dto.put("rentalYield", deal.getRentalYield());
            dto.put("buildingStatus", deal.getBuildingStatus().name().toLowerCase().replace("_", "-"));
            
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            logger.error("Error fetching deal: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch deal. Please try again later."));
        }
    }
}

