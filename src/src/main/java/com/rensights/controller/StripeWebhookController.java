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
                    case "checkout.session.completed":
                        handleCheckoutSessionCompleted(event);
                        break;
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
    
    /**
     * Handle checkout.session.completed event
     * This fires when a Checkout Session is successfully completed
     * We can send a confirmation email here as well
     */
    private void handleCheckoutSessionCompleted(Event event) {
        try {
            com.stripe.model.checkout.Session session = (com.stripe.model.checkout.Session) event.getDataObjectDeserializer()
                    .getObject()
                    .orElseThrow(() -> new RuntimeException("Failed to deserialize checkout session"));
            
            logger.info("Processing checkout.session.completed for session: {}", session.getId());
            logger.info("Session status: {}, Customer: {}, Payment status: {}", 
                       session.getStatus(), session.getCustomer(), session.getPaymentStatus());
            
            // If payment was successful, update subscription and send confirmation email
            if ("complete".equals(session.getStatus()) && "paid".equals(session.getPaymentStatus())) {
                // CRITICAL: Update user tier and subscription when checkout completes
                try {
                    String customerId = session.getCustomer();
                    String subscriptionId = session.getSubscription();
                    String invoiceId = session.getInvoice();
                    
                    if (customerId != null) {
                        subscriptionService.handlePaymentSuccess(customerId, subscriptionId, invoiceId);
                        logger.info("✅ User tier and subscription updated from checkout session for customer: {}", customerId);
                    }
                } catch (Exception e) {
                    logger.error("❌ Error updating user tier/subscription from checkout session: {}", e.getMessage(), e);
                    // Don't fail the webhook, but log the error
                }
                
                // Send confirmation email
                try {
                    // Get customer email from Stripe
                    String customerId = session.getCustomer();
                    if (customerId != null) {
                        com.stripe.model.Customer customer = com.stripe.model.Customer.retrieve(customerId);
                        String customerEmail = customer.getEmail();
                        
                        if (customerEmail != null && !customerEmail.isEmpty()) {
                            // Try to get invoice if available
                            String invoiceId = session.getInvoice();
                            String invoicePdfUrl = null;
                            String invoiceNumber = session.getId(); // Fallback to session ID
                            
                            if (invoiceId != null) {
                                try {
                                    com.stripe.model.Invoice invoice = com.stripe.model.Invoice.retrieve(invoiceId);
                                    invoicePdfUrl = invoice.getInvoicePdf();
                                    invoiceNumber = invoice.getNumber() != null ? invoice.getNumber() : invoiceId;
                                } catch (Exception e) {
                                    logger.warn("Could not retrieve invoice {}: {}", invoiceId, e.getMessage());
                                }
                            }
                            
                            // Send confirmation email
                            String customerName = customer.getName() != null ? customer.getName() : customerEmail;
                            emailService.sendPaymentReceiptEmail(
                                customerEmail,
                                customerName,
                                invoiceNumber,
                                String.valueOf(session.getAmountTotal() != null ? session.getAmountTotal() / 100.0 : 0),
                                session.getCurrency() != null ? session.getCurrency().toUpperCase() : "USD",
                                invoicePdfUrl != null ? invoicePdfUrl : "Available in your account dashboard"
                            );
                            
                            logger.info("✅ Checkout completion email sent to: {}", customerEmail);
                        } else {
                            logger.warn("⚠️ Customer {} has no email address - cannot send email", customerId);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error sending checkout completion email: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Error handling checkout.session.completed: {}", e.getMessage(), e);
        }
    }
    
    private void handleInvoicePaymentSucceeded(Event event) {
        try {
            com.stripe.model.Invoice stripeInvoice = (com.stripe.model.Invoice) event.getDataObjectDeserializer()
                    .getObject()
                    .orElseThrow(() -> new RuntimeException("Failed to deserialize invoice"));
            
            logger.info("Processing invoice.payment_succeeded for invoice: {}", stripeInvoice.getId());
            logger.info("Invoice status: {}, Customer: {}, Amount: {}", 
                       stripeInvoice.getStatus(), stripeInvoice.getCustomer(), stripeInvoice.getAmountPaid());
            
            // Process and store invoice
            com.rensights.model.Invoice invoice = null;
            try {
                invoice = invoiceService.processStripeInvoice(stripeInvoice);
                logger.info("Invoice processed successfully: {}", invoice != null ? invoice.getId() : "null");
            } catch (Exception e) {
                logger.error("Error processing invoice in database, but will still try to send email: {}", e.getMessage());
                // Continue to send email even if invoice processing fails
            }
            
            // CRITICAL: Update user tier and subscription when payment succeeds
            // This ensures user gets upgraded immediately when payment is processed
            try {
                String stripeCustomerId = stripeInvoice.getCustomer();
                String stripeSubscriptionId = stripeInvoice.getSubscription();
                String stripeInvoiceId = stripeInvoice.getId();
                
                if (stripeCustomerId != null) {
                    subscriptionService.handlePaymentSuccess(stripeCustomerId, stripeSubscriptionId, stripeInvoiceId);
                    logger.info("✅ User tier and subscription updated for customer: {}", stripeCustomerId);
                } else {
                    logger.warn("⚠️ No customer ID in invoice - cannot update user tier");
                }
            } catch (Exception e) {
                logger.error("❌ Error updating user tier/subscription for payment success: {}", e.getMessage(), e);
                // Don't fail the webhook, but log the error
            }
            
            // Always try to send payment confirmation email when payment succeeds
            // Use invoice data if available, otherwise use Stripe invoice data directly
            try {
                String customerEmail = null;
                String customerName = null;
                String invoiceNumber = null;
                String amount = null;
                String currency = null;
                String invoicePdfUrl = null;
                
                if (invoice != null && invoice.getUser() != null) {
                    // Use our processed invoice data
                    com.rensights.model.User user = invoice.getUser();
                    customerEmail = user.getEmail();
                    customerName = (user.getFirstName() != null ? user.getFirstName() : "") + 
                                  (user.getLastName() != null ? " " + user.getLastName() : "").trim();
                    if (customerName.isEmpty()) {
                        customerName = user.getEmail();
                    }
                    invoiceNumber = invoice.getInvoiceNumber();
                    amount = invoice.getAmount().toString();
                    currency = invoice.getCurrency();
                    invoicePdfUrl = invoice.getInvoicePdf();
                } else {
                    // Fallback: Get customer email directly from Stripe
                    try {
                        String stripeCustomerId = stripeInvoice.getCustomer();
                        com.stripe.model.Customer customer = com.stripe.model.Customer.retrieve(stripeCustomerId);
                        customerEmail = customer.getEmail();
                        customerName = customer.getName() != null ? customer.getName() : customerEmail;
                        logger.info("Retrieved customer email from Stripe: {}", customerEmail);
                    } catch (Exception e) {
                        logger.error("Could not retrieve customer email from Stripe: {}", e.getMessage());
                    }
                    
                    // Use Stripe invoice data
                    invoiceNumber = stripeInvoice.getNumber();
                    amount = String.valueOf(stripeInvoice.getAmountPaid() / 100.0); // Convert cents to dollars
                    currency = stripeInvoice.getCurrency() != null ? stripeInvoice.getCurrency().toUpperCase() : "USD";
                    invoicePdfUrl = stripeInvoice.getInvoicePdf() != null ? stripeInvoice.getInvoicePdf() : 
                                   (stripeInvoice.getHostedInvoiceUrl() != null ? stripeInvoice.getHostedInvoiceUrl() : null);
                }
                
                // Send email if we have customer email
                if (customerEmail != null && !customerEmail.isEmpty()) {
                    emailService.sendPaymentReceiptEmail(
                        customerEmail,
                        customerName != null ? customerName : customerEmail,
                        invoiceNumber != null ? invoiceNumber : stripeInvoice.getId(),
                        amount != null ? amount : "0",
                        currency != null ? currency : "USD",
                        invoicePdfUrl != null ? invoicePdfUrl : "Available in your account dashboard"
                    );
                    
                    logger.info("✅ Payment confirmation email sent successfully for invoice: {} to: {}", 
                               stripeInvoice.getId(), customerEmail);
                } else {
                    logger.warn("⚠️ Cannot send email - customer email not found for invoice: {}", stripeInvoice.getId());
                }
            } catch (Exception e) {
                logger.error("❌ Error sending payment confirmation email for invoice {}: {}", 
                           stripeInvoice.getId(), e.getMessage(), e);
                // Don't fail the webhook if email fails, but log the error
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

