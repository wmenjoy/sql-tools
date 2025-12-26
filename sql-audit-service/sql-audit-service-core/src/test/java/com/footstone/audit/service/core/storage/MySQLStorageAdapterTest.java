package com.footstone.audit.service.core.storage;

import com.footstone.audit.service.core.storage.adapter.MySQLStorageAdapter;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.core.model.SqlCommandType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MySQLStorageAdapter.
 *
 * @since 2.0.0
 */
@ExtendWith(MockitoExtension.class)
class MySQLStorageAdapterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MySQLStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MySQLStorageAdapter(jdbcTemplate);
    }

    @Test
    void testLog_singleEvent_shouldInsert() {
        // Given
        AuditEvent event = createTestEvent("SELECT * FROM users");

        // When
        adapter.log(event);

        // Then
        verify(jdbcTemplate, times(1)).update(
                contains("INSERT INTO sql_executions_mysql"),
                any(), // event_id
                eq(event.getSqlId()),
                eq(event.getSql()),
                eq("SELECT"),
                eq(event.getStatementId()),
                eq(event.getDatasource()),
                eq(event.getExecutionTimeMs()),
                eq(event.getRowsAffected()),
                eq(event.getErrorMessage()),
                any() // timestamp
        );
    }

    @Test
    void testLogBatch_multipleEvents_shouldBatchInsert() {
        // Given
        List<AuditEvent> events = Arrays.asList(
                createTestEvent("SELECT * FROM users"),
                createTestEvent("UPDATE users SET name = ?"),
                createTestEvent("DELETE FROM users WHERE id = ?")
        );

        // When
        adapter.logBatch(events);

        // Then
        verify(jdbcTemplate, times(1)).batchUpdate(
                contains("INSERT INTO sql_executions_mysql"),
                eq(events),
                eq(3),
                any(ParameterizedPreparedStatementSetter.class)
        );
    }

    @Test
    void testLogBatch_emptyList_shouldDoNothing() {
        // Given
        List<AuditEvent> events = List.of();

        // When
        adapter.logBatch(events);

        // Then
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void testCountByTimeRange_shouldReturnCount() {
        // Given
        Instant start = Instant.now().minusSeconds(3600);
        Instant end = Instant.now();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any()))
                .thenReturn(42L);

        // When
        long count = adapter.countByTimeRange(start, end);

        // Then
        assertEquals(42L, count);
    }

    @Test
    void testCountByTimeRange_nullResult_shouldReturnZero() {
        // Given
        Instant start = Instant.now().minusSeconds(3600);
        Instant end = Instant.now();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any()))
                .thenReturn(null);

        // When
        long count = adapter.countByTimeRange(start, end);

        // Then
        assertEquals(0L, count);
    }

    @Test
    void testDeleteOlderThan_shouldExecuteDelete() {
        // Given
        Instant threshold = Instant.now().minusSeconds(86400 * 90); // 90 days ago
        when(jdbcTemplate.update(anyString(), any(java.sql.Timestamp.class))).thenReturn(10);

        // When
        adapter.deleteOlderThan(threshold);

        // Then
        verify(jdbcTemplate, times(1)).update(
                contains("DELETE FROM sql_executions_mysql"),
                any(java.sql.Timestamp.class)
        );
    }

    @Test
    void testLog_withError_shouldThrowRuntimeException() {
        // Given
        AuditEvent event = createTestEvent("SELECT * FROM users", SqlCommandType.SELECT);
        doThrow(new RuntimeException("DB error"))
                .when(jdbcTemplate)
                .update(anyString(), (Object[]) any());

        // When/Then
        assertThrows(RuntimeException.class, () -> adapter.log(event));
    }

    private AuditEvent createTestEvent(String sql) {
        return createTestEvent(sql, SqlCommandType.SELECT);
    }

    private AuditEvent createTestEvent(String sql, SqlCommandType sqlType) {
        return AuditEvent.builder()
                .sql(sql)
                .sqlType(sqlType)
                .statementId("TestMapper.method")
                .datasource("testdb")
                .executionTimeMs(100L)
                .rowsAffected(1)
                .timestamp(Instant.now())
                .build();
    }
}

