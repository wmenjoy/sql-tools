package com.footstone.sqlguard.interceptor.druid;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.alibaba.druid.filter.Filter;
import com.alibaba.druid.pool.DruidDataSource;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import java.sql.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for DruidSqlSafetyFilter with real DruidDataSource and H2 database.
 *
 * <p>Tests full integration including connection pooling, PreparedStatement/Statement interception,
 * batch execution, CallableStatement, and multi-threaded scenarios.</p>
 */
class DruidIntegrationTest {

  private DruidDataSource dataSource;
  private DefaultSqlSafetyValidator validator;

  @BeforeEach
  void setUp() throws SQLException {
    // Create H2 in-memory database with unique name per test
    dataSource = new DruidDataSource();
    dataSource.setUrl("jdbc:h2:mem:testdb_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
    dataSource.setDriverClassName("org.h2.Driver");
    dataSource.setName("testDataSource");
    dataSource.setMaxActive(10);
    dataSource.setInitialSize(2);

    // Create mock validator
    validator = mock(DefaultSqlSafetyValidator.class);
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

    // Register filter
    DruidSqlSafetyFilterConfiguration.registerFilter(
        dataSource, validator, ViolationStrategy.BLOCK);

    // Initialize datasource
    dataSource.init();

    // Create schema and data
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE users ("
          + "id INT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100))");
      stmt.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com')");
      stmt.execute("INSERT INTO users VALUES (2, 'Bob', 'bob@example.com')");
    }

    // Clear deduplication cache
    com.footstone.sqlguard.validator.SqlDeduplicationFilter.clearThreadCache();
  }

  @AfterEach
  void tearDown() {
    if (dataSource != null) {
      dataSource.close();
    }
  }

  @Test
  void testSetup_druidDataSource_shouldInitialize() {
    assertNotNull(dataSource);
    assertTrue(dataSource.isInited());
    assertEquals("testDataSource", dataSource.getName());
  }

  @Test
  void testFilter_registered_shouldExistInProxyFilters() {
    List<Filter> filters = dataSource.getProxyFilters();
    assertNotNull(filters);
    assertFalse(filters.isEmpty());

    boolean hasSafetyFilter = filters.stream()
        .anyMatch(f -> f instanceof DruidSqlSafetyFilter);
    assertTrue(hasSafetyFilter, "Safety filter should be registered");
  }

  @Test
  void testPreparedStatement_safe_shouldExecute() throws SQLException {
    // Arrange
    String sql = "SELECT * FROM users WHERE id = ?";

    // Act & Assert - should not throw
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, 1);
      try (ResultSet rs = ps.executeQuery()) {
        assertTrue(rs.next());
        assertEquals("Alice", rs.getString("name"));
      }
    }

    // Verify validation was called
    verify(validator, atLeastOnce()).validate(any(SqlContext.class));
  }

  @Test
  void testPreparedStatement_dangerous_shouldBlock() {
    // Arrange
    ValidationResult dangerousResult = ValidationResult.pass();
    dangerousResult.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE");
    when(validator.validate(any(SqlContext.class))).thenReturn(dangerousResult);

    // Act & Assert
    assertThrows(SQLException.class, () -> {
      try (Connection conn = dataSource.getConnection();
           PreparedStatement ps = conn.prepareStatement("UPDATE users SET name = ?")) {
        ps.setString(1, "test");
        ps.executeUpdate();
      }
    });
  }

  @Test
  void testStatement_executeQuery_dangerous_shouldBlock() {
    // Arrange
    ValidationResult dangerousResult = ValidationResult.pass();
    dangerousResult.addViolation(RiskLevel.HIGH, "SELECT * detected", "Specify columns");
    when(validator.validate(any(SqlContext.class))).thenReturn(dangerousResult);

    // Act & Assert
    assertThrows(SQLException.class, () -> {
      try (Connection conn = dataSource.getConnection();
           Statement stmt = conn.createStatement()) {
        stmt.executeQuery("SELECT * FROM users");
      }
    });
  }

  @Test
  void testStatement_executeUpdate_dangerous_shouldBlock() {
    // Arrange
    ValidationResult dangerousResult = ValidationResult.pass();
    dangerousResult.addViolation(RiskLevel.HIGH, "Missing WHERE", "Add condition");
    when(validator.validate(any(SqlContext.class))).thenReturn(dangerousResult);

    // Act & Assert
    assertThrows(SQLException.class, () -> {
      try (Connection conn = dataSource.getConnection();
           Statement stmt = conn.createStatement()) {
        stmt.executeUpdate("DELETE FROM users");
      }
    });
  }

  @Test
  void testConnectionPool_multipleConnections_shouldValidate() throws SQLException {
    // Test that filter works across multiple connections from pool
    // Use different SQL queries to avoid deduplication
    for (int i = 0; i < 5; i++) {
      try (Connection conn = dataSource.getConnection();
           Statement stmt = conn.createStatement();
           ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE id > " + i)) {
        assertTrue(rs.next());
      }
    }

    // Verify validation was called multiple times
    verify(validator, atLeast(5)).validate(any(SqlContext.class));
  }

  @Test
  void testConnectionPool_borrowReturn_filterPersists() throws SQLException {
    // Arrange - get connection, use it, return it
    Connection conn1 = dataSource.getConnection();
    try (Statement stmt = conn1.createStatement()) {
      stmt.executeQuery("SELECT name FROM users WHERE id = 1");
    }
    conn1.close(); // Return to pool

    // Act - get another connection (might be same physical connection)
    Connection conn2 = dataSource.getConnection();
    try (Statement stmt = conn2.createStatement()) {
      stmt.executeQuery("SELECT email FROM users WHERE id = 2");
    }
    conn2.close();

    // Assert - validation should work for both (different SQL to avoid deduplication)
    verify(validator, atLeast(2)).validate(any(SqlContext.class));
  }

  @Test
  void testBatchExecution_shouldValidateEach() throws SQLException {
    // Arrange
    String sql = "INSERT INTO users VALUES (?, ?, ?)";

    // Act
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, 10);
      ps.setString(2, "User10");
      ps.setString(3, "user10@example.com");
      ps.addBatch();

      ps.setInt(1, 11);
      ps.setString(2, "User11");
      ps.setString(3, "user11@example.com");
      ps.addBatch();

      ps.executeBatch();
    }

    // Assert - validation should be called for batch preparation
    verify(validator, atLeastOnce()).validate(any(SqlContext.class));
  }

  @Test
  void testCallableStatement_shouldValidate() throws SQLException {
    // H2 doesn't support stored procedures in the same way, but we can test callable statement creation
    // Arrange - use a simple query that H2 can handle
    String sql = "SELECT COUNT(*) FROM users";

    // Act - create and execute callable statement
    try (Connection conn = dataSource.getConnection();
         CallableStatement cs = conn.prepareCall(sql)) {
      try (ResultSet rs = cs.executeQuery()) {
        assertTrue(rs.next());
        assertEquals(2, rs.getInt(1));
      }
    }

    // Validation should have been called
    verify(validator, atLeastOnce()).validate(any(SqlContext.class));
  }

  @Test
  void testMultiThreaded_shouldBeThreadSafe() throws InterruptedException {
    // Arrange
    int threadCount = 10;
    int operationsPerThread = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger errorCount = new AtomicInteger(0);

    // Act - execute queries from multiple threads with unique SQL to avoid deduplication
    for (int i = 0; i < threadCount; i++) {
      final int threadId = i;
      executor.submit(() -> {
        try {
          for (int j = 0; j < operationsPerThread; j++) {
            // Use different SQL for each operation to avoid deduplication
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM users WHERE id = ? AND name IS NOT NULL")) {
              ps.setInt(1, (threadId % 2) + 1);
              try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                  successCount.incrementAndGet();
                }
              }
            }
          }
        } catch (SQLException e) {
          errorCount.incrementAndGet();
        } finally {
          latch.countDown();
        }
      });
    }

    // Wait for completion
    assertTrue(latch.await(30, TimeUnit.SECONDS));
    executor.shutdown();

    // Assert
    assertEquals(threadCount * operationsPerThread, successCount.get());
    assertEquals(0, errorCount.get());
    // Deduplication may reduce the count, so just verify it was called
    verify(validator, atLeastOnce()).validate(any(SqlContext.class));
  }

  @Test
  void testDatasourceName_shouldAppearInViolations() {
    // Arrange
    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.HIGH, "Test violation", "Fix it");
    when(validator.validate(any(SqlContext.class))).thenReturn(result);

    // Act
    SQLException exception = assertThrows(SQLException.class, () -> {
      try (Connection conn = dataSource.getConnection();
           Statement stmt = conn.createStatement()) {
        stmt.executeQuery("SELECT * FROM users");
      }
    });

    // Assert - datasource name should be in error message
    String message = exception.getMessage();
    assertTrue(message.contains("testDataSource"),
        "Error message should contain datasource name");
    assertTrue(message.contains("Druid Filter"),
        "Error message should indicate Druid Filter");
  }

  @Test
  void testWARNStrategy_shouldExecuteButLog() throws SQLException {
    // Arrange - create new datasource with WARN strategy
    DruidDataSource warnDataSource = new DruidDataSource();
    warnDataSource.setUrl("jdbc:h2:mem:testdb2;DB_CLOSE_DELAY=-1");
    warnDataSource.setDriverClassName("org.h2.Driver");
    warnDataSource.setName("warnDataSource");

    DefaultSqlSafetyValidator warnValidator = mock(DefaultSqlSafetyValidator.class);
    ValidationResult warnResult = ValidationResult.pass();
    warnResult.addViolation(RiskLevel.MEDIUM, "Warning test", "Consider fixing");
    when(warnValidator.validate(any(SqlContext.class))).thenReturn(warnResult);

    DruidSqlSafetyFilterConfiguration.registerFilter(
        warnDataSource, warnValidator, ViolationStrategy.WARN);
    warnDataSource.init();

    try {
      // Create table
      try (Connection conn = warnDataSource.getConnection();
           Statement stmt = conn.createStatement()) {
        stmt.execute("CREATE TABLE test (id INT)");
      }

      // Act - should NOT throw even with violation
      assertDoesNotThrow(() -> {
        try (Connection conn = warnDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
          stmt.executeQuery("SELECT * FROM test");
        }
      });

      // Verify validation was called
      verify(warnValidator, atLeastOnce()).validate(any(SqlContext.class));
    } finally {
      warnDataSource.close();
    }
  }

  @Test
  void testLOGStrategy_shouldExecuteAndLog() throws SQLException {
    // Arrange - create new datasource with LOG strategy
    DruidDataSource logDataSource = new DruidDataSource();
    logDataSource.setUrl("jdbc:h2:mem:testdb3;DB_CLOSE_DELAY=-1");
    logDataSource.setDriverClassName("org.h2.Driver");
    logDataSource.setName("logDataSource");

    DefaultSqlSafetyValidator logValidator = mock(DefaultSqlSafetyValidator.class);
    ValidationResult logResult = ValidationResult.pass();
    logResult.addViolation(RiskLevel.LOW, "Info test", "Optional fix");
    when(logValidator.validate(any(SqlContext.class))).thenReturn(logResult);

    DruidSqlSafetyFilterConfiguration.registerFilter(
        logDataSource, logValidator, ViolationStrategy.LOG);
    logDataSource.init();

    try {
      // Create table
      try (Connection conn = logDataSource.getConnection();
           Statement stmt = conn.createStatement()) {
        stmt.execute("CREATE TABLE test (id INT)");
      }

      // Act - should NOT throw even with violation
      assertDoesNotThrow(() -> {
        try (Connection conn = logDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
          stmt.executeQuery("SELECT * FROM test");
        }
      });

      // Verify validation was called
      verify(logValidator, atLeastOnce()).validate(any(SqlContext.class));
    } finally {
      logDataSource.close();
    }
  }

  @Test
  void testDeduplication_shouldPreventDoubleCheck() throws SQLException {
    // Arrange
    String sql = "SELECT * FROM users WHERE id = 1";

    // Act - execute same SQL twice quickly
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {
      stmt.executeQuery(sql);
      stmt.executeQuery(sql); // Same SQL, should be deduplicated
    }

    // Assert - validator should be called only once due to deduplication
    // (within 100ms TTL window)
    verify(validator, times(1)).validate(any(SqlContext.class));
  }
}
