-- V7__add_statement_id_indexes_pg.sql
-- PostgreSQL-specific migration for adding statementId indexes

-- ========================================
-- 1. Add index on mapper_id in sql_executions_pg (stores statementId)
-- ========================================
-- Note: This table already has mapper_id column but no index on it
CREATE INDEX IF NOT EXISTS idx_executions_mapper_id ON sql_executions_pg(mapper_id);

-- ========================================
-- 2. Add statementId to audit_reports if using PostgreSQL storage
-- ========================================
-- This is compatible with the generic audit_reports table
-- The column will be added if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'audit_reports' AND column_name = 'statementid'
    ) THEN
        ALTER TABLE audit_reports ADD COLUMN statementId VARCHAR(255);
    END IF;
END $$;

-- Create index if it doesn't exist
CREATE INDEX IF NOT EXISTS idx_audit_reports_statement_id ON audit_reports(statementId);
