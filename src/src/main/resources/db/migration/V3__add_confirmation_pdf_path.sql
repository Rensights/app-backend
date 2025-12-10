-- Add confirmation_pdf_path column to invoices table
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS confirmation_pdf_path VARCHAR(500);
