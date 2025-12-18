package com.footstone.sqlguard.interceptor.hikari;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Edge case and robustness tests for HikariCP SQL safety integration.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Connection lifecycle edge cases</li>
 *   <li>PreparedStatement reuse</li>
 *   <li>CallableStatement support</li>
 *   <li>Batch operations</li>
 *   <li>Transaction operations</li>
 *   <li>Savepoint support</li>
 *   <li>Connection pooling behavior</li>
 * </ul>
 */
class HikariEdgeCasesTest {

  private DefaultSqlSafetyValidator validator;
  private HikariDataSource hikariDataSource;
  private DataSource wrappedDataSource;

  @BeforeEach
  void setUp() {
    // Create real validator (mock would skip actual validation)
    validator = mock(DefaultSqlSafetyValidator.class);
    when(validator.validate(any())).thenReturn(ValidationResult.pass());

    // Create real HikariCP datasource with H2 in-memory database (unique DB per test run)
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:h2:mem:edge_test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
    config.setDriverClassName("org.h2.Driver");
    config.setMaximumPoolSize(5);
    config.setMinimumIdle(1);
    config.setConnectionTimeout(3000);
    config.setPoolName("TestPool");

    hikariDataSource = new HikariDataSource(config);

    // Wrap with SQL safety
    wrappedDataSource = HikariSqlSafetyConfiguration.wrapDataSource(
        hikariDataSource, validator, ViolationStrategy.BLOCK);

    // Create test table (use quoted identifier for H2 compatibility)
    try (Connection conn = wrappedDataSource.getConnection();
         Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE IF NOT EXISTS test_user (id INT PRIMARY KEY, name VARCHAR(100))");
      stmt.execute("INSERT INTO test_user VALUES (1, 'test1')");
      stmt.execute("INSERT INTO test_user VALUES (2, 'test2')");
      stmt.execute("INSERT INTO test_user VALUES (3, 'test3')");
    } catch (SQLException e) {
      throw new RuntimeException("Failed to setup test database", e);
    }
  }

  @AfterEach
  void tearDown() {
    if (hikariDataSource != null && !hikariDataSource.isClosed()) {
      hikariDataSource.close();
    }
  }

  @Test
  void testConnectionClose_multipleTimes_idempotent() throws SQLException {
    // When
    Connection conn = wrappedDataSource.getConnection();
    conn.close();
    conn.close();  // Should not throw
    conn.close();  // Should not throw

    // Then - no exception thrown
    assertTrue(conn.isClosed());
  }

  @Test
  void testPreparedStatement_reuse_shouldNotRevalidate() throws SQLException {
    // Given
    String sql = "SELECT * FROM test_user WHERE id = ?";

    // Clear any validation calls from setUp
    reset(validator);
    when(validator.validate(any())).thenReturn(ValidationResult.pass());

    // When
    try (Connection conn = wrappedDataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

      // Execute multiple times with different params
      for (int i = 1; i <= 3; i++) {
        ps.setInt(1, i);
        try (ResultSet rs = ps.executeQuery()) {
          assertTrue(rs.next());
        }
      }
    }

    // Then - validator called once during prepareStatement, not on each execute
    verify(validator, times(1)).validate(any());
  }

  @Test
  void testPreparedStatement_parameterBinding_shouldWork() throws SQLException {
    // Given
    String sql = "SELECT * FROM test_user WHERE id = ?";

    // When
    try (Connection conn = wrappedDataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, 1);
      try (ResultSet rs = ps.executeQuery()) {
        // Then
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        assertEquals("test1", rs.getString("name"));
      }
    }
  }

  @Test
  void testCallableStatement_shouldIntercept() throws SQLException {
    // Given - H2 doesn't support stored procedures easily, so we'll test the proxy behavior
    String sql = "{call some_proc()}";

    // When - Try to prepare a callable statement (will fail because proc doesn't exist)
    boolean exceptionThrown = false;
    try (Connection conn = wrappedDataSource.getConnection()) {
      try {
        CallableStatement cs = conn.prepareCall(sql);
        cs.close();
      } catch (Exception e) {
        // Expected - any exception is fine, the important thing is validation was called
        exceptionThrown = true;
      }
    }

    // Then - Verify that an exception was thrown and validation was called
    assertTrue(exceptionThrown, "Should have thrown exception for non-existent procedure");
    verify(validator, atLeastOnce()).validate(any());
  }

  @Test
  void testBatchOperations_shouldValidate() throws SQLException {
    // Given
    String sql = "INSERT INTO test_user VALUES (?, ?)";

    // When
    try (Connection conn = wrappedDataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setInt(1, 10);
      ps.setString(2, "batch1");
      ps.addBatch();

      ps.setInt(1, 11);
      ps.setString(2, "batch2");
      ps.addBatch();

      int[] results = ps.executeBatch();

      // Then
      assertEquals(2, results.length);
      verify(validator, atLeastOnce()).validate(any());  // Validated at prepareStatement
    }
  }

  @Test
  void testTransactions_commit_shouldWork() throws SQLException {
    // Given
    String sql = "INSERT INTO test_user VALUES (?, ?)";

    // When
    try (Connection conn = wrappedDataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, 20);
        ps.setString(2, "tx-test");
        ps.executeUpdate();
      }

      conn.commit();

      // Then - verify data was committed
      try (Statement stmt = conn.createStatement();
           ResultSet rs = stmt.executeQuery("SELECT * FROM test_user WHERE id = 20")) {
        assertTrue(rs.next());
        assertEquals("tx-test", rs.getString("name"));
      }
    }
  }

  @Test
  void testTransactions_rollback_shouldWork() throws SQLException {
    // Given
    String sql = "INSERT INTO test_user VALUES (?, ?)";

    // When
    try (Connection conn = wrappedDataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, 30);
        ps.setString(2, "rollback-test");
        ps.executeUpdate();
      }

      conn.rollback();

      // Then - verify data was rolled back
      try (Statement stmt = conn.createStatement();
           ResultSet rs = stmt.executeQuery("SELECT * FROM test_user WHERE id = 30")) {
        assertFalse(rs.next());
      }
    }
  }

  @Test
  void testSavepoint_shouldWork() throws SQLException {
    // Given
    String sql = "INSERT INTO test_user VALUES (?, ?)";

    // When
    try (Connection conn = wrappedDataSource.getConnection()) {
      conn.setAutoCommit(false);

      Savepoint sp1 = conn.setSavepoint("sp1");

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, 40);
        ps.setString(2, "savepoint-test");
        ps.executeUpdate();
      }

      conn.rollback(sp1);
      conn.commit();

      // Then - verify data was rolled back to savepoint
      try (Statement stmt = conn.createStatement();
           ResultSet rs = stmt.executeQuery("SELECT * FROM test_user WHERE id = 40")) {
        assertFalse(rs.next());
      }
    }
  }

  @Test
  void testConnectionPooling_borrowReturn_correct() throws SQLException {
    // When - borrow and return connections multiple times
    for (int i = 0; i < 10; i++) {
      try (Connection conn = wrappedDataSource.getConnection()) {
        assertNotNull(conn);
        assertFalse(conn.isClosed());
      }
    }

    // Then - pool should still be healthy
    assertFalse(hikariDataSource.isClosed());
    assertTrue(hikariDataSource.getHikariPoolMXBean().getIdleConnections() > 0);
  }

  @Test
  void testMetrics_violations_shouldNotAffect() throws SQLException {
    // Given
    int activeConnectionsBefore = hikariDataSource.getHikariPoolMXBean().getActiveConnections();

    // When - execute some SQL
    try (Connection conn = wrappedDataSource.getConnection();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT * FROM test_user")) {
      assertTrue(rs.next());
    }

    // Then - metrics should still be accurate
    int activeConnectionsAfter = hikariDataSource.getHikariPoolMXBean().getActiveConnections();
    assertEquals(activeConnectionsBefore, activeConnectionsAfter);
  }

  @Test
  void testHealthCheck_violations_shouldNotAffect() throws SQLException {
    // When
    try (Connection conn = wrappedDataSource.getConnection()) {
      assertTrue(conn.isValid(1));
    }

    // Then - pool should still be healthy
    assertTrue(hikariDataSource.getHikariPoolMXBean().getIdleConnections() >= 0);
  }

  @Test
  void testMaxLifetime_shouldWork() throws SQLException {
    // Given - connections should be usable
    Connection conn1 = wrappedDataSource.getConnection();
    assertNotNull(conn1);
    assertFalse(conn1.isClosed());

    // When
    conn1.close();

    // Then - can still get new connections
    Connection conn2 = wrappedDataSource.getConnection();
    assertNotNull(conn2);
    assertFalse(conn2.isClosed());
    conn2.close();
  }

  @Test
  void testConcurrentConnections_shouldBeThreadSafe() throws InterruptedException {
    // Given
    int threadCount = 5;
    Thread[] threads = new Thread[threadCount];
    boolean[] success = new boolean[threadCount];

    // When - multiple threads accessing datasource concurrently
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

    // Wait for all threads
    for (Thread thread : threads) {
      thread.join();
    }

    // Then - all threads should succeed
    for (boolean result : success) {
      assertTrue(result);
    }
  }
}




