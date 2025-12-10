package com.rensights.service;

import com.rensights.model.Invoice;
import com.rensights.model.User;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ConfirmationPdfService {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfirmationPdfService.class);
    
    @Value("${app.confirmation-pdf.storage-path:./confirmation-pdfs}")
    private String storagePath;
    
    @Value("${app.confirmation-pdf.base-url:}")
    private String baseUrl;
    
    /**
     * Generate payment confirmation PDF
     */
    public String generateConfirmationPdf(Invoice invoice, User user) throws Exception {
        try {
            // Ensure storage directory exists
            Path storageDir = Paths.get(storagePath);
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }
            
            // Generate filename
            String filename = "confirmation_" + invoice.getId().toString().replace("-", "") + ".pdf";
            Path filePath = storageDir.resolve(filename);
            
            // Create PDF document
            try (PDDocument document = new PDDocument()) {
                PDPage page = new PDPage();
                document.addPage(page);
                
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    float yPosition = 750;
                    float margin = 50;
                    float lineHeight = 20;
                    
                    // Title
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 24);
                    contentStream.newLineAtOffset(margin, yPosition);
                    contentStream.showText("Payment Confirmation");
                    contentStream.endText();
                    yPosition -= 40;
                    
                    // Company Info
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    contentStream.newLineAtOffset(margin, yPosition);
                    contentStream.showText("Rensights");
                    contentStream.newLineAtOffset(0, -15);
                    contentStream.showText("Property Intelligence Platform");
                    contentStream.endText();
                    yPosition -= 50;
                    
                    // Confirmation Number
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                    contentStream.newLineAtOffset(margin, yPosition);
                    contentStream.showText("Confirmation Number: " + invoice.getInvoiceNumber());
                    contentStream.endText();
                    yPosition -= 25;
                    
                    // Date
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' hh:mm a");
                    String formattedDate = invoice.getPaidAt() != null ? 
                            invoice.getPaidAt().format(formatter) : 
                            LocalDateTime.now().format(formatter);
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    contentStream.newLineAtOffset(margin, yPosition);
                    contentStream.showText("Payment Date: " + formattedDate);
                    contentStream.endText();
                    yPosition -= 40;
                    
                    // Customer Information Section
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                    contentStream.newLineAtOffset(margin, yPosition);
                    contentStream.showText("Customer Information");
                    contentStream.endText();
                    yPosition -= 25;
                    
                    // Customer Details
                    String fullName = (user.getFirstName() != null ? user.getFirstName() : "") + 
                                    (user.getLastName() != null ? " " + user.getLastName() : "").trim();
                    if (fullName.isEmpty()) {
                        fullName = user.getEmail();
                    }
                    
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                    contentStream.newLineAtOffset(margin, yPosition);
                    contentStream.showText("Name: " + fullName);
                    contentStream.newLineAtOffset(0, -lineHeight);
                    contentStream.showText("Email: " + user.getEmail());
                    if (user.getCustomerId() != null && !user.getCustomerId().isEmpty()) {
                        contentStream.newLineAtOffset(0, -lineHeight);
                        contentStream.showText("Customer ID: " + user.getCustomerId());
                    }
                    contentStream.endText();
                    yPosition -= 60;
                    
                    // Payment Details Section
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                    contentStream.newLineAtOffset(margin, yPosition);
                    contentStream.showText("Payment Details");
                    contentStream.endText();
                    yPosition -= 25;
                    
                    // Payment Details
                    String amountStr = invoice.getCurrency() + " " + 
                                     invoice.getAmount().setScale(2, java.math.RoundingMode.HALF_UP).toString();
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                    contentStream.newLineAtOffset(margin, yPosition);
                    contentStream.showText("Amount Paid: " + amountStr);
                    contentStream.newLineAtOffset(0, -lineHeight);
                    contentStream.showText("Status: " + invoice.getStatus().toUpperCase());
                    if (invoice.getDescription() != null && !invoice.getDescription().isEmpty()) {
                        contentStream.newLineAtOffset(0, -lineHeight);
                        contentStream.showText("Description: " + invoice.getDescription());
                    }
                    contentStream.endText();
                    yPosition -= 80;
                    
                    // Thank You Message
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 11);
                    contentStream.newLineAtOffset(margin, yPosition);
                    contentStream.showText("Thank you for your payment. This confirmation serves as proof of your transaction.");
                    contentStream.endText();
                    
                    // Footer
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                    contentStream.newLineAtOffset(margin, 50);
                    contentStream.showText("This is a computer-generated confirmation. If you have any questions, please contact support.");
                    contentStream.endText();
                }
                
                document.save(filePath.toFile());
            }
            
            logger.info("Generated confirmation PDF: {}", filePath);
            
            // Return filename (relative path)
            return filename;
            
        } catch (Exception e) {
            logger.error("Error generating confirmation PDF for invoice {}: {}", invoice.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate confirmation PDF", e);
        }
    }
    
    /**
     * Get confirmation PDF file
     */
    public File getConfirmationPdfFile(String filename) {
        Path filePath = Paths.get(storagePath).resolve(filename);
        File file = filePath.toFile();
        if (file.exists() && file.isFile()) {
            return file;
        }
        return null;
    }
}
