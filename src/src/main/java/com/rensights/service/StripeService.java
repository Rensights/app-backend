package com.rensights.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerListParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodAttachParams;
import com.stripe.param.SubscriptionCreateParams;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
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
     * Create a Stripe customer.
     * Stripe automatically sends invoice emails when payment succeeds (enabled by default).
     * We also send our own confirmation email via webhook as backup.
     */
    @CircuitBreaker(name = "stripe", fallbackMethod = "stripeFallback")
    public Customer createCustomer(String email, String name) throws StripeException {
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(email)
                .setName(name)
                .build();

        Customer customer = Customer.create(params);

        logger.info("Created Stripe customer {} - Stripe will automatically send invoice emails to: {}",
                   customer.getId(), email);
        return customer;
    }

    // Fallback for createCustomer(String email, String name)
    private Customer stripeFallback(String email, String name, Exception ex) {
        throw new RuntimeException("Stripe service temporarily unavailable. Please try again later.", ex);
    }

    /**
     * Find existing Stripe customer by email; create one if not found.
     */
    @CircuitBreaker(name = "stripe", fallbackMethod = "stripeFallback")
    public Customer findOrCreateCustomerByEmail(String email, String name) throws StripeException {
        CustomerListParams listParams = CustomerListParams.builder()
                .setEmail(email)
                .setLimit(1L)
                .build();

        List<Customer> existingCustomers = Customer.list(listParams).getData();
        if (!existingCustomers.isEmpty()) {
            Customer existingCustomer = existingCustomers.get(0);
            logger.info("Reusing existing Stripe customer {} for email {}", existingCustomer.getId(), email);
            return existingCustomer;
        }

        return createCustomer(email, name);
    }

    /**
     * Get invoice by ID.
     */
    @CircuitBreaker(name = "stripe", fallbackMethod = "getInvoiceFallback")
    public com.stripe.model.Invoice getInvoice(String invoiceId) throws StripeException {
        return com.stripe.model.Invoice.retrieve(invoiceId);
    }

    private com.stripe.model.Invoice getInvoiceFallback(String invoiceId, Exception ex) {
        throw new RuntimeException("Stripe service temporarily unavailable. Please try again later.", ex);
    }

    /**
     * Stripe SDK v32 moved invoice subscription under parent.subscription_details.subscription.
     * Keep backward compatibility with older SDKs/events that still expose getSubscription().
     */
    public String extractInvoiceSubscriptionId(com.stripe.model.Invoice invoice) {
        if (invoice == null) {
            return null;
        }

        try {
            Object direct = invoice.getClass().getMethod("getSubscription").invoke(invoice);
            if (direct instanceof String directId && !directId.isBlank()) {
                return directId;
            }
        } catch (NoSuchMethodException ignored) {
            // Newer SDK: no direct getSubscription() on Invoice
        } catch (Exception e) {
            logger.warn("Failed to read invoice subscription using direct getter: {}", e.getMessage());
        }

        try {
            Object parent = invoice.getClass().getMethod("getParent").invoke(invoice);
            if (parent == null) {
                return null;
            }
            Object subscriptionDetails = parent.getClass().getMethod("getSubscriptionDetails").invoke(parent);
            if (subscriptionDetails == null) {
                return null;
            }
            Object nested = subscriptionDetails.getClass().getMethod("getSubscription").invoke(subscriptionDetails);
            if (nested instanceof String nestedId && !nestedId.isBlank()) {
                return nestedId;
            }
        } catch (Exception e) {
            logger.warn("Failed to read invoice subscription from parent.subscription_details: {}", e.getMessage());
        }

        return null;
    }

    /**
     * List invoices for a customer.
     */
    @CircuitBreaker(name = "stripe", fallbackMethod = "listCustomerInvoicesFallback")
    public List<com.stripe.model.Invoice> listCustomerInvoices(String customerId) throws StripeException {
        Map<String, Object> params = new HashMap<>();
        params.put("customer", customerId);
        params.put("limit", 100);

        com.stripe.model.InvoiceCollection invoices = com.stripe.model.Invoice.list(params);
        return invoices.getData();
    }

    private List<com.stripe.model.Invoice> listCustomerInvoicesFallback(String customerId, Exception ex) {
        throw new RuntimeException("Stripe service temporarily unavailable. Please try again later.", ex);
    }

    /**
     * Attach payment method to customer.
     */
    @CircuitBreaker(name = "stripe", fallbackMethod = "attachPaymentMethodFallback")
    public PaymentMethod attachPaymentMethod(String paymentMethodId, String customerId) throws StripeException {
        PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);

        PaymentMethodAttachParams params = PaymentMethodAttachParams.builder()
                .setCustomer(customerId)
                .build();

        paymentMethod = paymentMethod.attach(params);
        logger.info("Attached payment method {} to customer {}", paymentMethodId, customerId);
        return paymentMethod;
    }

    private PaymentMethod attachPaymentMethodFallback(String paymentMethodId, String customerId, Exception ex) {
        throw new RuntimeException("Stripe service temporarily unavailable. Please try again later.", ex);
    }

    /**
     * Create a subscription for a customer.
     */
    @CircuitBreaker(name = "stripe", fallbackMethod = "createSubscriptionFallback")
    public Subscription createSubscription(String customerId, String priceId, String paymentMethodId) throws StripeException {
        // Set default payment method
        Customer customer = Customer.retrieve(customerId);
        Map<String, Object> params = new HashMap<>();
        params.put("invoice_settings", Map.of("default_payment_method", paymentMethodId));
        customer.update(params);

        // Note: Stripe automatically sends invoices via email when payment succeeds
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

    private Subscription createSubscriptionFallback(String customerId, String priceId, String paymentMethodId, Exception ex) {
        throw new RuntimeException("Stripe service temporarily unavailable. Please try again later.", ex);
    }

    /**
     * Cancel a subscription at period end (doesn't cancel immediately).
     * This allows the user to keep access until their billing period ends.
     */
    @CircuitBreaker(name = "stripe", fallbackMethod = "cancelSubscriptionFallback")
    public Subscription cancelSubscription(String subscriptionId) throws StripeException {
        Subscription subscription = Subscription.retrieve(subscriptionId);
        Map<String, Object> params = new HashMap<>();
        params.put("cancel_at_period_end", true);
        subscription = subscription.update(params);
        logger.info("Scheduled Stripe subscription {} to cancel at period end", subscriptionId);
        return subscription;
    }

    private Subscription cancelSubscriptionFallback(String subscriptionId, Exception ex) {
        throw new RuntimeException("Stripe service temporarily unavailable. Please try again later.", ex);
    }

    /**
     * Get subscription by ID.
     */
    @CircuitBreaker(name = "stripe", fallbackMethod = "getSubscriptionFallback")
    public Subscription getSubscription(String subscriptionId) throws StripeException {
        return Subscription.retrieve(subscriptionId);
    }

    private Subscription getSubscriptionFallback(String subscriptionId, Exception ex) {
        throw new RuntimeException("Stripe service temporarily unavailable. Please try again later.", ex);
    }

    /**
     * Create payment intent for one-time payment.
     */
    @CircuitBreaker(name = "stripe", fallbackMethod = "createPaymentIntentFallback")
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

    private PaymentIntent createPaymentIntentFallback(Long amount, String currency, String customerId, String paymentMethodId, Exception ex) {
        throw new RuntimeException("Stripe service temporarily unavailable. Please try again later.", ex);
    }

    /**
     * Create Stripe Checkout Session for subscription.
     * Stripe automatically sends receipts when payment succeeds (if enabled in Stripe Dashboard).
     * We also send our own confirmation email via webhook as backup.
     */
    @CircuitBreaker(name = "stripe", fallbackMethod = "createCheckoutSessionFallback")
    public Session createCheckoutSession(
            String stripeCustomerId,
            String priceId,
            String successUrl,
            String cancelUrl,
            String customerId,
            String checkoutType,
            String billingInterval,
            String planType
    ) throws StripeException {
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
                .setAllowPromotionCodes(true)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build())
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putMetadata("customer_id", customerId)
                .putMetadata("checkout_type", checkoutType)
                .putMetadata("billing_interval", billingInterval)
                .putMetadata("plan_type", planType)
                .putMetadata("price_id", priceId)
                .build();

        Session session = Session.create(params);
        logger.info("Created Stripe Checkout Session: {} for customer: {} (internal ID: {}, checkoutType: {}, billingInterval: {})",
                   session.getId(), stripeCustomerId, customerId, checkoutType, billingInterval);
        logger.info("Stripe will automatically send receipt to: {} (if enabled in Dashboard -> Settings -> Emails)",
                   customerEmail != null ? customerEmail : "customer email");
        return session;
    }

    private Session createCheckoutSessionFallback(
            String stripeCustomerId, String priceId, String successUrl, String cancelUrl,
            String customerId, String checkoutType, String billingInterval, String planType,
            Exception ex) {
        throw new RuntimeException("Stripe service temporarily unavailable. Please try again later.", ex);
    }

    /**
     * Create a Stripe Customer Portal session for subscription management.
     */
    @CircuitBreaker(name = "stripe", fallbackMethod = "createCustomerPortalSessionFallback")
    public com.stripe.model.billingportal.Session createCustomerPortalSession(String stripeCustomerId, String returnUrl) throws StripeException {
        com.stripe.param.billingportal.SessionCreateParams params = com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(stripeCustomerId)
                .setReturnUrl(returnUrl)
                .build();

        com.stripe.model.billingportal.Session session = com.stripe.model.billingportal.Session.create(params);
        logger.info("Created Stripe Customer Portal session: {} for customer: {}", session.getId(), stripeCustomerId);
        return session;
    }

    private com.stripe.model.billingportal.Session createCustomerPortalSessionFallback(String stripeCustomerId, String returnUrl, Exception ex) {
        throw new RuntimeException("Stripe service temporarily unavailable. Please try again later.", ex);
    }
}
