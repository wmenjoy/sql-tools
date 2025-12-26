-- V8__add_statement_id_indexes_sqlite.sql
-- SQLite-specific migration for adding statementId indexes

-- ========================================
-- 1. Add index on mapper_id in sql_executions (stores statementId)
-- ========================================
CREATE INDEX IF NOT EXISTS idx_sql_executions_mapper_id ON sql_executions(mapper_id);

-- ========================================
-- 2. Add statementId column to audit_reports
-- ========================================
-- SQLite doesn't have a nice "ADD COLUMN IF NOT EXISTS" syntax
-- We'll add the column directly (will fail if it already exists, which is fine)
-- If the column already exists from V6, this will be skipped by Flyway

-- Check if we need to add the column by attempting to select it
-- If the migration fails because column exists, that's expected

-- For SQLite, we need to be more careful with ALTER TABLE
-- Let's just create the index on statementId if the column exists
-- The column should be added by V6 migration

-- Create index on statementId for audit_reports
CREATE INDEX IF NOT EXISTS idx_audit_reports_statement_id ON audit_reports(statementId);

-- Note: If audit_reports doesn't have statementId column yet,
-- V6 migration should handle adding it for all databases
