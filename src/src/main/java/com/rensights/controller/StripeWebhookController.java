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
    
    // Log controller initialization to verify it's being loaded
    public StripeWebhookController() {
        logger.info("StripeWebhookController initialized - endpoint: POST /api/webhooks/stripe");
    }
    
    @Autowired
    private InvoiceService invoiceService;
    
    @Autowired
    private com.rensights.service.EmailService emailService;
    
    @Autowired
    private com.rensights.service.SubscriptionService subscriptionService;
    
    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;
    
    /**
     * Handle Stripe webhook events
     * Endpoint: POST /api/webhooks/stripe
     */
    @PostMapping(value = {"", "/"})
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload, 
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
        
        logger.info("=== WEBHOOK ENDPOINT HIT ===");
        logger.info("Webhook received - payload length: {}, signature present: {}", 
                   payload != null ? payload.length() : 0, 
                   sigHeader != null && !sigHeader.isEmpty());
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
                    case "customer.subscription.deleted":
                        handleSubscriptionDeleted(event);
                        break;
                    case "customer.subscription.updated":
                        handleSubscriptionUpdated(event);
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
            com.rensights.model.Invoice invoice = invoiceService.processStripeInvoice(stripeInvoice);
            
            if (invoice != null && "paid".equalsIgnoreCase(invoice.getStatus()) && invoice.getInvoicePdf() != null) {
                // Send receipt email with Stripe invoice PDF link
                try {
                    com.rensights.model.User user = invoice.getUser();
                    
                    // Send receipt email with Stripe's invoice PDF link
                    String customerName = (user.getFirstName() != null ? user.getFirstName() : "") + 
                                        (user.getLastName() != null ? " " + user.getLastName() : "").trim();
                    if (customerName.isEmpty()) {
                        customerName = user.getEmail();
                    }
                    
                    emailService.sendPaymentReceiptEmail(
                        user.getEmail(),
                        customerName,
                        invoice.getInvoiceNumber(),
                        invoice.getAmount().toString(),
                        invoice.getCurrency(),
                        invoice.getInvoicePdf() // Stripe's invoice/receipt PDF URL
                    );
                    
                    logger.info("Receipt email sent with Stripe invoice PDF link for invoice: {}", invoice.getId());
                } catch (Exception e) {
                    logger.error("Error sending receipt email for invoice {}: {}", 
                               invoice.getId(), e.getMessage(), e);
                    // Don't fail the webhook if email fails
                }
            }
            
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
            
            // Store/update the invoice record with failed status
            invoiceService.processStripeInvoice(stripeInvoice);
            
            // Handle automatic downgrade to FREE tier when payment fails
            // Only process if this is a subscription invoice (not a one-time payment)
            String subscriptionId = stripeInvoice.getSubscription();
            if (subscriptionId != null && !subscriptionId.isEmpty()) {
                logger.info("Payment failed for subscription invoice, downgrading user to FREE tier");
                subscriptionService.handlePaymentFailure(subscriptionId);
            } else {
                // If no subscription ID, try using customer ID
                String customerId = stripeInvoice.getCustomer();
                if (customerId != null && !customerId.isEmpty()) {
                    logger.info("Payment failed for customer {}, downgrading to FREE tier", customerId);
                    subscriptionService.handlePaymentFailureByCustomer(customerId);
                }
            }
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
    
    /**
     * Handle subscription deleted event (when Stripe cancels subscription due to payment failure)
     */
    private void handleSubscriptionDeleted(Event event) {
        try {
            com.stripe.model.Subscription stripeSubscription = (com.stripe.model.Subscription) event.getDataObjectDeserializer()
                    .getObject()
                    .orElseThrow(() -> new RuntimeException("Failed to deserialize subscription"));
            
            logger.warn("Subscription deleted: {}", stripeSubscription.getId());
            
            // Downgrade user to FREE tier when subscription is deleted
            subscriptionService.handlePaymentFailure(stripeSubscription.getId());
        } catch (Exception e) {
            logger.error("Error handling customer.subscription.deleted: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handle subscription updated event (check for past_due or unpaid status)
     */
    private void handleSubscriptionUpdated(Event event) {
        try {
            com.stripe.model.Subscription stripeSubscription = (com.stripe.model.Subscription) event.getDataObjectDeserializer()
                    .getObject()
                    .orElseThrow(() -> new RuntimeException("Failed to deserialize subscription"));
            
            logger.info("Subscription updated: {} - status: {}", stripeSubscription.getId(), stripeSubscription.getStatus());
            
            // If subscription is past_due or unpaid, downgrade to FREE tier
            String status = stripeSubscription.getStatus();
            if ("past_due".equals(status) || "unpaid".equals(status) || "canceled".equals(status)) {
                logger.warn("Subscription {} is in {} status, downgrading user to FREE tier", 
                           stripeSubscription.getId(), status);
                subscriptionService.handlePaymentFailure(stripeSubscription.getId());
            }
        } catch (Exception e) {
            logger.error("Error handling customer.subscription.updated: {}", e.getMessage(), e);
        }
    }
}

