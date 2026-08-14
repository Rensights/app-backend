package com.rensights.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the upstream {@code GET /analysis_request/{analysis_request_id}} payload onto the
 * camelCase shape the analysis report screen renders.
 *
 * <p>The raw payload keeps living in {@code analysis_requests.analysis_result} untouched;
 * this mapping is applied on read, so reports fetched before the mapping existed render
 * correctly without re-fetching them from the analysis module.
 *
 * <p>Values are handed to the frontend display-ready. The module already sends most of them
 * that way ({@code "AED 3,653/sq ft"}, {@code "520 sq ft"}); when it sends a bare number
 * instead, it is formatted here so the frontend never recomputes a figure it was given.
 */
@Component
public class AnalysisResultMapper {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisResultMapper.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * The report view, or {@code null} when there is no analysis result yet (the screen then
     * falls back to the values the user submitted with the request).
     */
    public Map<String, Object> toReportView(JsonNode result) {
        if (result == null || result.isNull() || !result.isObject()) {
            return null;
        }

        Map<String, Object> view = new LinkedHashMap<>();

        // Header
        view.put("buildingName", text(result, "building_name"));
        view.put("area", text(result, "area"));
        view.put("city", text(result, "city"));

        // Summary
        view.put("bedrooms", text(result, "bedrooms"));
        view.put("size", sizeDisplay(result.get("size_sqft")));
        view.put("buildingStatus", text(result, "building_status"));
        view.put("marketGapPercentage", percentDisplay(result.get("market_gap_percentage")));
        view.put("marketDirectionLabel", text(result, "market_direction_label"));
        view.put("rentalYield", text(result, "rental_yield_estimate"));

        // Price analysis
        view.put("listedPrice", aedDisplay(result.get("listed_price_aed")));
        view.put("estimateRange", text(result, "our_price_estimate"));
        view.put("potentialSavings", text(result, "potential_savings"));
        view.put("pricePerSqft", pricePerSqftDisplay(result.get("price_per_sqft")));

        // Market comparison + investment insights
        view.put("marketPosition", text(result, "market_position"));
        view.put("dubaiComparison", text(result, "dubai_comparison"));
        view.put("valuationWarning", valuationWarning(result.get("valuation_warning")));

        // Property details
        view.put("furnishing", text(result, "furnishing"));
        view.put("developer", text(result, "developer"));
        view.put("view", text(result, "view"));
        view.put("serviceCharge", text(result, "service_charge"));
        view.put("nearestLandmark", text(result, "nearest_landmark"));
        view.put("buildingFeatures", text(result, "building_features"));

        // Comparables
        view.put("listingComparables", listingComparables(result.get("listing_comparables")));
        view.put("transactionComparables", transactionComparables(result.get("transaction_comparables")));

        return view;
    }

    /** {@code listing_comparables[]} -> the "Similar Deals" card shape. */
    private List<Map<String, Object>> listingComparables(JsonNode node) {
        List<Map<String, Object>> cards = new ArrayList<>();
        for (JsonNode entry : asArray(node, "listing_comparables")) {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("buildingName", firstText(entry, "building_name", "name"));
            card.put("area", text(entry, "area"));
            card.put("bedrooms", text(entry, "bedrooms"));
            card.put("sizeDisplay", comparableSize(entry));
            card.put("listedPriceDisplay", comparablePrice(entry, "listed_price_display", "listed_price_aed", "listed_price"));
            card.put("pricePerSqftDisplay", comparablePricePerSqft(entry));
            card.put("listingUrl", firstText(entry, "url", "listing_url"));
            cards.add(card);
        }
        return cards;
    }

    /** {@code transaction_comparables[]} -> the "Recent Sales" card shape. */
    private List<Map<String, Object>> transactionComparables(JsonNode node) {
        List<Map<String, Object>> cards = new ArrayList<>();
        for (JsonNode entry : asArray(node, "transaction_comparables")) {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("buildingName", firstText(entry, "building_name", "name"));
            card.put("area", text(entry, "area"));
            card.put("bedrooms", text(entry, "bedrooms"));
            card.put("sizeDisplay", comparableSize(entry));
            card.put("salePriceDisplay", comparablePrice(entry, "sale_price_display", "sale_price_aed", "sale_price"));
            card.put("pricePerSqftDisplay", comparablePricePerSqft(entry));
            card.put("transactionDate", firstText(entry, "transaction_date", "date"));
            cards.add(card);
        }
        return cards;
    }

    private String comparableSize(JsonNode entry) {
        String display = text(entry, "size_display");
        return !display.isEmpty() ? display : sizeDisplay(firstNode(entry, "size_sqft", "size"));
    }

    private String comparablePrice(JsonNode entry, String displayField, String... fallbackFields) {
        String display = text(entry, displayField);
        return !display.isEmpty() ? display : aedDisplay(firstNode(entry, fallbackFields));
    }

    private String comparablePricePerSqft(JsonNode entry) {
        String display = text(entry, "price_per_sqft_display");
        return !display.isEmpty() ? display : pricePerSqftDisplay(entry.get("price_per_sqft"));
    }

    /**
     * The comparables list arrives either as a real JSON array or as a string holding encoded
     * JSON, so both shapes are accepted. Anything unparseable logs and yields an empty list
     * rather than failing the whole report response.
     */
    private Iterable<JsonNode> asArray(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            return node;
        }
        if (node.isTextual()) {
            try {
                JsonNode parsed = OBJECT_MAPPER.readTree(node.asText());
                if (parsed.isArray()) {
                    return parsed;
                }
            } catch (Exception e) {
                logger.warn("Failed to parse {}: {}", field, e.getMessage());
            }
        }
        return List.of();
    }

    /** {@code valuation_warning} is an object; a missing or empty one yields {@code null}. */
    private Map<String, Object> valuationWarning(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return null;
        }
        String title = text(node, "title");
        String message = text(node, "message");
        if (title.isEmpty() && message.isEmpty()) {
            return null;
        }
        Map<String, Object> warning = new LinkedHashMap<>();
        warning.put("title", title);
        warning.put("message", message);
        return warning;
    }

    /** "520 sq ft". A value that already carries its unit is passed through unchanged. */
    private String sizeDisplay(JsonNode node) {
        if (isBlank(node)) {
            return "";
        }
        Double number = asNumber(node);
        if (number == null) {
            return node.asText().trim();
        }
        return String.format("%,d sq ft", Math.round(number));
    }

    /** "AED 1.9M" / "AED 180K". A value that already reads as a price is passed through. */
    private String aedDisplay(JsonNode node) {
        if (isBlank(node)) {
            return "";
        }
        Double number = asNumber(node);
        if (number == null) {
            return node.asText().trim();
        }
        double value = number;
        if (value >= 1_000_000) {
            return "AED " + trimZero(value / 1_000_000) + "M";
        }
        if (value >= 1_000) {
            return "AED " + trimZero(value / 1_000) + "K";
        }
        return String.format("AED %,d", Math.round(value));
    }

    /** "AED 3,653/sq ft". A value that already carries its unit is passed through. */
    private String pricePerSqftDisplay(JsonNode node) {
        if (isBlank(node)) {
            return "";
        }
        Double number = asNumber(node);
        if (number == null) {
            return node.asText().trim();
        }
        return String.format("AED %,d/sq ft", Math.round(number));
    }

    /** "33.7%". A value that already carries its sign is passed through. */
    private String percentDisplay(JsonNode node) {
        if (isBlank(node)) {
            return "";
        }
        Double number = asNumber(node);
        if (number == null) {
            return node.asText().trim();
        }
        return trimZero(number) + "%";
    }

    private String trimZero(double value) {
        String formatted = String.format("%,.1f", value);
        return formatted.endsWith(".0") ? formatted.substring(0, formatted.length() - 2) : formatted;
    }

    /**
     * The numeric value of a node, or {@code null} when it is text the module already
     * formatted ("AED 3,653/sq ft") and must therefore be passed through verbatim.
     */
    private Double asNumber(JsonNode node) {
        if (node.isNumber()) {
            return node.asDouble();
        }
        try {
            return Double.valueOf(node.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A field's text. Arrays of scalars (e.g. {@code building_features}) are joined into the
     * single line the report shows; objects are ignored. Missing fields yield {@code ""} so
     * the frontend can treat empty and absent identically.
     */
    private String text(JsonNode parent, String field) {
        if (parent == null) {
            return "";
        }
        return nodeText(parent.get(field));
    }

    private String firstText(JsonNode parent, String... fields) {
        for (String field : fields) {
            String value = text(parent, field);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private JsonNode firstNode(JsonNode parent, String... fields) {
        if (parent == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode node = parent.get(field);
            if (!isBlank(node)) {
                return node;
            }
        }
        return null;
    }

    private String nodeText(JsonNode node) {
        if (isBlank(node)) {
            return "";
        }
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode item : node) {
                String part = nodeText(item);
                if (!part.isEmpty()) {
                    parts.add(part);
                }
            }
            return String.join(", ", parts);
        }
        if (node.isObject()) {
            return "";
        }
        return node.asText().trim();
    }

    private boolean isBlank(JsonNode node) {
        return node == null || node.isNull() || (node.isTextual() && node.asText().trim().isEmpty());
    }
}
