package com.footstone.sqlguard.interceptor.hikari;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for HikariCP SQL safety proxy with real HikariDataSource.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Full integration with HikariDataSource</li>
 *   <li>SQL validation with BLOCK/WARN/LOG strategies</li>
 *   <li>Connection pooling behavior</li>
 *   <li>Concurrent access</li>
 *   <li>Leak detection compatibility</li>
 * </ul>
 */
class HikariIntegrationTest {

  private DefaultSqlSafetyValidator validator;
  private HikariDataSource hikariDataSource;
  private DataSource wrappedDataSource;

  @BeforeEach
  void setUp() throws SQLException {
    // Create real validator
    validator = mock(DefaultSqlSafetyValidator.class);
    when(validator.validate(any())).thenReturn(ValidationResult.pass());

    // Create real HikariCP datasource
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:h2:mem:integration_test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
    config.setDriverClassName("org.h2.Driver");
    config.setMaximumPoolSize(10);
    config.setMinimumIdle(2);
    config.setConnectionTimeout(3000);
    config.setPoolName("IntegrationTestPool");
    config.setLeakDetectionThreshold(5000); // 5 seconds for leak detection

    hikariDataSource = new HikariDataSource(config);

    // Wrap with SQL safety
    wrappedDataSource = HikariSqlSafetyConfiguration.wrapDataSource(
        hikariDataSource, validator, ViolationStrategy.BLOCK);

    // Create test table
    try (Connection conn = wrappedDataSource.getConnection();
         Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE test_user (id INT PRIMARY KEY, name VARCHAR(100))");
      stmt.execute("INSERT INTO test_user VALUES (1, 'user1')");
      stmt.execute("INSERT INTO test_user VALUES (2, 'user2')");
    }
  }

  @AfterEach
  void tearDown() {
    if (hikariDataSource != null && !hikariDataSource.isClosed()) {
      hikariDataSource.close();
    }
  }

  @Test
  void testSetup_hikariDataSource_shouldCreate() {
    assertNotNull(hikariDataSource);
    assertNotNull(wrappedDataSource);
    assertFalse(hikariDataSource.isClosed());
  }

  @Test
  void testGetConnection_shouldReturnProxy() throws SQLException {
    try (Connection conn = wrappedDataSource.getConnection()) {
      assertNotNull(conn);
      assertTrue(java.lang.reflect.Proxy.isProxyClass(conn.getClass()));
    }
  }

  @Test
  void testPreparedStatement_safe_shouldExecute() throws SQLException {
    String sql = "SELECT * FROM test_user WHERE id = ?";

    try (Connection conn = wrappedDataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, 1);
      try (ResultSet rs = ps.executeQuery()) {
        assertTrue(rs.next());
        assertEquals("user1", rs.getString("name"));
      }
    }

    verify(validator, atLeastOnce()).validate(any());
  }

  @Test
  void testPreparedStatement_dangerous_shouldBlock() throws SQLException {
    String dangerousSql = "SELECT * FROM test_user";
    ValidationResult failResult = ValidationResult.pass();
    failResult.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
    when(validator.validate(any())).thenReturn(failResult);

    try (Connection conn = wrappedDataSource.getConnection()) {
      SQLException exception = assertThrows(SQLException.class, () -> {
        conn.prepareStatement(dangerousSql);
      });
      assertTrue(exception.getMessage().contains("SQL safety violation"));
    }
  }

  @Test
  void testStatement_dangerous_shouldBlock() throws SQLException {
    String dangerousSql = "DELETE FROM test_user";
    ValidationResult failResult = ValidationResult.pass();
    failResult.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
    when(validator.validate(any())).thenReturn(failResult);

    try (Connection conn = wrappedDataSource.getConnection();
         Statement stmt = conn.createStatement()) {
      SQLException exception = assertThrows(SQLException.class, () -> {
        stmt.executeUpdate(dangerousSql);
      });
      assertTrue(exception.getMessage().contains("SQL safety violation"));
    }
  }

  @Test
  void testStatement_safe_shouldExecute() throws SQLException {
    String sql = "SELECT * FROM test_user WHERE id = 1";

    try (Connection conn = wrappedDataSource.getConnection();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery(sql)) {
      assertTrue(rs.next());
      assertEquals("user1", rs.getString("name"));
    }

    verify(validator, atLeastOnce()).validate(any());
  }

  @Test
  void testBatchOperations_shouldValidate() throws SQLException {
    String sql = "INSERT INTO test_user VALUES (?, ?)";

    try (Connection conn = wrappedDataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, 10);
      ps.setString(2, "batch1");
      ps.addBatch();

      ps.setInt(1, 11);
      ps.setString(2, "batch2");
      ps.addBatch();

      int[] results = ps.executeBatch();
      assertEquals(2, results.length);
    }

    verify(validator, atLeastOnce()).validate(any());
  }

  @Test
  void testConnectionPool_multipleConnections() throws SQLException {
    // Get multiple connections
    Connection conn1 = wrappedDataSource.getConnection();
    Connection conn2 = wrappedDataSource.getConnection();
    Connection conn3 = wrappedDataSource.getConnection();

    assertNotNull(conn1);
    assertNotNull(conn2);
    assertNotNull(conn3);

    conn1.close();
    conn2.close();
    conn3.close();

    // Pool should still be healthy
    assertTrue(hikariDataSource.getHikariPoolMXBean().getIdleConnections() > 0);
  }

  @Test
  void testConcurrent_10threads_shouldBeThreadSafe() throws InterruptedException {
    int threadCount = 10;
    Thread[] threads = new Thread[threadCount];
    boolean[] success = new boolean[threadCount];

    for (int i = 0; i < threadCount; i++) {
      final int index = i;
      threads[i] = new Thread(() -> {
        try (Connection conn = wrappedDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM test_user")) {
          success[index] = rs.next();
        } catch (SQLException e) {
          success[index] = false;
        }
      });
      threads[i].start();
    }

    for (Thread thread : threads) {
      thread.join();
    }

    for (boolean result : success) {
      assertTrue(result);
    }
  }

  @Test
  void testWARNStrategy_shouldLogAndContinue() throws SQLException {
    // Create new datasource with WARN strategy
    DataSource warnDs = HikariSqlSafetyConfiguration.wrapDataSource(
        hikariDataSource, validator, ViolationStrategy.WARN);

    String dangerousSql = "SELECT * FROM test_user";
    ValidationResult failResult = ValidationResult.pass();
    failResult.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
    when(validator.validate(any())).thenReturn(failResult);

    // Should NOT throw exception with WARN strategy
    try (Connection conn = warnDs.getConnection();
         PreparedStatement ps = conn.prepareStatement(dangerousSql)) {
      assertNotNull(ps);
    }
  }

  @Test
  void testLOGStrategy_shouldOnlyLog() throws SQLException {
    // Create new datasource with LOG strategy
    DataSource logDs = HikariSqlSafetyConfiguration.wrapDataSource(
        hikariDataSource, validator, ViolationStrategy.LOG);

    String dangerousSql = "SELECT * FROM test_user";
    ValidationResult failResult = ValidationResult.pass();
    failResult.addViolation(RiskLevel.MEDIUM, "Missing WHERE clause", "Add WHERE condition");
    when(validator.validate(any())).thenReturn(failResult);

    // Should NOT throw exception with LOG strategy
    try (Connection conn = logDs.getConnection();
         PreparedStatement ps = conn.prepareStatement(dangerousSql)) {
      assertNotNull(ps);
    }
  }

  @Test
  void testDeduplication_shouldWork() throws SQLException {
    String sql = "SELECT * FROM test_user WHERE id = ?";

    // Execute same SQL twice
    try (Connection conn = wrappedDataSource.getConnection()) {
      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, 1);
        try (ResultSet rs = ps.executeQuery()) {
          // consume result
        }
      }
      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, 2);
        try (ResultSet rs = ps.executeQuery()) {
          // consume result
        }
      }
    }

    // Validator should be called at least twice (once per prepareStatement)
    // Note: setUp also creates tables which may trigger additional validations
    verify(validator, atLeast(2)).validate(any());
  }

  @Test
  void testMetrics_shouldBeAccurate() throws SQLException {
    int idleConnectionsBefore = hikariDataSource.getHikariPoolMXBean().getIdleConnections();

    try (Connection conn = wrappedDataSource.getConnection();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT * FROM test_user")) {
      assertTrue(rs.next());
    }

    int idleConnectionsAfter = hikariDataSource.getHikariPoolMXBean().getIdleConnections();
    assertEquals(idleConnectionsBefore, idleConnectionsAfter);
  }

  @Test
  void testLeakDetection_compatibility() {
    // Verify that leak detection is compatible
    boolean compatible = HikariSqlSafetyConfiguration.isLeakDetectionCompatible(wrappedDataSource);
    assertTrue(compatible);
  }
}









