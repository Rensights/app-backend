package com.rensights.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Subscription;
import com.stripe.model.billingportal.Session;
import com.stripe.model.checkout.Session;
import com.stripe.param.billingportal.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodAttachParams;
import com.stripe.param.SubscriptionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Service
public class StripeService {
    
    private static final Logger logger = LoggerFactory.getLogger(StripeService.class);
    
    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;
    
    @PostConstruct
    public void init() {
        if (stripeSecretKey != null && !stripeSecretKey.isEmpty()) {
            Stripe.apiKey = stripeSecretKey;
            logger.info("Stripe API key initialized");
        } else {
            logger.warn("Stripe secret key not configured");
        }
    }
    
    /**
     * Create a Stripe customer
     * Stripe automatically sends invoice emails when payment succeeds (enabled by default)
     * We also send our own confirmation email via webhook as backup
     */
    public Customer createCustomer(String email, String name) throws StripeException {
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(email)
                .setName(name)
                .build();
        
        Customer customer = Customer.create(params);
        
        // Stripe automatically sends invoice emails when payment succeeds
        // This is enabled by default in Stripe dashboard settings
        // We also send our own confirmation email via webhook as backup
        logger.info("Created Stripe customer {} - Stripe will automatically send invoice emails to: {}", 
                   customer.getId(), email);
        return customer;
    }
    
    /**
     * Get invoice by ID
     */
    public com.stripe.model.Invoice getInvoice(String invoiceId) throws StripeException {
        return com.stripe.model.Invoice.retrieve(invoiceId);
    }
    
    /**
     * List invoices for a customer
     */
    public java.util.List<com.stripe.model.Invoice> listCustomerInvoices(String customerId) throws StripeException {
        Map<String, Object> params = new HashMap<>();
        params.put("customer", customerId);
        params.put("limit", 100);
        
        com.stripe.model.InvoiceCollection invoices = com.stripe.model.Invoice.list(params);
        return invoices.getData();
    }
    
    /**
     * Attach payment method to customer
     */
    public PaymentMethod attachPaymentMethod(String paymentMethodId, String customerId) throws StripeException {
        PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
        
        PaymentMethodAttachParams params = PaymentMethodAttachParams.builder()
                .setCustomer(customerId)
                .build();
        
        paymentMethod = paymentMethod.attach(params);
        logger.info("Attached payment method {} to customer {}", paymentMethodId, customerId);
        return paymentMethod;
    }
    
    /**
     * Create a subscription for a customer
     */
    public Subscription createSubscription(String customerId, String priceId, String paymentMethodId) throws StripeException {
        // Set default payment method
        Customer customer = Customer.retrieve(customerId);
        Map<String, Object> params = new HashMap<>();
        params.put("invoice_settings", Map.of("default_payment_method", paymentMethodId));
        customer.update(params);
        
        // Create subscription
        // Note: Stripe automatically sends invoices via email when payment succeeds
        // This is enabled by default in Stripe dashboard settings
        SubscriptionCreateParams subscriptionParams = SubscriptionCreateParams.builder()
                .setCustomer(customerId)
                .addItem(SubscriptionCreateParams.Item.builder()
                        .setPrice(priceId)
                        .build())
                .setDefaultPaymentMethod(paymentMethodId)
                .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE)
                .build();
        
        Subscription subscription = Subscription.create(subscriptionParams);
        logger.info("Created Stripe subscription: {} for customer: {}", subscription.getId(), customerId);
        return subscription;
    }
    
    /**
     * Cancel a subscription at period end (doesn't cancel immediately)
     * This allows the user to keep access until their billing period ends
     */
    public Subscription cancelSubscription(String subscriptionId) throws StripeException {
        Subscription subscription = Subscription.retrieve(subscriptionId);
        Map<String, Object> params = new HashMap<>();
        params.put("cancel_at_period_end", true);
        subscription = subscription.update(params);
        logger.info("Scheduled Stripe subscription {} to cancel at period end", subscriptionId);
        return subscription;
    }
    
    /**
     * Get subscription by ID
     */
    public Subscription getSubscription(String subscriptionId) throws StripeException {
        return Subscription.retrieve(subscriptionId);
    }
    
    /**
     * Create payment intent for one-time payment
     */
    public PaymentIntent createPaymentIntent(Long amount, String currency, String customerId, String paymentMethodId) throws StripeException {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency(currency)
                .setCustomer(customerId)
                .setPaymentMethod(paymentMethodId)
                .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.AUTOMATIC)
                .setConfirm(true)
                .build();
        
        PaymentIntent paymentIntent = PaymentIntent.create(params);
        logger.info("Created payment intent: {}", paymentIntent.getId());
        return paymentIntent;
    }
    
    /**
     * Create Stripe Checkout Session for subscription
     * Stripe automatically sends receipts when payment succeeds (if enabled in Stripe Dashboard)
     * According to Stripe docs: https://docs.stripe.com/receipts
     * - Checkout Sessions automatically send receipts when payment succeeds
     * - Ensure "Successful payments" is enabled in Stripe Dashboard → Settings → Emails
     * We also send our own confirmation email via webhook as backup
     */
    public Session createCheckoutSession(String stripeCustomerId, String priceId, String successUrl, String cancelUrl, String customerId) throws StripeException {
        // Retrieve customer to ensure email is set (required for receipts)
        Customer customer = Customer.retrieve(stripeCustomerId);
        String customerEmail = customer.getEmail();
        
        if (customerEmail == null || customerEmail.isEmpty()) {
            logger.warn("Customer {} has no email set - Stripe receipts may not be sent", stripeCustomerId);
        } else {
            logger.info("Customer {} has email {} - Stripe will send receipts automatically if enabled in dashboard", 
                       stripeCustomerId, customerEmail);
        }
        
        SessionCreateParams params = SessionCreateParams.builder()
                .setCustomer(stripeCustomerId)
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build())
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putMetadata("customer_id", customerId) // Pass our internal customer ID as metadata
                // Note: Stripe automatically sends receipts for Checkout Sessions when payment succeeds
                // This is controlled by Dashboard settings: Settings → Emails → "Successful payments"
                .build();
        
        Session session = Session.create(params);
        logger.info("Created Stripe Checkout Session: {} for customer: {} (internal ID: {})", 
                   session.getId(), stripeCustomerId, customerId);
        logger.info("Stripe will automatically send receipt to: {} (if enabled in Dashboard → Settings → Emails)", 
                   customerEmail != null ? customerEmail : "customer email");
        return session;
    }

    /**
     * Create a Stripe Customer Portal session for subscription management.
     */
    public Session createCustomerPortalSession(String stripeCustomerId, String returnUrl) throws StripeException {
        SessionCreateParams params = SessionCreateParams.builder()
                .setCustomer(stripeCustomerId)
                .setReturnUrl(returnUrl)
                .build();

        Session session = Session.create(params);
        logger.info("Created Stripe Customer Portal session: {} for customer: {}", session.getId(), stripeCustomerId);
        return session;
    }
}
