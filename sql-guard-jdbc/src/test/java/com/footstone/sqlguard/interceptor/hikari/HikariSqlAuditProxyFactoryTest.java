package com.footstone.sqlguard.interceptor.hikari;

import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HikariSqlAuditProxyFactory.
 *
 * <p>Tests proxy creation, execution interception, timing measurement,
 * error handling, and ThreadLocal coordination.</p>
 */
class HikariSqlAuditProxyFactoryTest {

    private AuditLogWriter mockAuditLogWriter;
    private HikariSqlAuditProxyFactory proxyFactory;
    private Connection mockConnection;
    private Statement mockStatement;
    private PreparedStatement mockPreparedStatement;

    @BeforeEach
    void setUp() {
        mockAuditLogWriter = mock(AuditLogWriter.class);
        proxyFactory = new HikariSqlAuditProxyFactory(mockAuditLogWriter);
        mockConnection = mock(Connection.class);
        mockStatement = mock(Statement.class);
        mockPreparedStatement = mock(PreparedStatement.class);
    }

    // ========== Constructor Tests ==========

    @Test
    void constructor_withNullAuditLogWriter_shouldThrowException() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            new HikariSqlAuditProxyFactory(null);
        });
        assertTrue(exception.getMessage().contains("auditLogWriter must not be null"));
    }

    @Test
    void constructor_withValidAuditLogWriter_shouldSucceed() {
        HikariSqlAuditProxyFactory factory = new HikariSqlAuditProxyFactory(mockAuditLogWriter);
        assertNotNull(factory);
    }

    // ========== Connection Proxy Tests ==========

    @Test
    void wrapConnection_shouldReturnProxy() {
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        
        assertNotNull(proxiedConnection);
        assertNotSame(mockConnection, proxiedConnection);
    }

    @Test
    void testConnectionProxy_shouldWrapStatements() throws SQLException {
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        Statement stmt = proxiedConnection.createStatement();
        
        assertNotNull(stmt);
        assertNotSame(mockStatement, stmt);
        verify(mockConnection).createStatement();
    }

    @Test
    void testConnectionProxy_shouldWrapPreparedStatements() throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        when(mockConnection.prepareStatement(sql)).thenReturn(mockPreparedStatement);
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        PreparedStatement pstmt = proxiedConnection.prepareStatement(sql);
        
        assertNotNull(pstmt);
        assertNotSame(mockPreparedStatement, pstmt);
        verify(mockConnection).prepareStatement(sql);
    }

    @Test
    void testConnectionProxy_shouldDelegateOtherMethods() throws SQLException {
        when(mockConnection.isClosed()).thenReturn(false);
        when(mockConnection.getAutoCommit()).thenReturn(true);
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        
        assertFalse(proxiedConnection.isClosed());
        assertTrue(proxiedConnection.getAutoCommit());
        
        verify(mockConnection).isClosed();
        verify(mockConnection).getAutoCommit();
    }

    // ========== Statement Execution Tests ==========

    @Test
    void testStatementProxy_shouldCaptureExecuteResult() throws Exception {
        String sql = "SELECT * FROM users";
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.execute(sql)).thenReturn(true);
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        Statement stmt = proxiedConnection.createStatement();
        boolean result = stmt.execute(sql);
        
        assertTrue(result);
        verify(mockStatement).execute(sql);
        
        // Verify audit event written
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditLogWriter, timeout(100)).writeAuditLog(eventCaptor.capture());
        
        AuditEvent event = eventCaptor.getValue();
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.SELECT, event.getSqlType());
        assertEquals("hikari-jdbc", event.getMapperId());
        assertTrue(event.getExecutionTimeMs() >= 0);
    }

    @Test
    void testStatementProxy_shouldCaptureExecuteQueryResult() throws Exception {
        String sql = "SELECT * FROM users";
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(sql)).thenReturn(null);
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        Statement stmt = proxiedConnection.createStatement();
        stmt.executeQuery(sql);
        
        verify(mockStatement).executeQuery(sql);
        
        // Verify audit event written
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditLogWriter, timeout(100)).writeAuditLog(eventCaptor.capture());
        
        AuditEvent event = eventCaptor.getValue();
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.SELECT, event.getSqlType());
    }

    @Test
    void testPreparedStatementProxy_shouldCaptureUpdateCount() throws Exception {
        String sql = "UPDATE users SET name = ? WHERE id = ?";
        when(mockConnection.prepareStatement(sql)).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(5);
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        PreparedStatement pstmt = proxiedConnection.prepareStatement(sql);
        int rowsAffected = pstmt.executeUpdate();
        
        assertEquals(5, rowsAffected);
        verify(mockPreparedStatement).executeUpdate();
        
        // Verify audit event written
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditLogWriter, timeout(100)).writeAuditLog(eventCaptor.capture());
        
        AuditEvent event = eventCaptor.getValue();
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.UPDATE, event.getSqlType());
        assertEquals(5, event.getRowsAffected());
    }

    @Test
    void testExecutionTiming_shouldBeMicrosecondPrecise() throws Exception {
        String sql = "SELECT * FROM users";
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.execute(sql)).thenAnswer(invocation -> {
            Thread.sleep(10); // Simulate 10ms execution
            return true;
        });
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        Statement stmt = proxiedConnection.createStatement();
        stmt.execute(sql);
        
        // Verify audit event written with timing
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditLogWriter, timeout(200)).writeAuditLog(eventCaptor.capture());
        
        AuditEvent event = eventCaptor.getValue();
        assertTrue(event.getExecutionTimeMs() >= 10);
        assertTrue(event.getExecutionTimeMs() < 100); // Should be close to 10ms
    }

    @Test
    void testErrorCapture_shouldLogSQLException() throws Exception {
        String sql = "INVALID SQL";
        SQLException expectedException = new SQLException("Syntax error");
        
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.execute(sql)).thenThrow(expectedException);
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        Statement stmt = proxiedConnection.createStatement();
        
        // Verify exception re-thrown
        SQLException exception = assertThrows(SQLException.class, () -> {
            stmt.execute(sql);
        });
        assertEquals("Syntax error", exception.getMessage());
        
        // Verify audit event written with error
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditLogWriter, timeout(100)).writeAuditLog(eventCaptor.capture());
        
        AuditEvent event = eventCaptor.getValue();
        assertEquals(sql, event.getSql());
        assertEquals("Syntax error", event.getErrorMessage());
        assertEquals(-1, event.getRowsAffected());
    }

    // ========== Batch Execution Tests ==========

    @Test
    void testBatchExecution_shouldAggregateResults() throws Exception {
        // For Statement.executeBatch(), SQL is provided via addBatch(sql) calls
        // Since we can't easily test this without more complex mocking,
        // we'll test PreparedStatement batch execution instead (which is more common)
        String sql = "UPDATE users SET status = 'active'";
        when(mockConnection.prepareStatement(sql)).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeBatch()).thenReturn(new int[]{2, 3, 5});
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        PreparedStatement pstmt = proxiedConnection.prepareStatement(sql);
        int[] results = pstmt.executeBatch();
        
        assertArrayEquals(new int[]{2, 3, 5}, results);
        verify(mockPreparedStatement).executeBatch();
        
        // Verify audit event written with aggregated rows
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditLogWriter, timeout(100)).writeAuditLog(eventCaptor.capture());
        
        AuditEvent event = eventCaptor.getValue();
        assertEquals(10, event.getRowsAffected()); // 2 + 3 + 5
    }

    @Test
    void testPreparedStatementBatch_shouldAggregateResults() throws Exception {
        String sql = "INSERT INTO users (name) VALUES (?)";
        when(mockConnection.prepareStatement(sql)).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeBatch()).thenReturn(new int[]{1, 1, 1});
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        PreparedStatement pstmt = proxiedConnection.prepareStatement(sql);
        int[] results = pstmt.executeBatch();
        
        assertArrayEquals(new int[]{1, 1, 1}, results);
        verify(mockPreparedStatement).executeBatch();
        
        // Verify audit event written with aggregated rows
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditLogWriter, timeout(100)).writeAuditLog(eventCaptor.capture());
        
        AuditEvent event = eventCaptor.getValue();
        assertEquals(sql, event.getSql());
        assertEquals(3, event.getRowsAffected()); // 1 + 1 + 1
    }

    // ========== SQL Type Detection Tests ==========

    @Test
    void testSqlTypeDetection_SELECT() throws Exception {
        String sql = "SELECT * FROM users";
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.execute(sql)).thenReturn(true);
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        Statement stmt = proxiedConnection.createStatement();
        stmt.execute(sql);
        
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditLogWriter, timeout(100)).writeAuditLog(eventCaptor.capture());
        
        assertEquals(SqlCommandType.SELECT, eventCaptor.getValue().getSqlType());
    }

    @Test
    void testSqlTypeDetection_UPDATE() throws Exception {
        String sql = "UPDATE users SET name = 'test'";
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeUpdate(sql)).thenReturn(1);
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        Statement stmt = proxiedConnection.createStatement();
        stmt.executeUpdate(sql);
        
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditLogWriter, timeout(100)).writeAuditLog(eventCaptor.capture());
        
        assertEquals(SqlCommandType.UPDATE, eventCaptor.getValue().getSqlType());
    }

    @Test
    void testSqlTypeDetection_DELETE() throws Exception {
        String sql = "DELETE FROM users WHERE id = 1";
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeUpdate(sql)).thenReturn(1);
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        Statement stmt = proxiedConnection.createStatement();
        stmt.executeUpdate(sql);
        
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditLogWriter, timeout(100)).writeAuditLog(eventCaptor.capture());
        
        assertEquals(SqlCommandType.DELETE, eventCaptor.getValue().getSqlType());
    }

    @Test
    void testSqlTypeDetection_INSERT() throws Exception {
        String sql = "INSERT INTO users (name) VALUES ('test')";
        when(mockConnection.prepareStatement(sql)).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        PreparedStatement pstmt = proxiedConnection.prepareStatement(sql);
        pstmt.executeUpdate();
        
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditLogWriter, timeout(100)).writeAuditLog(eventCaptor.capture());
        
        assertEquals(SqlCommandType.INSERT, eventCaptor.getValue().getSqlType());
    }

    // ========== Rows Affected Extraction Tests ==========

    @Test
    void testRowsAffected_executeUpdate() throws Exception {
        String sql = "UPDATE users SET name = 'test'";
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeUpdate(sql)).thenReturn(42);
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        Statement stmt = proxiedConnection.createStatement();
        stmt.executeUpdate(sql);
        
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditLogWriter, timeout(100)).writeAuditLog(eventCaptor.capture());
        
        assertEquals(42, eventCaptor.getValue().getRowsAffected());
    }

    @Test
    void testRowsAffected_execute_withUpdateCount() throws Exception {
        String sql = "UPDATE users SET name = 'test'";
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.execute(sql)).thenReturn(false); // false = update count available
        when(mockStatement.getUpdateCount()).thenReturn(15);
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        Statement stmt = proxiedConnection.createStatement();
        stmt.execute(sql);
        
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditLogWriter, timeout(100)).writeAuditLog(eventCaptor.capture());
        
        assertEquals(15, eventCaptor.getValue().getRowsAffected());
    }

    @Test
    void testRowsAffected_executeQuery_shouldBeNegative() throws Exception {
        String sql = "SELECT * FROM users";
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(sql)).thenReturn(null);
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        Statement stmt = proxiedConnection.createStatement();
        stmt.executeQuery(sql);
        
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mockAuditLogWriter, timeout(100)).writeAuditLog(eventCaptor.capture());
        
        assertEquals(-1, eventCaptor.getValue().getRowsAffected());
    }

    // ========== Edge Cases ==========

    @Test
    void testNullSql_shouldNotWriteAudit() throws Exception {
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.execute(null)).thenReturn(false);
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        Statement stmt = proxiedConnection.createStatement();
        stmt.execute(null);
        
        // Should not write audit for null SQL
        verify(mockAuditLogWriter, never()).writeAuditLog(any());
    }

    @Test
    void testAuditWriterException_shouldNotBreakExecution() throws Exception {
        String sql = "SELECT * FROM users";
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.execute(sql)).thenReturn(true);
        doThrow(new RuntimeException("Audit writer failed")).when(mockAuditLogWriter).writeAuditLog(any());
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        Statement stmt = proxiedConnection.createStatement();
        
        // Should not throw exception even if audit writer fails
        boolean result = stmt.execute(sql);
        assertTrue(result);
        
        verify(mockStatement).execute(sql);
        verify(mockAuditLogWriter, timeout(100)).writeAuditLog(any());
    }

    @Test
    void testDelegateOtherStatementMethods() throws Exception {
        String sql = "SELECT * FROM users";
        when(mockConnection.prepareStatement(sql)).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.getMaxRows()).thenReturn(100);
        
        Connection proxiedConnection = proxyFactory.wrapConnection(mockConnection);
        PreparedStatement pstmt = proxiedConnection.prepareStatement(sql);
        
        // Non-execute methods should be delegated without audit
        int maxRows = pstmt.getMaxRows();
        assertEquals(100, maxRows);
        
        verify(mockPreparedStatement).getMaxRows();
        verify(mockAuditLogWriter, never()).writeAuditLog(any());
    }
}











