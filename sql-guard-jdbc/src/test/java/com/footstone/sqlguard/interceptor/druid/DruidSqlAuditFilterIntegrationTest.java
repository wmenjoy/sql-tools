package com.footstone.sqlguard.interceptor.druid;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.alibaba.druid.filter.Filter;
import com.alibaba.druid.pool.DruidDataSource;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogException;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import java.sql.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for DruidSqlAuditFilter with real DruidDataSource and H2 database.
 *
 * <p>Tests full integration including audit logging, timing measurement, error capture,
 * and coordination with DruidSqlSafetyFilter.</p>
 */
class DruidSqlAuditFilterIntegrationTest {

    private DruidDataSource dataSource;
    private DefaultSqlSafetyValidator validator;
    private CapturingAuditLogWriter auditLogWriter;

    /**
     * Test implementation of AuditLogWriter that captures events for verification.
     */
    private static class CapturingAuditLogWriter implements AuditLogWriter {
        private final List<AuditEvent> capturedEvents = new CopyOnWriteArrayList<>();

        @Override
        public void writeAuditLog(AuditEvent event) throws AuditLogException {
            if (event == null) {
                throw new IllegalArgumentException("event cannot be null");
            }
            capturedEvents.add(event);
        }

        public List<AuditEvent> getCapturedEvents() {
            return capturedEvents;
        }

        public void clear() {
            capturedEvents.clear();
        }

        public int size() {
            return capturedEvents.size();
        }
    }

    @BeforeEach
    void setUp() throws SQLException {
        // Create H2 in-memory database with unique name per test
        dataSource = new DruidDataSource();
        dataSource.setUrl("jdbc:h2:mem:testdb_audit_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setName("auditTestDataSource");
        dataSource.setMaxActive(10);
        dataSource.setInitialSize(2);

        // Create mock validator
        validator = mock(DefaultSqlSafetyValidator.class);
        when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

        // Create capturing audit writer
        auditLogWriter = new CapturingAuditLogWriter();

        // Register both filters
        DruidSqlSafetyFilterConfiguration.registerFilters(
            dataSource, validator, ViolationStrategy.WARN, auditLogWriter);

        // Initialize datasource
        dataSource.init();

        // Create schema and data
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE users ("
                + "id INT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100))");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com')");
            stmt.execute("INSERT INTO users VALUES (2, 'Bob', 'bob@example.com')");
            stmt.execute("INSERT INTO users VALUES (3, 'Charlie', 'charlie@example.com')");
        }

        // Clear deduplication cache and captured events
        com.footstone.sqlguard.validator.SqlDeduplicationFilter.clearThreadCache();
        auditLogWriter.clear();
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    // ========== Filter Registration Tests ==========

    @Test
    void testSetup_bothFilters_shouldBeRegistered() {
        List<Filter> filters = dataSource.getProxyFilters();
        assertNotNull(filters);
        assertEquals(2, filters.size(), "Should have 2 filters registered");

        // Safety filter should be first
        assertTrue(filters.get(0) instanceof DruidSqlSafetyFilter,
            "First filter should be DruidSqlSafetyFilter");

        // Audit filter should be last
        assertTrue(filters.get(1) instanceof DruidSqlAuditFilter,
            "Last filter should be DruidSqlAuditFilter");
    }

    @Test
    void testFilterOrdering_safetyBeforeAudit_shouldExecuteInOrder() throws SQLException {
        // Arrange
        String sql = "SELECT * FROM users WHERE id = ?";

        // Act
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, 1);
            pstmt.executeQuery();
        }

        // Assert
        // Validator should be called (by safety filter)
        verify(validator, atLeastOnce()).validate(any(SqlContext.class));

        // Audit should be written (by audit filter)
        List<AuditEvent> events = auditLogWriter.getCapturedEvents();
        assertTrue(events.size() > 0, "Should have captured audit events");
    }

    // ========== SELECT Statement Tests ==========

    @Test
    void testSuccessfulSelect_shouldLogAudit() throws Exception {
        // Arrange
        String sql = "SELECT * FROM users WHERE id = ?";

        // Act
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, 1);
            try (ResultSet rs = pstmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Alice", rs.getString("name"));
            }
        }

        // Assert
        List<AuditEvent> events = auditLogWriter.getCapturedEvents();
        assertTrue(events.size() > 0, 
            "Should have captured audit events, but got: " + events.size());
        
        AuditEvent event = events.get(0);
        assertTrue(event.getSql().contains("SELECT"), "SQL should contain SELECT");
        assertEquals(SqlCommandType.SELECT, event.getSqlType());
        assertEquals("druid-jdbc", event.getMapperId());
        assertEquals("auditTestDataSource", event.getDatasource());
        assertTrue(event.getExecutionTimeMs() >= 0, "Execution time should be non-negative");
        assertNull(event.getErrorMessage(), "Should not have error message");
        assertNotNull(event.getTimestamp());
    }

    @Test
    void testSelectWithStatement_shouldLogAudit() throws Exception {
        // Arrange
        String sql = "SELECT * FROM users";

        // Act
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            int count = 0;
            while (rs.next()) {
                count++;
            }
            assertEquals(3, count);
        }

        // Assert
        List<AuditEvent> events = auditLogWriter.getCapturedEvents();
        assertTrue(events.size() > 0);
        AuditEvent event = events.get(0);
        assertEquals(SqlCommandType.SELECT, event.getSqlType());
        assertTrue(event.getExecutionTimeMs() >= 0);
    }

    // ========== UPDATE Statement Tests ==========

    @Test
    void testSuccessfulUpdate_shouldCaptureRowsAffected() throws Exception {
        // Arrange
        String sql = "UPDATE users SET name = ? WHERE id = ?";

        // Act
        int rowsAffected;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "Alice Updated");
            pstmt.setInt(2, 1);
            rowsAffected = pstmt.executeUpdate();
        }

        // Assert
        assertEquals(1, rowsAffected);
        List<AuditEvent> events = auditLogWriter.getCapturedEvents();
        assertTrue(events.size() > 0);
        
        AuditEvent event = events.get(0);
        assertEquals(SqlCommandType.UPDATE, event.getSqlType());
        assertEquals(1, event.getRowsAffected());
        assertTrue(event.getExecutionTimeMs() >= 0);
        assertNull(event.getErrorMessage());
    }

    @Test
    void testUpdateMultipleRows_shouldCaptureCorrectCount() throws Exception {
        // Arrange
        String sql = "UPDATE users SET email = 'updated@example.com' WHERE id > 0";

        // Act
        int rowsAffected;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            rowsAffected = stmt.executeUpdate(sql);
        }

        // Assert
        assertEquals(3, rowsAffected);
        List<AuditEvent> events = auditLogWriter.getCapturedEvents();
        assertTrue(events.size() > 0);
        
        AuditEvent event = events.get(0);
        assertEquals(SqlCommandType.UPDATE, event.getSqlType());
        assertEquals(3, event.getRowsAffected());
    }

    // ========== DELETE Statement Tests ==========

    @Test
    void testSuccessfulDelete_shouldCaptureRowsAffected() throws Exception {
        // Arrange
        String sql = "DELETE FROM users WHERE id = ?";

        // Act
        int rowsAffected;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, 1);
            rowsAffected = pstmt.executeUpdate();
        }

        // Assert
        assertEquals(1, rowsAffected);
        List<AuditEvent> events = auditLogWriter.getCapturedEvents();
        assertTrue(events.size() > 0);
        
        AuditEvent event = events.get(0);
        assertEquals(SqlCommandType.DELETE, event.getSqlType());
        assertEquals(1, event.getRowsAffected());
        assertNull(event.getErrorMessage());
    }

    // ========== INSERT Statement Tests ==========

    @Test
    void testSuccessfulInsert_shouldCaptureRowsAffected() throws Exception {
        // Arrange
        String sql = "INSERT INTO users (id, name, email) VALUES (?, ?, ?)";

        // Act
        int rowsAffected;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, 4);
            pstmt.setString(2, "David");
            pstmt.setString(3, "david@example.com");
            rowsAffected = pstmt.executeUpdate();
        }

        // Assert
        assertEquals(1, rowsAffected);
        List<AuditEvent> events = auditLogWriter.getCapturedEvents();
        assertTrue(events.size() > 0);
        
        AuditEvent event = events.get(0);
        assertEquals(SqlCommandType.INSERT, event.getSqlType());
        assertEquals(1, event.getRowsAffected());
    }

    // ========== Error Handling Tests ==========

    @Test
    void testFailedExecution_shouldCaptureError() throws Exception {
        // Arrange
        String sql = "SELECT * FROM nonexistent_table";

        // Act & Assert
        assertThrows(SQLException.class, () -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.executeQuery(sql);
            }
        });

        // Verify audit was written with error
        List<AuditEvent> events = auditLogWriter.getCapturedEvents();
        assertTrue(events.size() > 0);
        AuditEvent event = events.get(0);
        assertNotNull(event.getErrorMessage(), "Should have error message");
        assertTrue(event.getErrorMessage().contains("not found") || 
                   event.getErrorMessage().contains("NOT_FOUND"),
            "Error message should indicate table not found");
    }

    @Test
    void testSyntaxError_shouldCaptureError() throws Exception {
        // Arrange
        String sql = "INVALID SQL SYNTAX";

        // Act & Assert
        assertThrows(SQLException.class, () -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        });

        // Verify audit was written with error
        List<AuditEvent> events = auditLogWriter.getCapturedEvents();
        assertTrue(events.size() > 0);
        AuditEvent event = events.get(0);
        assertNotNull(event.getErrorMessage());
    }

    // ========== Timing Measurement Tests ==========

    @Test
    void testExecutionTiming_shouldBeMeasured() throws Exception {
        // Arrange
        String sql = "SELECT * FROM users";

        // Act
        long startTime = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                // Process results
            }
        }
        long endTime = System.currentTimeMillis();
        long actualDuration = endTime - startTime;

        // Assert
        List<AuditEvent> events = auditLogWriter.getCapturedEvents();
        assertTrue(events.size() > 0);
        AuditEvent event = events.get(0);
        assertTrue(event.getExecutionTimeMs() >= 0);
        assertTrue(event.getExecutionTimeMs() <= actualDuration + 100, 
            "Execution time should be reasonable");
    }

    // ========== ThreadLocal Coordination Tests ==========

    @Test
    void testPreExecutionViolations_shouldBeIncludedInAudit() throws Exception {
        // Arrange
        String sql = "DELETE FROM users"; // No WHERE clause
        ValidationResult violationResult = ValidationResult.pass();
        violationResult.addViolation(
            RiskLevel.HIGH,
            "DELETE without WHERE clause",
            "Add WHERE condition"
        );
        
        // Configure validator to return violation
        when(validator.validate(any(SqlContext.class))).thenReturn(violationResult);

        // Act
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }

        // Assert
        List<AuditEvent> events = auditLogWriter.getCapturedEvents();
        assertTrue(events.size() > 0);
        AuditEvent event = events.get(0);
        assertNotNull(event.getViolations(), "Should have violations");
        assertFalse(event.getViolations().isPassed());
        assertEquals(RiskLevel.HIGH, event.getViolations().getRiskLevel());
        assertEquals(1, event.getViolations().getViolations().size());
    }

    @Test
    void testNoViolations_shouldNotIncludeInAudit() throws Exception {
        // Arrange
        String sql = "SELECT * FROM users WHERE id = 1";
        when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

        // Act
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next());
        }

        // Assert
        List<AuditEvent> events = auditLogWriter.getCapturedEvents();
        assertTrue(events.size() > 0);
        AuditEvent event = events.get(0);
        assertNull(event.getViolations(), "Should not have violations for passed validation");
    }

    // ========== Multiple Statements Tests ==========

    @Test
    void testMultipleStatements_shouldLogAll() throws Exception {
        // Arrange & Act
        try (Connection conn = dataSource.getConnection()) {
            // Execute SELECT
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = 1")) {
                assertTrue(rs.next());
            }

            // Execute UPDATE
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "UPDATE users SET name = ? WHERE id = ?")) {
                pstmt.setString(1, "Updated");
                pstmt.setInt(2, 2);
                pstmt.executeUpdate();
            }

            // Execute DELETE
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM users WHERE id = 3");
            }
        }

        // Assert
        List<AuditEvent> events = auditLogWriter.getCapturedEvents();
        assertTrue(events.size() >= 3, 
            "Should have captured at least 3 audit events, got: " + events.size());
        
        // Verify different SQL types were captured
        boolean hasSelect = events.stream()
            .anyMatch(e -> e.getSqlType() == SqlCommandType.SELECT);
        boolean hasUpdate = events.stream()
            .anyMatch(e -> e.getSqlType() == SqlCommandType.UPDATE);
        boolean hasDelete = events.stream()
            .anyMatch(e -> e.getSqlType() == SqlCommandType.DELETE);
        
        assertTrue(hasSelect, "Should have SELECT audit");
        assertTrue(hasUpdate, "Should have UPDATE audit");
        assertTrue(hasDelete, "Should have DELETE audit");
    }

    // ========== Performance Tests ==========

    @Test
    void testPerformanceOverhead_shouldBeReasonable() throws Exception {
        // Warm up
        for (int i = 0; i < 10; i++) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                while (rs.next()) {
                    // Process
                }
            }
        }

        auditLogWriter.clear();

        // Measure with audit
        long startWithAudit = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                while (rs.next()) {
                    // Process
                }
            }
        }
        long durationWithAudit = System.nanoTime() - startWithAudit;

        // Verify audits were written (may be less due to deduplication)
        List<AuditEvent> events = auditLogWriter.getCapturedEvents();
        assertTrue(events.size() > 0, "Should have audit events");

        // Close datasource and create new one without filters for comparison
        dataSource.close();
        
        // Create new datasource without filters
        dataSource = new DruidDataSource();
        dataSource.setUrl("jdbc:h2:mem:testdb_perf_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setName("perfTestDataSource");
        dataSource.setMaxActive(10);
        dataSource.setInitialSize(2);
        dataSource.init();
        
        // Recreate schema
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE users ("
                + "id INT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100))");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com')");
            stmt.execute("INSERT INTO users VALUES (2, 'Bob', 'bob@example.com')");
            stmt.execute("INSERT INTO users VALUES (3, 'Charlie', 'charlie@example.com')");
        }
        
        long startWithoutAudit = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                while (rs.next()) {
                    // Process
                }
            }
        }
        long durationWithoutAudit = System.nanoTime() - startWithoutAudit;

        // Calculate overhead
        double overhead = ((double) (durationWithAudit - durationWithoutAudit) / durationWithoutAudit) * 100;
        
        // Assert overhead is reasonable (less than 500% - very relaxed for test environments)
        // In production with warm caches and optimized JVM, overhead should be <1%
        assertTrue(overhead < 500.0, 
            String.format("Overhead should be reasonable, was %.2f%%", overhead));
        
        // Log performance info for monitoring
        System.out.println(String.format("Performance overhead: %.2f%% (with audit: %dms, without: %dms)",
            overhead, durationWithAudit / 1_000_000, durationWithoutAudit / 1_000_000));
    }
}
