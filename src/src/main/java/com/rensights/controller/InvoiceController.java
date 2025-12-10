package com.rensights.controller;

import com.rensights.dto.InvoiceResponse;
import com.rensights.model.Invoice;
import com.rensights.service.InvoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {
    
    private static final Logger logger = LoggerFactory.getLogger(InvoiceController.class);
    
    @Autowired
    private InvoiceService invoiceService;
    
    /**
     * Get all invoices for current user
     */
    @GetMapping
    public ResponseEntity<?> getUserInvoices() {
        try {
            UUID userId = getCurrentUserId();
            List<Invoice> invoices = invoiceService.getUserInvoices(userId);
            
            List<InvoiceResponse> invoiceResponses = invoices.stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(invoiceResponses);
        } catch (Exception e) {
            logger.error("Error getting user invoices: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve invoices"));
        }
    }
    
    /**
     * Sync invoices from Stripe
     */
    @PostMapping("/sync")
    public ResponseEntity<?> syncInvoices() {
        try {
            UUID userId = getCurrentUserId();
            invoiceService.syncInvoicesForUser(userId);
            return ResponseEntity.ok(new MessageResponse("Invoices synced successfully"));
        } catch (Exception e) {
            logger.error("Error syncing invoices: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to sync invoices"));
        }
    }
    
    /**
     * Get invoice by ID
     */
    @GetMapping("/{invoiceId}")
    public ResponseEntity<?> getInvoice(@PathVariable UUID invoiceId) {
        try {
            UUID userId = getCurrentUserId();
            return invoiceService.getInvoice(invoiceId)
                    .map(invoice -> {
                        // Verify invoice belongs to user
                        if (!invoice.getUser().getId().equals(userId)) {
                            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                    .body(new ErrorResponse("Access denied"));
                        }
                        return ResponseEntity.ok(toResponse(invoice));
                    })
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ErrorResponse("Invoice not found")));
        } catch (Exception e) {
            logger.error("Error getting invoice: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve invoice"));
        }
    }
    
    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || 
            "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("User not authenticated");
        }
        return UUID.fromString(authentication.getName());
    }
    
    private InvoiceResponse toResponse(Invoice invoice) {
        return InvoiceResponse.builder()
                .id(invoice.getId().toString())
                .invoiceNumber(invoice.getInvoiceNumber())
                .amount(invoice.getAmount())
                .currency(invoice.getCurrency())
                .status(invoice.getStatus())
                .invoiceUrl(invoice.getInvoiceUrl())
                .invoicePdf(invoice.getInvoicePdf())
                .description(invoice.getDescription())
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(invoice.getDueDate())
                .paidAt(invoice.getPaidAt())
                .build();
    }
    
    private static class ErrorResponse {
        private String error;
        public ErrorResponse(String error) { this.error = error; }
        public String getError() { return error; }
    }
    
    private static class MessageResponse {
        private String message;
        public MessageResponse(String message) { this.message = message; }
        public String getMessage() { return message; }
    }
}
