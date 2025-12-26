package com.footstone.audit.service.core.storage.adapter;

import com.footstone.audit.service.core.storage.repository.ExecutionLogRepository;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.core.model.ExecutionLayer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "audit.storage.mode", havingValue = "postgresql-only")
@RequiredArgsConstructor
@Slf4j
public class PostgreSQLOnlyStorageAdapter implements ExecutionLogRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void log(AuditEvent event) {
        String sql = "INSERT INTO sql_executions_pg (event_id, sql_id, sql, sql_type, mapper_id, datasource, execution_time_ms, rows_affected, error_message, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try {
            jdbcTemplate.update(sql,
                    UUID.randomUUID(),
                    event.getSqlId(),
                    event.getSql(),
                    event.getSqlType().name(),
                    event.getStatementId(),
                    event.getDatasource(),
                    event.getExecutionTimeMs(),
                    event.getRowsAffected(),
                    event.getErrorMessage(),
                    Timestamp.from(event.getTimestamp())
            );
        } catch (Exception e) {
            log.error("Failed to log audit event to PostgreSQL", e);
            throw new RuntimeException("PostgreSQL write failed", e);
        }
    }

    @Override
    public void logBatch(List<AuditEvent> events) {
        if (events.isEmpty()) return;
        
        String sql = "INSERT INTO sql_executions_pg (event_id, sql_id, sql, sql_type, mapper_id, datasource, execution_time_ms, rows_affected, error_message, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try {
            jdbcTemplate.batchUpdate(sql, events, events.size(), (ps, event) -> {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, event.getSqlId());
                ps.setString(3, event.getSql());
                ps.setString(4, event.getSqlType().name());
                ps.setString(5, event.getStatementId());
                ps.setString(6, event.getDatasource());
                ps.setLong(7, event.getExecutionTimeMs());
                ps.setInt(8, event.getRowsAffected());
                ps.setString(9, event.getErrorMessage());
                ps.setTimestamp(10, Timestamp.from(event.getTimestamp()));
            });
        } catch (Exception e) {
            log.error("Failed to log batch audit events to PostgreSQL", e);
            throw new RuntimeException("PostgreSQL batch write failed", e);
        }
    }

    @Override
    public List<AuditEvent> findByTimeRange(Instant startTime, Instant endTime) {
        String sql = "SELECT * FROM sql_executions_pg WHERE timestamp BETWEEN ? AND ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> AuditEvent.builder()
                .sql(rs.getString("sql"))
                .sqlType(com.footstone.sqlguard.core.model.SqlCommandType.valueOf(rs.getString("sql_type")))
                .executionLayer(ExecutionLayer.UNKNOWN)  // Default for legacy data
                .statementId(rs.getString("mapper_id"))
                .datasource(rs.getString("datasource"))
                .executionTimeMs(rs.getLong("execution_time_ms"))
                .rowsAffected(rs.getInt("rows_affected"))
                .errorMessage(rs.getString("error_message"))
                .timestamp(rs.getTimestamp("timestamp").toInstant())
                .build(),
                Timestamp.from(startTime), Timestamp.from(endTime));
    }

    @Override
    public long countByTimeRange(Instant startTime, Instant endTime) {
        String sql = "SELECT COUNT(*) FROM sql_executions_pg WHERE timestamp BETWEEN ? AND ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, Timestamp.from(startTime), Timestamp.from(endTime));
        return count != null ? count : 0;
    }

    @Override
    public void deleteOlderThan(Instant timestamp) {
        String sql = "DELETE FROM sql_executions_pg WHERE timestamp < ?";
        jdbcTemplate.update(sql, Timestamp.from(timestamp));
    }
}
