package com.rensights.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceResponse {
    private String id;
    private String invoiceNumber;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String invoiceUrl; // Stripe hosted invoice URL
    private String invoicePdf; // Stripe invoice/receipt PDF download URL
    private String description;
    private LocalDateTime invoiceDate;
    private LocalDateTime dueDate;
    private LocalDateTime paidAt;
}

