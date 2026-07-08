package com.rensights.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fetches and normalizes weekly-deals data from the upstream third-party API.
 *
 * <p>Extracted from {@code DealController} so {@code @Cacheable} sits at the correct boundary
 * (never on a controller). The fetch + full normalize is cached; per-request filtering and
 * pagination stay in the controller and operate on the returned (cached) list, so the HTTP
 * response is byte-for-byte identical to the previous inline implementation.
 *
 * <p>Reuses the shared, timeout-bounded {@link RestTemplate} bean from
 * {@code com.rensights.config.RestClientConfig} via constructor injection.
 */
@Service
public class DealsFetchService {

    private static final Logger logger = LoggerFactory.getLogger(DealsFetchService.class);

    private final RestTemplate restTemplate;

    @Value("${deals.api.url}")
    private String dealsApiUrl;

    public DealsFetchService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fetch + normalize the full deals list (post-transform, PRE-filter, PRE-paginate).
     *
     * <p>Cached under {@code dealsAll:'all'}. If the upstream call throws
     * (HttpClientErrorException / HttpServerErrorException / connectivity), the exception
     * propagates and nothing is cached, so a transient failure never sticks. An upstream response
     * with no {@code data} array yields an empty list (matching the previous empty-page behavior).
     */
    @Cacheable(cacheNames = "dealsAll", key = "'all'")
    public List<Map<String, Object>> getAllDeals() {
        JsonNode apiResponse = restTemplate.getForObject(dealsApiUrl, JsonNode.class);

        List<Map<String, Object>> allDeals = new ArrayList<>();
        if (apiResponse == null || !apiResponse.has("data")) {
            return allDeals;
        }

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

        return allDeals;
    }

    /**
     * Fetch + parse a single deal by id.
     *
     * <p>Cached under {@code dealDetail:#dealId} with {@code unless="#result == null"} so a
     * not-found result is NOT cached (a transient 404 must not stick for the TTL). Upstream HTTP
     * errors propagate as exceptions (never cached); a {@code null} upstream body returns
     * {@code null} and is likewise skipped by {@code unless}.
     */
    @Cacheable(cacheNames = "dealDetail", key = "#dealId", unless = "#result == null")
    public Map<String, Object> getDealById(String dealId) {
        String apiUrl = dealsApiUrl + "/" + dealId;
        JsonNode apiResponse = restTemplate.getForObject(apiUrl, JsonNode.class);

        if (apiResponse == null) {
            return null;
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

        // Market gap (percentage + direction, from AI model)
        dto.put("marketGapPercentage", apiResponse.has("market_gap_percentage")
            ? apiResponse.get("market_gap_percentage").asText() : "N/A");
        dto.put("marketDirection", apiResponse.has("market_direction")
            ? apiResponse.get("market_direction").asText() : "");
        dto.put("marketDirectionLabel", apiResponse.has("market_direction_label")
            ? apiResponse.get("market_direction_label").asText() : "");

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
                    new TypeReference<List<Map<String, Object>>>() {}
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
                    new TypeReference<List<Map<String, Object>>>() {}
                );
                dto.put("recentSales", transactions);
            } catch (Exception e) {
                logger.warn("Failed to parse transaction_comparables: {}", e.getMessage());
                dto.put("recentSales", new ArrayList<>());
            }
        }

        return dto;
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
}
