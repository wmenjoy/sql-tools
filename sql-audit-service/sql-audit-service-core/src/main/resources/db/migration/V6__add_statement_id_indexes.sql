-- V6__add_statement_id_indexes.sql
-- Add statementId column and indexes for better audit query performance

-- ========================================
-- 1. Add statementId column to audit_reports (default MySQL/H2 table)
-- ========================================
ALTER TABLE audit_reports ADD COLUMN statementId VARCHAR(255);

-- Create index on statementId for fast lookups
CREATE INDEX idx_statement_id ON audit_reports(statementId);

-- ========================================
-- 2. Add index on mapper_id in sql_executions_mysql (stores statementId)
-- ========================================
-- Note: This table already has mapper_id column but no index on it
-- mapper_id stores the statementId value from AuditEvent
CREATE INDEX IF NOT EXISTS idx_mapper_id ON sql_executions_mysql(mapper_id);

-- ========================================
-- 3. Add statementId column to audit_reports_mysql
-- ========================================
ALTER TABLE IF EXISTS audit_reports_mysql ADD COLUMN statementId VARCHAR(255);

-- Create index on statementId
CREATE INDEX IF NOT EXISTS idx_report_statement_id ON audit_reports_mysql(statementId);
