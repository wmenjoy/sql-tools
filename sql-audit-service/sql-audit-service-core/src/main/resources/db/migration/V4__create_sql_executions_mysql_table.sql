-- V4__create_sql_executions_mysql_table.sql
-- MySQL audit log storage table for mysql-only mode
-- Optimized for high-throughput audit event storage

-- Main audit log table for MySQL storage mode
CREATE TABLE IF NOT EXISTS sql_executions_mysql (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key, auto-incremented',
    event_id VARCHAR(36) NOT NULL COMMENT 'Unique event identifier (UUID)',
    sql_id VARCHAR(32) NOT NULL COMMENT 'MD5 hash of SQL statement for deduplication',
    sql_text TEXT NOT NULL COMMENT 'Full SQL statement text',
    sql_type VARCHAR(20) NOT NULL COMMENT 'SQL command type: SELECT, INSERT, UPDATE, DELETE',
    mapper_id VARCHAR(255) COMMENT 'MyBatis mapper identifier (e.g., UserMapper.selectById)',
    datasource VARCHAR(100) COMMENT 'Datasource name',
    execution_time_ms BIGINT NOT NULL DEFAULT 0 COMMENT 'Execution time in milliseconds',
    rows_affected INT NOT NULL DEFAULT -1 COMMENT 'Number of rows affected (-1 if not applicable)',
    error_message TEXT COMMENT 'Error message if execution failed',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Event creation timestamp with microseconds',
    
    -- Indexes for common query patterns
    INDEX idx_created_at (created_at) COMMENT 'Index for time-range queries',
    INDEX idx_sql_id (sql_id) COMMENT 'Index for SQL deduplication queries',
    INDEX idx_sql_type (sql_type) COMMENT 'Index for filtering by SQL type',
    INDEX idx_datasource (datasource) COMMENT 'Index for filtering by datasource',
    INDEX idx_execution_time (execution_time_ms) COMMENT 'Index for slow query analysis',
    
    -- Composite index for common dashboard queries
    INDEX idx_type_time (sql_type, created_at) COMMENT 'Composite index for type + time queries'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci 
COMMENT='SQL audit execution log for MySQL-only storage mode';

-- Summary statistics table for dashboard queries
CREATE TABLE IF NOT EXISTS sql_audit_stats_mysql (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    stat_date DATE NOT NULL COMMENT 'Statistics date',
    stat_hour TINYINT COMMENT 'Statistics hour (0-23), NULL for daily stats',
    sql_type VARCHAR(20) NOT NULL COMMENT 'SQL command type',
    datasource VARCHAR(100) COMMENT 'Datasource name',
    event_count BIGINT NOT NULL DEFAULT 0 COMMENT 'Number of events',
    total_execution_time_ms BIGINT NOT NULL DEFAULT 0 COMMENT 'Total execution time',
    avg_execution_time_ms DOUBLE NOT NULL DEFAULT 0 COMMENT 'Average execution time',
    max_execution_time_ms BIGINT NOT NULL DEFAULT 0 COMMENT 'Maximum execution time',
    error_count BIGINT NOT NULL DEFAULT 0 COMMENT 'Number of errors',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record update time',
    
    UNIQUE KEY uk_stat_key (stat_date, stat_hour, sql_type, datasource),
    INDEX idx_stat_date (stat_date) COMMENT 'Index for date-based queries'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci 
COMMENT='Pre-aggregated statistics for audit dashboard';

-- Audit report table for MySQL (mirrors PostgreSQL audit_reports)
CREATE TABLE IF NOT EXISTS audit_reports_mysql (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    report_id VARCHAR(36) NOT NULL UNIQUE COMMENT 'Unique report identifier (UUID)',
    sql_id VARCHAR(32) NOT NULL COMMENT 'MD5 hash of SQL statement',
    sql_text TEXT NOT NULL COMMENT 'Original SQL statement',
    sql_type VARCHAR(20) NOT NULL COMMENT 'SQL command type',
    mapper_id VARCHAR(255) COMMENT 'MyBatis mapper identifier',
    datasource VARCHAR(100) COMMENT 'Datasource name',
    risk_score INT NOT NULL DEFAULT 0 COMMENT 'Overall risk score (0-100)',
    severity VARCHAR(20) NOT NULL COMMENT 'Severity level: CRITICAL, HIGH, MEDIUM, LOW, INFO',
    checker_results JSON COMMENT 'JSON array of checker results',
    original_event JSON COMMENT 'Original AuditEvent as JSON',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Report creation timestamp',
    
    INDEX idx_report_created (created_at) COMMENT 'Index for time-based queries',
    INDEX idx_report_severity (severity) COMMENT 'Index for severity filtering',
    INDEX idx_report_risk (risk_score) COMMENT 'Index for risk score queries',
    INDEX idx_report_sql_id (sql_id) COMMENT 'Index for SQL deduplication',
    INDEX idx_report_datasource (datasource) COMMENT 'Index for datasource filtering'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci 
COMMENT='Audit analysis reports for MySQL storage mode';



