-- Performance optimization indexes
-- These indexes improve query performance for frequently accessed columns

-- User table indexes
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_customer_id ON users(customer_id) WHERE customer_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_stripe_customer_id ON users(stripe_customer_id) WHERE stripe_customer_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_email_verified ON users(email_verified) WHERE email_verified = true;
CREATE INDEX IF NOT EXISTS idx_users_is_active ON users(is_active) WHERE is_active = true;

-- Device table indexes (composite index for common query pattern)
CREATE INDEX IF NOT EXISTS idx_devices_user_fingerprint ON devices(user_id, device_fingerprint);
CREATE INDEX IF NOT EXISTS idx_devices_user_id ON devices(user_id);
CREATE INDEX IF NOT EXISTS idx_devices_last_used_at ON devices(last_used_at DESC);

-- Subscription table indexes
CREATE INDEX IF NOT EXISTS idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_user_status ON subscriptions(user_id, status);
CREATE INDEX IF NOT EXISTS idx_subscriptions_stripe_subscription_id ON subscriptions(stripe_subscription_id) WHERE stripe_subscription_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_subscriptions_status ON subscriptions(status) WHERE status = 'ACTIVE';

-- Composite index for active subscriptions lookup (most common query)
CREATE INDEX IF NOT EXISTS idx_subscriptions_user_active ON subscriptions(user_id, status) WHERE status = 'ACTIVE';

