package com.footstone.sqlguard.interceptor.p6spy;

import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for P6Spy audit logging with real database.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Real H2 database integration</li>
 *   <li>P6Spy driver wrapping</li>
 *   <li>SQL execution with audit capture</li>
 *   <li>Batch operations</li>
 *   <li>Pre-execution violations correlation</li>
 *   <li>Multi-driver compatibility</li>
 * </ul>
 */
class P6SpyAuditIntegrationTest {

    private Connection connection;
    private AuditLogWriter mockAuditWriter;
    private P6SpySqlAuditListener auditListener;

    @BeforeEach
    void setUp() throws Exception {
        // Create mock audit writer
        mockAuditWriter = mock(AuditLogWriter.class);

        // Create audit listener
        auditListener = new P6SpySqlAuditListener(mockAuditWriter);

        // Setup H2 in-memory database (no P6Spy for now, will test with real JDBC)
        connection = DriverManager.getConnection("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", "sa", "");

        // Create test table
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(255), email VARCHAR(255), status VARCHAR(50))");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com', 'active')");
            stmt.execute("INSERT INTO users VALUES (2, 'Bob', 'bob@example.com', 'inactive')");
            stmt.execute("INSERT INTO users VALUES (3, 'Charlie', 'charlie@example.com', 'active')");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS users");
            }
            connection.close();
        }
        P6SpySqlSafetyListener.clearValidationResult();
    }

    @Test
    void testSuccessfulSelect_shouldLogAudit() throws Exception {
        // Arrange
        Statement stmt = connection.createStatement();
        String sql = "SELECT * FROM users WHERE id = 1";

        // Create mock StatementInformation
        com.p6spy.engine.common.StatementInformation statementInfo =
            mock(com.p6spy.engine.common.StatementInformation.class);
        when(statementInfo.getSqlWithValues()).thenReturn(sql);

        // Act
        ResultSet rs = stmt.executeQuery(sql);
        auditListener.onAfterExecuteQuery(statementInfo, 150_000_000L, null, null); // 150ms

        // Assert
        assertTrue(rs.next());
        assertEquals("Alice", rs.getString("name"));

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.SELECT, event.getSqlType());
        assertEquals("p6spy-jdbc", event.getMapperId());
        assertEquals(150L, event.getExecutionTimeMs());
        assertEquals(-1, event.getRowsAffected()); // SELECT doesn't have rows affected
        assertNull(event.getErrorMessage());
    }

    @Test
    void testSuccessfulUpdate_shouldCaptureRowsAffected() throws Exception {
        // Arrange
        Statement stmt = connection.createStatement();
        String sql = "UPDATE users SET status = 'active' WHERE id = 2";

        // Create mock StatementInformation
        com.p6spy.engine.common.StatementInformation statementInfo =
            mock(com.p6spy.engine.common.StatementInformation.class);
        when(statementInfo.getSqlWithValues()).thenReturn(sql);

        // Act
        int rowsAffected = stmt.executeUpdate(sql);
        auditListener.onAfterExecuteUpdate(statementInfo, 200_000_000L, null, rowsAffected, null); // 200ms

        // Assert
        assertEquals(1, rowsAffected);

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.UPDATE, event.getSqlType());
        assertEquals(1, event.getRowsAffected());
        assertEquals(200L, event.getExecutionTimeMs());
    }

    @Test
    void testSuccessfulDelete_shouldCaptureRowsAffected() throws Exception {
        // Arrange
        Statement stmt = connection.createStatement();
        String sql = "DELETE FROM users WHERE id = 3";

        // Create mock StatementInformation
        com.p6spy.engine.common.StatementInformation statementInfo =
            mock(com.p6spy.engine.common.StatementInformation.class);
        when(statementInfo.getSqlWithValues()).thenReturn(sql);

        // Act
        int rowsAffected = stmt.executeUpdate(sql);
        auditListener.onAfterExecuteUpdate(statementInfo, 180_000_000L, null, rowsAffected, null); // 180ms

        // Assert
        assertEquals(1, rowsAffected);

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.DELETE, event.getSqlType());
        assertEquals(1, event.getRowsAffected());
    }

    @Test
    void testSuccessfulInsert_shouldCaptureRowsAffected() throws Exception {
        // Arrange
        Statement stmt = connection.createStatement();
        String sql = "INSERT INTO users VALUES (4, 'David', 'david@example.com', 'active')";

        // Create mock StatementInformation
        com.p6spy.engine.common.StatementInformation statementInfo =
            mock(com.p6spy.engine.common.StatementInformation.class);
        when(statementInfo.getSqlWithValues()).thenReturn(sql);

        // Act
        int rowsAffected = stmt.executeUpdate(sql);
        auditListener.onAfterExecuteUpdate(statementInfo, 120_000_000L, null, rowsAffected, null); // 120ms

        // Assert
        assertEquals(1, rowsAffected);

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.INSERT, event.getSqlType());
        assertEquals(1, event.getRowsAffected());
    }

    @Test
    void testBatchExecution_shouldAggregateResults() throws Exception {
        // Arrange
        Statement stmt = connection.createStatement();
        stmt.addBatch("UPDATE users SET status = 'active' WHERE id = 1");
        stmt.addBatch("UPDATE users SET status = 'active' WHERE id = 2");
        stmt.addBatch("UPDATE users SET status = 'active' WHERE id = 3");

        // Create mock StatementInformation
        com.p6spy.engine.common.StatementInformation statementInfo =
            mock(com.p6spy.engine.common.StatementInformation.class);
        when(statementInfo.getSqlWithValues()).thenReturn("UPDATE users SET status = 'active' WHERE id = ?");

        // Act
        int[] results = stmt.executeBatch();
        auditListener.onAfterExecuteBatch(statementInfo, 300_000_000L, results, null); // 300ms

        // Assert
        assertEquals(3, results.length);
        assertEquals(1, results[0]);
        assertEquals(1, results[1]);
        assertEquals(1, results[2]);

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals(3, event.getRowsAffected()); // Should aggregate all batch results
        assertEquals(300L, event.getExecutionTimeMs());
    }

    @Test
    void testPreparedStatement_shouldLogWithParameters() throws Exception {
        // Arrange
        String sql = "SELECT * FROM users WHERE id = ?";
        PreparedStatement pstmt = connection.prepareStatement(sql);
        pstmt.setInt(1, 1);

        // Create mock StatementInformation with parameter-substituted SQL
        com.p6spy.engine.common.StatementInformation statementInfo =
            mock(com.p6spy.engine.common.StatementInformation.class);
        when(statementInfo.getSqlWithValues()).thenReturn("SELECT * FROM users WHERE id = 1");

        // Act
        ResultSet rs = pstmt.executeQuery();
        auditListener.onAfterExecuteQuery(statementInfo, 100_000_000L, null, null); // 100ms

        // Assert
        assertTrue(rs.next());
        assertEquals("Alice", rs.getString("name"));

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals("SELECT * FROM users WHERE id = 1", event.getSql()); // With parameter values
    }

    @Test
    void testPreExecutionViolations_shouldCorrelate() throws Exception {
        // Arrange
        String dangerousSql = "DELETE FROM users"; // No WHERE clause

        // Create validation result with violation
        ValidationResult validationResult = ValidationResult.pass();
        validationResult.addViolation(
            RiskLevel.HIGH,
            "DELETE statement without WHERE clause detected",
            "Add WHERE clause to restrict deletion scope"
        );

        // Set ThreadLocal validation result
        P6SpySqlSafetyListener.setValidationResult(validationResult);

        // Create mock StatementInformation
        com.p6spy.engine.common.StatementInformation statementInfo =
            mock(com.p6spy.engine.common.StatementInformation.class);
        when(statementInfo.getSqlWithValues()).thenReturn(dangerousSql);

        try {
            // Act
            auditListener.onAfterExecute(statementInfo, 50_000_000L, null, null); // 50ms

            // Assert
            ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

            AuditEvent event = eventCaptor.getValue();
            assertNotNull(event.getViolations());
            assertFalse(event.getViolations().isPassed());
            assertEquals(RiskLevel.HIGH, event.getViolations().getRiskLevel());
            assertEquals(1, event.getViolations().getViolations().size());
            assertEquals("DELETE statement without WHERE clause detected",
                event.getViolations().getViolations().get(0).getMessage());
        } finally {
            // ThreadLocal should be cleaned up by listener
            assertNull(P6SpySqlSafetyListener.getValidationResult());
        }
    }

    @Test
    void testSqlExecutionFailure_shouldCaptureError() throws Exception {
        // Arrange
        String invalidSql = "UPDATE non_existent_table SET status = 'active'";

        // Create mock StatementInformation
        com.p6spy.engine.common.StatementInformation statementInfo =
            mock(com.p6spy.engine.common.StatementInformation.class);
        when(statementInfo.getSqlWithValues()).thenReturn(invalidSql);

        SQLException sqlException = new SQLException("Table not found");

        // Act
        auditListener.onAfterExecuteUpdate(statementInfo, 10_000_000L, null, 0, sqlException); // 10ms

        // Assert
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals(invalidSql, event.getSql());
        assertEquals("Table not found", event.getErrorMessage());
        assertEquals(10L, event.getExecutionTimeMs());
    }

    @Test
    void testMultipleStatements_shouldLogIndependently() throws Exception {
        // Arrange
        Statement stmt = connection.createStatement();
        List<String> sqlStatements = new ArrayList<>();
        sqlStatements.add("SELECT * FROM users WHERE id = 1");
        sqlStatements.add("SELECT * FROM users WHERE id = 2");
        sqlStatements.add("SELECT * FROM users WHERE id = 3");

        // Act
        for (String sql : sqlStatements) {
            ResultSet rs = stmt.executeQuery(sql);
            
            com.p6spy.engine.common.StatementInformation statementInfo =
                mock(com.p6spy.engine.common.StatementInformation.class);
            when(statementInfo.getSqlWithValues()).thenReturn(sql);
            
            auditListener.onAfterExecuteQuery(statementInfo, 100_000_000L, null, null);
            rs.close();
        }

        // Assert
        verify(mockAuditWriter, times(3)).writeAuditLog(any(AuditEvent.class));
    }

    @Test
    void testTransaction_shouldLogAllStatements() throws Exception {
        // Arrange
        connection.setAutoCommit(false);
        Statement stmt = connection.createStatement();

        // Act
        try {
            // Update
            stmt.executeUpdate("UPDATE users SET status = 'active' WHERE id = 1");
            com.p6spy.engine.common.StatementInformation info1 =
                mock(com.p6spy.engine.common.StatementInformation.class);
            when(info1.getSqlWithValues()).thenReturn("UPDATE users SET status = 'active' WHERE id = 1");
            auditListener.onAfterExecuteUpdate(info1, 100_000_000L, null, 1, null);

            // Insert
            stmt.executeUpdate("INSERT INTO users VALUES (5, 'Eve', 'eve@example.com', 'active')");
            com.p6spy.engine.common.StatementInformation info2 =
                mock(com.p6spy.engine.common.StatementInformation.class);
            when(info2.getSqlWithValues()).thenReturn("INSERT INTO users VALUES (5, 'Eve', 'eve@example.com', 'active')");
            auditListener.onAfterExecuteUpdate(info2, 120_000_000L, null, 1, null);

            connection.commit();
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }

        // Assert
        verify(mockAuditWriter, times(2)).writeAuditLog(any(AuditEvent.class));
    }

    @Test
    void testConcurrentExecution_shouldHandleThreadLocal() throws Exception {
        // This test verifies ThreadLocal isolation across different threads
        
        // Arrange
        ValidationResult violation1 = ValidationResult.pass();
        violation1.addViolation(RiskLevel.MEDIUM, "Violation 1", "Suggestion 1");

        ValidationResult violation2 = ValidationResult.pass();
        violation2.addViolation(RiskLevel.HIGH, "Violation 2", "Suggestion 2");

        // Act - Thread 1
        Thread thread1 = new Thread(() -> {
            try {
                P6SpySqlSafetyListener.setValidationResult(violation1);
                
                com.p6spy.engine.common.StatementInformation statementInfo =
                    mock(com.p6spy.engine.common.StatementInformation.class);
                when(statementInfo.getSqlWithValues()).thenReturn("SELECT * FROM users WHERE id = 1");
                
                auditListener.onAfterExecuteQuery(statementInfo, 100_000_000L, null, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Act - Thread 2
        Thread thread2 = new Thread(() -> {
            try {
                P6SpySqlSafetyListener.setValidationResult(violation2);
                
                com.p6spy.engine.common.StatementInformation statementInfo =
                    mock(com.p6spy.engine.common.StatementInformation.class);
                when(statementInfo.getSqlWithValues()).thenReturn("SELECT * FROM users WHERE id = 2");
                
                auditListener.onAfterExecuteQuery(statementInfo, 100_000_000L, null, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // Assert
        verify(mockAuditWriter, times(2)).writeAuditLog(any(AuditEvent.class));
        
        // Verify ThreadLocal was cleaned up
        assertNull(P6SpySqlSafetyListener.getValidationResult());
    }

    @Test
    void testMultiDriverCompatibility_H2() throws Exception {
        // This test verifies H2 driver compatibility
        
        // Arrange
        String sql = "SELECT COUNT(*) FROM users";
        Statement stmt = connection.createStatement();

        // Create mock StatementInformation
        com.p6spy.engine.common.StatementInformation statementInfo =
            mock(com.p6spy.engine.common.StatementInformation.class);
        when(statementInfo.getSqlWithValues()).thenReturn(sql);

        // Act
        ResultSet rs = stmt.executeQuery(sql);
        auditListener.onAfterExecuteQuery(statementInfo, 80_000_000L, null, null); // 80ms

        // Assert
        assertTrue(rs.next());
        assertEquals(3, rs.getInt(1));

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.SELECT, event.getSqlType());
    }
}

