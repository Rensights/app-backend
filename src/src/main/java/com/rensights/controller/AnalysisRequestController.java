package com.rensights.controller;

import com.rensights.model.AnalysisRequest;
import com.rensights.service.AnalysisRequestService;
import com.rensights.service.FileStorageService;
import com.rensights.util.InputValidationUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analysis-requests")
public class AnalysisRequestController {
    
    private static final Logger logger = LoggerFactory.getLogger(AnalysisRequestController.class);
    
    @Autowired
    private AnalysisRequestService analysisRequestService;
    
    @Autowired
    private FileStorageService fileStorageService;
    
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> submitAnalysisRequest(
            @RequestParam("email") String email,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "area", required = false) String area,
            @RequestParam(value = "buildingName", required = false) String buildingName,
            @RequestParam(value = "listingUrl", required = false) String listingUrl,
            @RequestParam(value = "propertyType", required = false) String propertyType,
            @RequestParam(value = "bedrooms", required = false) String bedrooms,
            @RequestParam(value = "size", required = false) String size,
            @RequestParam(value = "plotSize", required = false) String plotSize,
            @RequestParam(value = "floor", required = false) String floor,
            @RequestParam(value = "totalFloors", required = false) String totalFloors,
            @RequestParam(value = "buildingStatus", required = false) String buildingStatus,
            @RequestParam(value = "condition", required = false) String condition,
            @RequestParam(value = "latitude", required = false) String latitude,
            @RequestParam(value = "longitude", required = false) String longitude,
            @RequestParam(value = "askingPrice", required = false) String askingPrice,
            @RequestParam(value = "serviceCharge", required = false) String serviceCharge,
            @RequestParam(value = "handoverDate", required = false) String handoverDate,
            @RequestParam(value = "developer", required = false) String developer,
            @RequestParam(value = "paymentPlan", required = false) String paymentPlan,
            @RequestParam(value = "features", required = false) String features, // JSON array as string
            @RequestParam(value = "view", required = false) String view,
            @RequestParam(value = "furnishing", required = false) String furnishing,
            @RequestParam(value = "additionalNotes", required = false) String additionalNotes,
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        logger.info("=== Submit analysis request called for email: {}", email);
        
        try {
            // SECURITY: Validate and sanitize all inputs
            try {
                InputValidationUtil.validateAnalysisRequestInputs(
                    email, city, area, buildingName, listingUrl, latitude, longitude, additionalNotes);
                
                // Sanitize string inputs
                city = InputValidationUtil.sanitizeString(city, 500);
                area = InputValidationUtil.sanitizeString(area, 500);
                buildingName = InputValidationUtil.sanitizeString(buildingName, 500);
                propertyType = InputValidationUtil.sanitizeString(propertyType, 100);
                bedrooms = InputValidationUtil.sanitizeString(bedrooms, 10);
                size = InputValidationUtil.sanitizeString(size, 50);
                plotSize = InputValidationUtil.sanitizeString(plotSize, 50);
                floor = InputValidationUtil.sanitizeString(floor, 10);
                totalFloors = InputValidationUtil.sanitizeString(totalFloors, 10);
                buildingStatus = InputValidationUtil.sanitizeString(buildingStatus, 50);
                condition = InputValidationUtil.sanitizeString(condition, 50);
                askingPrice = InputValidationUtil.sanitizeString(askingPrice, 50);
                serviceCharge = InputValidationUtil.sanitizeString(serviceCharge, 50);
                handoverDate = InputValidationUtil.sanitizeString(handoverDate, 50);
                developer = InputValidationUtil.sanitizeString(developer, 200);
                paymentPlan = InputValidationUtil.sanitizeString(paymentPlan, 200);
                view = InputValidationUtil.sanitizeString(view, 100);
                furnishing = InputValidationUtil.sanitizeString(furnishing, 100);
                additionalNotes = InputValidationUtil.sanitizeString(additionalNotes, 5000);
            } catch (IllegalArgumentException e) {
                logger.warn("Input validation failed: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse(e.getMessage()));
            }
            
            // Get user ID if authenticated
            UUID userId = null;
            if (authentication != null && authentication.isAuthenticated()) {
                try {
                    userId = UUID.fromString(authentication.getName());
                } catch (Exception e) {
                    logger.warn("Could not parse user ID from authentication: {}", authentication.getName());
                }
            }
            
            // Parse features JSON array
            List<String> featuresList = null;
            if (features != null && !features.isEmpty()) {
                try {
                    // Remove brackets and split by comma
                    String cleaned = features.replaceAll("[\\[\\]\"\\s]", "");
                    if (!cleaned.isEmpty()) {
                        featuresList = Arrays.asList(cleaned.split(","));
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing features: {}", features);
                }
            }
            
            AnalysisRequest request = analysisRequestService.createAnalysisRequest(
                    email,
                    userId,
                    city,
                    area,
                    buildingName,
                    listingUrl,
                    propertyType,
                    bedrooms,
                    size,
                    plotSize,
                    floor,
                    totalFloors,
                    buildingStatus,
                    condition,
                    latitude,
                    longitude,
                    askingPrice,
                    serviceCharge,
                    handoverDate,
                    developer,
                    paymentPlan,
                    featuresList,
                    view,
                    furnishing,
                    additionalNotes,
                    files
            );
            
            logger.info("✅ Analysis request created successfully: {}", request.getId());
            return ResponseEntity.ok(new SubmitResponse(
                    "Your property price analysis request has been submitted successfully! " +
                    "You will receive a comprehensive price analysis report via email within 24-48 hours.",
                    request.getId().toString()
            ));
        } catch (IllegalArgumentException e) {
            logger.error("❌ Validation error: {}", e.getMessage());
            // SECURITY FIX: Only expose validation errors (they're user-facing and safe)
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("❌ Error creating analysis request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to submit analysis request. Please try again later."));
        }
    }
    
    @GetMapping("/my-requests")
    public ResponseEntity<?> getMyRequests(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Authentication required"));
        }
        
        try {
            String userIdStr = authentication.getName();
            if (userIdStr == null || userIdStr.isEmpty()) {
                logger.error("❌ Authentication name is null or empty");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Invalid authentication"));
            }
            
            UUID userId;
            try {
                userId = UUID.fromString(userIdStr);
            } catch (IllegalArgumentException e) {
                logger.error("❌ Invalid UUID format in authentication name: {}", userIdStr, e);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Invalid authentication token"));
            }
            
            List<AnalysisRequest> requests = analysisRequestService.getRequestsByUserId(userId);
            List<AnalysisRequestResponse> response = requests.stream()
                .map(request -> toResponse(request, request.getStatus() == AnalysisRequest.AnalysisRequestStatus.COMPLETED))
                .collect(Collectors.toList());
            logger.info("✅ Retrieved {} analysis requests for user: {}", response.size(), userId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("❌ Invalid argument error getting user requests: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Invalid request parameters"));
        } catch (Exception e) {
            logger.error("❌ Error getting user requests: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to get requests. Please try again later."));
        }
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<?> getRequestById(@PathVariable UUID requestId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Authentication required"));
        }

        try {
            String userIdStr = authentication.getName();
            if (userIdStr == null || userIdStr.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid authentication"));
            }
            UUID userId = UUID.fromString(userIdStr);
            AnalysisRequest request = analysisRequestService.getRequestById(requestId);

            if (request.getUser() == null || request.getUser().getId() == null || !request.getUser().getId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Access denied"));
            }

            boolean includeResult = request.getStatus() == AnalysisRequest.AnalysisRequestStatus.COMPLETED;
            return ResponseEntity.ok(toResponse(request, includeResult));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Invalid request parameters"));
        } catch (Exception e) {
            logger.error("❌ Error getting analysis request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to get request. Please try again later."));
        }
    }

    private AnalysisRequestResponse toResponse(AnalysisRequest request, boolean includeResult) {
        return AnalysisRequestResponse.builder()
            .id(request.getId().toString())
            .email(request.getEmail())
            .city(request.getCity())
            .area(request.getArea())
            .buildingName(request.getBuildingName())
            .listingUrl(request.getListingUrl())
            .propertyType(request.getPropertyType())
            .bedrooms(request.getBedrooms())
            .size(request.getSize())
            .plotSize(request.getPlotSize())
            .floor(request.getFloor())
            .totalFloors(request.getTotalFloors())
            .buildingStatus(request.getBuildingStatus())
            .condition(request.getCondition())
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .askingPrice(request.getAskingPrice())
            .serviceCharge(request.getServiceCharge())
            .handoverDate(request.getHandoverDate())
            .developer(request.getDeveloper())
            .paymentPlan(request.getPaymentPlan())
            .features(request.getFeatures())
            .view(request.getView())
            .furnishing(request.getFurnishing())
            .additionalNotes(request.getAdditionalNotes())
            .filePaths(request.getFilePaths())
            .status(request.getStatus() != null ? request.getStatus().name() : "PENDING")
            .analysisResult(includeResult ? request.getAnalysisResult() : null)
            .createdAt(request.getCreatedAt() != null ? request.getCreatedAt().toString() : "")
            .updatedAt(request.getUpdatedAt() != null ? request.getUpdatedAt().toString() : "")
            .build();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class AnalysisRequestResponse {
        private String id;
        private String email;
        private String city;
        private String area;
        private String buildingName;
        private String listingUrl;
        private String propertyType;
        private String bedrooms;
        private String size;
        private String plotSize;
        private String floor;
        private String totalFloors;
        private String buildingStatus;
        private String condition;
        private String latitude;
        private String longitude;
        private String askingPrice;
        private String serviceCharge;
        private String handoverDate;
        private String developer;
        private String paymentPlan;
        private List<String> features;
        private String view;
        private String furnishing;
        private String additionalNotes;
        private List<String> filePaths;
        private String status;
        private Object analysisResult;
        private String createdAt;
        private String updatedAt;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class SubmitResponse {
        private String message;
        private String requestId;
    }
    
    @GetMapping("/report-count")
    public ResponseEntity<?> getReportCount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Authentication required"));
        }
        
        try {
            String userIdStr = authentication.getName();
            if (userIdStr == null || userIdStr.isEmpty()) {
                logger.error("❌ Authentication name is null or empty");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Invalid authentication"));
            }
            
            UUID userId;
            try {
                userId = UUID.fromString(userIdStr);
            } catch (IllegalArgumentException e) {
                logger.error("❌ Invalid UUID format in authentication name: {}", userIdStr, e);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Invalid authentication token"));
            }
            
            AnalysisRequestService.ReportCountInfo countInfo = analysisRequestService.getReportCountInfo(userId);
            return ResponseEntity.ok(countInfo);
        } catch (Exception e) {
            logger.error("❌ Error getting report count: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to get report count. Please try again later."));
        }
    }
    
    @GetMapping("/files/{filePath:.+}")
    public ResponseEntity<Resource> getFile(
            @PathVariable String filePath,
            Authentication authentication) {
        try {
            // SECURITY: Verify user is authenticated
            if (authentication == null || !authentication.isAuthenticated()) {
                logger.warn("SECURITY ALERT: Unauthenticated file access attempt: {}", filePath);
                return ResponseEntity.status(401).build();
            }
            
            // SECURITY FIX: Extract requestId from filePath and verify user owns the request
            // File path format: "analysis-requests/{requestId}/{filename}"
            UUID requestId = null;
            try {
                // Extract requestId from path: "analysis-requests/{requestId}/..."
                String pathWithoutPrefix = filePath.startsWith("analysis-requests/") 
                    ? filePath.substring("analysis-requests/".length())
                    : filePath;
                
                // Get the first part (requestId) before the next slash
                int firstSlash = pathWithoutPrefix.indexOf('/');
                if (firstSlash > 0) {
                    String requestIdStr = pathWithoutPrefix.substring(0, firstSlash);
                    requestId = UUID.fromString(requestIdStr);
                } else {
                    // If no slash, the entire path might be the requestId
                    requestId = UUID.fromString(pathWithoutPrefix);
                }
                
                // SECURITY FIX: Verify user owns the request
                UUID userId = UUID.fromString(authentication.getName());
                AnalysisRequest request = analysisRequestService.getRequestById(requestId);
                
                // Check ownership: user must own the request
                // Anonymous requests (no user) are not accessible through this authenticated endpoint
                if (request.getUser() == null || !request.getUser().getId().equals(userId)) {
                    logger.warn("SECURITY ALERT: Unauthorized file access attempt by user {} for request {} (request user: {})", 
                        userId, requestId, request.getUser() != null ? request.getUser().getId() : "anonymous");
                    return ResponseEntity.status(403).build();
                }
                
            } catch (IllegalArgumentException e) {
                logger.warn("SECURITY ALERT: Invalid requestId in file path: {}", filePath);
                return ResponseEntity.status(400).build();
            } catch (RuntimeException e) {
                // Request not found - don't reveal this information, return 404
                logger.warn("SECURITY ALERT: Request not found for file path: {}", filePath);
                return ResponseEntity.notFound().build();
            }
            
            byte[] fileContent = fileStorageService.getFile(filePath);
            ByteArrayResource resource = new ByteArrayResource(fileContent);
            
            String filename = filePath.substring(filePath.lastIndexOf('/') + 1);
            
            // SECURITY: Sanitize filename to prevent header injection
            filename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header("X-Content-Type-Options", "nosniff") // Prevent MIME sniffing
                    .body(resource);
        } catch (SecurityException e) {
            logger.error("SECURITY ALERT: Security violation accessing file: {}", filePath, e);
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            logger.error("Error serving file: {}", filePath, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    private static class ErrorResponse {
        private String error;
        
        public ErrorResponse(String error) {
            this.error = error;
        }
        
        public String getError() {
            return error;
        }
    }
    
    private static class MessageResponse {
        private String message;
        
        public MessageResponse(String message) {
            this.message = message;
        }
        
        public String getMessage() {
            return message;
        }
    }
}
