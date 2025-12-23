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
 * SQLite implementation of ExecutionLogRepository for local development.
 *
 * <h2>Purpose</h2>
 * <p>Provides lightweight SQLite-based storage for SQL audit events during
 * local development and testing. No external database server required.</p>
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Local development environment</li>
 *   <li>Unit and integration testing</li>
 *   <li>Demo and POC deployments</li>
 *   <li>Single-node deployments with low volume</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <pre>
 * audit:
 *   storage:
 *     mode: sqlite  # or: local (alias)
 *
 * spring:
 *   datasource:
 *     url: jdbc:sqlite:./audit.db
 *     driver-class-name: org.sqlite.JDBC
 * </pre>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>Not suitable for high-concurrency production use</li>
 *   <li>No full-text search (use mysql-es for that)</li>
 *   <li>Single-writer limitation</li>
 * </ul>
 *
 * @see ExecutionLogRepository
 * @since 2.0.0
 */
@Component
@ConditionalOnProperty(name = "audit.storage.mode", havingValue = "sqlite")
@RequiredArgsConstructor
@Slf4j
public class SQLiteStorageAdapter implements ExecutionLogRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = 
        "INSERT INTO sql_executions " +
        "(event_id, sql_id, sql_text, sql_type, mapper_id, datasource, execution_time_ms, rows_affected, error_message, created_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public void log(AuditEvent event) {
        try {
            jdbcTemplate.update(INSERT_SQL,
                    UUID.randomUUID().toString(),
                    event.getSqlId(),
                    event.getSql(),
                    event.getSqlType().name(),
                    event.getMapperId(),
                    event.getDatasource(),
                    event.getExecutionTimeMs(),
                    event.getRowsAffected(),
                    event.getErrorMessage(),
                    Timestamp.from(event.getTimestamp()).toString()
            );
        } catch (Exception e) {
            log.error("Failed to log audit event to SQLite: {}", e.getMessage(), e);
            throw new RuntimeException("SQLite write failed", e);
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
                ps.setString(3, event.getSql());
                ps.setString(4, event.getSqlType().name());
                ps.setString(5, event.getMapperId());
                ps.setString(6, event.getDatasource());
                ps.setLong(7, event.getExecutionTimeMs());
                ps.setInt(8, event.getRowsAffected());
                ps.setString(9, event.getErrorMessage());
                ps.setString(10, Timestamp.from(event.getTimestamp()).toString());
            });
            log.debug("Batch inserted {} audit events to SQLite", events.size());
        } catch (Exception e) {
            log.error("Failed to batch log audit events to SQLite: {}", e.getMessage(), e);
            throw new RuntimeException("SQLite batch write failed", e);
        }
    }

    @Override
    public List<AuditEvent> findByTimeRange(Instant startTime, Instant endTime) {
        String sql = "SELECT * FROM sql_executions WHERE created_at BETWEEN ? AND ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> AuditEvent.builder()
                .sql(rs.getString("sql_text"))
                .sqlType(SqlCommandType.valueOf(rs.getString("sql_type")))
                .mapperId(rs.getString("mapper_id"))
                .datasource(rs.getString("datasource"))
                .executionTimeMs(rs.getLong("execution_time_ms"))
                .rowsAffected(rs.getInt("rows_affected"))
                .errorMessage(rs.getString("error_message"))
                .timestamp(Instant.parse(rs.getString("created_at").replace(" ", "T") + "Z"))
                .build(),
                Timestamp.from(startTime).toString(), Timestamp.from(endTime).toString());
    }

    @Override
    public long countByTimeRange(Instant startTime, Instant endTime) {
        String sql = "SELECT COUNT(*) FROM sql_executions WHERE created_at BETWEEN ? AND ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, 
                Timestamp.from(startTime).toString(), Timestamp.from(endTime).toString());
        return count != null ? count : 0;
    }

    @Override
    public void deleteOlderThan(Instant timestamp) {
        String sql = "DELETE FROM sql_executions WHERE created_at < ?";
        int deleted = jdbcTemplate.update(sql, Timestamp.from(timestamp).toString());
        log.info("Deleted {} old audit events from SQLite", deleted);
    }
}

