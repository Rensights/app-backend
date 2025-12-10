package com.rensights.repository;

import com.rensights.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByUserIdOrderByInvoiceDateDesc(UUID userId);
    
    Optional<Invoice> findByStripeInvoiceId(String stripeInvoiceId);
    
    List<Invoice> findByUserIdAndStatus(UUID userId, String status);
}
