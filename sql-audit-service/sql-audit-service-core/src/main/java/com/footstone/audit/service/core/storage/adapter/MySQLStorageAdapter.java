package com.footstone.audit.service.core.storage.adapter;

import com.footstone.audit.service.core.storage.repository.ExecutionLogRepository;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.core.model.SqlCommandType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MySQL implementation of ExecutionLogRepository for audit log storage.
 *
 * <h2>Purpose</h2>
 * <p>Provides MySQL-based storage for SQL audit events. Suitable for mid-scale
 * deployments using MySQL as the primary database.</p>
 *
 * <h2>Configuration</h2>
 * <p>Activated when {@code audit.storage.mode=mysql-only} in application configuration.</p>
 *
 * <h2>Table Schema</h2>
 * <p>Requires {@code sql_executions_mysql} table created by Flyway migration V4.</p>
 *
 * <h2>Performance Optimizations</h2>
 * <ul>
 *   <li>Uses batch inserts for high-throughput scenarios</li>
 *   <li>Index on timestamp for time-range queries</li>
 *   <li>Index on sql_id for deduplication queries</li>
 * </ul>
 *
 * @see ExecutionLogRepository
 * @see PostgreSQLOnlyStorageAdapter
 * @since 2.0.0
 */
@Component
@ConditionalOnProperty(name = "audit.storage.mode", havingValue = "mysql-only")
@RequiredArgsConstructor
@Slf4j
public class MySQLStorageAdapter implements ExecutionLogRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = 
        "INSERT INTO sql_executions_mysql " +
        "(event_id, sql_id, sql_text, sql_type, mapper_id, datasource, execution_time_ms, rows_affected, error_message, created_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public void log(AuditEvent event) {
        try {
            jdbcTemplate.update(INSERT_SQL,
                    UUID.randomUUID().toString(),
                    event.getSqlId(),
                    truncateSql(event.getSql()),
                    event.getSqlType().name(),
                    event.getStatementId(),
                    event.getDatasource(),
                    event.getExecutionTimeMs(),
                    event.getRowsAffected(),
                    event.getErrorMessage(),
                    Timestamp.from(event.getTimestamp())
            );
        } catch (Exception e) {
            log.error("Failed to log audit event to MySQL: {}", e.getMessage(), e);
            throw new RuntimeException("MySQL write failed", e);
        }
    }

    @Override
    public void logBatch(List<AuditEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        
        try {
            jdbcTemplate.batchUpdate(INSERT_SQL, events, events.size(), (ps, event) -> {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, event.getSqlId());
                ps.setString(3, truncateSql(event.getSql()));
                ps.setString(4, event.getSqlType().name());
                ps.setString(5, event.getStatementId());
                ps.setString(6, event.getDatasource());
                ps.setLong(7, event.getExecutionTimeMs());
                ps.setInt(8, event.getRowsAffected());
                ps.setString(9, event.getErrorMessage());
                ps.setTimestamp(10, Timestamp.from(event.getTimestamp()));
            });
            log.debug("Batch inserted {} audit events to MySQL", events.size());
        } catch (Exception e) {
            log.error("Failed to log batch audit events to MySQL: {}", e.getMessage(), e);
            throw new RuntimeException("MySQL batch write failed", e);
        }
    }

    @Override
    public List<AuditEvent> findByTimeRange(Instant startTime, Instant endTime) {
        String sql = "SELECT * FROM sql_executions_mysql WHERE created_at BETWEEN ? AND ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> AuditEvent.builder()
                .sql(rs.getString("sql_text"))
                .sqlType(SqlCommandType.valueOf(rs.getString("sql_type")))
                .statementId(rs.getString("mapper_id"))
                .datasource(rs.getString("datasource"))
                .executionTimeMs(rs.getLong("execution_time_ms"))
                .rowsAffected(rs.getInt("rows_affected"))
                .errorMessage(rs.getString("error_message"))
                .timestamp(rs.getTimestamp("created_at").toInstant())
                .build(),
                Timestamp.from(startTime), Timestamp.from(endTime));
    }

    @Override
    public long countByTimeRange(Instant startTime, Instant endTime) {
        String sql = "SELECT COUNT(*) FROM sql_executions_mysql WHERE created_at BETWEEN ? AND ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, 
                Timestamp.from(startTime), Timestamp.from(endTime));
        return count != null ? count : 0;
    }

    @Override
    public void deleteOlderThan(Instant timestamp) {
        String sql = "DELETE FROM sql_executions_mysql WHERE created_at < ?";
        int deleted = jdbcTemplate.update(sql, Timestamp.from(timestamp));
        log.info("Deleted {} old audit events from MySQL", deleted);
    }

    /**
     * Truncates SQL to MySQL TEXT column max length if needed.
     * MySQL TEXT type supports up to 65,535 bytes.
     */
    private String truncateSql(String sql) {
        if (sql == null) {
            return null;
        }
        // TEXT column max is 65535 bytes, leave some room for multi-byte chars
        final int maxLength = 60000;
        if (sql.length() > maxLength) {
            return sql.substring(0, maxLength) + "... [TRUNCATED]";
        }
        return sql;
    }
}



