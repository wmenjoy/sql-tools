package com.footstone.audit.service.core.storage.clickhouse;

import com.clickhouse.jdbc.ClickHouseDataSource;
import com.footstone.audit.service.core.storage.repository.ExecutionLogRepository;
import com.footstone.sqlguard.audit.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "audit.storage.mode", havingValue = "full", matchIfMissing = true)
public class ClickHouseExecutionLogger implements ExecutionLogRepository {

    private final ClickHouseDataSource dataSource;

    @Override
    public void log(AuditEvent event) {
        String sql = "INSERT INTO sql_executions (event_id, sql_id, sql, sql_type, mapper_id, datasource, execution_time_ms, rows_affected, error_message, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            setParameters(ps, event);
            ps.executeUpdate();
            
        } catch (SQLException e) {
            log.error("Failed to log audit event to ClickHouse", e);
            throw new RuntimeException("ClickHouse write failed", e);
        }
    }

    @Override
    public void logBatch(List<AuditEvent> events) {
        if (events.isEmpty()) return;
        
        String sql = "INSERT INTO sql_executions (event_id, sql_id, sql, sql_type, mapper_id, datasource, execution_time_ms, rows_affected, error_message, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            for (AuditEvent event : events) {
                setParameters(ps, event);
                ps.addBatch();
            }
            
            ps.executeBatch();
            
        } catch (SQLException e) {
            log.error("Failed to log batch audit events to ClickHouse", e);
            throw new RuntimeException("ClickHouse batch write failed", e);
        }
    }

    @Override
    public List<AuditEvent> findByTimeRange(Instant startTime, Instant endTime) {
        String sql = "SELECT * FROM sql_executions WHERE timestamp BETWEEN ? AND ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(startTime));
            ps.setTimestamp(2, Timestamp.from(endTime));
            try (ResultSet rs = ps.executeQuery()) {
                List<AuditEvent> events = new ArrayList<>();
                while (rs.next()) {
                    events.add(AuditEvent.builder()
                            .sql(rs.getString("sql"))
                            .sqlType(com.footstone.sqlguard.core.model.SqlCommandType.valueOf(rs.getString("sql_type")))
                            .mapperId(rs.getString("mapper_id"))
                            .datasource(rs.getString("datasource"))
                            .executionTimeMs(rs.getLong("execution_time_ms"))
                            .rowsAffected(rs.getInt("rows_affected"))
                            .errorMessage(rs.getString("error_message"))
                            .timestamp(rs.getTimestamp("timestamp").toInstant())
                            .build());
                }
                return events;
            }
        } catch (SQLException e) {
            log.error("Failed to query ClickHouse", e);
            throw new RuntimeException("ClickHouse query failed", e);
        }
    }

    @Override
    public long countByTimeRange(Instant startTime, Instant endTime) {
        String sql = "SELECT count() FROM sql_executions WHERE timestamp BETWEEN ? AND ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(startTime));
            ps.setTimestamp(2, Timestamp.from(endTime));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            log.error("Failed to count ClickHouse", e);
            throw new RuntimeException("ClickHouse count failed", e);
        }
    }

    @Override
    public void deleteOlderThan(Instant timestamp) {
         // ClickHouse deletion is heavy (ALTER DELETE). Usually done via TTL.
         String sql = "ALTER TABLE sql_executions DELETE WHERE timestamp < ?";
         try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(timestamp));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete from ClickHouse", e);
            throw new RuntimeException("ClickHouse delete failed", e);
        }
    }
    
    private void setParameters(PreparedStatement ps, AuditEvent event) throws SQLException {
        ps.setObject(1, UUID.randomUUID()); // event_id
        ps.setString(2, event.getSqlId());
        ps.setString(3, event.getSql());
        ps.setString(4, event.getSqlType().name());
        ps.setString(5, event.getMapperId());
        ps.setString(6, event.getDatasource());
        ps.setLong(7, event.getExecutionTimeMs());
        ps.setInt(8, event.getRowsAffected());
        ps.setString(9, event.getErrorMessage());
        ps.setTimestamp(10, Timestamp.from(event.getTimestamp()));
    }
}
