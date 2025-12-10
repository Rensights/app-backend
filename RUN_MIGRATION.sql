-- Run this SQL in your PostgreSQL database to create the invoices table
-- Connect to your database first, then execute this script

-- Create invoices table
CREATE TABLE IF NOT EXISTS invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stripe_invoice_id VARCHAR(255) UNIQUE NOT NULL,
    stripe_customer_id VARCHAR(255),
    stripe_subscription_id VARCHAR(255),
    amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    status VARCHAR(50),
    invoice_url TEXT,
    invoice_pdf TEXT,
    invoice_number VARCHAR(255),
    description TEXT,
    invoice_date TIMESTAMP,
    due_date TIMESTAMP,
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_invoices_user_id ON invoices(user_id);
CREATE INDEX IF NOT EXISTS idx_invoices_stripe_invoice_id ON invoices(stripe_invoice_id);
CREATE INDEX IF NOT EXISTS idx_invoices_stripe_customer_id ON invoices(stripe_customer_id);
CREATE INDEX IF NOT EXISTS idx_invoices_invoice_date ON invoices(invoice_date DESC);

-- Verify the table was created
SELECT 'invoices table created successfully' AS status;
\d invoices;
