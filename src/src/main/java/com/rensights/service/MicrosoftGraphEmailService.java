package com.rensights.service;

import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.core.credential.TokenRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class MicrosoftGraphEmailService {
    
    private static final Logger logger = LoggerFactory.getLogger(MicrosoftGraphEmailService.class);
    
    @Value("${microsoft.graph.tenant-id:}")
    private String tenantId;
    
    @Value("${microsoft.graph.client-id:}")
    private String clientId;
    
    @Value("${microsoft.graph.client-secret:}")
    private String clientSecret;
    
    @Value("${microsoft.graph.from-email:no-reply@rensights.com}")
    private String fromEmail;
    
    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;
    
    private GraphServiceClient<?> graphClient;
    
    /**
     * Initialize Microsoft Graph client with client credentials
     */
    private GraphServiceClient<?> getGraphClient() {
        if (graphClient == null) {
            try {
                ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                    .tenantId(tenantId)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .build();
                
                // Create custom authentication provider
                IAuthenticationProvider authProvider = new IAuthenticationProvider() {
                    @Override
                    public CompletableFuture<String> getAuthorizationTokenAsync(URL requestUrl) {
                        try {
                            TokenRequestContext tokenRequestContext = new TokenRequestContext()
                                .addScopes("https://graph.microsoft.com/.default");
                            String accessToken = credential.getToken(tokenRequestContext).block().getToken();
                            return CompletableFuture.completedFuture(accessToken);
                        } catch (Exception e) {
                            logger.error("Failed to get access token", e);
                            return CompletableFuture.failedFuture(e);
                        }
                    }
                };
                
                graphClient = GraphServiceClient.builder()
                    .authenticationProvider(authProvider)
                    .buildClient();
                
                logger.info("Microsoft Graph client initialized successfully");
            } catch (Exception e) {
                logger.error("Failed to initialize Microsoft Graph client", e);
                throw new RuntimeException("Failed to initialize Microsoft Graph client: " + e.getMessage(), e);
            }
        }
        return graphClient;
    }
    
    /**
     * Send email using Microsoft Graph API
     */
    public void sendEmail(String toEmail, String subject, String body) {
        sendEmail(toEmail, subject, body, false);
    }
    
    /**
     * Send email using Microsoft Graph API with HTML support
     */
    public void sendEmail(String toEmail, String subject, String body, boolean isHtml) {
        if (!emailEnabled) {
            logger.warn("Email is disabled. Email to {}: [REDACTED]", toEmail);
            return;
        }
        
        if (tenantId == null || tenantId.isEmpty() || 
            clientId == null || clientId.isEmpty() || 
            clientSecret == null || clientSecret.isEmpty()) {
            logger.error("Microsoft Graph credentials are not configured! Tenant: {}, Client ID: {}, Secret: {}", 
                        tenantId != null && !tenantId.isEmpty() ? "SET" : "MISSING",
                        clientId != null && !clientId.isEmpty() ? "SET" : "MISSING",
                        clientSecret != null && !clientSecret.isEmpty() ? "SET" : "MISSING");
            throw new RuntimeException("Microsoft Graph credentials are not configured. Please set MICROSOFT_TENANT_ID, MICROSOFT_CLIENT_ID, and MICROSOFT_CLIENT_SECRET environment variables.");
        }
        
        try {
            GraphServiceClient<?> client = getGraphClient();
            
            // Create message
            Message message = new Message();
            message.subject = subject;
            
            // Set body
            ItemBody itemBody = new ItemBody();
            itemBody.contentType = isHtml ? BodyType.HTML : BodyType.TEXT;
            itemBody.content = body;
            message.body = itemBody;
            
            // Set recipients
            List<Recipient> toRecipients = new LinkedList<>();
            Recipient recipient = new Recipient();
            EmailAddress emailAddress = new EmailAddress();
            emailAddress.address = toEmail;
            recipient.emailAddress = emailAddress;
            toRecipients.add(recipient);
            message.toRecipients = toRecipients;
            
            // Send email from the specified mailbox
            // Format: users/{email} or users/{userId}
            String userPrincipalName = fromEmail;
            if (!userPrincipalName.contains("@")) {
                userPrincipalName = fromEmail + "@rensights.com";
            }
            
            logger.info("Sending email via Microsoft Graph from {} to {}", fromEmail, toEmail);
            
            client.users(userPrincipalName)
                .sendMail(com.microsoft.graph.models.UserSendMailParameterSet.newBuilder()
                    .withMessage(message)
                    .build())
                .buildRequest()
                .post();
            
            logger.info("✅ Email sent successfully via Microsoft Graph to: {}", toEmail);
        } catch (Exception e) {
            logger.error("❌ Failed to send email via Microsoft Graph to: {}", toEmail, e);
            throw new RuntimeException("Failed to send email via Microsoft Graph: " + e.getMessage(), e);
        }
    }
}

