package com.rensights.controller;

import com.rensights.model.AnalysisRequest;
import com.rensights.service.AnalysisRequestService;
import com.rensights.service.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
            return ResponseEntity.ok(new MessageResponse(
                    "Your property price analysis request has been submitted successfully! " +
                    "You will receive a comprehensive price analysis report via email within 24-48 hours."
            ));
        } catch (IllegalArgumentException e) {
            logger.error("❌ Validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("❌ Error creating analysis request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to submit analysis request: " + e.getMessage()));
        }
    }
    
    @GetMapping("/my-requests")
    public ResponseEntity<?> getMyRequests(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Authentication required"));
        }
        
        try {
            UUID userId = UUID.fromString(authentication.getName());
            List<AnalysisRequest> requests = analysisRequestService.getRequestsByUserId(userId);
            return ResponseEntity.ok(requests);
        } catch (Exception e) {
            logger.error("❌ Error getting user requests: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to get requests: " + e.getMessage()));
        }
    }
    
    @GetMapping("/files/{filePath:.+}")
    public ResponseEntity<Resource> getFile(@PathVariable String filePath) {
        try {
            byte[] fileContent = fileStorageService.getFile(filePath);
            ByteArrayResource resource = new ByteArrayResource(fileContent);
            
            String filename = filePath.substring(filePath.lastIndexOf('/') + 1);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
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

