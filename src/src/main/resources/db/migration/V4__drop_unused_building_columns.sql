-- Building catalogue: drop the columns the product no longer stores
--
-- The catalogue behind the analysis request form's type-ahead holds building NAMES only.
-- The area / city / developer columns were part of the first cut and are no longer read or
-- written by either service. Hibernate's ddl-auto=update never drops columns, so they linger
-- in the database until removed by hand - this script does that.
--
-- IMPORTANT: Flyway is EXCLUDED in this project (see RensightsApplication), so this file is
-- NOT applied automatically. Run it MANUALLY against Postgres.
--
-- Both the app and admin services share one database, so this only needs to run ONCE.
--
-- Safe to re-run: every statement is guarded with IF EXISTS, so a second run is a no-op.
--
-- Ordering: deploy the code that stopped using these columns FIRST, then run this. Dropping
-- them under a running old build would break its inserts.

-- Postgres drops any index or constraint that depends on a column along with the column
-- itself, so idx_buildings_area needs no separate statement.
ALTER TABLE IF EXISTS buildings DROP COLUMN IF EXISTS area;
ALTER TABLE IF EXISTS buildings DROP COLUMN IF EXISTS city;
ALTER TABLE IF EXISTS buildings DROP COLUMN IF EXISTS developer;

-- Verification (run manually, expects exactly: id, name, created_at, updated_at):
--   SELECT column_name FROM information_schema.columns
--    WHERE table_name = 'buildings' ORDER BY ordinal_position;
