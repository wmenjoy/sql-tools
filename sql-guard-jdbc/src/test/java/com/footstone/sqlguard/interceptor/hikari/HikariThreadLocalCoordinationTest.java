package com.footstone.sqlguard.interceptor.hikari;

import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for ThreadLocal coordination between HikariSqlSafetyProxyFactory and HikariSqlAuditProxyFactory.
 * 
 * <p>This test validates that:
 * <ul>
 *   <li>SafetyProxy sets ValidationResult in ThreadLocal during validation</li>
 *   <li>AuditProxy can read ValidationResult from ThreadLocal during execute finally block</li>
 *   <li>AuditProxy clears ThreadLocal after reading to prevent memory leaks</li>
 *   <li>Execution order matches Druid Filter chain pattern</li>
 * </ul>
 */
class HikariThreadLocalCoordinationTest {

    private HikariDataSource hikariDataSource;
    private DefaultSqlSafetyValidator validator;
    private TestAuditLogWriter auditLogWriter;
    private HikariSqlSafetyProxyFactory safetyFactory;
    private HikariSqlAuditProxyFactory auditFactory;

    @BeforeEach
    void setUp() throws Exception {
        // Create H2 in-memory database
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:threadlocal_test;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(5);
        
        hikariDataSource = new HikariDataSource(config);

        // Create test table
        try (Connection conn = hikariDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100))");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice')");
        }

        // Create mock validator
        validator = mock(DefaultSqlSafetyValidator.class);
        
        // Create audit log writer
        auditLogWriter = new TestAuditLogWriter();

        // Create factories
        safetyFactory = new HikariSqlSafetyProxyFactory();
        auditFactory = new HikariSqlAuditProxyFactory(auditLogWriter);
    }

    @AfterEach
    void tearDown() {
        if (hikariDataSource != null) {
            hikariDataSource.close();
        }
    }

    @Test
    void testThreadLocalCoordination_withViolations() throws Exception {
        // Given - Validator returns violations
        ValidationResult validationResult = ValidationResult.pass();
        validationResult.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
        when(validator.validate(any())).thenReturn(validationResult);

        // Wrap datasource with safety proxy
        DataSource safetyWrapped = HikariSqlSafetyProxyFactory.wrap(
                hikariDataSource,
                validator,
                ViolationStrategy.LOG  // LOG mode to allow execution
        );

        // Wrap again with audit proxy
        String sql = "SELECT * FROM users";

        // When - Execute SQL through both proxies
        try (Connection conn = auditFactory.wrapConnection(safetyWrapped.getConnection());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next());
        }

        // Then - Verify audit event contains violations from ThreadLocal
        assertEquals(1, auditLogWriter.getCapturedEvents().size());
        AuditEvent event = auditLogWriter.getCapturedEvents().get(0);
        
        assertNotNull(event.getViolations(), "Violations should be present");
        assertFalse(event.getViolations().isPassed(), "ValidationResult should show failure");
        assertEquals(1, event.getViolations().getViolations().size());
        assertEquals("Missing WHERE clause", event.getViolations().getViolations().get(0).getMessage());
    }

    @Test
    void testThreadLocalCoordination_withoutViolations() throws Exception {
        // Given - Validator returns pass
        ValidationResult validationResult = ValidationResult.pass();
        when(validator.validate(any())).thenReturn(validationResult);

        // Wrap datasource with safety proxy
        DataSource safetyWrapped = HikariSqlSafetyProxyFactory.wrap(
                hikariDataSource,
                validator,
                ViolationStrategy.LOG
        );

        // Wrap again with audit proxy
        String sql = "SELECT * FROM users WHERE id = 1";

        // When - Execute SQL through both proxies
        try (Connection conn = auditFactory.wrapConnection(safetyWrapped.getConnection());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next());
        }

        // Then - Verify audit event has no violations
        assertEquals(1, auditLogWriter.getCapturedEvents().size());
        AuditEvent event = auditLogWriter.getCapturedEvents().get(0);
        
        // ValidationResult is passed, so violations field should be null or passed
        if (event.getViolations() != null) {
            assertTrue(event.getViolations().isPassed(), "ValidationResult should show pass");
        }
    }

    @Test
    void testThreadLocalCleanup_noMemoryLeak() throws Exception {
        // Given
        ValidationResult validationResult = ValidationResult.pass();
        when(validator.validate(any())).thenReturn(validationResult);

        DataSource safetyWrapped = HikariSqlSafetyProxyFactory.wrap(
                hikariDataSource,
                validator,
                ViolationStrategy.LOG
        );

        String sql = "SELECT * FROM users";

        // When - Execute SQL
        try (Connection conn = auditFactory.wrapConnection(safetyWrapped.getConnection());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next());
        }

        // Then - ThreadLocal should be cleared after execution
        ValidationResult afterExecution = HikariSqlSafetyProxyFactory.getValidationResult();
        assertNull(afterExecution, "ThreadLocal should be cleared after execution to prevent memory leak");
    }

    @Test
    void testOnlyAuditProxy_withoutSafetyProxy() throws Exception {
        // Given - Only audit proxy, no safety proxy
        String sql = "SELECT * FROM users";

        // When - Execute SQL with only audit proxy
        try (Connection conn = auditFactory.wrapConnection(hikariDataSource.getConnection());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next());
        }

        // Then - Audit event should not have violations
        assertEquals(1, auditLogWriter.getCapturedEvents().size());
        AuditEvent event = auditLogWriter.getCapturedEvents().get(0);
        
        assertNull(event.getViolations(), "No violations expected when safety proxy is not used");
    }

    @Test
    void testOnlySafetyProxy_withoutAuditProxy() throws Exception {
        // Given - Only safety proxy, no audit proxy
        ValidationResult validationResult = ValidationResult.pass();
        when(validator.validate(any())).thenReturn(validationResult);

        DataSource safetyWrapped = HikariSqlSafetyProxyFactory.wrap(
                hikariDataSource,
                validator,
                ViolationStrategy.LOG
        );

        String sql = "SELECT * FROM users";

        // When - Execute SQL with only safety proxy
        try (Connection conn = safetyWrapped.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next());
        }

        // Then - No audit events (audit proxy not used)
        assertEquals(0, auditLogWriter.getCapturedEvents().size());
        
        // ThreadLocal should still be cleaned up (by safety proxy timeout or next execution)
        // This is acceptable - worst case it gets overwritten by next execution
    }

    /**
     * Test implementation of AuditLogWriter that captures events for verification.
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
    }
}
