package com.footstone.sqlguard.interceptor.p6spy;

import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogException;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.StatementInformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for P6SpySqlAuditListener.
 */
class P6SpySqlAuditListenerTest {

    private AuditLogWriter mockAuditWriter;
    private P6SpySqlAuditListener listener;
    private StatementInformation mockStatementInfo;
    private ConnectionInformation mockConnectionInfo;

    @BeforeEach
    void setUp() {
        mockAuditWriter = mock(AuditLogWriter.class);
        listener = new P6SpySqlAuditListener(mockAuditWriter);
        mockStatementInfo = mock(StatementInformation.class);
        mockConnectionInfo = mock(ConnectionInformation.class);
    }

    @Test
    void testConstructor_nullAuditWriter_throwsException() {
        assertThrows(NullPointerException.class, () -> new P6SpySqlAuditListener(null));
    }

    @Test
    void testOnAfterExecuteQuery_simpleSelect_shouldLogAudit() throws Exception {
        // Arrange
        when(mockStatementInfo.getSqlWithValues()).thenReturn("SELECT * FROM users WHERE id = 1");

        // Act
        listener.onAfterExecuteQuery(mockStatementInfo, 150_000_000L, null, null); // 150ms in nanos

        // Assert
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals("SELECT * FROM users WHERE id = 1", event.getSql());
        assertEquals(SqlCommandType.SELECT, event.getSqlType());
        assertEquals("p6spy-jdbc", event.getMapperId());
        assertEquals(150L, event.getExecutionTimeMs());
        assertNotNull(event.getTimestamp());
    }

    @Test
    void testOnAfterExecuteUpdate_updateStatement_shouldCaptureRowsAffected() throws Exception {
        // Arrange
        when(mockStatementInfo.getSqlWithValues()).thenReturn("UPDATE users SET status = 'active' WHERE id = 1");

        // Act
        listener.onAfterExecuteUpdate(mockStatementInfo, 200_000_000L, null, 1, null); // 200ms in nanos, 1 row

        // Assert
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals("UPDATE users SET status = 'active' WHERE id = 1", event.getSql());
        assertEquals(SqlCommandType.UPDATE, event.getSqlType());
        assertEquals(1, event.getRowsAffected());
    }

    @Test
    void testOnAfterExecuteUpdate_deleteStatement_shouldLogCorrectly() throws Exception {
        // Arrange
        when(mockStatementInfo.getSqlWithValues()).thenReturn("DELETE FROM users WHERE id = 1");

        // Act
        listener.onAfterExecuteUpdate(mockStatementInfo, 180_000_000L, null, 1, null); // 180ms in nanos, 1 row

        // Assert
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals(SqlCommandType.DELETE, event.getSqlType());
    }

    @Test
    void testOnAfterExecuteUpdate_insertStatement_shouldLogCorrectly() throws Exception {
        // Arrange
        when(mockStatementInfo.getSqlWithValues()).thenReturn("INSERT INTO users (name, email) VALUES ('John', 'john@example.com')");

        // Act
        listener.onAfterExecuteUpdate(mockStatementInfo, 120_000_000L, null, 1, null); // 120ms in nanos, 1 row

        // Assert
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals(SqlCommandType.INSERT, event.getSqlType());
    }

    @Test
    void testOnAfterExecuteQuery_nullSql_shouldNotLogAudit() throws Exception {
        // Arrange
        when(mockStatementInfo.getSqlWithValues()).thenReturn(null);

        // Act
        listener.onAfterExecuteQuery(mockStatementInfo, 100_000_000L, null, null);

        // Assert
        verify(mockAuditWriter, never()).writeAuditLog(any());
    }

    @Test
    void testOnAfterExecuteQuery_emptySql_shouldNotLogAudit() throws Exception {
        // Arrange
        when(mockStatementInfo.getSqlWithValues()).thenReturn("");

        // Act
        listener.onAfterExecuteQuery(mockStatementInfo, 100_000_000L, null, null);

        // Assert
        verify(mockAuditWriter, never()).writeAuditLog(any());
    }

    @Test
    void testOnAfterExecuteUpdate_sqlWithException_shouldCaptureError() throws Exception {
        // Arrange
        SQLException sqlException = new SQLException("Constraint violation");
        when(mockStatementInfo.getSqlWithValues()).thenReturn("UPDATE users SET id = 999 WHERE id = 1");

        // Act
        listener.onAfterExecuteUpdate(mockStatementInfo, 50_000_000L, null, 0, sqlException); // 50ms in nanos

        // Assert
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals("Constraint violation", event.getErrorMessage());
    }

    @Test
    void testOnAfterExecuteBatch_shouldAggregateResults() throws Exception {
        // Arrange
        when(mockStatementInfo.getSqlWithValues()).thenReturn("UPDATE users SET status = 'active' WHERE id = ?");

        // Act
        listener.onAfterExecuteBatch(mockStatementInfo, 300_000_000L, new int[]{1, 1, 1}, null); // 3 updates

        // Assert
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals(3, event.getRowsAffected()); // Should aggregate all batch results
    }

    @Test
    void testOnAfterExecuteBatch_withFailures_shouldHandleGracefully() throws Exception {
        // Arrange
        when(mockStatementInfo.getSqlWithValues()).thenReturn("UPDATE users SET status = 'active' WHERE id = ?");

        // Act
        listener.onAfterExecuteBatch(mockStatementInfo, 300_000_000L, new int[]{1, -3, 1}, null); // One failure (-3)

        // Assert
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals(2, event.getRowsAffected()); // Should only count successful updates
    }

    @Test
    void testOnAfterExecute_preExecutionViolations_shouldIncludeInAudit() throws Exception {
        // Arrange
        ValidationResult validationResult = ValidationResult.pass();
        validationResult.addViolation(
            RiskLevel.HIGH,
            "DELETE statement without WHERE clause",
            "Add WHERE clause to restrict deletion scope"
        );

        // Set ThreadLocal validation result
        P6SpySqlSafetyListener.setValidationResult(validationResult);

        when(mockStatementInfo.getSqlWithValues()).thenReturn("DELETE FROM users");

        try {
            // Act
            listener.onAfterExecute(mockStatementInfo, 100_000_000L, null, null); // 100ms in nanos

            // Assert
            ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

            AuditEvent event = eventCaptor.getValue();
            assertNotNull(event.getViolations());
            assertFalse(event.getViolations().isPassed());
            assertEquals(1, event.getViolations().getViolations().size());
            assertEquals("DELETE statement without WHERE clause", event.getViolations().getViolations().get(0).getMessage());
        } finally {
            // Clean up ThreadLocal
            P6SpySqlSafetyListener.clearValidationResult();
        }
    }

    @Test
    void testOnAfterExecuteQuery_auditWriterThrowsException_shouldNotPropagateError() throws Exception {
        // Arrange
        when(mockStatementInfo.getSqlWithValues()).thenReturn("SELECT * FROM users");
        
        doThrow(new AuditLogException("Disk full"))
            .when(mockAuditWriter).writeAuditLog(any());

        // Act & Assert - should not throw
        assertDoesNotThrow(() -> listener.onAfterExecuteQuery(mockStatementInfo, 100_000_000L, null, null));
    }

    @Test
    void testExecuteTime_shouldCaptureAccurately() throws Exception {
        // Arrange
        long expectedTimeNanos = 1234_000_000L; // 1234ms in nanos
        when(mockStatementInfo.getSqlWithValues()).thenReturn("SELECT * FROM users");

        // Act
        listener.onAfterExecuteQuery(mockStatementInfo, expectedTimeNanos, null, null);

        // Assert
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals(1234L, event.getExecutionTimeMs());
    }

    @Test
    void testDetermineSqlType_variousStatements() throws Exception {
        testSqlType("SELECT * FROM users", SqlCommandType.SELECT);
        testSqlType("  select * FROM users", SqlCommandType.SELECT);
        testSqlType("UPDATE users SET name = 'test'", SqlCommandType.UPDATE);
        testSqlType("DELETE FROM users", SqlCommandType.DELETE);
        testSqlType("INSERT INTO users VALUES (1)", SqlCommandType.INSERT);
        testSqlType("CREATE TABLE users (id INT)", SqlCommandType.SELECT); // Default
    }

    private void testSqlType(String sql, SqlCommandType expectedType) throws Exception {
        // Reset mock
        reset(mockAuditWriter);
        when(mockStatementInfo.getSqlWithValues()).thenReturn(sql);

        // Act
        listener.onAfterExecuteQuery(mockStatementInfo, 100_000_000L, null, null);

        // Assert
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals(expectedType, event.getSqlType(), "Failed for SQL: " + sql);
    }

    @Test
    void testExtractRowsAffected_selectStatement_shouldReturnMinusOne() throws Exception {
        // Arrange
        when(mockStatementInfo.getSqlWithValues()).thenReturn("SELECT * FROM users");

        // Act
        listener.onAfterExecuteQuery(mockStatementInfo, 100_000_000L, null, null);

        // Assert
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals(-1, event.getRowsAffected()); // -1 indicates not applicable
    }

    @Test
    void testThreadLocalCoordination_noValidationResult_shouldStillLogAudit() throws Exception {
        // Arrange
        P6SpySqlSafetyListener.clearValidationResult(); // Ensure no result
        
        when(mockStatementInfo.getSqlWithValues()).thenReturn("SELECT * FROM users");

        // Act
        listener.onAfterExecuteQuery(mockStatementInfo, 100_000_000L, null, null);

        // Assert
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertNull(event.getViolations()); // No violations
    }

    @Test
    void testMultipleInvocations_shouldLogIndependently() throws Exception {
        // Arrange
        when(mockStatementInfo.getSqlWithValues())
            .thenReturn("SELECT * FROM users WHERE id = 1")
            .thenReturn("SELECT * FROM users WHERE id = 2");

        // Act
        listener.onAfterExecuteQuery(mockStatementInfo, 100_000_000L, null, null);
        listener.onAfterExecuteQuery(mockStatementInfo, 100_000_000L, null, null);

        // Assert
        verify(mockAuditWriter, times(2)).writeAuditLog(any());
    }
}
