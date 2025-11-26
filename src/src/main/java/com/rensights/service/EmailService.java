package com.rensights.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

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
            logger.warn("Email is disabled. Verification code for {}: {}", toEmail, code);
            return;
        }
        
        if (mailSender == null) {
            logger.error("JavaMailSender is not available! Email configuration may be missing.");
            logger.warn("DEV MODE: Verification Code for {}: {}", toEmail, code);
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
                "This code will expire in 10 minutes.",
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
}

