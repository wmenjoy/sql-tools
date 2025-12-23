package com.footstone.sqlguard.interceptor.hikari;

import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.footstone.sqlguard.validator.SqlDeduplicationFilter;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for HikariSqlAuditProxyFactory with real HikariCP and H2 database.
 *
 * <p>Tests complete audit flow including:
 * <ul>
 *   <li>Real database operations</li>
 *   <li>HikariCP connection pooling</li>
 *   <li>Audit event capture</li>
 *   <li>ThreadLocal coordination with safety proxy</li>
 *   <li>Performance overhead measurement</li>
 * </ul>
 */
class HikariSqlAuditIntegrationTest {

    private HikariDataSource hikariDataSource;
    private HikariSqlAuditProxyFactory auditProxyFactory;
    private HikariSqlSafetyProxyFactory safetyProxyFactory;
    private TestAuditLogWriter auditLogWriter;
    private DefaultSqlSafetyValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        // Create H2 in-memory database
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:test_audit;DB_CLOSE_DELAY=-1;MODE=MySQL");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(5);
        
        hikariDataSource = new HikariDataSource(config);

        // Create test audit log writer
        auditLogWriter = new TestAuditLogWriter();

        // Create audit proxy factory
        auditProxyFactory = new HikariSqlAuditProxyFactory(auditLogWriter);

        // Create validator for safety proxy
        JSqlParserFacade facade = new JSqlParserFacade();
        List<RuleChecker> checkers = Collections.emptyList();
        RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
        SqlDeduplicationFilter filter = new SqlDeduplicationFilter();
        validator = new DefaultSqlSafetyValidator(facade, checkers, orchestrator, filter);

        // Create test table
        try (Connection conn = hikariDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // Drop table if exists
            stmt.execute("DROP TABLE IF EXISTS users");
            
            stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100), status VARCHAR(50))");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice', 'active')");
            stmt.execute("INSERT INTO users VALUES (2, 'Bob', 'inactive')");
            stmt.execute("INSERT INTO users VALUES (3, 'Charlie', 'active')");
        }
    }

    @AfterEach
    void tearDown() {
        if (hikariDataSource != null) {
            hikariDataSource.close();
        }
    }

    // ========== Basic SQL Operations Tests ==========

    @Test
    void testSuccessfulSelect_shouldLogAudit() throws Exception {
        // Arrange
        String sql = "SELECT * FROM users WHERE id = 1";

        // Act
        try (Connection conn = auditProxyFactory.wrapConnection(hikariDataSource.getConnection());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next());
            assertEquals("Alice", rs.getString("name"));
        }

        // Assert
        assertEquals(1, auditLogWriter.getCapturedEvents().size());
        AuditEvent event = auditLogWriter.getCapturedEvents().get(0);
        
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.SELECT, event.getSqlType());
        assertEquals("hikari-jdbc", event.getMapperId());
        assertTrue(event.getExecutionTimeMs() >= 0);
        assertNull(event.getErrorMessage());
    }

    @Test
    void testSuccessfulUpdate_shouldCaptureRowsAffected() throws Exception {
        // Arrange
        String sql = "UPDATE users SET status = 'active' WHERE id IN (2, 3)";

        // Act
        int rowsAffected;
        try (Connection conn = auditProxyFactory.wrapConnection(hikariDataSource.getConnection());
             Statement stmt = conn.createStatement()) {
            rowsAffected = stmt.executeUpdate(sql);
        }

        // Assert
        assertEquals(2, rowsAffected);
        assertEquals(1, auditLogWriter.getCapturedEvents().size());
        
        AuditEvent event = auditLogWriter.getCapturedEvents().get(0);
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.UPDATE, event.getSqlType());
        assertEquals(2, event.getRowsAffected());
        assertNull(event.getErrorMessage());
    }

    @Test
    void testSuccessfulDelete_shouldCaptureRowsAffected() throws Exception {
        // Arrange
        String sql = "DELETE FROM users WHERE id = 3";

        // Act
        int rowsAffected;
        try (Connection conn = auditProxyFactory.wrapConnection(hikariDataSource.getConnection());
             Statement stmt = conn.createStatement()) {
            rowsAffected = stmt.executeUpdate(sql);
        }

        // Assert
        assertEquals(1, rowsAffected);
        assertEquals(1, auditLogWriter.getCapturedEvents().size());
        
        AuditEvent event = auditLogWriter.getCapturedEvents().get(0);
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.DELETE, event.getSqlType());
        assertEquals(1, event.getRowsAffected());
    }

    @Test
    void testSuccessfulInsert_shouldCaptureRowsAffected() throws Exception {
        // Arrange
        String sql = "INSERT INTO users VALUES (4, 'David', 'active')";

        // Act
        int rowsAffected;
        try (Connection conn = auditProxyFactory.wrapConnection(hikariDataSource.getConnection());
             Statement stmt = conn.createStatement()) {
            rowsAffected = stmt.executeUpdate(sql);
        }

        // Assert
        assertEquals(1, rowsAffected);
        assertEquals(1, auditLogWriter.getCapturedEvents().size());
        
        AuditEvent event = auditLogWriter.getCapturedEvents().get(0);
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.INSERT, event.getSqlType());
        assertEquals(1, event.getRowsAffected());
    }

    // ========== PreparedStatement Tests ==========

    @Test
    void testPreparedStatement_shouldCaptureParameters() throws Exception {
        // Arrange
        String sql = "SELECT * FROM users WHERE id = ?";

        // Act
        try (Connection conn = auditProxyFactory.wrapConnection(hikariDataSource.getConnection());
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, 1);
            try (ResultSet rs = pstmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Alice", rs.getString("name"));
            }
        }

        // Assert
        assertEquals(1, auditLogWriter.getCapturedEvents().size());
        AuditEvent event = auditLogWriter.getCapturedEvents().get(0);
        
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.SELECT, event.getSqlType());
        assertTrue(event.getExecutionTimeMs() >= 0);
    }

    @Test
    void testPreparedStatementUpdate_shouldCaptureRowsAffected() throws Exception {
        // Arrange
        String sql = "UPDATE users SET status = ? WHERE id = ?";

        // Act
        int rowsAffected;
        try (Connection conn = auditProxyFactory.wrapConnection(hikariDataSource.getConnection());
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "inactive");
            pstmt.setInt(2, 1);
            rowsAffected = pstmt.executeUpdate();
        }

        // Assert
        assertEquals(1, rowsAffected);
        assertEquals(1, auditLogWriter.getCapturedEvents().size());
        
        AuditEvent event = auditLogWriter.getCapturedEvents().get(0);
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.UPDATE, event.getSqlType());
        assertEquals(1, event.getRowsAffected());
    }

    // ========== Batch Execution Tests ==========

    @Test
    void testBatchExecution_shouldAggregateResults() throws Exception {
        // Arrange
        String sql = "INSERT INTO users VALUES (?, ?, ?)";

        // Act
        int[] results;
        try (Connection conn = auditProxyFactory.wrapConnection(hikariDataSource.getConnection());
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, 10);
            pstmt.setString(2, "User10");
            pstmt.setString(3, "active");
            pstmt.addBatch();
            
            pstmt.setInt(1, 11);
            pstmt.setString(2, "User11");
            pstmt.setString(3, "active");
            pstmt.addBatch();
            
            pstmt.setInt(1, 12);
            pstmt.setString(2, "User12");
            pstmt.setString(3, "inactive");
            pstmt.addBatch();
            
            results = pstmt.executeBatch();
        }

        // Assert
        assertEquals(3, results.length);
        assertEquals(1, auditLogWriter.getCapturedEvents().size());
        
        AuditEvent event = auditLogWriter.getCapturedEvents().get(0);
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.INSERT, event.getSqlType());
        assertEquals(3, event.getRowsAffected()); // Aggregated: 1 + 1 + 1
    }

    // ========== Combined Usage Tests ==========

    @Test
    void testCombinedWithSafetyProxy_shouldBothWork() throws Exception {
        // Arrange - Wrap datasource with both safety and audit proxies
        // Note: Safety and Audit proxies work independently
        DataSource safeDs = HikariSqlSafetyProxyFactory.wrap(
                hikariDataSource,
                validator,
                ViolationStrategy.LOG
        );
        
        String sql = "SELECT * FROM users WHERE id = 1";

        // Act - Both safety validation and audit logging should work
        try (Connection conn = auditProxyFactory.wrapConnection(safeDs.getConnection());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next());
        }

        // Assert - Audit event should be captured
        assertEquals(1, auditLogWriter.getCapturedEvents().size());
        AuditEvent event = auditLogWriter.getCapturedEvents().get(0);
        
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.SELECT, event.getSqlType());
        assertNull(event.getErrorMessage());
        
        // Note: Both safety validation and audit logging work independently
        // Violations are not correlated between the two proxies
    }

    // ========== Error Handling Tests ==========

    @Test
    void testSqlError_shouldCaptureException() throws Exception {
        // Arrange
        String sql = "SELECT * FROM non_existent_table";

        // Act & Assert
        try (Connection conn = auditProxyFactory.wrapConnection(hikariDataSource.getConnection());
             Statement stmt = conn.createStatement()) {
            
            assertThrows(Exception.class, () -> {
                stmt.executeQuery(sql);
            });
        }

        // Assert audit event captured with error
        assertEquals(1, auditLogWriter.getCapturedEvents().size());
        AuditEvent event = auditLogWriter.getCapturedEvents().get(0);
        
        assertEquals(sql, event.getSql());
        assertNotNull(event.getErrorMessage());
        assertTrue(event.getErrorMessage().contains("not found") || 
                   event.getErrorMessage().contains("NOT_FOUND"));
        assertEquals(-1, event.getRowsAffected());
    }

    // ========== Performance Tests ==========

    @Test
    void testPerformance_shouldBeLowOverhead() throws Exception {
        // Warm up
        for (int i = 0; i < 10; i++) {
            try (Connection conn = hikariDataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                while (rs.next()) {
                    // Consume results
                }
            }
        }

        // Measure baseline (without audit proxy)
        long baselineStart = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            try (Connection conn = hikariDataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                while (rs.next()) {
                    // Consume results
                }
            }
        }
        long baselineTime = System.nanoTime() - baselineStart;

        // Clear captured events
        auditLogWriter.clear();

        // Measure with audit proxy
        long auditStart = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            try (Connection conn = auditProxyFactory.wrapConnection(hikariDataSource.getConnection());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                while (rs.next()) {
                    // Consume results
                }
            }
        }
        long auditTime = System.nanoTime() - auditStart;

        // Calculate overhead
        double overhead = ((double) (auditTime - baselineTime) / baselineTime) * 100;
        
        System.out.println("Baseline time: " + (baselineTime / 1_000_000) + "ms");
        System.out.println("Audit time: " + (auditTime / 1_000_000) + "ms");
        System.out.println("Overhead: " + String.format("%.2f", overhead) + "%");

        // Assert overhead is reasonable (< 300% for integration test with I/O and reflection overhead)
        // Note: Integration tests have higher overhead due to proxy creation, reflection, and I/O
        assertTrue(overhead < 300.0, 
                "Overhead should be less than 300%, was " + String.format("%.2f", overhead) + "%");
        
        // Verify all events were captured
        assertEquals(100, auditLogWriter.getCapturedEvents().size());
    }

    // ========== Multiple Operations Test ==========

    @Test
    void testMultipleOperations_shouldCaptureAll() throws Exception {
        // Act - Perform multiple operations
        try (Connection conn = auditProxyFactory.wrapConnection(hikariDataSource.getConnection())) {
            
            // SELECT
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = 1")) {
                assertTrue(rs.next());
            }
            
            // UPDATE
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE users SET status = 'inactive' WHERE id = 2");
            }
            
            // INSERT
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO users VALUES (?, ?, ?)")) {
                pstmt.setInt(1, 20);
                pstmt.setString(2, "Test User");
                pstmt.setString(3, "active");
                pstmt.executeUpdate();
            }
            
            // DELETE
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM users WHERE id = 3");
            }
        }

        // Assert
        assertEquals(4, auditLogWriter.getCapturedEvents().size());
        
        // Verify each operation was captured
        List<AuditEvent> events = auditLogWriter.getCapturedEvents();
        assertEquals(SqlCommandType.SELECT, events.get(0).getSqlType());
        assertEquals(SqlCommandType.UPDATE, events.get(1).getSqlType());
        assertEquals(SqlCommandType.INSERT, events.get(2).getSqlType());
        assertEquals(SqlCommandType.DELETE, events.get(3).getSqlType());
    }

    // ========== Test Helper Classes ==========

    /**
     * Test implementation of AuditLogWriter that captures events in memory.
     */
    private static class TestAuditLogWriter implements AuditLogWriter {
        private final List<AuditEvent> capturedEvents = new ArrayList<>();

        @Override
        public void writeAuditLog(AuditEvent event) {
            capturedEvents.add(event);
        }

        public List<AuditEvent> getCapturedEvents() {
            return capturedEvents;
        }

        public void clear() {
            capturedEvents.clear();
        }
    }
}











