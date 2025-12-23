-- V5__create_sqlite_tables.sql
-- SQLite schema for local development and testing
-- Compatible with both SQLite and H2 (for integration tests)

-- Main audit log table (simplified for SQLite)
CREATE TABLE IF NOT EXISTS sql_executions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id TEXT NOT NULL,
    sql_id TEXT NOT NULL,
    sql_text TEXT NOT NULL,
    sql_type TEXT NOT NULL,
    mapper_id TEXT,
    datasource TEXT,
    execution_time_ms INTEGER NOT NULL DEFAULT 0,
    rows_affected INTEGER NOT NULL DEFAULT -1,
    error_message TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_sql_executions_created_at ON sql_executions(created_at);
CREATE INDEX IF NOT EXISTS idx_sql_executions_sql_id ON sql_executions(sql_id);
CREATE INDEX IF NOT EXISTS idx_sql_executions_sql_type ON sql_executions(sql_type);
CREATE INDEX IF NOT EXISTS idx_sql_executions_datasource ON sql_executions(datasource);

-- Audit reports table
CREATE TABLE IF NOT EXISTS audit_reports (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    report_id TEXT NOT NULL UNIQUE,
    sql_id TEXT NOT NULL,
    sql_text TEXT NOT NULL,
    sql_type TEXT NOT NULL,
    mapper_id TEXT,
    datasource TEXT,
    risk_score INTEGER NOT NULL DEFAULT 0,
    severity TEXT NOT NULL,
    checker_results TEXT,
    original_event TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Indexes for audit reports
CREATE INDEX IF NOT EXISTS idx_audit_reports_created_at ON audit_reports(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_reports_severity ON audit_reports(severity);
CREATE INDEX IF NOT EXISTS idx_audit_reports_risk_score ON audit_reports(risk_score);

-- Checker configuration table
CREATE TABLE IF NOT EXISTS checker_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    checker_id TEXT NOT NULL UNIQUE,
    enabled INTEGER NOT NULL DEFAULT 1,
    config_json TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

