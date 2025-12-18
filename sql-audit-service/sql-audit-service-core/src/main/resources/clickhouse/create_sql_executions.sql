CREATE TABLE IF NOT EXISTS sql_executions (
    event_id UUID,
    sql_id String,
    sql String,
    sql_type Enum8('SELECT'=1, 'UPDATE'=2, 'DELETE'=3, 'INSERT'=4, 'UNKNOWN'=5),
    mapper_id String,
    datasource String,
    execution_time_ms UInt64,
    rows_affected Int32,
    error_message Nullable(String),
    timestamp DateTime64(3),
    date Date MATERIALIZED toDate(timestamp)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, sql_id, timestamp)
TTL date + INTERVAL 90 DAY;
