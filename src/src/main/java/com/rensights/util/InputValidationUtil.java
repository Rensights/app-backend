package com.rensights.util;

import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

/**
 * SECURITY: Input validation utility to sanitize and validate user inputs
 */
@Component
public class InputValidationUtil {
    
    // Patterns for validation
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
    );
    
    private static final Pattern URL_PATTERN = Pattern.compile(
        "^https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]$"
    );
    
    private static final Pattern LATITUDE_PATTERN = Pattern.compile(
        "^-?([1-8]?[0-9](\\.[0-9]{1,6})?|90(\\.0{1,6})?)$"
    );
    
    private static final Pattern LONGITUDE_PATTERN = Pattern.compile(
        "^-?((1[0-7][0-9]|[1-9]?[0-9])(\\.[0-9]{1,6})?|180(\\.0{1,6})?)$"
    );
    
    private static final int MAX_STRING_LENGTH = 500;
    private static final int MAX_TEXT_LENGTH = 5000;
    
    public static void validateEmail(String email) {
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (email.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("Email is too long");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
    }
    
    public static void validateUrl(String url, String fieldName) {
        if (url != null && !url.isEmpty()) {
            if (url.length() > MAX_STRING_LENGTH) {
                throw new IllegalArgumentException(fieldName + " URL is too long");
            }
            if (!URL_PATTERN.matcher(url).matches()) {
                throw new IllegalArgumentException("Invalid " + fieldName + " URL format");
            }
        }
    }
    
    public static void validateLatitude(String latitude) {
        if (latitude != null && !latitude.isEmpty()) {
            if (!LATITUDE_PATTERN.matcher(latitude.trim()).matches()) {
                throw new IllegalArgumentException("Invalid latitude. Must be between -90 and 90");
            }
        }
    }
    
    public static void validateLongitude(String longitude) {
        if (longitude != null && !longitude.isEmpty()) {
            if (!LONGITUDE_PATTERN.matcher(longitude.trim()).matches()) {
                throw new IllegalArgumentException("Invalid longitude. Must be between -180 and 180");
            }
        }
    }
    
    public static void validateStringLength(String value, String fieldName, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long (max " + maxLength + " characters)");
        }
    }
    
    public static String sanitizeString(String input, int maxLength) {
        if (input == null) {
            return null;
        }
        // Remove control characters and trim
        String sanitized = input.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").trim();
        // Limit length
        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
        }
        return sanitized;
    }
    
    public static void validateAnalysisRequestInputs(
            String email,
            String city,
            String area,
            String buildingName,
            String listingUrl,
            String latitude,
            String longitude,
            String additionalNotes) {
        
        // Validate email
        validateEmail(email);
        
        // Validate optional fields
        validateStringLength(city, "City", MAX_STRING_LENGTH);
        validateStringLength(area, "Area", MAX_STRING_LENGTH);
        validateStringLength(buildingName, "Building name", MAX_STRING_LENGTH);
        validateUrl(listingUrl, "Listing URL");
        validateLatitude(latitude);
        validateLongitude(longitude);
        validateStringLength(additionalNotes, "Additional notes", MAX_TEXT_LENGTH);
    }
}




