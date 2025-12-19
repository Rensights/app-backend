package com.rensights.service;

import com.rensights.model.AnalysisRequest;
import com.rensights.model.User;
import com.rensights.repository.AnalysisRequestRepository;
import com.rensights.repository.UserRepository;
import com.rensights.util.InputValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public class AnalysisRequestService {
    
    private static final Logger logger = LoggerFactory.getLogger(AnalysisRequestService.class);
    
    @Autowired
    private AnalysisRequestRepository analysisRequestRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private FileStorageService fileStorageService;
    
    @Autowired(required = false)
    private EmailService emailService;
    
    @Value("${app.admin.email:admin@rensights.com}")
    private String adminEmail;
    
    @Transactional
    public AnalysisRequest createAnalysisRequest(
            String email,
            UUID userId,
            String city,
            String area,
            String buildingName,
            String listingUrl,
            String propertyType,
            String bedrooms,
            String size,
            String plotSize,
            String floor,
            String totalFloors,
            String buildingStatus,
            String condition,
            String latitude,
            String longitude,
            String askingPrice,
            String serviceCharge,
            String handoverDate,
            String developer,
            String paymentPlan,
            List<String> features,
            String view,
            String furnishing,
            String additionalNotes,
            MultipartFile[] files
    ) throws Exception {
        // SECURITY: Validate and sanitize all inputs before processing
        // Note: Basic validation already done in controller, but add defensive validation here too
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        
        // Sanitize all string inputs to prevent injection and control character attacks
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
        
        // Get user if authenticated
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        
        // Check report limits based on user tier
        if (user != null) {
            java.time.LocalDateTime oneMonthAgo = java.time.LocalDateTime.now().minusMonths(1);
            long reportsThisMonth = analysisRequestRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(req -> req.getCreatedAt() != null && req.getCreatedAt().isAfter(oneMonthAgo))
                .count();
            
            int maxReports;
            if (user.getUserTier() == com.rensights.model.UserTier.FREE) {
                maxReports = 1;
            } else if (user.getUserTier() == com.rensights.model.UserTier.PREMIUM) {
                maxReports = 5;
            } else {
                maxReports = Integer.MAX_VALUE; // Enterprise/Trusted Advisor - unlimited
            }
            
            if (reportsThisMonth >= maxReports) {
                throw new IllegalStateException(
                    String.format("You have reached your monthly report limit (%d report%s). %s", 
                        maxReports,
                        maxReports == 1 ? "" : "s",
                        user.getUserTier() == com.rensights.model.UserTier.FREE 
                            ? "Upgrade to Standard Package for 5 reports per month." 
                            : "Please wait until next month or upgrade your plan.")
                );
            }
        }
        
        // Create analysis request with sanitized inputs
        AnalysisRequest request = AnalysisRequest.builder()
                .user(user)
                .email(email.trim().toLowerCase()) // Normalize email
                .city(city)
                .area(area)
                .buildingName(buildingName)
                .listingUrl(listingUrl) // URL already validated in controller
                .propertyType(propertyType)
                .bedrooms(bedrooms)
                .size(size)
                .plotSize(plotSize)
                .floor(floor)
                .totalFloors(totalFloors)
                .buildingStatus(buildingStatus)
                .condition(condition)
                .latitude(latitude) // Already validated in controller
                .longitude(longitude) // Already validated in controller
                .askingPrice(askingPrice)
                .serviceCharge(serviceCharge)
                .handoverDate(handoverDate)
                .developer(developer)
                .paymentPlan(paymentPlan)
                .features(features) // List already sanitized in controller
                .view(view)
                .furnishing(furnishing)
                .additionalNotes(additionalNotes)
                .status(AnalysisRequest.AnalysisRequestStatus.PENDING)
                .build();
        
        // Save request first to get ID
        request = analysisRequestRepository.save(request);
        
        // Store files if provided
        if (files != null && files.length > 0) {
            try {
                List<String> filePaths = fileStorageService.storeFiles(files, request.getId());
                request.setFilePaths(filePaths);
                request = analysisRequestRepository.save(request);
            } catch (Exception e) {
                logger.error("Error storing files for analysis request: {}", request.getId(), e);
                // Continue without files rather than failing the entire request
            }
        }
        
        logger.info("Created analysis request: {} for email: {}", request.getId(), email);
        
        // Send email notification to admin
        if (emailService != null) {
            try {
                String propertyAddress = String.format("%s, %s, %s", buildingName, area, city);
                emailService.sendAnalysisRequestNotification(
                    adminEmail,
                    request.getId().toString(),
                    email,
                    propertyAddress
                );
                logger.info("✅ Admin notification email sent for analysis request: {}", request.getId());
            } catch (Exception e) {
                logger.error("Failed to send admin notification email for analysis request: {}", request.getId(), e);
                // Don't fail the request creation if email fails
            }
        }
        
        return request;
    }
    
    public Page<AnalysisRequest> getAllRequests(Pageable pageable) {
        return analysisRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
    
    public AnalysisRequest getRequestById(UUID id) {
        return analysisRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Analysis request not found"));
    }
    
    public List<AnalysisRequest> getRequestsByEmail(String email) {
        return analysisRequestRepository.findByEmailOrderByCreatedAtDesc(email);
    }
    
    public List<AnalysisRequest> getRequestsByUserId(UUID userId) {
        return analysisRequestRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    /**
     * Get report count information for a user (used, remaining, max)
     */
    public ReportCountInfo getReportCountInfo(UUID userId) {
        com.rensights.model.User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return new ReportCountInfo(0, 0, 0);
        }
        
        java.time.LocalDateTime oneMonthAgo = java.time.LocalDateTime.now().minusMonths(1);
        long reportsThisMonth = analysisRequestRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .filter(req -> req.getCreatedAt() != null && req.getCreatedAt().isAfter(oneMonthAgo))
            .count();
        
        int maxReports;
        if (user.getUserTier() == com.rensights.model.UserTier.FREE) {
            maxReports = 1;
        } else if (user.getUserTier() == com.rensights.model.UserTier.PREMIUM) {
            maxReports = 5;
        } else {
            maxReports = Integer.MAX_VALUE; // Enterprise/Trusted Advisor - unlimited
        }
        
        long remaining = maxReports == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, maxReports - reportsThisMonth);
        
        return new ReportCountInfo((int) reportsThisMonth, (int) remaining, maxReports);
    }
    
    /**
     * DTO for report count information
     */
    public static class ReportCountInfo {
        private int used;
        private int remaining;
        private int max;
        
        public ReportCountInfo(int used, int remaining, int max) {
            this.used = used;
            this.remaining = remaining;
            this.max = max;
        }
        
        public int getUsed() { return used; }
        public void setUsed(int used) { this.used = used; }
        
        public int getRemaining() { return remaining; }
        public void setRemaining(int remaining) { this.remaining = remaining; }
        
        public int getMax() { return max; }
        public void setMax(int max) { this.max = max; }
    }
    
    @Transactional
    public AnalysisRequest updateStatus(UUID id, AnalysisRequest.AnalysisRequestStatus status) {
        AnalysisRequest request = getRequestById(id);
        request.setStatus(status);
        return analysisRequestRepository.save(request);
    }
    
    public long getPendingCount() {
        return analysisRequestRepository.countByStatus(AnalysisRequest.AnalysisRequestStatus.PENDING);
    }
}

