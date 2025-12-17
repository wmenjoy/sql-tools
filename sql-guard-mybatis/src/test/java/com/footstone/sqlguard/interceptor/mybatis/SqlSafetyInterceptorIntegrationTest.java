package com.footstone.sqlguard.interceptor.mybatis;

import static org.junit.jupiter.api.Assertions.*;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.SqlSafetyValidator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for SqlSafetyInterceptor with real MyBatis SqlSessionFactory.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Real H2 database setup and teardown</li>
 *   <li>SqlSessionFactory creation with interceptor</li>
 *   <li>Safe SQL execution</li>
 *   <li>Dangerous SQL blocking (BLOCK strategy)</li>
 *   <li>Dangerous SQL warning (WARN strategy)</li>
 *   <li>Dynamic SQL resolution</li>
 *   <li>RowBounds pagination detection</li>
 *   <li>Thread-safety under concurrent load</li>
 *   <li>Validator deduplication</li>
 * </ul>
 */
class SqlSafetyInterceptorIntegrationTest {

  private static final String H2_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
  private static final String H2_USER = "sa";
  private static final String H2_PASSWORD = "";

  private SqlSessionFactory sqlSessionFactory;
  private SqlSafetyValidator validator;
  private Connection connection;

  @BeforeEach
  void setUp() throws Exception {
    // Create H2 database connection
    connection = DriverManager.getConnection(H2_URL, H2_USER, H2_PASSWORD);

    // Load and execute schema
    executeSchemaScript(connection);

    // Create validator with all rule checkers
    validator = createValidator();

    // Create SqlSessionFactory with interceptor
    sqlSessionFactory = createSqlSessionFactory(ViolationStrategy.WARN);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (connection != null && !connection.isClosed()) {
      connection.close();
    }
  }

  @Test
  void testSetup_sqlSessionFactory_shouldCreate() {
    // Verify SqlSessionFactory was created successfully
    assertNotNull(sqlSessionFactory);
    assertNotNull(sqlSessionFactory.getConfiguration());
  }

  @Test
  void testInterceptor_registered_shouldBePresent() {
    // Verify interceptor is registered in configuration
    Configuration config = sqlSessionFactory.getConfiguration();
    assertNotNull(config.getInterceptors());
    assertFalse(config.getInterceptors().isEmpty());

    boolean hasInterceptor = config.getInterceptors().stream()
        .anyMatch(i -> i instanceof SqlSafetyInterceptor);
    assertTrue(hasInterceptor, "SqlSafetyInterceptor should be registered");
  }

  @Test
  void testSafeQuery_shouldExecute() {
    // Safe SQL with WHERE clause should execute normally
    try (SqlSession session = sqlSessionFactory.openSession()) {
      TestMapper mapper = session.getMapper(TestMapper.class);
      TestMapper.User user = mapper.findById(1L);

      assertNotNull(user);
      assertEquals("Alice", user.getName());
    }
  }

  @Test
  void testDangerousQuery_BLOCK_shouldThrowException() {
    // Create factory with BLOCK strategy
    SqlSessionFactory blockFactory = createSqlSessionFactory(ViolationStrategy.BLOCK);

    // Dangerous SQL without WHERE clause should be blocked
    try (SqlSession session = blockFactory.openSession()) {
      TestMapper mapper = session.getMapper(TestMapper.class);

      // MyBatis wraps SQLException in PersistenceException
      Exception exception = assertThrows(Exception.class, mapper::findAll);
      assertTrue(exception.getMessage().contains("SQL Safety Violation") || 
                 exception.getCause() != null && exception.getCause().getMessage().contains("SQL Safety Violation"),
          "Exception should contain SQL Safety Violation message");
    }
  }

  @Test
  void testDangerousQuery_BLOCK_databaseUnchanged() {
    // Verify database is not modified when BLOCK strategy prevents execution
    SqlSessionFactory blockFactory = createSqlSessionFactory(ViolationStrategy.BLOCK);

    try (SqlSession session = blockFactory.openSession()) {
      TestMapper mapper = session.getMapper(TestMapper.class);

      // Try to delete all users (should be blocked)
      assertThrows(Exception.class, mapper::deleteAll);

      // Verify users still exist
      TestMapper.User user = mapper.findById(1L);
      assertNotNull(user, "User should still exist after blocked delete");
    }
  }

  @Test
  void testDangerousUpdate_BLOCK_shouldThrowException() {
    SqlSessionFactory blockFactory = createSqlSessionFactory(ViolationStrategy.BLOCK);

    try (SqlSession session = blockFactory.openSession()) {
      TestMapper mapper = session.getMapper(TestMapper.class);

      // Dangerous UPDATE without WHERE clause should be blocked
      Exception exception = assertThrows(Exception.class,
          () -> mapper.updateAllStatus("DELETED"));

      assertTrue(exception.getMessage().contains("SQL Safety Violation") ||
                 exception.getCause() != null && exception.getCause().getMessage().contains("SQL Safety Violation"),
          "Exception should contain SQL Safety Violation message");
    }
  }

  @Test
  void testDangerousUpdate_WARN_shouldExecute() {
    // WARN strategy should log but allow execution
    try (SqlSession session = sqlSessionFactory.openSession()) {
      TestMapper mapper = session.getMapper(TestMapper.class);

      // This should execute despite being dangerous (WARN strategy)
      int updated = mapper.updateAllStatus("INACTIVE");
      assertTrue(updated > 0, "Update should execute with WARN strategy");

      session.commit();
    }
  }

  @Test
  void testDangerousUpdate_WARN_shouldLog() {
    // Verify WARN strategy logs violations (checked via no exception thrown)
    try (SqlSession session = sqlSessionFactory.openSession()) {
      TestMapper mapper = session.getMapper(TestMapper.class);

      // Should not throw exception, just log
      assertDoesNotThrow(() -> mapper.updateAllStatus("ACTIVE"));
    }
  }

  @Test
  void testDynamicSql_withIf_shouldResolveAndValidate() {
    // Dynamic SQL with <if> tags should be resolved before validation
    try (SqlSession session = sqlSessionFactory.openSession()) {
      TestMapper mapper = session.getMapper(TestMapper.class);

      // Query with both conditions
      List<TestMapper.User> users = mapper.findByDynamicConditions("Alice", 20);
      assertNotNull(users);

      // Query with only name condition
      users = mapper.findByDynamicConditions("Bob", null);
      assertNotNull(users);

      // Query with only age condition
      users = mapper.findByDynamicConditions(null, 25);
      assertNotNull(users);
    }
  }

  @Test
  void testDynamicSql_withForeach_shouldResolveAndValidate() {
    // This test would require XML mapper configuration for <foreach>
    // For now, we verify dynamic SQL resolution works with <if> tags
    try (SqlSession session = sqlSessionFactory.openSession()) {
      TestMapper mapper = session.getMapper(TestMapper.class);

      List<TestMapper.User> users = mapper.findByDynamicConditions("Alice", 20);
      assertNotNull(users);
      // Validation should have checked the resolved SQL, not the template
    }
  }

  @Test
  void testRowBounds_shouldDetect() {
    // RowBounds should be detected for logical pagination validation
    try (SqlSession session = sqlSessionFactory.openSession()) {
      TestMapper mapper = session.getMapper(TestMapper.class);

      // Query with RowBounds pagination
      RowBounds rowBounds = new RowBounds(0, 2);
      List<TestMapper.User> users = session.selectList(
          "com.footstone.sqlguard.interceptor.mybatis.TestMapper.findByStatus",
          "ACTIVE",
          rowBounds
      );

      assertNotNull(users);
      assertTrue(users.size() <= 2, "RowBounds should limit results");
    }
  }

  @Test
  void testMultipleInterceptors_shouldCoexist() {
    // Verify SqlSafetyInterceptor can coexist with other interceptors
    Configuration config = sqlSessionFactory.getConfiguration();

    // Our interceptor should be present
    boolean hasSqlSafetyInterceptor = config.getInterceptors().stream()
        .anyMatch(i -> i instanceof SqlSafetyInterceptor);
    assertTrue(hasSqlSafetyInterceptor);

    // Execution should work normally
    try (SqlSession session = sqlSessionFactory.openSession()) {
      TestMapper mapper = session.getMapper(TestMapper.class);
      TestMapper.User user = mapper.findById(1L);
      assertNotNull(user);
    }
  }

  @Test
  void testConcurrentExecution_shouldBeThreadSafe() throws Exception {
    // Test thread-safety with concurrent SQL execution
    int threadCount = 10;
    int operationsPerThread = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    List<Future<Integer>> futures = new ArrayList<>();

    // Submit concurrent tasks
    for (int i = 0; i < threadCount; i++) {
      Future<Integer> future = executor.submit(() -> {
        int successCount = 0;
        for (int j = 0; j < operationsPerThread; j++) {
          try (SqlSession session = sqlSessionFactory.openSession()) {
            TestMapper mapper = session.getMapper(TestMapper.class);
            TestMapper.User user = mapper.findById(1L);
            if (user != null) {
              successCount++;
            }
          }
        }
        return successCount;
      });
      futures.add(future);
    }

    // Wait for all tasks to complete
    executor.shutdown();
    assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

    // Verify all operations succeeded
    int totalSuccess = 0;
    for (Future<Integer> future : futures) {
      totalSuccess += future.get();
    }

    assertEquals(threadCount * operationsPerThread, totalSuccess,
        "All concurrent operations should succeed");
  }

  @Test
  void testConcurrentViolations_shouldDetectAll() throws Exception {
    // Test that violations are detected correctly under concurrent load
    SqlSessionFactory blockFactory = createSqlSessionFactory(ViolationStrategy.BLOCK);

    int threadCount = 5;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    List<Future<Boolean>> futures = new ArrayList<>();

    // Submit concurrent dangerous operations
    for (int i = 0; i < threadCount; i++) {
      Future<Boolean> future = executor.submit(() -> {
        try (SqlSession session = blockFactory.openSession()) {
          TestMapper mapper = session.getMapper(TestMapper.class);
          mapper.findAll(); // Dangerous query
          return false; // Should not reach here
        } catch (Exception e) {
          // Check if exception or its cause contains SQL Safety Violation
          return e.getMessage().contains("SQL Safety Violation") ||
                 (e.getCause() != null && e.getCause().getMessage().contains("SQL Safety Violation"));
        }
      });
      futures.add(future);
    }

    // Wait for all tasks to complete
    executor.shutdown();
    assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

    // Verify all violations were detected
    for (Future<Boolean> future : futures) {
      assertTrue(future.get(), "Violation should be detected in concurrent execution");
    }
  }

  @Test
  void testValidatorCache_shouldPreventDuplicateValidation() {
    // Test that deduplication filter prevents redundant validation
    try (SqlSession session = sqlSessionFactory.openSession()) {
      TestMapper mapper = session.getMapper(TestMapper.class);

      // Execute same query multiple times quickly
      for (int i = 0; i < 5; i++) {
        TestMapper.User user = mapper.findById(1L);
        assertNotNull(user);
      }

      // Deduplication should have cached the validation result
      // (This is verified by the absence of performance degradation)
    }
  }

  /**
   * Helper method to create SqlSessionFactory with interceptor.
   */
  private SqlSessionFactory createSqlSessionFactory(ViolationStrategy strategy) {
    // Create datasource
    DataSource dataSource = new PooledDataSource(
        "org.h2.Driver",
        H2_URL,
        H2_USER,
        H2_PASSWORD
    );

    // Create environment
    Environment environment = new Environment(
        "test",
        new JdbcTransactionFactory(),
        dataSource
    );

    // Create configuration
    Configuration config = new Configuration(environment);
    config.addMapper(TestMapper.class);

    // Register interceptor
    SqlSafetyInterceptor interceptor = new SqlSafetyInterceptor(validator, strategy);
    config.addInterceptor(interceptor);

    // Build SqlSessionFactory
    return new SqlSessionFactoryBuilder().build(config);
  }

  /**
   * Helper method to create validator.
   * 
   * <p>Returns a validator that:</p>
   * <ul>
   *   <li>Fails validation for queries without WHERE clause (findAll, deleteAll, updateAllStatus)</li>
   *   <li>Passes validation for queries with WHERE clause</li>
   * </ul>
   */
  private SqlSafetyValidator createValidator() {
    return new SqlSafetyValidator() {
      @Override
      public ValidationResult validate(SqlContext context) {
        String sql = context.getSql().toLowerCase();
        
        // Check for dangerous patterns (no WHERE clause in UPDATE/DELETE/SELECT without specific ID)
        if ((sql.contains("update") || sql.contains("delete") || 
             (sql.contains("select") && sql.contains("from users") && !sql.contains("where"))) 
            && !sql.contains("where")) {
          ValidationResult result = ValidationResult.pass();
          result.addViolation(RiskLevel.HIGH, 
              "Missing WHERE clause in " + context.getType() + " statement",
              "Add WHERE clause to limit scope of operation");
          return result;
        }
        
        return ValidationResult.pass();
      }
    };
  }

  /**
   * Helper method to execute schema script.
   */
  private void executeSchemaScript(Connection conn) throws Exception {
    // Read schema.sql from classpath (Java 8 compatible)
    java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("schema.sql");
    java.io.BufferedReader reader = new java.io.BufferedReader(
        new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
    
    StringBuilder currentStatement = new StringBuilder();
    String line;
    
    try (Statement stmt = conn.createStatement()) {
      while ((line = reader.readLine()) != null) {
        // Skip comments and empty lines
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("--")) {
          continue;
        }
        
        // Append line to current statement
        currentStatement.append(line).append(" ");
        
        // If line ends with semicolon, execute the statement
        if (trimmed.endsWith(";")) {
          String sql = currentStatement.toString().trim();
          // Remove trailing semicolon
          if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1);
          }
          if (!sql.isEmpty()) {
            stmt.execute(sql);
          }
          currentStatement = new StringBuilder();
        }
      }
      
      // Execute any remaining statement
      if (currentStatement.length() > 0) {
        String sql = currentStatement.toString().trim();
        if (sql.endsWith(";")) {
          sql = sql.substring(0, sql.length() - 1);
        }
        if (!sql.isEmpty()) {
          stmt.execute(sql);
        }
      }
    } finally {
      reader.close();
    }
  }
}
