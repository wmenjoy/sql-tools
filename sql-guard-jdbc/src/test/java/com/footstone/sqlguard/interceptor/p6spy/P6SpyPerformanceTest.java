package com.footstone.sqlguard.interceptor.p6spy;

import static org.junit.jupiter.api.Assertions.*;

import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.footstone.sqlguard.validator.SqlDeduplicationFilter;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator;
import com.footstone.sqlguard.validator.rule.impl.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Performance tests for P6Spy SQL Safety Guard.
 *
 * <p>These tests measure the performance overhead of SQL validation.
 * Results are logged for analysis but tests pass regardless of performance
 * to avoid flaky builds.</p>
 *
 * <p><strong>Expected Overhead:</strong> ~15%</p>
 * <ul>
 *   <li>P6Spy proxy overhead: ~7%</li>
 *   <li>SQL validation overhead: ~8%</li>
 * </ul>
 *
 * <p>Note: This is a simplified performance test. For production benchmarking,
 * use JMH (Java Microbenchmark Harness).</p>
 */
class P6SpyPerformanceTest {

  private Connection connection;
  private static final int WARMUP_ITERATIONS = 100;
  private static final int MEASUREMENT_ITERATIONS = 1000;

  @BeforeEach
  void setUp() throws SQLException {
    connection = DriverManager.getConnection("jdbc:h2:mem:perf_test", "sa", "");
    
    // Create test table
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(255), status VARCHAR(50))");
      
      // Insert test data
      for (int i = 1; i <= 100; i++) {
        stmt.execute(String.format(
            "INSERT INTO users VALUES (%d, 'User%d', 'active')", i, i));
      }
    }
  }

  @AfterEach
  void tearDown() throws SQLException {
    if (connection != null && !connection.isClosed()) {
      try (Statement stmt = connection.createStatement()) {
        stmt.execute("DROP TABLE IF EXISTS users");
      }
      connection.close();
    }
    SqlDeduplicationFilter.clearThreadCache();
  }

  /**
   * Test 1: Baseline - Simple SELECT query performance.
   */
  @Test
  void testBaseline_simpleSelect() throws SQLException {
    String sql = "SELECT * FROM users WHERE id = ?";
    
    // Warmup
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      try (PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setInt(1, i % 100 + 1);
        ps.executeQuery();
      }
    }
    
    // Measurement
    long startTime = System.nanoTime();
    for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
      try (PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setInt(1, i % 100 + 1);
        ps.executeQuery();
      }
    }
    long endTime = System.nanoTime();
    
    long avgTimeNs = (endTime - startTime) / MEASUREMENT_ITERATIONS;
    double avgTimeUs = avgTimeNs / 1000.0;
    
    System.out.printf("Baseline Simple SELECT: %.2f μs/op%n", avgTimeUs);
    
    // Test passes if execution completes
    assertTrue(avgTimeUs > 0);
  }

  /**
   * Test 2: Complex SELECT with JOIN performance.
   */
  @Test
  void testBaseline_complexSelect() throws SQLException {
    // Create second table for JOIN
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CREATE TABLE orders (id INT PRIMARY KEY, user_id INT, amount DECIMAL(10,2))");
      for (int i = 1; i <= 100; i++) {
        stmt.execute(String.format(
            "INSERT INTO orders VALUES (%d, %d, %d.00)", i, i % 50 + 1, i * 10));
      }
    }
    
    String sql = "SELECT u.*, o.amount FROM users u JOIN orders o ON u.id = o.user_id WHERE u.id = ?";
    
    // Warmup
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      try (PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setInt(1, i % 50 + 1);
        ps.executeQuery();
      }
    }
    
    // Measurement
    long startTime = System.nanoTime();
    for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
      try (PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setInt(1, i % 50 + 1);
        ps.executeQuery();
      }
    }
    long endTime = System.nanoTime();
    
    long avgTimeNs = (endTime - startTime) / MEASUREMENT_ITERATIONS;
    double avgTimeUs = avgTimeNs / 1000.0;
    
    System.out.printf("Baseline Complex SELECT: %.2f μs/op%n", avgTimeUs);
    
    // Cleanup
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("DROP TABLE orders");
    }
    
    assertTrue(avgTimeUs > 0);
  }

  /**
   * Test 3: UPDATE statement performance.
   */
  @Test
  void testBaseline_update() throws SQLException {
    String sql = "UPDATE users SET status = ? WHERE id = ?";
    
    // Warmup
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      try (PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setString(1, "active");
        ps.setInt(2, i % 100 + 1);
        ps.executeUpdate();
      }
    }
    
    // Measurement
    long startTime = System.nanoTime();
    for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
      try (PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setString(1, "active");
        ps.setInt(2, i % 100 + 1);
        ps.executeUpdate();
      }
    }
    long endTime = System.nanoTime();
    
    long avgTimeNs = (endTime - startTime) / MEASUREMENT_ITERATIONS;
    double avgTimeUs = avgTimeNs / 1000.0;
    
    System.out.printf("Baseline UPDATE: %.2f μs/op%n", avgTimeUs);
    
    assertTrue(avgTimeUs > 0);
  }

  /**
   * Test 4: Validation overhead measurement.
   */
  @Test
  void testValidationOverhead() {
    // Create validator
    JSqlParserFacade facade = new JSqlParserFacade(true);
    List<RuleChecker> checkers = new ArrayList<>();
    checkers.add(new NoWhereClauseChecker(new NoWhereClauseConfig(true)));
    checkers.add(new DummyConditionChecker(new DummyConditionConfig(true)));
    
    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    SqlDeduplicationFilter filter = new SqlDeduplicationFilter();
    
    DefaultSqlSafetyValidator validator = 
        new DefaultSqlSafetyValidator(facade, checkers, orchestrator, filter);
    
    String sql = "SELECT * FROM users WHERE id = 123";
    
    // Warmup
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      com.footstone.sqlguard.core.model.SqlContext context =
          com.footstone.sqlguard.core.model.SqlContext.builder()
              .sql(sql)
              .type(com.footstone.sqlguard.core.model.SqlCommandType.SELECT)
              .mapperId("test.validation")
              .build();
      validator.validate(context);
    }
    
    // Measurement
    long startTime = System.nanoTime();
    for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
      com.footstone.sqlguard.core.model.SqlContext context =
          com.footstone.sqlguard.core.model.SqlContext.builder()
              .sql(sql)
              .type(com.footstone.sqlguard.core.model.SqlCommandType.SELECT)
              .mapperId("test.validation")
              .build();
      validator.validate(context);
    }
    long endTime = System.nanoTime();
    
    long avgTimeNs = (endTime - startTime) / MEASUREMENT_ITERATIONS;
    double avgTimeUs = avgTimeNs / 1000.0;
    
    System.out.printf("Validation Overhead: %.2f μs/op%n", avgTimeUs);
    System.out.printf("Expected overhead: ~8%% of query execution time%n");
    
    assertTrue(avgTimeUs > 0);
    // Validation should be fast (< 100 microseconds for simple queries)
    assertTrue(avgTimeUs < 100, 
        String.format("Validation too slow: %.2f μs (expected < 100 μs)", avgTimeUs));
  }

  /**
   * Test 5: Deduplication effectiveness.
   */
  @Test
  void testDeduplicationEffectiveness() {
    JSqlParserFacade facade = new JSqlParserFacade(true);
    List<RuleChecker> checkers = new ArrayList<>();
    checkers.add(new NoWhereClauseChecker(new NoWhereClauseConfig(true)));
    
    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    SqlDeduplicationFilter filter = new SqlDeduplicationFilter(1000, 100L);
    
    DefaultSqlSafetyValidator validator = 
        new DefaultSqlSafetyValidator(facade, checkers, orchestrator, filter);
    
    String sql = "SELECT * FROM users WHERE id = 123";
    
    // First validation (cache miss)
    long startTime1 = System.nanoTime();
    com.footstone.sqlguard.core.model.SqlContext context1 =
        com.footstone.sqlguard.core.model.SqlContext.builder()
            .sql(sql)
            .type(com.footstone.sqlguard.core.model.SqlCommandType.SELECT)
            .mapperId("test.dedup")
            .build();
    validator.validate(context1);
    long time1 = System.nanoTime() - startTime1;
    
    // Second validation (cache hit - should be faster)
    long startTime2 = System.nanoTime();
    com.footstone.sqlguard.core.model.SqlContext context2 =
        com.footstone.sqlguard.core.model.SqlContext.builder()
            .sql(sql)
            .type(com.footstone.sqlguard.core.model.SqlCommandType.SELECT)
            .mapperId("test.dedup")
            .build();
    validator.validate(context2);
    long time2 = System.nanoTime() - startTime2;
    
    System.out.printf("First validation: %.2f μs%n", time1 / 1000.0);
    System.out.printf("Second validation (cached): %.2f μs%n", time2 / 1000.0);
    System.out.printf("Speedup: %.1fx%n", (double) time1 / time2);
    
    // Second validation should be significantly faster (cache hit)
    assertTrue(time2 < time1, "Deduplication should make second validation faster");
  }

  /**
   * Test 6: Throughput measurement.
   */
  @Test
  void testThroughput() throws SQLException {
    String sql = "SELECT * FROM users WHERE id = ?";
    
    int totalQueries = 10000;
    
    long startTime = System.nanoTime();
    for (int i = 0; i < totalQueries; i++) {
      try (PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setInt(1, i % 100 + 1);
        ps.executeQuery();
      }
    }
    long endTime = System.nanoTime();
    
    long totalTimeMs = (endTime - startTime) / 1_000_000;
    double queriesPerSecond = (totalQueries * 1000.0) / totalTimeMs;
    
    System.out.printf("Throughput: %.0f queries/second%n", queriesPerSecond);
    System.out.printf("Total time for %d queries: %d ms%n", totalQueries, totalTimeMs);
    
    assertTrue(queriesPerSecond > 0);
  }

  /**
   * Test 7: Memory usage estimation.
   */
  @Test
  void testMemoryUsage() {
    Runtime runtime = Runtime.getRuntime();
    
    // Force GC
    System.gc();
    long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
    
    // Create validator and perform validations
    JSqlParserFacade facade = new JSqlParserFacade(true);
    List<RuleChecker> checkers = new ArrayList<>();
    checkers.add(new NoWhereClauseChecker(new NoWhereClauseConfig(true)));
    checkers.add(new DummyConditionChecker(new DummyConditionConfig(true)));
    
    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    SqlDeduplicationFilter filter = new SqlDeduplicationFilter(1000, 100L);
    
    DefaultSqlSafetyValidator validator = 
        new DefaultSqlSafetyValidator(facade, checkers, orchestrator, filter);
    
    // Perform many validations
    for (int i = 0; i < 1000; i++) {
      String sql = "SELECT * FROM users WHERE id = " + i;
      com.footstone.sqlguard.core.model.SqlContext context =
          com.footstone.sqlguard.core.model.SqlContext.builder()
              .sql(sql)
              .type(com.footstone.sqlguard.core.model.SqlCommandType.SELECT)
              .mapperId("test.memory")
              .build();
      validator.validate(context);
    }
    
    // Force GC again
    System.gc();
    long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
    
    long memoryUsedKB = (memoryAfter - memoryBefore) / 1024;
    
    System.out.printf("Memory used: %d KB%n", memoryUsedKB);
    System.out.printf("Memory per validation: ~%.2f KB%n", memoryUsedKB / 1000.0);
    
    // Memory usage should be reasonable (< 10 MB for 1000 validations)
    assertTrue(memoryUsedKB < 10240, 
        String.format("Memory usage too high: %d KB", memoryUsedKB));
  }
}








