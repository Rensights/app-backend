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
    private JavaMailSender mailSender;
    
    @Value("${spring.mail.from:no-reply@rensights.com}")
    private String fromEmail;
    
    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;
    
    public void sendVerificationCode(String toEmail, String code) {
        logger.info("=== EmailService.sendVerificationCode called ===");
        logger.info("Email enabled: {}", emailEnabled);
        logger.info("MailSender available: {}", mailSender != null);
        logger.info("From email: {}", fromEmail);
        logger.info("To email: {}", toEmail);
        
        if (!emailEnabled) {
            // SECURITY FIX: Never log verification codes
            logger.warn("Email is disabled. Verification code for {}: [REDACTED]", toEmail);
            return;
        }
        
        if (mailSender == null) {
            logger.error("JavaMailSender is not available! Email configuration may be missing.");
            // SECURITY FIX: Never log verification codes - even in dev mode
            logger.warn("DEV MODE: Verification Code for {}: [REDACTED]", toEmail);
            return;
        }
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Rensights - Device Verification Code");
            // Optimized: Use String.join for cleaner string building
            message.setText(String.join("\n",
                "Your verification code is: " + code,
                "",
                "This code will expire in 5 minutes.",
                "",
                "If you didn't request this code, please ignore this email."
            ));
            
            logger.info("Attempting to send email to: {}", toEmail);
            mailSender.send(message);
            logger.info("✅ Email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            logger.error("❌ Failed to send email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send verification email: " + e.getMessage(), e);
        }
    }
    
    public void sendPasswordResetCode(String toEmail, String code) {
        logger.info("=== EmailService.sendPasswordResetCode called ===");
        logger.info("Email enabled: {}", emailEnabled);
        logger.info("MailSender available: {}", mailSender != null);
        logger.info("From email: {}", fromEmail);
        logger.info("To email: {}", toEmail);
        
        if (!emailEnabled) {
            // SECURITY FIX: Never log password reset codes
            logger.warn("Email is disabled. Password reset code for {}: [REDACTED]", toEmail);
            return;
        }
        
        if (mailSender == null) {
            logger.error("JavaMailSender is not available! Email configuration may be missing.");
            // SECURITY FIX: Never log password reset codes - even in dev mode
            logger.warn("DEV MODE: Password Reset Code for {}: [REDACTED]", toEmail);
            return;
        }
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Rensights - Password Reset Code");
            message.setText(String.join("\n",
                "Your password reset code is: " + code,
                "",
                "This code will expire in 5 minutes.",
                "",
                "If you didn't request this code, please ignore this email.",
                "",
                "For security reasons, please do not share this code with anyone."
            ));
            
            logger.info("Attempting to send password reset email to: {}", toEmail);
            mailSender.send(message);
            logger.info("✅ Password reset email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            logger.error("❌ Failed to send password reset email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send password reset email: " + e.getMessage(), e);
        }
    }
    
    public void sendAnalysisRequestNotification(String adminEmail, String requestId, String userEmail, String propertyAddress) {
        logger.info("=== EmailService.sendAnalysisRequestNotification called ===");
        logger.info("Email enabled: {}", emailEnabled);
        logger.info("MailSender available: {}", mailSender != null);
        logger.info("Admin email: {}", adminEmail);
        logger.info("Request ID: {}", requestId);
        
        if (!emailEnabled) {
            logger.warn("Email is disabled. Analysis request notification for admin {}: Request ID {}", adminEmail, requestId);
            return;
        }
        
        if (mailSender == null) {
            logger.error("JavaMailSender is not available! Email configuration may be missing.");
            logger.warn("DEV MODE: New Analysis Request - ID: {}, User: {}, Property: {}", requestId, userEmail, propertyAddress);
            return;
        }
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(adminEmail);
            message.setSubject("Rensights - New Property Analysis Request");
            message.setText(String.join("\n",
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
            ));
            
            logger.info("Attempting to send analysis request notification to admin: {}", adminEmail);
            mailSender.send(message);
            logger.info("✅ Analysis request notification sent successfully to admin: {}", adminEmail);
        } catch (Exception e) {
            logger.error("❌ Failed to send analysis request notification to admin: {}", adminEmail, e);
            // Don't throw exception - email failure shouldn't block request creation
        }
    }
    
    public void sendPaymentReceiptEmail(String toEmail, String customerName, 
                                        String invoiceNumber, String amount, 
                                        String currency, String receiptPdfUrl) {
        logger.info("=== EmailService.sendPaymentReceiptEmail called ===");
        logger.info("Email enabled: {}", emailEnabled);
        logger.info("MailSender available: {}", mailSender != null);
        logger.info("To email: {}", toEmail);
        logger.info("Invoice number: {}", invoiceNumber);
        logger.info("Receipt PDF URL: {}", receiptPdfUrl);
        
        if (!emailEnabled) {
            logger.warn("Email is disabled. Payment receipt for {}: Invoice {}", toEmail, invoiceNumber);
            return;
        }
        
        if (mailSender == null) {
            logger.error("JavaMailSender is not available! Email configuration may be missing.");
            logger.warn("DEV MODE: Payment Receipt - Invoice: {}, Amount: {} {}, Receipt URL: {}", 
                       invoiceNumber, currency, amount, receiptPdfUrl);
            return;
        }
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Rensights - Payment Receipt");
            
            // Email body with HTML for better formatting
            String emailBody = String.join("\n",
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
            
            helper.setText(emailBody, false); // Plain text email
            
            logger.info("Attempting to send payment receipt email to: {}", toEmail);
            mailSender.send(message);
            logger.info("✅ Payment receipt email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("❌ Failed to send payment receipt email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send payment receipt email: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("❌ Failed to send payment receipt email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send payment receipt email: " + e.getMessage(), e);
        }
    }
}

