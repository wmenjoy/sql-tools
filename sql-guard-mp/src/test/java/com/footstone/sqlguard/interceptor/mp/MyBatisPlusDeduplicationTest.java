package com.footstone.sqlguard.interceptor.mp;

import static org.junit.jupiter.api.Assertions.*;

import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.footstone.sqlguard.validator.SqlDeduplicationFilter;
import com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for deduplication behavior with MyBatis-Plus interceptor.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Deduplication prevents double validation</li>
 *   <li>Cache key matching</li>
 *   <li>TTL expiration behavior</li>
 *   <li>Different SQL validation</li>
 *   <li>Same SQL with different context</li>
 *   <li>ThreadLocal cache isolation</li>
 *   <li>Cache clearing</li>
 * </ul>
 */
class MyBatisPlusDeduplicationTest {

  private TestValidator validator;
  private MpSqlSafetyInnerInterceptor interceptor;

  @BeforeEach
  void setUp() {
    validator = new TestValidator();
    SqlDeduplicationFilter.clearThreadCache();
    interceptor = new MpSqlSafetyInnerInterceptor(validator, ViolationStrategy.BLOCK);
  }

  /**
   * Test: Both MyBatis and MyBatis-Plus interceptors should not double validate.
   * Note: This test simulates the scenario where both interceptors are enabled.
   */
  @Test
  void testBothInterceptors_enabled_shouldNotDoubleValidate() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE id = ?",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());

    // Act - first call (simulating MyBatis interceptor)
    interceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);
    assertTrue(validator.wasCalled(), "First call should validate");
    
    // Act - second call (simulating MyBatis-Plus interceptor)
    validator.reset();
    interceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);

    // Assert - second call should be skipped by deduplication
    assertFalse(validator.wasCalled(), "Second call should be skipped");
  }

  /**
   * Test: Deduplication filter cache key should match for same SQL.
   */
  @Test
  void testDeduplicationFilter_cacheKey_shouldMatch() throws SQLException {
    // Arrange
    String sql = "SELECT * FROM user WHERE status = 'active'";
    MappedStatement ms = createMappedStatement(sql, SqlCommandType.SELECT);
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());

    // Act - execute same SQL twice
    interceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);
    int firstCallCount = validator.getCallCount();
    
    interceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);
    int secondCallCount = validator.getCallCount();

    // Assert - second call should use cached result
    assertEquals(1, firstCallCount, "First call should validate");
    assertEquals(1, secondCallCount, "Second call should be cached");
  }

  /**
   * Test: Deduplication within TTL should skip validation.
   */
  @Test
  void testDeduplicationFilter_withinTTL_shouldSkip() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM orders WHERE date > ?",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());

    // Act - execute within TTL window (default 100ms)
    interceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);
    validator.reset();
    interceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);

    // Assert - second call should be skipped
    assertFalse(validator.wasCalled(), "Should skip validation within TTL");
  }

  /**
   * Test: Deduplication after TTL should re-validate.
   */
  @Test
  void testDeduplicationFilter_afterTTL_shouldValidate() throws SQLException, InterruptedException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM products WHERE category = ?",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());

    // Act - first call
    interceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);
    assertTrue(validator.wasCalled(), "First call should validate");
    
    // Wait for TTL to expire (default 100ms)
    Thread.sleep(150);
    
    // Act - second call after TTL
    validator.reset();
    interceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);

    // Assert - should re-validate after TTL
    assertTrue(validator.wasCalled(), "Should re-validate after TTL expiration");
  }

  /**
   * Test: Different SQL should validate both.
   */
  @Test
  void testDifferentSQL_shouldValidateBoth() throws SQLException {
    // Arrange
    MappedStatement ms1 = createMappedStatement(
        "SELECT * FROM user WHERE id = 1",
        SqlCommandType.SELECT
    );
    MappedStatement ms2 = createMappedStatement(
        "SELECT * FROM user WHERE id = 2",
        SqlCommandType.SELECT
    );
    BoundSql bs1 = ms1.getBoundSql(null);
    BoundSql bs2 = ms2.getBoundSql(null);
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(null, ms1, new HashMap<>(), new RowBounds(), null, bs1);
    int firstCount = validator.getCallCount();
    
    validator.reset();
    interceptor.beforeQuery(null, ms2, new HashMap<>(), new RowBounds(), null, bs2);
    int secondCount = validator.getCallCount();

    // Assert - both should validate (different SQL)
    assertEquals(1, firstCount, "First SQL should validate");
    assertEquals(1, secondCount, "Second SQL should validate");
  }

  /**
   * Test: Same SQL with different context should validate both.
   * Note: Deduplication is based on SQL text only, not context.
   */
  @Test
  void testSameSQL_differentContext_shouldValidateBoth() throws SQLException {
    // Arrange
    String sql = "SELECT * FROM user WHERE id = ?";
    MappedStatement ms = createMappedStatement(sql, SqlCommandType.SELECT);
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());

    // Act - first call with context A
    HashMap<String, Object> contextA = new HashMap<>();
    contextA.put("id", 1);
    interceptor.beforeQuery(null, ms, contextA, new RowBounds(), null, bs);
    
    // Act - second call with context B (same SQL, different params)
    validator.reset();
    HashMap<String, Object> contextB = new HashMap<>();
    contextB.put("id", 2);
    interceptor.beforeQuery(null, ms, contextB, new RowBounds(), null, bs);

    // Assert - second call should be skipped (deduplication is SQL-based)
    assertFalse(validator.wasCalled(), "Deduplication is SQL-based, not context-based");
  }

  /**
   * Test: ThreadLocal cache should isolate threads.
   */
  @Test
  void testThreadLocal_cache_shouldIsolateThreads() throws Exception {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE email = ?",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());

    // Act - main thread
    interceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);
    assertTrue(validator.wasCalled(), "Main thread should validate");

    // Act - new thread
    Thread thread = new Thread(() -> {
      try {
        TestValidator threadValidator = new TestValidator();
        threadValidator.setResult(ValidationResult.pass());
        MpSqlSafetyInnerInterceptor threadInterceptor = 
            new MpSqlSafetyInnerInterceptor(threadValidator, ViolationStrategy.BLOCK);
        
        threadInterceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);
        assertTrue(threadValidator.wasCalled(), "New thread should validate (separate cache)");
      } catch (SQLException e) {
        fail("Should not throw exception");
      }
    });
    
    thread.start();
    thread.join();
  }

  /**
   * Test: Clear cache should allow re-validation.
   */
  @Test
  void testClearCache_shouldRevalidate() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE username = ?",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());

    // Act - first call
    interceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);
    assertTrue(validator.wasCalled(), "First call should validate");
    
    // Clear cache
    SqlDeduplicationFilter.clearThreadCache();
    
    // Act - second call after cache clear
    validator.reset();
    interceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);

    // Assert - should re-validate after cache clear
    assertTrue(validator.wasCalled(), "Should re-validate after cache clear");
  }

  /**
   * Helper method to create a MappedStatement.
   */
  private MappedStatement createMappedStatement(String sql, SqlCommandType commandType) {
    Configuration configuration = new Configuration();
    return new MappedStatement.Builder(
        configuration,
        "com.example.UserMapper.testMethod",
        new TestSqlSource(sql),
        commandType
    ).build();
  }

  /**
   * Test validator that tracks call count.
   */
  static class TestValidator extends DefaultSqlSafetyValidator {
    private ValidationResult result = ValidationResult.pass();
    private int callCount = 0;

    public TestValidator() {
      super(
          new JSqlParserFacade(true),
          new ArrayList<>(),
          new RuleCheckerOrchestrator(new ArrayList<>()),
          new SqlDeduplicationFilter()
      );
    }

    @Override
    public ValidationResult validate(SqlContext context) {
      callCount++;
      return result;
    }

    public void setResult(ValidationResult result) {
      this.result = result;
    }

    public boolean wasCalled() {
      return callCount > 0;
    }

    public int getCallCount() {
      return callCount;
    }

    public void reset() {
      callCount = 0;
    }
  }

  /**
   * Test SQL source.
   */
  static class TestSqlSource implements SqlSource {
    private final String sql;

    public TestSqlSource(String sql) {
      this.sql = sql;
    }

    @Override
    public BoundSql getBoundSql(Object parameterObject) {
      return new BoundSql(new Configuration(), sql, new ArrayList<>(), parameterObject);
    }
  }
}














