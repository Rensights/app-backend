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
     * The full upstream {@code GET /deals} payload: the normalized deals list plus the
     * upstream {@code summary} object that backs the four headline stat cards.
     *
     * <p>{@code summary} is {@code null} when upstream sends no summary object.
     */
    public record DealsPayload(List<Map<String, Object>> deals, Map<String, Object> summary) {}

    /**
     * Fetch + normalize the full upstream payload (post-transform, PRE-filter, PRE-paginate).
     *
     * <p>Cached under {@code dealsAll:'all'}. If the upstream call throws
     * (HttpClientErrorException / HttpServerErrorException / connectivity), the exception
     * propagates and nothing is cached, so a transient failure never sticks. An upstream response
     * with no {@code data} array yields an empty list (matching the previous empty-page behavior).
     */
    @Cacheable(cacheNames = "dealsAll", key = "'all'")
    public DealsPayload getDealsPayload() {
        JsonNode apiResponse = restTemplate.getForObject(dealsApiUrl, JsonNode.class);

        List<Map<String, Object>> allDeals = new ArrayList<>();
        if (apiResponse == null) {
            return new DealsPayload(allDeals, null);
        }

        Map<String, Object> summary = parseSummary(apiResponse.get("summary"));
        if (!apiResponse.has("data")) {
            return new DealsPayload(allDeals, summary);
        }

        JsonNode dataArray = apiResponse.get("data");
        for (JsonNode item : dataArray) {
            Map<String, Object> deal = new HashMap<>();

            // Per the agreed mapping these live on the row itself (data[].building_name,
            // data[].area, data[].building_status). Older payloads nested them under
            // "property", so that shape is still accepted as a fallback.
            JsonNode property = item.get("property");
            String buildingName = itemText(item, property, "building_name", "");
            String dealArea = itemText(item, property, "area", "");
            String dealBuildingStatus = itemText(item, property, "building_status", "");

            // Map API fields to your DTO structure
            deal.put("id", item.has("listing_id") ? item.get("listing_id").asText() : UUID.randomUUID().toString());
            deal.put("name", buildingName);
            deal.put("location", dealArea); // Using area as location
            deal.put("city", "Dubai"); // Default to Dubai as per your data
            deal.put("area", dealArea);
            deal.put("bedrooms", item.has("bedrooms") ? item.get("bedrooms").asText() : "N/A");
            deal.put("bedroomCount", item.has("bedrooms") ? item.get("bedrooms").asText() : "N/A");

            int sizeSqft = parseSize(item.get("size"));
            deal.put("size", sizeSqft);

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
            if (sizeSqft > 0) {
                long pricePerSqft = listedPrice / sizeSqft;
                deal.put("pricePerSqft", pricePerSqft);
            } else {
                deal.put("pricePerSqft", 0);
            }

            deal.put("pricePerSqftVsMarket", item.has("price_vs_market") ? item.get("price_vs_market").asText() : "N/A");

            // Market gap: the percentage and the "Below Market" / "Above Market" wording that
            // the deals table shows next to it. Previously list rows carried neither — only
            // the detail endpoint did — so the table had nothing to render.
            deal.put("marketGapPercentage", item.has("market_gap_percentage")
                ? item.get("market_gap_percentage").asText() : "N/A");
            deal.put("marketDirection", item.has("market_direction")
                ? item.get("market_direction").asText() : "");
            deal.put("marketDirectionLabel", item.has("market_direction_label")
                ? item.get("market_direction_label").asText() : "");
            deal.put("valuationConfidence", item.has("valuation_confidence")
                ? item.get("valuation_confidence").asText() : "");

            deal.put("propertyDescription", "");
            deal.put("buildingFeatures", "");
            deal.put("serviceCharge", "");
            deal.put("developer", "");
            deal.put("propertyLink", item.has("link_for_property")
                ? item.get("link_for_property").asText() : "");
            deal.put("propertyId", item.has("listing_id") ? item.get("listing_id").asText() : "");

            allDeals.add(deal);
        }

        return new DealsPayload(allDeals, summary);
    }

    /**
     * Map the upstream {@code summary} object onto the camelCase names the website backend
     * exposes. This is a rename, not a reformat — values are handed through exactly as the
     * valuation module sends them:
     *
     * <p>The /deals stat cards:
     * <pre>
     *   available_deals        -> availableDeals        454
     *   avg_price_vs_market    -> avgPriceVsMarket      "19.9%"
     *   most_liquid_size_range -> mostLiquidSizeRange   "600-909 sq ft"
     *   avg_gross_rental_yield -> avgGrossRentalYield    "6.9%"
     * </pre>
     *
     * <p>The /weekly-deals highlights:
     * <pre>
     *   total_active_deals           -> totalActiveDeals     454
     *   top_areas                    -> topAreas             [{area, count}]
     *   hottest_area                 -> hottestArea          "Business Bay"
     *   best_discount_display        -> bestDiscountDisplay  "30.9% below market"
     *   best_performing_area_display -> bestPerformingArea   "Jumeirah Village (JVC/JVT) & ..."
     * </pre>
     *
     * <p>A key the module omits comes through as {@code null} so the UI can show "N/A"
     * instead of a fabricated value.
     */
    private Map<String, Object> parseSummary(JsonNode summaryNode) {
        if (summaryNode == null || summaryNode.isNull() || !summaryNode.isObject()) {
            return null;
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("availableDeals", rawValue(summaryNode.get("available_deals")));
        summary.put("avgPriceVsMarket", rawValue(summaryNode.get("avg_price_vs_market")));
        summary.put("mostLiquidSizeRange", rawValue(summaryNode.get("most_liquid_size_range")));
        summary.put("avgGrossRentalYield", rawValue(summaryNode.get("avg_gross_rental_yield")));

        summary.put("totalActiveDeals", rawValue(summaryNode.get("total_active_deals")));
        summary.put("topAreas", rawValue(summaryNode.get("top_areas")));
        summary.put("hottestArea", rawValue(summaryNode.get("hottest_area")));
        summary.put("bestDiscountDisplay", rawValue(summaryNode.get("best_discount_display")));
        // Note the asymmetry: the module's key keeps the _display suffix, the website field
        // drops it. Kept as specified in the agreed mapping.
        summary.put("bestPerformingArea", rawValue(summaryNode.get("best_performing_area_display")));
        return summary;
    }

    /** Text field of a detail response; an absent or null key yields an empty string. */
    private String detailText(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : "";
    }

    /**
     * Text field of a deal row, read from the row itself and falling back to the legacy
     * nested {@code property} object when the row does not carry it.
     */
    private String itemText(JsonNode item, JsonNode property, String field, String fallback) {
        if (item.has(field) && !item.get(field).isNull()) {
            return item.get(field).asText();
        }
        if (property != null && !property.isNull() && property.has(field) && !property.get(field).isNull()) {
            return property.get(field).asText();
        }
        return fallback;
    }

    /** The JSON value as-is: a number stays numeric, a string stays a string, absent -> null. */
    private Object rawValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node; // object/array: pass the structure through untouched
    }

    private static final java.util.regex.Pattern SIGNED_NUMBER =
        java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?");

    /**
     * First number in free-form text, preserving an explicit sign.
     *
     * <p>Used for percentages by {@code DealController} when a filter narrows the deal set and
     * the summary's averages have to be recomputed from the per-deal strings ({@code "12.4%"},
     * {@code "-3.1%"}, {@code "N/A"}), and for sizes by {@link #parseSize}. Returns
     * {@code null} when the text carries no number.
     */
    public static Double parseNumberText(String text) {
        if (text == null) {
            return null;
        }
        java.util.regex.Matcher m = SIGNED_NUMBER.matcher(text.replace(",", ""));
        return m.find() ? Double.parseDouble(m.group()) : null;
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

        int sizeSqft = parseSize(apiResponse.get("size_sqft"));
        dto.put("size", sizeSqft);

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

        // Potential savings. "discount" is the legacy name the UI still reads; both carry the
        // module's potential_savings verbatim.
        String discount = apiResponse.has("potential_savings")
            ? apiResponse.get("potential_savings").asText()
            : "N/A";
        dto.put("potentialSavings", discount);
        dto.put("discount", discount);

        // Rental yield
        String rentalYield = apiResponse.has("rental_yield_estimate")
            ? apiResponse.get("rental_yield_estimate").asText()
            : apiResponse.has("gross_rental_yield")
                ? apiResponse.get("gross_rental_yield").asText()
                : "N/A";
        dto.put("rentalYield", rentalYield);
        dto.put("grossRentalYield", rentalYield);
        dto.put("annualRentEstimate", detailText(apiResponse, "annual_rent_estimate"));
        dto.put("averageMarketYield", detailText(apiResponse, "average_market_yield_estimate"));

        // Building status
        String buildingStatus = apiResponse.has("building_status")
            ? apiResponse.get("building_status").asText()
            : "";
        dto.put("buildingStatus", buildingStatus);

        // Property type. The mapping asks for property_sub_type alone ("Apartments"); the
        // broader property_type is only a fallback when the module omits the sub type. It is
        // no longer concatenated ("Residential - Apartments" was never a wanted label).
        String propertyType = apiResponse.has("property_sub_type")
            ? apiResponse.get("property_sub_type").asText()
            : detailText(apiResponse, "property_type");
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
        dto.put("priceVsEstimateRange", detailText(apiResponse, "price_vs_estimate_range_display"));

        // Price per sqft, handed through as the module sends it ("AED 1,394/sq ft"). Only when
        // the module omits it do we fall back to a computed figure. The previous
        // Long.parseLong() blew up on anything but bare digits, which is exactly what a
        // display string like "AED 1,394/sq ft" is.
        dto.put("pricePerSqft", apiResponse.has("price_per_sqft")
            ? rawValue(apiResponse.get("price_per_sqft"))
            : (sizeSqft > 0 ? listedPrice / sizeSqft : 0));
        dto.put("marketAveragePricePerSqft",
            rawValue(apiResponse.get("market_average_price_per_sqft")));
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
        dto.put("view", detailText(apiResponse, "view"));
        dto.put("furnishing", detailText(apiResponse, "furnishing"));
        dto.put("valuationConfidence", detailText(apiResponse, "valuation_confidence"));
        dto.put("marketPosition", detailText(apiResponse, "market_position"));
        dto.put("dubaiComparison", detailText(apiResponse, "dubai_comparison"));
        dto.put("nearestLandmark", detailText(apiResponse, "nearest_landmark"));

        // Scores
        dto.put("rensightsScore", detailText(apiResponse, "rensights_score"));
        dto.put("locationTransportScore", detailText(apiResponse, "location_transport_score"));
        dto.put("liquidityScore", detailText(apiResponse, "liquidity_score"));

        // investment_appeal is retired: the "Excellent / Good / Fair Investment Opportunity"
        // subtitle is derived on the frontend from the market gap, so the module's value is
        // deliberately not mapped here.

        dto.put("listedDeals", parseComparables(
            apiResponse.get("listing_comparables"), "listing_comparables", LISTING_COMPARABLE_FIELDS));
        dto.put("recentSales", parseComparables(
            apiResponse.get("transaction_comparables"), "transaction_comparables", TRANSACTION_COMPARABLE_FIELDS));

        return dto;
    }

    /** listing_comparables[] -> the "Similar Deals" card shape. */
    private static final Map<String, String> LISTING_COMPARABLE_FIELDS = Map.of(
        "id", "id",
        "building_name", "name",
        "area", "area",
        "bedrooms", "bedrooms",
        "size_display", "sizeDisplay",
        "listed_price_display", "listedPrice",
        "price_per_sqft_display", "pricePerSqft",
        "listing_date", "date",
        "url", "url"
    );

    /** transaction_comparables[] -> the "Recent Sales" card shape. */
    private static final Map<String, String> TRANSACTION_COMPARABLE_FIELDS = Map.of(
        "id", "id",
        "building_name", "name",
        "area", "area",
        "bedrooms", "bedrooms",
        "size_display", "sizeDisplay",
        "sale_price_display", "salePrice",
        "price_per_sqft_display", "pricePerSqft",
        "transaction_date", "date"
    );

    /**
     * Comparables list, with each entry's keys renamed to the card shape the UI consumes.
     *
     * <p>The module sends the list either as a real JSON array or as a string holding encoded
     * JSON, so both shapes are accepted. The entries carry display-ready values
     * ({@code size_display}, {@code listed_price_display}, {@code price_per_sqft_display}) and
     * are passed through as such — nothing is recomputed here. A key the module omits lands as
     * {@code null}. Anything unparseable logs and yields an empty list rather than failing the
     * whole detail response.
     */
    private List<Map<String, Object>> parseComparables(JsonNode node, String field,
                                                       Map<String, String> fieldMap) {
        if (node == null || node.isNull()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> raw;
        try {
            ObjectMapper mapper = new ObjectMapper();
            TypeReference<List<Map<String, Object>>> listType = new TypeReference<>() {};
            raw = node.isArray()
                ? mapper.convertValue(node, listType)
                : mapper.readValue(node.asText(), listType);
        } catch (Exception e) {
            logger.warn("Failed to parse {}: {}", field, e.getMessage());
            return new ArrayList<>();
        }

        List<Map<String, Object>> cards = new ArrayList<>();
        for (Map<String, Object> entry : raw) {
            Map<String, Object> card = new HashMap<>();
            fieldMap.forEach((from, to) -> card.put(to, entry.get(from)));
            cards.add(card);
        }
        return cards;
    }

    private static final java.util.regex.Pattern PRICE = java.util.regex.Pattern.compile(
        "(\\d+(?:\\.\\d+)?)\\s*([kKmMbB])?");

    /**
     * Price in AED from whatever the module sends.
     *
     * <p>Previously this was a bare {@code Long.parseLong(s.replace(",", ""))}, which only ever
     * succeeded on pure digits — {@code "AED 650,000"} and {@code "AED 650k"} both fell into
     * the catch and became {@code 0}, so the table showed "AED 0". Now the currency prefix,
     * separators and a k/m/b suffix are all handled:
     *
     * <pre>
     *   "650000"       -> 650000
     *   "650,000"      -> 650000
     *   "AED 650,000"  -> 650000
     *   "AED 650k"     -> 650000
     *   "AED 1.2m"     -> 1200000
     * </pre>
     *
     * <p>Returns 0 when the text carries no number at all.
     */
    private long parsePrice(String priceStr) {
        if (priceStr == null) {
            return 0;
        }
        java.util.regex.Matcher m = PRICE.matcher(priceStr.replace(",", "").trim());
        if (!m.find()) {
            return 0;
        }
        double value = Double.parseDouble(m.group(1));
        String suffix = m.group(2);
        if (suffix != null) {
            switch (Character.toLowerCase(suffix.charAt(0))) {
                case 'k' -> value *= 1_000d;
                case 'm' -> value *= 1_000_000d;
                case 'b' -> value *= 1_000_000_000d;
                default -> { /* no scaling */ }
            }
        }
        return Math.round(value);
    }

    /**
     * Size in sqft from either a JSON number or a display string ("466 sq ft", "1,250").
     *
     * <p>{@code asInt()} used to be called straight on the node, and Jackson returns 0 for any
     * text that is not a bare integer — so a module sending {@code "466 sq ft"} produced a
     * size of 0, which the UI rendered as "N/A" (and which also zeroed the price-per-sqft
     * calculation that divides by it).
     *
     * <p>The module's value is taken as given. An earlier version divided anything above
     * 50,000 by 1000 to undo a suspected upstream scaling bug; that guess also shrank
     * genuinely large units by 1000x, so it is gone.
     */
    private int parseSize(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0;
        }
        if (node.isNumber()) {
            return node.asInt();
        }
        Double parsed = parseNumberText(node.asText());
        return parsed == null ? 0 : (int) Math.round(parsed);
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
