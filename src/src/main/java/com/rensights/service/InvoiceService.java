package com.rensights.service;

import com.rensights.model.Invoice;
import com.rensights.model.User;
import com.rensights.repository.InvoiceRepository;
import com.rensights.repository.UserRepository;
import com.stripe.exception.StripeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InvoiceService {
    
    private static final Logger logger = LoggerFactory.getLogger(InvoiceService.class);
    
    @Autowired
    private InvoiceRepository invoiceRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private StripeService stripeService;
    
    @Autowired
    private ConfirmationPdfService confirmationPdfService;
    
    /**
     * Process invoice from Stripe webhook
     */
    @Transactional
    public Invoice processStripeInvoice(com.stripe.model.Invoice stripeInvoice) {
        try {
            String stripeCustomerId = stripeInvoice.getCustomer();
            
            // Find user by Stripe customer ID
            User user = userRepository.findByStripeCustomerId(stripeCustomerId)
                    .orElse(null);
            
            if (user == null) {
                logger.error("User not found for Stripe customer ID: {}", stripeCustomerId);
                return null;
            }
            
            // Check if invoice already exists
            Optional<Invoice> existingInvoice = invoiceRepository.findByStripeInvoiceId(stripeInvoice.getId());
            if (existingInvoice.isPresent()) {
                logger.info("Invoice {} already exists, updating...", stripeInvoice.getId());
                return updateInvoiceFromStripe(existingInvoice.get(), stripeInvoice);
            }
            
            // Create new invoice record
            Invoice invoice = Invoice.builder()
                    .user(user)
                    .stripeInvoiceId(stripeInvoice.getId())
                    .stripeCustomerId(stripeCustomerId)
                    .stripeSubscriptionId(stripeInvoice.getSubscription())
                    .amount(BigDecimal.valueOf(stripeInvoice.getAmountPaid()).divide(BigDecimal.valueOf(100))) // Convert cents to dollars
                    .currency(stripeInvoice.getCurrency() != null ? stripeInvoice.getCurrency().toUpperCase() : "USD")
                    .status(stripeInvoice.getStatus())
                    .invoiceUrl(stripeInvoice.getHostedInvoiceUrl())
                    .invoicePdf(stripeInvoice.getInvoicePdf())
                    .invoiceNumber(stripeInvoice.getNumber())
                    .description(stripeInvoice.getDescription())
                    .invoiceDate(stripeInvoice.getCreated() != null ? 
                            LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeInvoice.getCreated()), ZoneId.systemDefault()) : null)
                    .dueDate(stripeInvoice.getDueDate() != null ? 
                            LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeInvoice.getDueDate()), ZoneId.systemDefault()) : null)
                    .paidAt(stripeInvoice.getStatusTransitions() != null && stripeInvoice.getStatusTransitions().getPaidAt() != null ?
                            LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeInvoice.getStatusTransitions().getPaidAt()), ZoneId.systemDefault()) : null)
                    .build();
            
            invoice = invoiceRepository.save(invoice);
            logger.info("Saved invoice {} for user {}", invoice.getId(), user.getId());
            
            return invoice;
        } catch (Exception e) {
            logger.error("Error processing Stripe invoice {}: {}", stripeInvoice.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to process invoice", e);
        }
    }
    
    /**
     * Update existing invoice from Stripe
     */
    @Transactional
    public Invoice updateInvoiceFromStripe(Invoice invoice, com.stripe.model.Invoice stripeInvoice) {
        invoice.setAmount(BigDecimal.valueOf(stripeInvoice.getAmountPaid()).divide(BigDecimal.valueOf(100)));
        invoice.setCurrency(stripeInvoice.getCurrency() != null ? stripeInvoice.getCurrency().toUpperCase() : "USD");
        invoice.setStatus(stripeInvoice.getStatus());
        invoice.setInvoiceUrl(stripeInvoice.getHostedInvoiceUrl());
        invoice.setInvoicePdf(stripeInvoice.getInvoicePdf());
        invoice.setInvoiceNumber(stripeInvoice.getNumber());
        invoice.setDescription(stripeInvoice.getDescription());
        
        if (stripeInvoice.getStatusTransitions() != null && stripeInvoice.getStatusTransitions().getPaidAt() != null) {
            invoice.setPaidAt(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(stripeInvoice.getStatusTransitions().getPaidAt()), 
                    ZoneId.systemDefault()));
        }
        
        invoice = invoiceRepository.save(invoice);
        logger.info("Updated invoice {}", invoice.getId());
        
        return invoice;
    }
    
    /**
     * Sync invoices from Stripe for a user
     */
    @Transactional
    public void syncInvoicesForUser(UUID userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            if (user.getStripeCustomerId() == null || user.getStripeCustomerId().isEmpty()) {
                logger.warn("User {} does not have a Stripe customer ID", userId);
                return;
            }
            
            List<com.stripe.model.Invoice> stripeInvoices = stripeService.listCustomerInvoices(user.getStripeCustomerId());
            
            for (com.stripe.model.Invoice stripeInvoice : stripeInvoices) {
                Optional<Invoice> existing = invoiceRepository.findByStripeInvoiceId(stripeInvoice.getId());
                if (existing.isPresent()) {
                    updateInvoiceFromStripe(existing.get(), stripeInvoice);
                } else {
                    processStripeInvoice(stripeInvoice);
                }
            }
            
            logger.info("Synced {} invoices for user {}", stripeInvoices.size(), userId);
        } catch (StripeException e) {
            logger.error("Error syncing invoices for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to sync invoices", e);
        }
    }
    
    /**
     * Get all invoices for a user
     */
    public List<Invoice> getUserInvoices(UUID userId) {
        return invoiceRepository.findByUserIdOrderByInvoiceDateDesc(userId);
    }
    
    /**
     * Get invoice by ID
     */
    public Optional<Invoice> getInvoice(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId);
    }
    
    /**
     * Save invoice (for updating confirmation PDF path)
     */
    @Transactional
    public Invoice saveInvoice(Invoice invoice) {
        return invoiceRepository.save(invoice);
    }
    
    /**
     * Get confirmation PDF file
     */
    public java.io.File getConfirmationPdfFile(String filename) {
        return confirmationPdfService.getConfirmationPdfFile(filename);
    }
}
