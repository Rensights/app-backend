package com.rensights.controller;

import com.rensights.service.InvoiceService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks/stripe")
public class StripeWebhookController {
    
    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookController.class);
    
    @Autowired
    private InvoiceService invoiceService;
    
    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;
    
    /**
     * Handle Stripe webhook events
     */
    @PostMapping
    public ResponseEntity<String> handleWebhook(@RequestBody String payload, 
                                                @RequestHeader("Stripe-Signature") String sigHeader) {
        if (webhookSecret == null || webhookSecret.isEmpty()) {
            logger.warn("Stripe webhook secret not configured - skipping signature verification");
            // In development, you might want to process without verification
            // In production, always verify signatures
        } else {
            try {
                Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
                logger.info("Received Stripe webhook event: {}", event.getType());
                
                // Handle the event
                switch (event.getType()) {
                    case "invoice.payment_succeeded":
                        handleInvoicePaymentSucceeded(event);
                        break;
                    case "invoice.payment_failed":
                        handleInvoicePaymentFailed(event);
                        break;
                    case "invoice.created":
                        handleInvoiceCreated(event);
                        break;
                    case "invoice.updated":
                        handleInvoiceUpdated(event);
                        break;
                    default:
                        logger.info("Unhandled event type: {}", event.getType());
                }
                
                return ResponseEntity.ok("Webhook processed successfully");
            } catch (SignatureVerificationException e) {
                logger.error("Invalid webhook signature: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
            } catch (Exception e) {
                logger.error("Error processing webhook: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook processing failed");
            }
        }
        
        // If no webhook secret configured, try to process anyway (development only)
        try {
            // In production, you should never skip signature verification
            logger.warn("Processing webhook without signature verification (development mode)");
            // Parse event manually for development
            return ResponseEntity.ok("Webhook received (signature verification skipped)");
        } catch (Exception e) {
            logger.error("Error processing webhook without verification: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook processing failed");
        }
    }
    
    private void handleInvoicePaymentSucceeded(Event event) {
        try {
            com.stripe.model.Invoice stripeInvoice = (com.stripe.model.Invoice) event.getDataObjectDeserializer()
                    .getObject()
                    .orElseThrow(() -> new RuntimeException("Failed to deserialize invoice"));
            
            logger.info("Processing invoice.payment_succeeded for invoice: {}", stripeInvoice.getId());
            
            // Process and store invoice
            invoiceService.processStripeInvoice(stripeInvoice);
            
            logger.info("Successfully processed invoice: {}", stripeInvoice.getId());
        } catch (Exception e) {
            logger.error("Error handling invoice.payment_succeeded: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process invoice payment", e);
        }
    }
    
    private void handleInvoicePaymentFailed(Event event) {
        try {
            com.stripe.model.Invoice stripeInvoice = (com.stripe.model.Invoice) event.getDataObjectDeserializer()
                    .getObject()
                    .orElseThrow(() -> new RuntimeException("Failed to deserialize invoice"));
            
            logger.warn("Invoice payment failed for invoice: {}", stripeInvoice.getId());
            
            // Still store/update the invoice record with failed status
            invoiceService.processStripeInvoice(stripeInvoice);
        } catch (Exception e) {
            logger.error("Error handling invoice.payment_failed: {}", e.getMessage(), e);
        }
    }
    
    private void handleInvoiceCreated(Event event) {
        try {
            com.stripe.model.Invoice stripeInvoice = (com.stripe.model.Invoice) event.getDataObjectDeserializer()
                    .getObject()
                    .orElseThrow(() -> new RuntimeException("Failed to deserialize invoice"));
            
            logger.info("Processing invoice.created for invoice: {}", stripeInvoice.getId());
            invoiceService.processStripeInvoice(stripeInvoice);
        } catch (Exception e) {
            logger.error("Error handling invoice.created: {}", e.getMessage(), e);
        }
    }
    
    private void handleInvoiceUpdated(Event event) {
        try {
            com.stripe.model.Invoice stripeInvoice = (com.stripe.model.Invoice) event.getDataObjectDeserializer()
                    .getObject()
                    .orElseThrow(() -> new RuntimeException("Failed to deserialize invoice"));
            
            logger.info("Processing invoice.updated for invoice: {}", stripeInvoice.getId());
            invoiceService.processStripeInvoice(stripeInvoice);
        } catch (Exception e) {
            logger.error("Error handling invoice.updated: {}", e.getMessage(), e);
        }
    }
}
