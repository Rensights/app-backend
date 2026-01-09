package com.rensights.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    
    
    @Autowired(required = false)
    private MicrosoftGraphEmailService graphEmailService;
    
    @Value("${spring.mail.from:no-reply@rensights.com}")
    private String fromEmail;
    
    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;
    
    @Value("${app.email.use-graph-api:true}")
    private boolean useGraphApi;
    
    public void sendVerificationCode(String toEmail, String code) {
        logger.info("=== EmailService.sendVerificationCode called ===");
        logger.info("Email enabled: {}", emailEnabled);
        logger.info("Use Graph API: {}", useGraphApi);
        logger.info("From email: {}", fromEmail);
        logger.info("To email: {}", toEmail);
        
        if (!emailEnabled) {
            // SECURITY FIX: Never log verification codes
            logger.warn("Email is disabled. Verification code for {}: [REDACTED]", toEmail);
            return;
        }
        
        String subject = "Rensights - Device Verification Code";
        String body = String.join("\n",
            "Your verification code is: " + code,
            "",
            "This code will expire in 5 minutes.",
            "",
            "If you didn't request this code, please ignore this email."
        );
        
        // Use Microsoft Graph API (required - no SMTP fallback)
        if (!useGraphApi || graphEmailService == null) {
            logger.error("Microsoft Graph API is not configured! Email cannot be sent.");
            throw new RuntimeException("Microsoft Graph API is required for email sending. Please configure MICROSOFT_TENANT_ID, MICROSOFT_CLIENT_ID, and MICROSOFT_CLIENT_SECRET.");
        }

        try {
            graphEmailService.sendEmail(toEmail, subject, body);
            logger.info("✅ Email sent successfully via Microsoft Graph to: {}", toEmail);
        } catch (Exception e) {
            logger.error("❌ Failed to send email via Microsoft Graph to: {}", toEmail, e);
            throw new RuntimeException("Failed to send verification email: " + e.getMessage(), e);
        }
    }
    
    public void sendPasswordResetCode(String toEmail, String code) {
        logger.info("=== EmailService.sendPasswordResetCode called ===");
        logger.info("Email enabled: {}", emailEnabled);
        logger.info("Use Graph API: {}", useGraphApi);
        logger.info("From email: {}", fromEmail);
        logger.info("To email: {}", toEmail);
        
        if (!emailEnabled) {
            // SECURITY FIX: Never log password reset codes
            logger.warn("Email is disabled. Password reset code for {}: [REDACTED]", toEmail);
            return;
        }
        
        String subject = "Rensights - Password Reset Code";
        String body = String.join("\n",
            "Your password reset code is: " + code,
            "",
            "This code will expire in 5 minutes.",
            "",
            "If you didn't request this code, please ignore this email.",
            "",
            "For security reasons, please do not share this code with anyone."
        );
        
        // Use Microsoft Graph API (required - no SMTP fallback)
        if (!useGraphApi || graphEmailService == null) {
            logger.error("Microsoft Graph API is not configured! Password reset email cannot be sent.");
            throw new RuntimeException("Microsoft Graph API is required for email sending. Please configure MICROSOFT_TENANT_ID, MICROSOFT_CLIENT_ID, and MICROSOFT_CLIENT_SECRET.");
        }

        try {
            logger.info("Sending password reset email via Microsoft Graph API to: {}", toEmail);
            graphEmailService.sendEmail(toEmail, subject, body);
            logger.info("✅ Password reset email sent successfully via Microsoft Graph API to: {}", toEmail);
        } catch (Exception e) {
            logger.error("❌ Failed to send password reset email via Microsoft Graph API to: {}", toEmail, e);
            throw new RuntimeException("Failed to send password reset email: " + e.getMessage(), e);
        }
    }
    
    public void sendAnalysisRequestNotification(String adminEmail, String requestId, String userEmail, String propertyAddress) {
        logger.info("=== EmailService.sendAnalysisRequestNotification called ===");
        logger.info("Email enabled: {}", emailEnabled);
        logger.info("Use Graph API: {}", useGraphApi);
        logger.info("Admin email: {}", adminEmail);
        logger.info("Request ID: {}", requestId);
        
        if (!emailEnabled) {
            logger.warn("Email is disabled. Analysis request notification for admin {}: Request ID {}", adminEmail, requestId);
            return;
        }
        
        String subject = "Rensights - New Property Analysis Request";
        String body = String.join("\n",
            "A new property analysis request has been submitted.",
            "",
            "Request Details:",
            "  Request ID: " + requestId,
            "  User Email: " + userEmail,
            "  Property: " + propertyAddress,
            "",
            "Please review the request in the admin dashboard.",
            "",
            "Login to admin panel to view full details and process the request."
        );
        
        // Use Microsoft Graph API (required - no SMTP fallback)
        if (!useGraphApi || graphEmailService == null) {
            logger.error("Microsoft Graph API is not configured! Analysis request notification cannot be sent.");
            logger.warn("DEV MODE: New Analysis Request - ID: {}, User: {}, Property: {}", requestId, userEmail, propertyAddress);
            return;
        }

        try {
            logger.info("Sending analysis request notification via Microsoft Graph API to admin: {}", adminEmail);
            graphEmailService.sendEmail(adminEmail, subject, body);
            logger.info("✅ Analysis request notification sent successfully via Microsoft Graph API to admin: {}", adminEmail);
        } catch (Exception e) {
            logger.error("❌ Failed to send analysis request notification via Microsoft Graph API to admin: {}", adminEmail, e);
            // Don't throw exception - email failure shouldn't block request creation
        }
    }
    
    public void sendPaymentReceiptEmail(String toEmail, String customerName, 
                                        String invoiceNumber, String amount, 
                                        String currency, String receiptPdfUrl) {
        logger.info("=== EmailService.sendPaymentReceiptEmail called ===");
        logger.info("Email enabled: {}", emailEnabled);
        logger.info("Use Graph API: {}", useGraphApi);
        logger.info("To email: {}", toEmail);
        logger.info("Invoice number: {}", invoiceNumber);
        logger.info("Receipt PDF URL: {}", receiptPdfUrl);

        if (!emailEnabled) {
            logger.warn("Email is disabled. Payment receipt for {}: Invoice {}", toEmail, invoiceNumber);
            return;
        }
        
        String subject = "Rensights - Payment Receipt";
        String body = String.join("\n",
            "Dear " + (customerName != null && !customerName.isEmpty() ? customerName : "Valued Customer") + ",",
            "",
            "Thank you for your payment!",
            "",
            "Payment Details:",
            "  Invoice Number: " + invoiceNumber,
            "  Amount: " + currency + " " + amount,
            "  Status: Paid",
            "",
            "Your payment receipt is available for download:",
            receiptPdfUrl,
            "",
            "You can also download your receipt from your account page:",
            "https://app.rensights.com/account",
            "",
            "This receipt serves as proof of payment for your records.",
            "",
            "If you have any questions or concerns, please don't hesitate to contact our support team.",
            "",
            "Best regards,",
            "Rensights Team"
        );
        
        // Use Microsoft Graph API (required - no SMTP fallback)
        if (!useGraphApi || graphEmailService == null) {
            logger.error("Microsoft Graph API is not configured! Payment receipt email cannot be sent.");
            logger.warn("DEV MODE: Payment Receipt - Invoice: {}, Amount: {} {}, Receipt URL: {}",
                       invoiceNumber, currency, amount, receiptPdfUrl);
            return;
        }

        try {
            logger.info("Sending payment receipt email via Microsoft Graph API to: {}", toEmail);
            graphEmailService.sendEmail(toEmail, subject, body);
            logger.info("✅ Payment receipt email sent successfully via Microsoft Graph API to: {}", toEmail);
        } catch (Exception e) {
            logger.error("❌ Failed to send payment receipt email via Microsoft Graph API to: {}", toEmail, e);
            throw new RuntimeException("Failed to send payment receipt email: " + e.getMessage(), e);
        }
    }
}

