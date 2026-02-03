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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    
    // Constructor injection (better performance and testability)
    public DealController(DealRepository dealRepository, UserRepository userRepository) {
        this.dealRepository = dealRepository;
        this.userRepository = userRepository;
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
        
        // Check if user has access (not FREE tier)
        ResponseEntity<?> accessCheck = checkUserAccess(authentication);
        if (accessCheck != null) {
            return accessCheck;
        }
        
        try {
            // Fetch data from third-party API
            RestTemplate restTemplate = new RestTemplate();
            String apiUrl = "http://72.62.40.154:8000/deals";
            
            JsonNode apiResponse;
            try {
                apiResponse = restTemplate.getForObject(apiUrl, JsonNode.class);
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                logger.error("Error fetching deals from API: {}", e.getMessage());
                return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("error", "Failed to fetch deals from external API: " + e.getMessage()));
            } catch (Exception e) {
                logger.error("Unexpected error calling API: {}", e.getMessage(), e);
                return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to connect to deals API"));
            }
            
            // Check if response is null or empty
            if (apiResponse == null || !apiResponse.has("data")) {
                return ResponseEntity.ok(Map.of(
                    "content", Collections.emptyList(),
                    "totalElements", 0,
                    "totalPages", 0,
                    "size", size,
                    "number", page
                ));
            }
            
            // Parse the data array
            List<Map<String, Object>> allDeals = new ArrayList<>();
            JsonNode dataArray = apiResponse.get("data");
            
            for (JsonNode item : dataArray) {
                Map<String, Object> deal = new HashMap<>();
                
                // Extract property information
                JsonNode property = item.get("property");
                String buildingName = property != null && !property.isNull() && property.has("building_name") 
                    ? property.get("building_name").asText() : "";
                String dealArea = property != null && !property.isNull() && property.has("area") 
                    ? property.get("area").asText() : (item.has("area") ? item.get("area").asText() : "");
                String dealBuildingStatus = property != null && !property.isNull() && property.has("building_status") 
                    ? property.get("building_status").asText() : (item.has("building_status") ? item.get("building_status").asText() : "");
                
                // Map API fields to your DTO structure
                deal.put("id", item.has("listing_id") ? item.get("listing_id").asText() : UUID.randomUUID().toString());
                deal.put("name", buildingName);
                deal.put("location", dealArea); // Using area as location
                deal.put("city", "Dubai"); // Default to Dubai as per your data
                deal.put("area", dealArea);
                deal.put("bedrooms", item.has("bedrooms") ? item.get("bedrooms").asText() : "N/A");
                deal.put("bedroomCount", item.has("bedrooms") ? item.get("bedrooms").asText() : "N/A");

                // Handle size - API may return very large numbers, normalize to reasonable sqft
                int rawSize = item.has("size") ? item.get("size").asInt() : 0;
                int normalizedSize = rawSize > 50000 ? rawSize / 1000 : rawSize;
                deal.put("size", normalizedSize);
                
                String listedPriceStr = item.has("listed_price") ? item.get("listed_price").asText() : "0";
                long listedPrice = parsePrice(listedPriceStr);
                deal.put("listedPrice", listedPrice);
                deal.put("priceValue", listedPrice);
                
                // Parse estimate range
                String estimate = item.has("our_estimate") ? item.get("our_estimate").asText() : "";
                Map<String, Long> estimateValues = parseEstimateRange(estimate);
                deal.put("estimateMin", estimateValues.get("min"));
                deal.put("estimateMax", estimateValues.get("max"));
                deal.put("estimateRange", estimate);
                
                // Calculate discount
                long estimateMin = estimateValues.get("min");
                String discount = calculateDiscount(listedPrice, estimateMin, estimateValues.get("max"));
                deal.put("discount", discount);
                
                deal.put("rentalYield", item.has("rental_yield") ? item.get("rental_yield").asText() : "N/A");
                deal.put("grossRentalYield", item.has("rental_yield") ? item.get("rental_yield").asText() : "N/A");
                deal.put("buildingStatus", dealBuildingStatus);
                deal.put("propertyType", ""); // Not available in API
                deal.put("priceVsEstimations", item.has("price_vs_market") ? item.get("price_vs_market").asText() : "N/A");

                // Calculate price per sqft using normalized size
                if (normalizedSize > 0) {
                    long pricePerSqft = listedPrice / normalizedSize;
                    deal.put("pricePerSqft", pricePerSqft);
                } else {
                    deal.put("pricePerSqft", 0);
                }

                deal.put("pricePerSqftVsMarket", item.has("price_vs_market") ? item.get("price_vs_market").asText() : "N/A");
                deal.put("propertyDescription", "");
                deal.put("buildingFeatures", "");
                deal.put("serviceCharge", "");
                deal.put("developer", "");
                deal.put("propertyLink", "");
                deal.put("propertyId", item.has("listing_id") ? item.get("listing_id").asText() : "");
                
                allDeals.add(deal);
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

    // Helper method to parse price strings (e.g., "36,000,000" -> 36000000)
    private long parsePrice(String priceStr) {
        try {
            return Long.parseLong(priceStr.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Helper method to parse estimate range (e.g., "AED 523,799,490 - 556,199,459")
    private Map<String, Long> parseEstimateRange(String estimate) {
        Map<String, Long> result = new HashMap<>();
        try {
            String cleaned = estimate.replace("AED", "").trim();
            String[] parts = cleaned.split("-");
            if (parts.length == 2) {
                result.put("min", Long.parseLong(parts[0].trim().replace(",", "")));
                result.put("max", Long.parseLong(parts[1].trim().replace(",", "")));
            } else {
                result.put("min", 0L);
                result.put("max", 0L);
            }
        } catch (Exception e) {
            result.put("min", 0L);
            result.put("max", 0L);
        }
        return result;
    }

    // Helper method to calculate discount
    private String calculateDiscount(long listedPrice, long estimateMin, long estimateMax) {
        if (estimateMin == 0 || estimateMax == 0) {
            return "N/A";
        }
        long avgEstimate = (estimateMin + estimateMax) / 2;
        long discount = avgEstimate - listedPrice;
        if (discount > 0) {
            return String.format("AED %,d", discount);
        }
        return "N/A";
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
        // Check if user has access (not FREE tier)
        ResponseEntity<?> accessCheck = checkUserAccess(authentication);
        if (accessCheck != null) {
            return accessCheck;
        }

        try {
            // Fetch data from third-party API
            RestTemplate restTemplate = new RestTemplate();
            String apiUrl = "http://72.62.40.154:8000/deals/" + dealId;

            JsonNode apiResponse;
            try {
                apiResponse = restTemplate.getForObject(apiUrl, JsonNode.class);
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

            if (apiResponse == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Deal not found"));
            }

            // Build deal DTO
            Map<String, Object> dto = new HashMap<>();

            // Basic information
            dto.put("id", apiResponse.get("listing_id").asText());
            dto.put("name", apiResponse.has("building_name") ? apiResponse.get("building_name").asText() : "");
            dto.put("location", apiResponse.has("area") ? apiResponse.get("area").asText() : "");
            dto.put("city", apiResponse.has("city") ? apiResponse.get("city").asText() : "Dubai");
            dto.put("area", apiResponse.has("area") ? apiResponse.get("area").asText() : "");
            dto.put("bedrooms", apiResponse.has("bedrooms") ? apiResponse.get("bedrooms").asText() : "0");
            dto.put("bedroomCount", apiResponse.has("bedrooms") ? apiResponse.get("bedrooms").asText() : "0");

            // Size - normalize if needed
            int rawSize = apiResponse.has("size_sqft") ? apiResponse.get("size_sqft").asInt() : 0;
            int normalizedSize = rawSize > 50000 ? rawSize / 1000 : rawSize;
            dto.put("size", normalizedSize);

            // Price information
            long listedPrice = apiResponse.has("listed_price_aed")
                ? parsePrice(apiResponse.get("listed_price_aed").asText())
                : 0;
            dto.put("listedPrice", listedPrice);
            dto.put("priceValue", listedPrice);

            // Estimate range
            String estimate = apiResponse.has("our_price_estimate")
                ? apiResponse.get("our_price_estimate").asText()
                : "";
            Map<String, Long> estimateValues = parseEstimateRange(estimate);
            dto.put("estimateMin", estimateValues.get("min"));
            dto.put("estimateMax", estimateValues.get("max"));
            dto.put("estimateRange", estimate);

            // Discount/Savings
            String discount = apiResponse.has("potential_savings")
                ? apiResponse.get("potential_savings").asText()
                : "N/A";
            dto.put("discount", discount);

            // Rental yield
            String rentalYield = apiResponse.has("rental_yield_estimate")
                ? apiResponse.get("rental_yield_estimate").asText()
                : apiResponse.has("gross_rental_yield")
                    ? apiResponse.get("gross_rental_yield").asText()
                    : "N/A";
            dto.put("rentalYield", rentalYield);
            dto.put("grossRentalYield", rentalYield);

            // Building status
            String buildingStatus = apiResponse.has("building_status")
                ? apiResponse.get("building_status").asText()
                : "";
            dto.put("buildingStatus", buildingStatus);

            // Property type
            String propertyType = apiResponse.has("property_type")
                ? apiResponse.get("property_type").asText()
                : "";
            if (apiResponse.has("property_sub_type")) {
                propertyType += " - " + apiResponse.get("property_sub_type").asText();
            }
            dto.put("propertyType", propertyType);

            // Price vs market
            String priceVsMarket = apiResponse.has("price_vs_estimations")
                ? apiResponse.get("price_vs_estimations").asText()
                : apiResponse.has("price_per_sqft_vs_market")
                    ? apiResponse.get("price_per_sqft_vs_market").asText()
                    : "N/A";
            dto.put("priceVsEstimations", priceVsMarket);

            // Price per sqft
            long pricePerSqft = apiResponse.has("price_per_sqft")
                ? Long.parseLong(apiResponse.get("price_per_sqft").asText().replace(",", ""))
                : (normalizedSize > 0 ? listedPrice / normalizedSize : 0);
            dto.put("pricePerSqft", pricePerSqft);
            dto.put("pricePerSqftVsMarket", priceVsMarket);

            // Additional details
            dto.put("propertyDescription", apiResponse.has("property_description")
                ? apiResponse.get("property_description").asText() : "");
            dto.put("buildingFeatures", apiResponse.has("building_features")
                ? apiResponse.get("building_features").asText() : "");
            dto.put("serviceCharge", apiResponse.has("service_charge")
                ? apiResponse.get("service_charge").asText() : "");
            dto.put("developer", apiResponse.has("developer")
                ? apiResponse.get("developer").asText() : "");
            dto.put("propertyLink", apiResponse.has("link_for_property")
                ? apiResponse.get("link_for_property").asText() : "");
            dto.put("propertyId", apiResponse.get("listing_id").asText());

            // Additional fields from API
            if (apiResponse.has("view")) {
                dto.put("view", apiResponse.get("view").asText());
            }
            if (apiResponse.has("furnishing")) {
                dto.put("furnishing", apiResponse.get("furnishing").asText());
            }
            if (apiResponse.has("rensights_score")) {
                dto.put("rensightsScore", apiResponse.get("rensights_score").asText());
            }
            if (apiResponse.has("investment_appeal")) {
                dto.put("investmentAppeal", apiResponse.get("investment_appeal").asText());
            }
            if (apiResponse.has("market_position")) {
                dto.put("marketPosition", apiResponse.get("market_position").asText());
            }
            if (apiResponse.has("nearest_landmark")) {
                dto.put("nearestLandmark", apiResponse.get("nearest_landmark").asText());
            }

            // Parse comparables (listing_comparables)
            if (apiResponse.has("listing_comparables")) {
                try {
                    String comparablesJson = apiResponse.get("listing_comparables").asText();
                    ObjectMapper mapper = new ObjectMapper();
                    List<Map<String, Object>> comparables = mapper.readValue(
                        comparablesJson,
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>(){}
                    );
                    dto.put("listedDeals", comparables);
                } catch (Exception e) {
                    logger.warn("Failed to parse listing_comparables: {}", e.getMessage());
                    dto.put("listedDeals", new ArrayList<>());
                }
            }

            // Parse transaction comparables (transaction_comparables)
            if (apiResponse.has("transaction_comparables")) {
                try {
                    String transactionsJson = apiResponse.get("transaction_comparables").asText();
                    ObjectMapper mapper = new ObjectMapper();
                    List<Map<String, Object>> transactions = mapper.readValue(
                        transactionsJson,
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>(){}
                    );
                    dto.put("recentSales", transactions);
                } catch (Exception e) {
                    logger.warn("Failed to parse transaction_comparables: {}", e.getMessage());
                    dto.put("recentSales", new ArrayList<>());
                }
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

