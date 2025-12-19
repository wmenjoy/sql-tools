package com.footstone.sqlguard.interceptor.jdbc.druid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.alibaba.druid.pool.DruidDataSource;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Integration Tests for sql-guard-jdbc-druid module.
 *
 * <p>Verifies end-to-end functionality with real Druid DataSource and H2 database,
 * testing actual SQL interception through the filter chain.</p>
 *
 * @since 2.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Druid Integration Tests")
class DruidIntegrationTest {

    private DruidDataSource dataSource;
    
    @Mock
    private DefaultSqlSafetyValidator validator;

    @BeforeEach
    void setUp() throws SQLException {
        // Create H2 in-memory database with Druid
        dataSource = new DruidDataSource();
        dataSource.setUrl("jdbc:h2:mem:druid_test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setInitialSize(1);
        dataSource.setMinIdle(1);
        dataSource.setMaxActive(5);
        dataSource.setMaxWait(3000);
        dataSource.setName("testDruidDataSource");
        dataSource.setTestWhileIdle(false); // Disable to avoid validation query warning
        
        // Configure mock validator to return pass by default
        lenient().when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());
        
        // Initialize datasource
        dataSource.init();
        
        // Create test tables
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100))");
            stmt.execute("CREATE TABLE IF NOT EXISTS orders (id INT PRIMARY KEY, user_id INT, amount DECIMAL(10,2))");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com')");
            stmt.execute("INSERT INTO users VALUES (2, 'Bob', 'bob@example.com')");
            stmt.execute("INSERT INTO orders VALUES (1, 1, 100.00)");
        }
        
        // Clear thread-local state
        com.footstone.sqlguard.validator.SqlDeduplicationFilter.clearThreadCache();
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // ==================== Test 1: End-to-End Query Interception ====================
    
    @Test
    @DisplayName("1. End-to-end: Filter chain should process queries correctly")
    @Timeout(10)
    void testDruid_endToEnd_interceptsQueries() throws SQLException {
        // Given: Register safety filter with WARN strategy
        DruidInterceptorConfig config = createTestConfig(ViolationStrategy.WARN);
        DruidJdbcInterceptor interceptor = new DruidJdbcInterceptor(config, validator);
        DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(interceptor);
        
        // Add filter to datasource
        dataSource.getProxyFilters().add(0, filter);
        
        // When: Execute queries through the filter chain
        try (Connection conn = dataSource.getConnection()) {
            // SELECT query
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = 1")) {
                assertTrue(rs.next(), "Should return result for valid SELECT");
                assertThat(rs.getString("name")).isEqualTo("Alice");
            }
            
            // PreparedStatement query
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE email = ?")) {
                pstmt.setString(1, "bob@example.com");
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next(), "Should return result for prepared SELECT");
                    assertThat(rs.getString("name")).isEqualTo("Bob");
                }
            }
        }
        
        // Then: Filter should be properly registered
        assertThat(dataSource.getProxyFilters()).contains(filter);
    }

    // ==================== Test 2: H2 Database Validation ====================
    
    @Test
    @DisplayName("2. H2 validation: Filter should integrate with H2 in-memory database")
    @Timeout(10)
    void testDruid_withH2_validates() throws SQLException {
        // Given: Register safety filter with LOG strategy (non-blocking)
        DruidInterceptorConfig config = createTestConfig(ViolationStrategy.LOG);
        DruidJdbcInterceptor interceptor = new DruidJdbcInterceptor(config, validator);
        DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(interceptor);
        dataSource.getProxyFilters().add(0, filter);
        
        // When: Execute queries through filter chain
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT id, name FROM users WHERE id = ?")) {
            pstmt.setInt(1, 1);
            try (ResultSet rs = pstmt.executeQuery()) {
                // Then: Query should execute successfully through filter
                assertTrue(rs.next(), "Valid SELECT should execute through filter");
                assertEquals(1, rs.getInt("id"));
                assertEquals("Alice", rs.getString("name"));
            }
        }
        
        // And: Filter should be registered
        assertThat(dataSource.getProxyFilters())
            .as("Filter should be registered with datasource")
            .contains(filter);
    }

    // ==================== Test 3: Multiple DataSources Handling ====================
    
    @Test
    @DisplayName("3. Multiple datasources: Filter should handle multiple Druid pools")
    @Timeout(15)
    void testDruid_multipleDataSources_handles() throws SQLException {
        // Given: Create second datasource
        DruidDataSource dataSource2 = new DruidDataSource();
        dataSource2.setUrl("jdbc:h2:mem:druid_test2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource2.setUsername("sa");
        dataSource2.setPassword("");
        dataSource2.setDriverClassName("org.h2.Driver");
        dataSource2.setInitialSize(1);
        dataSource2.setMinIdle(1);
        dataSource2.setMaxActive(5);
        dataSource2.setName("secondDataSource");
        dataSource2.setTestWhileIdle(false);
        
        try {
            dataSource2.init();
            
            // Create tables in second datasource
            try (Connection conn = dataSource2.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE products (id INT PRIMARY KEY, name VARCHAR(100), price DECIMAL(10,2))");
                stmt.execute("INSERT INTO products VALUES (1, 'Widget', 29.99)");
            }
            
            // Create shared filter config and interceptor
            DruidInterceptorConfig config = createTestConfig(ViolationStrategy.WARN);
            DruidJdbcInterceptor interceptor = new DruidJdbcInterceptor(config, validator);
            DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(interceptor);
            
            // Register filter with both datasources
            dataSource.getProxyFilters().add(0, filter);
            dataSource2.getProxyFilters().add(0, filter);
            
            // When: Execute queries on both datasources
            try (Connection conn1 = dataSource.getConnection();
                 Statement stmt1 = conn1.createStatement();
                 ResultSet rs1 = stmt1.executeQuery("SELECT * FROM users WHERE id = 1")) {
                assertTrue(rs1.next(), "Should query first datasource");
                assertEquals("Alice", rs1.getString("name"));
            }
            
            try (Connection conn2 = dataSource2.getConnection();
                 Statement stmt2 = conn2.createStatement();
                 ResultSet rs2 = stmt2.executeQuery("SELECT * FROM products WHERE id = 1")) {
                assertTrue(rs2.next(), "Should query second datasource");
                assertThat(rs2.getString("name")).isEqualTo("Widget");
            }
            
            // Then: Both datasources should have filter registered
            assertThat(dataSource.getProxyFilters()).contains(filter);
            assertThat(dataSource2.getProxyFilters()).contains(filter);
        } finally {
            if (!dataSource2.isClosed()) {
                dataSource2.close();
            }
        }
    }

    // ==================== Test 4: Spring Boot Auto-Configuration ====================
    
    @Test
    @DisplayName("4. Spring Boot: Auto-configuration classes should be available")
    void testDruid_springBoot_autoConfigures() {
        // Then: DruidSqlSafetyFilterConfiguration should be available
        boolean configClassAvailable = isClassAvailable(
            "com.footstone.sqlguard.interceptor.jdbc.druid.DruidSqlSafetyFilterConfiguration");
        
        assertTrue(configClassAvailable,
            "DruidSqlSafetyFilterConfiguration should be available for Spring Boot integration");
        
        // And: DruidInterceptorConfig interface should be available
        boolean configInterfaceAvailable = isClassAvailable(
            "com.footstone.sqlguard.interceptor.jdbc.druid.DruidInterceptorConfig");
        
        assertTrue(configInterfaceAvailable,
            "DruidInterceptorConfig should be available for configuration properties binding");
    }

    // ==================== Test 5: Performance Baseline ====================
    
    @Test
    @DisplayName("5. Performance: Filter should not significantly impact query latency")
    @Timeout(30)
    void testDruid_performance_meetsBaseline() throws SQLException {
        // Given: Register safety filter
        DruidInterceptorConfig config = createTestConfig(ViolationStrategy.LOG);
        DruidJdbcInterceptor interceptor = new DruidJdbcInterceptor(config, validator);
        DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(interceptor);
        dataSource.getProxyFilters().add(0, filter);
        
        // When: Execute multiple queries and measure time
        int iterations = 100;
        long startTime = System.nanoTime();
        
        for (int i = 0; i < iterations; i++) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                pstmt.setInt(1, (i % 2) + 1);
                try (ResultSet rs = pstmt.executeQuery()) {
                    rs.next(); // Consume result
                }
            }
        }
        
        long endTime = System.nanoTime();
        long totalTimeMs = (endTime - startTime) / 1_000_000;
        double avgTimeMs = (double) totalTimeMs / iterations;
        
        // Then: Average query time should be reasonable (< 50ms with filter)
        assertThat(avgTimeMs)
            .as("Average query time should be under 50ms")
            .isLessThan(50.0);
        
        System.out.printf("Performance: %d iterations, total=%dms, avg=%.2fms%n", 
            iterations, totalTimeMs, avgTimeMs);
    }

    // ==================== Helper Methods ====================
    
    /**
     * Creates a test configuration with specified strategy.
     */
    private DruidInterceptorConfig createTestConfig(ViolationStrategy strategy) {
        return new DruidInterceptorConfig() {
            @Override
            public boolean isEnabled() {
                return true;
            }
            
            @Override
            public ViolationStrategy getStrategy() {
                return strategy;
            }
            
            @Override
            public boolean isAuditEnabled() {
                return false;
            }
            
            @Override
            public List<String> getExcludePatterns() {
                return Collections.emptyList();
            }
            
            @Override
            public int getFilterPosition() {
                return 0;
            }
            
            @Override
            public boolean isConnectionProxyEnabled() {
                return true;
            }
        };
    }
    
    /**
     * Checks if a class is available on the classpath.
     */
    private boolean isClassAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
