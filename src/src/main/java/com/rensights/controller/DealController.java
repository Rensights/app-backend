package com.rensights.controller;

import com.rensights.model.Deal;
import com.rensights.model.User;
import com.rensights.model.UserTier;
import com.rensights.repository.DealRepository;
import com.rensights.repository.UserRepository;
import com.rensights.service.DealsFetchService;
import com.rensights.service.WeeklyDealsSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/deals")
public class DealController {
    
    private static final Logger logger = LoggerFactory.getLogger(DealController.class);
    
    private final DealRepository dealRepository;
    private final UserRepository userRepository;
    private final WeeklyDealsSettingsService weeklyDealsSettingsService;
    private final DealsFetchService dealsFetchService;

    // Constructor injection (better performance and testability)
    public DealController(DealRepository dealRepository, UserRepository userRepository, WeeklyDealsSettingsService weeklyDealsSettingsService, DealsFetchService dealsFetchService) {
        this.dealRepository = dealRepository;
        this.userRepository = userRepository;
        this.weeklyDealsSettingsService = weeklyDealsSettingsService;
        this.dealsFetchService = dealsFetchService;
    }

    private ResponseEntity<?> checkWeeklyDealsEnabled() {
        if (!weeklyDealsSettingsService.isWeeklyDealsEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return null;
    }

    @GetMapping("/enabled")
    public ResponseEntity<?> getWeeklyDealsEnabled() {
        return ResponseEntity.ok(Map.of("enabled", weeklyDealsSettingsService.isWeeklyDealsEnabled()));
    }
    
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
    // @GetMapping
    // public ResponseEntity<?> getApprovedDeals(
    //         Authentication authentication,
    //         @RequestParam(defaultValue = "0") int page,
    //         @RequestParam(defaultValue = "20") int size,
    //         @RequestParam(required = false) String city,
    //         @RequestParam(required = false) String area,
    //         @RequestParam(required = false) String bedroomCount,
    //         @RequestParam(required = false) String buildingStatus) {
    //     // Check if user has access (not FREE tier)
    //     ResponseEntity<?> accessCheck = checkUserAccess(authentication);
    //     if (accessCheck != null) {
    //         return accessCheck;
    //     }
        
    //     try {
    //         Sort sort = Sort.by("createdAt").descending();
    //         Pageable pageable = PageRequest.of(page, size, sort);
            
    //         Page<Deal> deals;
            
    //         if (city != null && !city.isEmpty()) {
    //             Deal.BuildingStatus statusEnum = null;
    //             if (buildingStatus != null && !buildingStatus.isEmpty()) {
    //                 try {
    //                     statusEnum = Deal.BuildingStatus.valueOf(buildingStatus.toUpperCase().replace("-", "_"));
    //                 } catch (IllegalArgumentException e) {
    //                     // Invalid status, ignore
    //                 }
    //             }
    //             // Normalize city: handle "dubai" -> "Dubai" and "abudhabi" -> "Abu Dhabi"
    //             String normalizedCity;
    //             String cityLower = city.toLowerCase().trim();
    //             if (cityLower.equals("dubai")) {
    //                 normalizedCity = "Dubai";
    //             } else if (cityLower.equals("abudhabi") || cityLower.equals("abu dhabi")) {
    //                 normalizedCity = "Abu Dhabi";
    //             } else {
    //                 // Fallback: capitalize first letter
    //                 normalizedCity = city.length() > 0 
    //                     ? city.substring(0, 1).toUpperCase() + (city.length() > 1 ? city.substring(1).toLowerCase() : "")
    //                     : city;
    //             }
    //             deals = dealRepository.findApprovedDealsWithFilters(normalizedCity, area, bedroomCount, statusEnum, pageable);
    //         } else {
    //             // Only return approved AND active deals
    //             deals = dealRepository.findApprovedAndActiveDeals(pageable);
    //         }
            
    //         Page<Map<String, Object>> dealDTOs = deals.map(deal -> {
    //             Map<String, Object> dto = new HashMap<>();
    //             dto.put("id", deal.getId().toString());
    //             dto.put("name", deal.getName()); // Building name
    //             dto.put("location", deal.getLocation());
    //             dto.put("city", deal.getCity());
    //             dto.put("area", deal.getArea());
    //             dto.put("bedrooms", deal.getBedrooms());
    //             dto.put("bedroomCount", deal.getBedroomCount());
    //             dto.put("size", deal.getSize()); // Size, sqft
    //             dto.put("listedPrice", deal.getListedPrice()); // Listed price, AED
    //             dto.put("priceValue", deal.getPriceValue());
    //             dto.put("estimateMin", deal.getEstimateMin()); // Our price estimate (min)
    //             dto.put("estimateMax", deal.getEstimateMax()); // Our price estimate (max)
    //             dto.put("estimateRange", deal.getEstimateRange());
    //             dto.put("discount", deal.getDiscount()); // Potential savings range
    //             dto.put("rentalYield", deal.getRentalYield());
    //             dto.put("grossRentalYield", deal.getGrossRentalYield()); // Gross rental yield
    //             dto.put("buildingStatus", deal.getBuildingStatus().name().toLowerCase().replace("_", "-")); // Building status
    //             dto.put("propertyType", deal.getPropertyType()); // Property type
    //             dto.put("priceVsEstimations", deal.getPriceVsEstimations()); // Price vs. Estimations
    //             dto.put("pricePerSqft", deal.getPricePerSqft()); // Price per sqft
    //             dto.put("pricePerSqftVsMarket", deal.getPricePerSqftVsMarket()); // Price per sqft (vs. market)
    //             dto.put("propertyDescription", deal.getPropertyDescription()); // Property description
    //             dto.put("buildingFeatures", deal.getBuildingFeatures()); // Building features
    //             dto.put("serviceCharge", deal.getServiceCharge()); // Service charge
    //             dto.put("developer", deal.getDeveloper()); // Developer
    //             dto.put("propertyLink", deal.getPropertyLink()); // Link for the property
    //             dto.put("propertyId", deal.getPropertyId()); // Property id
    //             return dto;
    //         });
            
    //         Map<String, Object> response = new HashMap<>();
    //         response.put("content", dealDTOs.getContent());
    //         response.put("totalElements", dealDTOs.getTotalElements());
    //         response.put("totalPages", dealDTOs.getTotalPages());
    //         response.put("size", dealDTOs.getSize());
    //         response.put("number", dealDTOs.getNumber());
            
    //         return ResponseEntity.ok(response);
    //     } catch (Exception e) {
    //         logger.error("Error fetching approved deals: {}", e.getMessage(), e);
    //         return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch deals. Please try again later."));
    //     }
    // }


    @GetMapping
    public ResponseEntity<?> getApprovedDeals(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String bedroomCount,
            @RequestParam(required = false) String buildingStatus) {
        
        ResponseEntity<?> enabledCheck = checkWeeklyDealsEnabled();
        if (enabledCheck != null) {
            return enabledCheck;
        }

        // Check if user has access (not FREE tier)
        ResponseEntity<?> accessCheck = checkUserAccess(authentication);
        if (accessCheck != null) {
            return accessCheck;
        }
        
        try {
            // Fetch + normalize from third-party API (cached; per-request filter/paginate below).
            List<Map<String, Object>> allDeals;
            try {
                allDeals = dealsFetchService.getAllDeals();
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                logger.error("Error fetching deals from API: {}", e.getMessage());
                return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("error", "Failed to fetch deals from external API: " + e.getMessage()));
            } catch (Exception e) {
                logger.error("Unexpected error calling API: {}", e.getMessage(), e);
                return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to connect to deals API"));
            }

            // Apply filters
            List<Map<String, Object>> filteredDeals = allDeals.stream()
                .filter(deal -> {
                    // City filter (normalize)
                    if (city != null && !city.isEmpty()) {
                        String normalizedCity = normalizeCity(city);
                        if (!deal.get("city").toString().equalsIgnoreCase(normalizedCity)) {
                            return false;
                        }
                    }
                    
                    // Area filter
                    if (area != null && !area.isEmpty()) {
                        if (!deal.get("area").toString().equalsIgnoreCase(area)) {
                            return false;
                        }
                    }
                    
                    // Bedroom count filter
                    if (bedroomCount != null && !bedroomCount.isEmpty()) {
                        if (!deal.get("bedroomCount").toString().equals(bedroomCount)) {
                            return false;
                        }
                    }
                    
                    // Building status filter
                    if (buildingStatus != null && !buildingStatus.isEmpty()) {
                        String normalizedStatus = buildingStatus.toLowerCase().replace("-", "_");
                        String dealStatus = deal.get("buildingStatus").toString().toLowerCase().replace("-", "_");
                        if (!dealStatus.equals(normalizedStatus)) {
                            return false;
                        }
                    }
                    
                    return true;
                })
                .collect(Collectors.toList());
            
            // Apply pagination
            int totalElements = filteredDeals.size();
            int totalPages = (int) Math.ceil((double) totalElements / size);
            int fromIndex = page * size;
            int toIndex = Math.min(fromIndex + size, totalElements);
            
            List<Map<String, Object>> paginatedDeals = fromIndex < totalElements 
                ? filteredDeals.subList(fromIndex, toIndex) 
                : Collections.emptyList();
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("content", paginatedDeals);
            response.put("totalElements", totalElements);
            response.put("totalPages", totalPages);
            response.put("size", size);
            response.put("number", page);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error processing deals: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of("error", "Failed to process deals. Please try again later."));
        }
    }

    // Helper method to normalize city names
    private String normalizeCity(String city) {
        String cityLower = city.toLowerCase().trim();
        if (cityLower.equals("dubai")) {
            return "Dubai";
        } else if (cityLower.equals("abudhabi") || cityLower.equals("abu dhabi")) {
            return "Abu Dhabi";
        } else {
            return city.length() > 0 
                ? city.substring(0, 1).toUpperCase() + (city.length() > 1 ? city.substring(1).toLowerCase() : "")
                : city;
        }
    }
    
    /**
     * Get deal by ID - fetches from third-party API
     */
    @GetMapping("/{dealId}")
    public ResponseEntity<?> getDealById(
            Authentication authentication,
            @PathVariable String dealId) {
        ResponseEntity<?> enabledCheck = checkWeeklyDealsEnabled();
        if (enabledCheck != null) {
            return enabledCheck;
        }

        // Check if user has access (not FREE tier)
        ResponseEntity<?> accessCheck = checkUserAccess(authentication);
        if (accessCheck != null) {
            return accessCheck;
        }

        try {
            // Fetch + parse from third-party API (cached; null/404 never cached).
            Map<String, Object> dto;
            try {
                dto = dealsFetchService.getDealById(dealId);
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                logger.error("Error fetching deal from API: {}", e.getMessage());
                if (e.getStatusCode().value() == 404) {
                    return ResponseEntity.status(404).body(Map.of("error", "Deal not found"));
                }
                return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("error", "Failed to fetch deal from external API: " + e.getMessage()));
            } catch (Exception e) {
                logger.error("Unexpected error calling API: {}", e.getMessage(), e);
                return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to connect to deals API"));
            }

            if (dto == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Deal not found"));
            }

            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            logger.error("Error fetching deal: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch deal. Please try again later."));
        }
    }
    
    /**
     * Helper method to convert Deal to Map (for listed deals and recent sales)
     */
    private Map<String, Object> dealToMap(Deal deal) {
        Map<String, Object> dealMap = new HashMap<>();
        dealMap.put("id", deal.getId().toString());
        dealMap.put("name", deal.getName());
        dealMap.put("location", deal.getLocation());
        dealMap.put("city", deal.getCity());
        dealMap.put("area", deal.getArea());
        dealMap.put("bedrooms", deal.getBedrooms());
        dealMap.put("bedroomCount", deal.getBedroomCount());
        dealMap.put("size", deal.getSize());
        dealMap.put("listedPrice", deal.getListedPrice());
        dealMap.put("priceValue", deal.getPriceValue());
        dealMap.put("estimateMin", deal.getEstimateMin());
        dealMap.put("estimateMax", deal.getEstimateMax());
        dealMap.put("estimateRange", deal.getEstimateRange());
        dealMap.put("discount", deal.getDiscount());
        dealMap.put("rentalYield", deal.getRentalYield());
        dealMap.put("grossRentalYield", deal.getGrossRentalYield());
        dealMap.put("buildingStatus", deal.getBuildingStatus() != null ? deal.getBuildingStatus().name().toLowerCase().replace("_", "-") : null);
        dealMap.put("propertyType", deal.getPropertyType());
        dealMap.put("priceVsEstimations", deal.getPriceVsEstimations());
        dealMap.put("pricePerSqft", deal.getPricePerSqft());
        dealMap.put("pricePerSqftVsMarket", deal.getPricePerSqftVsMarket());
        dealMap.put("propertyDescription", deal.getPropertyDescription());
        dealMap.put("buildingFeatures", deal.getBuildingFeatures());
        dealMap.put("serviceCharge", deal.getServiceCharge());
        dealMap.put("developer", deal.getDeveloper());
        dealMap.put("propertyLink", deal.getPropertyLink());
        dealMap.put("propertyId", deal.getPropertyId());
        return dealMap;
    }
}
