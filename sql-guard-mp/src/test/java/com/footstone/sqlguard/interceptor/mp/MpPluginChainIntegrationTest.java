package com.footstone.sqlguard.interceptor.mp;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for MyBatis-Plus plugin chain ordering.
 * 
 * Tests the interaction between:
 * 1. PaginationInnerInterceptor (adds LIMIT clause)
 * 2. MpSqlSafetyInnerInterceptor (pre-execution validation)
 * 3. MpSqlAuditInnerInterceptor (post-execution audit)
 */
class MpPluginChainIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;
    private DataSource dataSource;
    private CapturingAuditLogWriter auditLogWriter;
    private DefaultSqlSafetyValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        // Create H2 in-memory database
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:test_mp_chain;MODE=MySQL;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(5);
        dataSource = new HikariDataSource(config);

        // Initialize schema
        try (SqlSession session = createBasicSqlSessionFactory().openSession()) {
            // Drop and recreate table to ensure clean state
            try {
                session.getConnection().createStatement().execute("DROP TABLE IF EXISTS mp_user");
            } catch (Exception e) {
                // Ignore if table doesn't exist
            }
            
            session.getConnection().createStatement().execute(
                "CREATE TABLE mp_user (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  name VARCHAR(100)," +
                "  age INT," +
                "  email VARCHAR(100)," +
                "  status VARCHAR(50)," +
                "  deleted INT DEFAULT 0" +
                ")"
            );
            
            // Insert test data
            session.getConnection().createStatement().execute(
                "INSERT INTO mp_user (name, age, email, status) VALUES " +
                "('Alice', 25, 'alice@example.com', 'active')," +
                "('Bob', 30, 'bob@example.com', 'active')," +
                "('Charlie', 35, 'charlie@example.com', 'inactive')," +
                "('Dave', 40, 'dave@example.com', 'active')," +
                "('Eve', 45, 'eve@example.com', 'inactive')"
            );
            session.commit();
        }

        // Create capturing audit writer (real implementation for better debugging)
        auditLogWriter = new CapturingAuditLogWriter();

        // Create mock validator
        validator = mock(DefaultSqlSafetyValidator.class);
        when(validator.validate(any())).thenReturn(ValidationResult.pass());

        // Create SqlSessionFactory with full plugin chain
        sqlSessionFactory = createFullPluginChainFactory();
    }
    
    /**
     * Simple capturing audit log writer for testing.
     */
    private static class CapturingAuditLogWriter implements AuditLogWriter {
        private final List<AuditEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        
        @Override
        public void writeAuditLog(AuditEvent event) {
            events.add(event);
            System.out.println(">>> Audit Event: " + event.getSqlType() + " - " + 
                             event.getSql().substring(0, Math.min(50, event.getSql().length())));
        }
        
        public List<AuditEvent> getEvents() {
            return events;
        }
        
        public void clear() {
            events.clear();
        }
        
        public int size() {
            return events.size();
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
        if (auditLogWriter != null) {
            auditLogWriter.clear();
        }
    }

    // ==================== Plugin Chain Ordering Tests ====================

    @Test
    void testFullPluginChain_shouldExecuteInCorrectOrder() throws Exception {
        // Arrange - IPage query
        auditLogWriter.clear(); // Clear before test
        
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            IPage<User> page = new Page<>(1, 2);

            // Act
            IPage<User> result = mapper.selectPage(page, null);

            // Assert
            assertNotNull(result);
            assertEquals(2, result.getRecords().size()); // Page size
            assertEquals(5, result.getTotal()); // Total records
            
            // Verify audit events were written
            // Note: MyBatis-Plus may or may not execute COUNT query depending on configuration
            System.out.println(">>> Total audit events: " + auditLogWriter.size());
            assertTrue(auditLogWriter.size() >= 1, 
                      "Expected at least 1 audit event, got: " + auditLogWriter.size());
            
            // Get the last event (should be the SELECT query)
            AuditEvent event = auditLogWriter.getEvents().get(auditLogWriter.size() - 1);
            
            // Verify pagination metadata (captured by audit interceptor)
            // Note: PaginationInnerInterceptor may use different SQL syntax depending on DB
            assertNotNull(event.getParams(), "Audit event should have params");
            assertNotNull(event.getParams().get("pagination.total"), "Should have pagination.total");
            assertEquals(5L, event.getParams().get("pagination.total"));
            assertEquals(1L, event.getParams().get("pagination.current"));
            assertEquals(2L, event.getParams().get("pagination.size"));
        }
    }

    @Test
    void testPluginChain_withQueryWrapper_shouldDetectAndAudit() throws Exception {
        // Arrange
        auditLogWriter.clear(); // Clear before test
        
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            QueryWrapper<User> wrapper = new QueryWrapper<>();
            wrapper.eq("status", "active");

            // Act
            List<User> users = mapper.selectList(wrapper);

            // Assert
            assertNotNull(users);
            assertEquals(3, users.size()); // 3 active users
            
            // Verify audit event was written
            System.out.println(">>> Total audit events: " + auditLogWriter.size());
            assertTrue(auditLogWriter.size() >= 1, 
                      "Expected at least 1 audit event, got: " + auditLogWriter.size());
            
            AuditEvent event = auditLogWriter.getEvents().get(auditLogWriter.size() - 1);
            assertEquals(SqlCommandType.SELECT, event.getSqlType());
            assertEquals(3, event.getRowsAffected());
            
            // Verify QueryWrapper flag
            assertNotNull(event.getParams());
            assertEquals(true, event.getParams().get("queryWrapper"));
        }
    }

    @Test
    void testPluginChain_paginationWithWrapper_shouldCaptureBoth() throws Exception {
        // Arrange
        auditLogWriter.clear(); // Clear before test
        
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            IPage<User> page = new Page<>(1, 2);
            QueryWrapper<User> wrapper = new QueryWrapper<>();
            wrapper.eq("status", "active");

            // Act
            IPage<User> result = mapper.selectPage(page, wrapper);

            // Assert
            assertNotNull(result);
            assertEquals(2, result.getRecords().size());
            assertEquals(3, result.getTotal()); // 3 active users total
            
            // Verify audit events were written
            System.out.println(">>> Total audit events: " + auditLogWriter.size());
            assertTrue(auditLogWriter.size() >= 1, 
                      "Expected at least 1 audit event, got: " + auditLogWriter.size());
            
            // Get the last event (should have both pagination and wrapper)
            AuditEvent event = auditLogWriter.getEvents().get(auditLogWriter.size() - 1);
            assertNotNull(event.getParams());
            
            // Verify pagination metadata
            assertEquals(3L, event.getParams().get("pagination.total"));
            assertEquals(1L, event.getParams().get("pagination.current"));
            assertEquals(2L, event.getParams().get("pagination.size"));
            
            // Verify QueryWrapper flag
            assertEquals(true, event.getParams().get("queryWrapper"));
        }
    }

    @Test
    void testPluginChain_withSafetyViolation_shouldCorrelate() throws Exception {
        // Arrange - Setup validator to return violation
        ValidationResult violationResult = ValidationResult.pass();
        violationResult.addViolation(RiskLevel.HIGH, "Missing WHERE clause", 
            "Add WHERE condition to avoid full table scan");
        
        reset(validator); // Reset to set new behavior
        when(validator.validate(any())).thenReturn(violationResult);
        
        // Create new factory with violation validator
        sqlSessionFactory = createFullPluginChainFactory();
        
        // Arrange - Query without WHERE (will trigger safety violation)
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);

            // Act - Execute query
            List<User> users = mapper.selectList(null);

            // Assert
            assertNotNull(users);
            assertEquals(5, users.size()); // All users
            
            // Verify audit event contains violation information
            System.out.println(">>> Total audit events: " + auditLogWriter.size());
            assertTrue(auditLogWriter.size() >= 1, 
                      "Expected at least 1 audit event, got: " + auditLogWriter.size());
            
            AuditEvent event = auditLogWriter.getEvents().get(auditLogWriter.size() - 1);
            assertEquals(SqlCommandType.SELECT, event.getSqlType());
            assertEquals(5, event.getRowsAffected());
            
            // Verify pre-execution validation violations are correlated
            assertNotNull(event.getViolations());
            assertFalse(event.getViolations().isPassed());
            assertEquals(RiskLevel.HIGH, event.getViolations().getRiskLevel());
        }
    }

    @Test
    void testPluginChain_updateWithWrapper_shouldAudit() throws Exception {
        // Arrange
        auditLogWriter.clear(); // Clear before test
        
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            
            // Act - Update using QueryWrapper
            User updateUser = new User();
            updateUser.setStatus("suspended");
            
            QueryWrapper<User> wrapper = new QueryWrapper<>();
            wrapper.eq("id", 1L);
            
            int rows = mapper.update(updateUser, wrapper);

            // Assert
            assertEquals(1, rows);
            
            // Verify audit event was written
            System.out.println(">>> Total audit events: " + auditLogWriter.size());
            assertTrue(auditLogWriter.size() >= 1, 
                      "Expected at least 1 audit event, got: " + auditLogWriter.size());
            
            AuditEvent event = auditLogWriter.getEvents().get(auditLogWriter.size() - 1);
            assertEquals(SqlCommandType.UPDATE, event.getSqlType());
            assertEquals(1, event.getRowsAffected());
            
            // Verify QueryWrapper flag
            assertNotNull(event.getParams());
            assertEquals(true, event.getParams().get("queryWrapper"));
        }
    }

    @Test
    void testPluginChain_multipleQueries_shouldAuditAll() throws Exception {
        // Arrange
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);

            // Act - Execute multiple queries
            auditLogWriter.clear(); // Clear before test
            
            mapper.selectList(null); // Query 1
            mapper.selectPage(new Page<>(1, 2), null); // Query 2
            QueryWrapper<User> wrapper = new QueryWrapper<>();
            wrapper.eq("status", "active");
            mapper.selectList(wrapper); // Query 3

            // Assert - All 3 queries should be audited
            System.out.println(">>> Total audit events: " + auditLogWriter.size());
            assertTrue(auditLogWriter.size() >= 3, 
                      "Expected at least 3 audit events, got: " + auditLogWriter.size());
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a basic SqlSessionFactory without plugins (for schema initialization).
     */
    private SqlSessionFactory createBasicSqlSessionFactory() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(UserMapper.class);
        
        Environment environment = new Environment("test", 
            new JdbcTransactionFactory(), dataSource);
        configuration.setEnvironment(environment);

        return new SqlSessionFactoryBuilder().build(configuration);
    }

    /**
     * Creates SqlSessionFactory with full plugin chain:
     * 1. MybatisPlusInterceptor (with PaginationInnerInterceptor and MpSqlSafetyInnerInterceptor)
     * 2. MpSqlAuditInnerInterceptor (standard MyBatis interceptor)
     */
    private SqlSessionFactory createFullPluginChainFactory() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(UserMapper.class);
        
        Environment environment = new Environment("test", 
            new JdbcTransactionFactory(), dataSource);
        configuration.setEnvironment(environment);

        // Step 1: Create MybatisPlusInterceptor (for InnerInterceptors)
        MybatisPlusInterceptor mpInterceptor = new MybatisPlusInterceptor();
        
        // Add PaginationInnerInterceptor (order 1)
        mpInterceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        
        // Add MpSqlSafetyInnerInterceptor (order 2)
        mpInterceptor.addInnerInterceptor(new MpSqlSafetyInnerInterceptor(
            validator, ViolationStrategy.WARN)); // WARN to allow execution
        
        configuration.addInterceptor(mpInterceptor);

        // Step 2: Add MpSqlAuditInnerInterceptor (standard MyBatis interceptor)
        configuration.addInterceptor(new MpSqlAuditInnerInterceptor(auditLogWriter));

        return new SqlSessionFactoryBuilder().build(configuration);
    }
}








