-- Tier 3 backend optimization: composite indexes for hot query filters
--
-- IMPORTANT: Flyway is EXCLUDED in this project (see pom.xml), so this file is
-- NOT applied automatically. Prod runs with hibernate.ddl-auto=validate, which
-- does NOT create indexes. Run this script MANUALLY against Postgres.
--
-- CREATE INDEX CONCURRENTLY avoids taking an ACCESS EXCLUSIVE lock / blocking
-- writes on large tables. It CANNOT run inside a transaction block, so execute
-- with autocommit on (default in psql) and NOT wrapped in BEGIN/COMMIT.
--
-- In dev (ddl-auto=update) the matching @Index annotations on the entities
-- auto-create equivalent (non-concurrent) indexes, so this script is only
-- required for validate/prod databases.

-- analysis_requests: backs findByUserIdOrderByCreatedAtDesc + countByUserIdAndCreatedAtAfter (dashboard path)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_requests_user_created
    ON analysis_requests (user_id, created_at);

-- invoices: backs findByUserIdOrderByInvoiceDateDesc
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_invoices_user_invoice_date
    ON invoices (user_id, invoice_date);

-- invoices: backs findByUserIdAndStatus
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_invoices_user_status
    ON invoices (user_id, status);

-- articles: backs findByIsActiveTrueOrderByPublishedAtDesc
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_articles_active_published
    ON articles (is_active, published_at);

-- report_sections: backs findByLanguageCodeAndIsActiveTrue[AndAccessTierIn]OrderByDisplayOrderAsc
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_report_sections_lang_active_order
    ON report_sections (language_code, is_active, display_order);

-- translations: backs findByLanguageCodeAndNamespace
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_translations_lang_namespace
    ON translations (language_code, namespace);

-- NOTE (subscriptions): (user_id) and (user_id, status) already exist from
-- V1__add_performance_indexes.sql (idx_subscriptions_user_id /
-- idx_subscriptions_user_status), so no new subscription index is needed.
