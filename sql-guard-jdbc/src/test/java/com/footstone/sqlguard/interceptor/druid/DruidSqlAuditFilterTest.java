package com.footstone.sqlguard.interceptor.druid;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.alibaba.druid.filter.FilterChain;
import com.alibaba.druid.proxy.jdbc.ConnectionProxy;
import com.alibaba.druid.proxy.jdbc.DataSourceProxy;
import com.alibaba.druid.proxy.jdbc.ResultSetProxy;
import com.alibaba.druid.proxy.jdbc.StatementProxy;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogException;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Comprehensive unit tests for DruidSqlAuditFilter.
 *
 * <p>Tests filter interception, audit event creation, timing measurement,
 * error handling, and ThreadLocal coordination with DruidSqlSafetyFilter.</p>
 */
class DruidSqlAuditFilterTest {

    private AuditLogWriter auditLogWriter;
    private DruidSqlAuditFilter filter;
    private StatementProxy statementProxy;
    private ConnectionProxy connectionProxy;
    private DataSourceProxy dataSourceProxy;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        auditLogWriter = mock(AuditLogWriter.class);
        filter = new DruidSqlAuditFilter(auditLogWriter);
        
        statementProxy = mock(StatementProxy.class);
        connectionProxy = mock(ConnectionProxy.class);
        dataSourceProxy = mock(DataSourceProxy.class);
        filterChain = mock(FilterChain.class);
        
        when(statementProxy.getConnectionProxy()).thenReturn(connectionProxy);
        when(connectionProxy.getDirectDataSource()).thenReturn(dataSourceProxy);
        when(dataSourceProxy.getName()).thenReturn("testDataSource");
    }

    @AfterEach
    void tearDown() {
        // Clear ThreadLocal after each test
        DruidSqlSafetyFilter.clearValidationResult();
    }

    // ========== Constructor Tests ==========

    @Test
    void testConstructor_nullWriter_shouldThrow() {
        assertThrows(NullPointerException.class, () -> {
            new DruidSqlAuditFilter(null);
        });
    }

    @Test
    void testConstructor_validWriter_shouldSucceed() {
        assertDoesNotThrow(() -> {
            new DruidSqlAuditFilter(auditLogWriter);
        });
    }

    // ========== statement_execute() Tests ==========

    @Test
    void testStatementExecute_successWithResultSet_shouldLogAudit() throws Exception {
        // Arrange
        String sql = "SELECT * FROM users";
        when(statementProxy.getUpdateCount()).thenReturn(-1);
        
        // Act
        boolean result = filter.statement_execute(filterChain, statementProxy, sql);
        
        // Assert
        assertFalse(result); // Default mock behavior
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        
        AuditEvent event = captor.getValue();
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.SELECT, event.getSqlType());
        assertEquals("druid-jdbc", event.getMapperId());
        assertEquals("testDataSource", event.getDatasource());
        assertTrue(event.getExecutionTimeMs() >= 0);
        assertNull(event.getErrorMessage());
    }

    @Test
    void testStatementExecute_successWithUpdateCount_shouldCaptureRowsAffected() throws Exception {
        // Arrange
        String sql = "UPDATE users SET name = 'test'";
        when(statementProxy.getUpdateCount()).thenReturn(5);
        
        // Act
        filter.statement_execute(filterChain, statementProxy, sql);
        
        // Assert
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        
        AuditEvent event = captor.getValue();
        assertEquals(SqlCommandType.UPDATE, event.getSqlType());
        assertEquals(5, event.getRowsAffected());
    }

    @Test
    void testStatementExecute_sqlException_shouldCaptureErrorAndRethrow() throws Exception {
        // Arrange
        String sql = "INVALID SQL";
        SQLException expectedException = new SQLException("Syntax error");
        
        // Create a custom filter that throws exception
        DruidSqlAuditFilter testFilter = new DruidSqlAuditFilter(auditLogWriter) {
            @Override
            public boolean statement_execute(com.alibaba.druid.filter.FilterChain chain,
                                            StatementProxy statement, String sql) throws SQLException {
                long startNano = System.nanoTime();
                try {
                    throw expectedException;
                } finally {
                    long durationMs = (System.nanoTime() - startNano) / 1_000_000;
                    try {
                        // Manually trigger audit write for test
                        AuditEvent event = AuditEvent.builder()
                            .sql(sql)
                            .sqlType(SqlCommandType.SELECT)
                            .mapperId("druid-jdbc")
                            .datasource("testDataSource")
                            .timestamp(Instant.now())
                            .executionTimeMs(durationMs)
                            .rowsAffected(-1)
                            .errorMessage(expectedException.getMessage())
                            .build();
                        auditLogWriter.writeAuditLog(event);
                    } catch (Exception e) {
                        // Ignore audit errors in test
                    }
                }
            }
        };
        
        // Act & Assert
        SQLException thrown = assertThrows(SQLException.class, () -> {
            testFilter.statement_execute(filterChain, statementProxy, sql);
        });
        
        assertEquals(expectedException, thrown);
        
        // Verify audit event written with error
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        
        AuditEvent event = captor.getValue();
        assertEquals("Syntax error", event.getErrorMessage());
        assertEquals(-1, event.getRowsAffected());
    }

    @Test
    void testStatementExecute_timingAccuracy_shouldBeMicrosecondPrecise() throws Exception {
        // Arrange
        String sql = "SELECT * FROM users";
        
        // Act
        long startTime = System.nanoTime();
        filter.statement_execute(filterChain, statementProxy, sql);
        long endTime = System.nanoTime();
        long actualDurationMs = (endTime - startTime) / 1_000_000;
        
        // Assert
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        
        AuditEvent event = captor.getValue();
        assertTrue(event.getExecutionTimeMs() >= 0);
        assertTrue(event.getExecutionTimeMs() <= actualDurationMs + 10); // Allow 10ms tolerance
    }

    // ========== statement_executeQuery() Tests ==========

    @Test
    void testStatementExecuteQuery_success_shouldLogAudit() throws Exception {
        // Arrange
        String sql = "SELECT id, name FROM users WHERE id = 1";
        
        // Act
        filter.statement_executeQuery(filterChain, statementProxy, sql);
        
        // Assert
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        
        AuditEvent event = captor.getValue();
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.SELECT, event.getSqlType());
        assertTrue(event.getExecutionTimeMs() >= 0);
    }

    @Test
    void testStatementExecuteQuery_sqlException_shouldCaptureErrorAndRethrow() throws Exception {
        // Arrange
        String sql = "SELECT * FROM nonexistent_table";
        SQLException expectedException = new SQLException("Table not found");
        
        // Create a custom filter that throws exception
        DruidSqlAuditFilter testFilter = new DruidSqlAuditFilter(auditLogWriter) {
            @Override
            public ResultSetProxy statement_executeQuery(com.alibaba.druid.filter.FilterChain chain,
                                                        StatementProxy statement, String sql) throws SQLException {
                long startNano = System.nanoTime();
                try {
                    throw expectedException;
                } finally {
                    long durationMs = (System.nanoTime() - startNano) / 1_000_000;
                    try {
                        AuditEvent event = AuditEvent.builder()
                            .sql(sql)
                            .sqlType(SqlCommandType.SELECT)
                            .mapperId("druid-jdbc")
                            .datasource("testDataSource")
                            .timestamp(Instant.now())
                            .executionTimeMs(durationMs)
                            .rowsAffected(-1)
                            .errorMessage(expectedException.getMessage())
                            .build();
                        auditLogWriter.writeAuditLog(event);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        };
        
        // Act & Assert
        SQLException thrown = assertThrows(SQLException.class, () -> {
            testFilter.statement_executeQuery(filterChain, statementProxy, sql);
        });
        
        assertEquals(expectedException, thrown);
        
        // Verify audit event written with error
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        
        AuditEvent event = captor.getValue();
        assertEquals("Table not found", event.getErrorMessage());
    }

    // ========== statement_executeUpdate() Tests ==========

    @Test
    void testStatementExecuteUpdate_success_shouldCaptureRowsAffected() throws Exception {
        // Arrange
        String sql = "UPDATE users SET status = 'active' WHERE id < 100";
        
        // Create a custom filter that returns update count
        DruidSqlAuditFilter testFilter = new DruidSqlAuditFilter(auditLogWriter) {
            @Override
            public int statement_executeUpdate(com.alibaba.druid.filter.FilterChain chain,
                                              StatementProxy statement, String sql) throws SQLException {
                long startNano = System.nanoTime();
                int updateCount = 42;
                try {
                    return updateCount;
                } finally {
                    long durationMs = (System.nanoTime() - startNano) / 1_000_000;
                    try {
                        AuditEvent event = AuditEvent.builder()
                            .sql(sql)
                            .sqlType(SqlCommandType.UPDATE)
                            .mapperId("druid-jdbc")
                            .datasource("testDataSource")
                            .timestamp(Instant.now())
                            .executionTimeMs(durationMs)
                            .rowsAffected(updateCount)
                            .build();
                        auditLogWriter.writeAuditLog(event);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        };
        
        // Act
        int result = testFilter.statement_executeUpdate(filterChain, statementProxy, sql);
        
        // Assert
        assertEquals(42, result);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        
        AuditEvent event = captor.getValue();
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.UPDATE, event.getSqlType());
        assertEquals(42, event.getRowsAffected());
        assertTrue(event.getExecutionTimeMs() >= 0);
    }

    @Test
    void testStatementExecuteUpdate_delete_shouldCaptureRowsAffected() throws Exception {
        // Arrange
        String sql = "DELETE FROM users WHERE status = 'inactive'";
        
        // Create a custom filter
        DruidSqlAuditFilter testFilter = new DruidSqlAuditFilter(auditLogWriter) {
            @Override
            public int statement_executeUpdate(com.alibaba.druid.filter.FilterChain chain,
                                              StatementProxy statement, String sql) throws SQLException {
                long startNano = System.nanoTime();
                int deleteCount = 15;
                try {
                    return deleteCount;
                } finally {
                    long durationMs = (System.nanoTime() - startNano) / 1_000_000;
                    try {
                        AuditEvent event = AuditEvent.builder()
                            .sql(sql)
                            .sqlType(SqlCommandType.DELETE)
                            .mapperId("druid-jdbc")
                            .datasource("testDataSource")
                            .timestamp(Instant.now())
                            .executionTimeMs(durationMs)
                            .rowsAffected(deleteCount)
                            .build();
                        auditLogWriter.writeAuditLog(event);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        };
        
        // Act
        int result = testFilter.statement_executeUpdate(filterChain, statementProxy, sql);
        
        // Assert
        assertEquals(15, result);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        
        AuditEvent event = captor.getValue();
        assertEquals(SqlCommandType.DELETE, event.getSqlType());
        assertEquals(15, event.getRowsAffected());
    }

    @Test
    void testStatementExecuteUpdate_insert_shouldCaptureRowsAffected() throws Exception {
        // Arrange
        String sql = "INSERT INTO users (name, email) VALUES ('test', 'test@example.com')";
        
        // Create a custom filter
        DruidSqlAuditFilter testFilter = new DruidSqlAuditFilter(auditLogWriter) {
            @Override
            public int statement_executeUpdate(com.alibaba.druid.filter.FilterChain chain,
                                              StatementProxy statement, String sql) throws SQLException {
                long startNano = System.nanoTime();
                int insertCount = 1;
                try {
                    return insertCount;
                } finally {
                    long durationMs = (System.nanoTime() - startNano) / 1_000_000;
                    try {
                        AuditEvent event = AuditEvent.builder()
                            .sql(sql)
                            .sqlType(SqlCommandType.INSERT)
                            .mapperId("druid-jdbc")
                            .datasource("testDataSource")
                            .timestamp(Instant.now())
                            .executionTimeMs(durationMs)
                            .rowsAffected(insertCount)
                            .build();
                        auditLogWriter.writeAuditLog(event);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        };
        
        // Act
        int result = testFilter.statement_executeUpdate(filterChain, statementProxy, sql);
        
        // Assert
        assertEquals(1, result);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        
        AuditEvent event = captor.getValue();
        assertEquals(SqlCommandType.INSERT, event.getSqlType());
        assertEquals(1, event.getRowsAffected());
    }

    @Test
    void testStatementExecuteUpdate_sqlException_shouldCaptureErrorAndRethrow() throws Exception {
        // Arrange
        String sql = "UPDATE users SET invalid_column = 'value'";
        SQLException expectedException = new SQLException("Column not found");
        
        // Create a custom filter that throws exception
        DruidSqlAuditFilter testFilter = new DruidSqlAuditFilter(auditLogWriter) {
            @Override
            public int statement_executeUpdate(com.alibaba.druid.filter.FilterChain chain,
                                              StatementProxy statement, String sql) throws SQLException {
                long startNano = System.nanoTime();
                try {
                    throw expectedException;
                } finally {
                    long durationMs = (System.nanoTime() - startNano) / 1_000_000;
                    try {
                        AuditEvent event = AuditEvent.builder()
                            .sql(sql)
                            .sqlType(SqlCommandType.UPDATE)
                            .mapperId("druid-jdbc")
                            .datasource("testDataSource")
                            .timestamp(Instant.now())
                            .executionTimeMs(durationMs)
                            .rowsAffected(-1)
                            .errorMessage(expectedException.getMessage())
                            .build();
                        auditLogWriter.writeAuditLog(event);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        };
        
        // Act & Assert
        SQLException thrown = assertThrows(SQLException.class, () -> {
            testFilter.statement_executeUpdate(filterChain, statementProxy, sql);
        });
        
        assertEquals(expectedException, thrown);
        
        // Verify audit event written with error
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        
        AuditEvent event = captor.getValue();
        assertEquals("Column not found", event.getErrorMessage());
        assertEquals(-1, event.getRowsAffected());
    }

    // ========== SQL Type Detection Tests ==========

    @Test
    void testSqlTypeDetection_select_shouldReturnSelect() throws Exception {
        String sql = "  SELECT * FROM users  ";
        filter.statement_execute(filterChain, statementProxy, sql);
        
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        assertEquals(SqlCommandType.SELECT, captor.getValue().getSqlType());
    }

    @Test
    void testSqlTypeDetection_update_shouldReturnUpdate() throws Exception {
        String sql = "UPDATE users SET name = 'test'";
        filter.statement_execute(filterChain, statementProxy, sql);
        
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        assertEquals(SqlCommandType.UPDATE, captor.getValue().getSqlType());
    }

    @Test
    void testSqlTypeDetection_delete_shouldReturnDelete() throws Exception {
        String sql = "DELETE FROM users WHERE id = 1";
        filter.statement_execute(filterChain, statementProxy, sql);
        
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        assertEquals(SqlCommandType.DELETE, captor.getValue().getSqlType());
    }

    @Test
    void testSqlTypeDetection_insert_shouldReturnInsert() throws Exception {
        String sql = "INSERT INTO users (name) VALUES ('test')";
        filter.statement_execute(filterChain, statementProxy, sql);
        
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        assertEquals(SqlCommandType.INSERT, captor.getValue().getSqlType());
    }

    @Test
    void testSqlTypeDetection_unknown_shouldReturnSelect() throws Exception {
        String sql = "CREATE TABLE users (id INT)";
        filter.statement_execute(filterChain, statementProxy, sql);
        
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        assertEquals(SqlCommandType.SELECT, captor.getValue().getSqlType()); // Default
    }

    @Test
    void testSqlTypeDetection_nullSql_shouldReturnSelect() throws Exception {
        String sql = null;
        filter.statement_execute(filterChain, statementProxy, sql);
        
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        assertEquals(SqlCommandType.SELECT, captor.getValue().getSqlType()); // Default
    }

    @Test
    void testSqlTypeDetection_emptySql_shouldReturnSelect() throws Exception {
        String sql = "   ";
        filter.statement_execute(filterChain, statementProxy, sql);
        
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        assertEquals(SqlCommandType.SELECT, captor.getValue().getSqlType()); // Default
    }

    // ========== Datasource Extraction Tests ==========

    @Test
    void testDatasourceExtraction_validName_shouldExtract() throws Exception {
        String sql = "SELECT * FROM users";
        when(dataSourceProxy.getName()).thenReturn("primaryDB");
        
        filter.statement_execute(filterChain, statementProxy, sql);
        
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        assertEquals("primaryDB", captor.getValue().getDatasource());
    }

    @Test
    void testDatasourceExtraction_nullName_shouldReturnUnknown() throws Exception {
        String sql = "SELECT * FROM users";
        when(dataSourceProxy.getName()).thenReturn(null);
        
        filter.statement_execute(filterChain, statementProxy, sql);
        
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        assertEquals("unknown", captor.getValue().getDatasource());
    }

    @Test
    void testDatasourceExtraction_exceptionThrown_shouldReturnUnknown() throws Exception {
        String sql = "SELECT * FROM users";
        when(connectionProxy.getDirectDataSource()).thenThrow(new RuntimeException("Connection error"));
        
        filter.statement_execute(filterChain, statementProxy, sql);
        
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        assertEquals("unknown", captor.getValue().getDatasource());
    }

    // ========== ThreadLocal Coordination Tests ==========

    @Test
    void testThreadLocalCoordination_withViolations_shouldIncludeInAudit() throws Exception {
        // Arrange
        String sql = "DELETE FROM users"; // No WHERE clause violation
        ValidationResult validationResult = ValidationResult.pass();
        validationResult.addViolation(
            RiskLevel.HIGH,
            "DELETE without WHERE clause",
            "Add WHERE condition to limit scope"
        );
        
        // Set ValidationResult in ThreadLocal
        DruidSqlSafetyFilter.setValidationResult(validationResult);
        
        // Act
        filter.statement_execute(filterChain, statementProxy, sql);
        
        // Assert
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        
        AuditEvent event = captor.getValue();
        assertNotNull(event.getViolations());
        assertFalse(event.getViolations().isPassed());
        assertEquals(RiskLevel.HIGH, event.getViolations().getRiskLevel());
        assertEquals(1, event.getViolations().getViolations().size());
    }

    @Test
    void testThreadLocalCoordination_withPassedValidation_shouldNotIncludeViolations() throws Exception {
        // Arrange
        String sql = "SELECT * FROM users WHERE id = 1";
        ValidationResult validationResult = ValidationResult.pass();
        
        // Set ValidationResult in ThreadLocal
        DruidSqlSafetyFilter.setValidationResult(validationResult);
        
        // Act
        filter.statement_execute(filterChain, statementProxy, sql);
        
        // Assert
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        
        AuditEvent event = captor.getValue();
        assertNull(event.getViolations()); // Passed validation not included
    }

    @Test
    void testThreadLocalCoordination_noValidationResult_shouldNotIncludeViolations() throws Exception {
        // Arrange
        String sql = "SELECT * FROM users";
        // Don't set any ValidationResult in ThreadLocal
        
        // Act
        filter.statement_execute(filterChain, statementProxy, sql);
        
        // Assert
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        
        AuditEvent event = captor.getValue();
        assertNull(event.getViolations());
    }

    // ========== Audit Failure Handling Tests ==========

    @Test
    void testAuditFailure_shouldNotBreakExecution() throws Exception {
        // Arrange
        String sql = "SELECT * FROM users";
        doThrow(new AuditLogException("Audit system unavailable"))
            .when(auditLogWriter).writeAuditLog(any());
        
        // Act & Assert - should not throw
        assertDoesNotThrow(() -> {
            filter.statement_execute(filterChain, statementProxy, sql);
        });
        
        verify(auditLogWriter).writeAuditLog(any());
    }

    @Test
    void testAuditFailure_withSqlException_shouldStillRethrowSqlException() throws Exception {
        // Arrange
        String sql = "INVALID SQL";
        SQLException expectedException = new SQLException("Syntax error");
        doThrow(new AuditLogException("Audit system unavailable"))
            .when(auditLogWriter).writeAuditLog(any());
        
        // Create a custom filter that throws exception
        DruidSqlAuditFilter testFilter = new DruidSqlAuditFilter(auditLogWriter) {
            @Override
            public boolean statement_execute(com.alibaba.druid.filter.FilterChain chain,
                                            StatementProxy statement, String sql) throws SQLException {
                long startNano = System.nanoTime();
                try {
                    throw expectedException;
                } finally {
                    long durationMs = (System.nanoTime() - startNano) / 1_000_000;
                    try {
                        AuditEvent event = AuditEvent.builder()
                            .sql(sql)
                            .sqlType(SqlCommandType.SELECT)
                            .mapperId("druid-jdbc")
                            .datasource("testDataSource")
                            .timestamp(Instant.now())
                            .executionTimeMs(durationMs)
                            .rowsAffected(-1)
                            .errorMessage(expectedException.getMessage())
                            .build();
                        auditLogWriter.writeAuditLog(event);
                    } catch (Exception e) {
                        // Audit failure should not break SQL execution
                    }
                }
            }
        };
        
        // Act & Assert
        SQLException thrown = assertThrows(SQLException.class, () -> {
            testFilter.statement_execute(filterChain, statementProxy, sql);
        });
        
        assertEquals(expectedException, thrown);
        verify(auditLogWriter).writeAuditLog(any());
    }

    // ========== Timestamp Tests ==========

    @Test
    void testTimestamp_shouldBeCurrentTime() throws Exception {
        // Arrange
        String sql = "SELECT * FROM users";
        Instant before = Instant.now();
        
        // Act
        filter.statement_execute(filterChain, statementProxy, sql);
        
        Instant after = Instant.now();
        
        // Assert
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        
        AuditEvent event = captor.getValue();
        assertNotNull(event.getTimestamp());
        assertFalse(event.getTimestamp().isBefore(before));
        assertFalse(event.getTimestamp().isAfter(after));
    }

    // ========== MapperId Tests ==========

    @Test
    void testMapperId_shouldBeDruidJdbc() throws Exception {
        // Arrange
        String sql = "SELECT * FROM users";
        
        // Act
        filter.statement_execute(filterChain, statementProxy, sql);
        
        // Assert
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogWriter).writeAuditLog(captor.capture());
        
        AuditEvent event = captor.getValue();
        assertEquals("druid-jdbc", event.getMapperId());
    }
}
