-- ClickHouse Audit Log Tables Initialization
-- SQL Guard Audit Platform Demo

-- Create audit database
CREATE DATABASE IF NOT EXISTS audit;

-- SQL Executions table - stores all audit events
CREATE TABLE IF NOT EXISTS audit.sql_executions
(
    -- Primary identifiers
    id UUID DEFAULT generateUUIDv4(),
    sql_id String,
    
    -- SQL details
    sql String,
    sql_type Enum8('SELECT' = 1, 'INSERT' = 2, 'UPDATE' = 3, 'DELETE' = 4, 'OTHER' = 5),
    mapper_id String,
    datasource String,
    
    -- Execution metrics
    execution_time_ms UInt64,
    rows_affected Int32,
    
    -- Risk assessment
    severity Enum8('CRITICAL' = 1, 'HIGH' = 2, 'MEDIUM' = 3, 'LOW' = 4, 'INFO' = 5),
    risk_score UInt8,
    checker_name String,
    
    -- Error tracking
    error_message Nullable(String),
    
    -- Metadata
    timestamp DateTime64(3),
    environment String DEFAULT 'default',
    
    -- Partition key
    date Date DEFAULT toDate(timestamp)
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, timestamp, sql_id)
TTL date + INTERVAL 90 DAY
SETTINGS index_granularity = 8192;

-- SQL Statistics materialized view for aggregated metrics
CREATE MATERIALIZED VIEW IF NOT EXISTS audit.sql_statistics_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, sql_id, severity)
AS SELECT
    toDate(timestamp) as date,
    sql_id,
    sql,
    severity,
    count() as execution_count,
    sum(execution_time_ms) as total_execution_time_ms,
    max(execution_time_ms) as max_execution_time_ms,
    countIf(error_message IS NOT NULL AND error_message != '') as error_count
FROM audit.sql_executions
GROUP BY date, sql_id, sql, severity;

-- Hourly aggregation view for trend analysis
CREATE MATERIALIZED VIEW IF NOT EXISTS audit.hourly_stats_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (hour, severity)
AS SELECT
    toStartOfHour(timestamp) as hour,
    severity,
    count() as query_count,
    sum(execution_time_ms) as total_time_ms,
    countIf(error_message IS NOT NULL AND error_message != '') as error_count
FROM audit.sql_executions
GROUP BY hour, severity;

-- Create user and grant permissions
-- Note: In production, use proper user management
-- CREATE USER IF NOT EXISTS audit IDENTIFIED BY 'audit';
-- GRANT ALL ON audit.* TO audit;




