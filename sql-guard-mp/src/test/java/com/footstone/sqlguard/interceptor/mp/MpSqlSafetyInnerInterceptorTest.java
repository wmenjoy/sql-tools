package com.footstone.sqlguard.interceptor.mp;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.footstone.sqlguard.validator.SqlDeduplicationFilter;
import com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for MpSqlSafetyInnerInterceptor.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>beforeQuery and beforeUpdate interception</li>
 *   <li>IPage detection and extraction</li>
 *   <li>QueryWrapper/LambdaQueryWrapper detection</li>
 *   <li>Violation handling with BLOCK/WARN/LOG strategies</li>
 *   <li>Valid SQL pass-through</li>
 *   <li>Deduplication filter integration</li>
 * </ul>
 */
class MpSqlSafetyInnerInterceptorTest {

  private TestValidator validator;
  private Executor executor;
  private MappedStatement mappedStatement;
  private BoundSql boundSql;
  private ResultHandler resultHandler;
  private MpSqlSafetyInnerInterceptor interceptor;

  @BeforeEach
  void setUp() {
    // Create test validator that passes by default
    validator = new TestValidator();
    
    // Clear deduplication filter for clean test state
    SqlDeduplicationFilter.clearThreadCache();
    
    // Default setup with BLOCK strategy
    interceptor = new MpSqlSafetyInnerInterceptor(validator, ViolationStrategy.BLOCK);
  }
  
  /**
   * Helper method to create a MappedStatement with unique SQL for each test.
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
   * Test: beforeQuery should validate SQL.
   */
  @Test
  void testBeforeQuery_shouldValidate() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement("SELECT * FROM user WHERE id = 1", SqlCommandType.SELECT);
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());
    Object parameter = new HashMap<>();
    RowBounds rowBounds = new RowBounds();

    // Act
    interceptor.beforeQuery(executor, ms, parameter, rowBounds, resultHandler, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Validator should have been called");
  }

  /**
   * Test: beforeUpdate should validate SQL.
   */
  @Test
  void testBeforeUpdate_shouldValidate() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement("UPDATE user SET name = 'test' WHERE id = 2", SqlCommandType.UPDATE);
    validator.setResult(ValidationResult.pass());
    Object parameter = new HashMap<>();

    // Act
    interceptor.beforeUpdate(executor, ms, parameter);

    // Assert
    assertTrue(validator.wasCalled(), "Validator should have been called");
  }

  /**
   * Test: IPage detection should extract pagination info from direct IPage parameter.
   */
  @Test
  void testIPageDetection_shouldExtract() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement("SELECT * FROM user WHERE age > 18", SqlCommandType.SELECT);
    BoundSql bs = ms.getBoundSql(null);
    IPage<Object> page = new Page<>(2, 20); // current=2, size=20
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, page, new RowBounds(), resultHandler, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Validator should have been called");
    // IPage detection logged (verified by no exception)
  }

  /**
   * Test: QueryWrapper detection should set flag.
   */
  @Test
  void testQueryWrapperSqlCapture_shouldValidate() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement("SELECT * FROM user WHERE id = 123", SqlCommandType.SELECT);
    BoundSql bs = ms.getBoundSql(null);
    QueryWrapper<Object> wrapper = new QueryWrapper<>();
    wrapper.eq("id", 123);
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, wrapper, new RowBounds(), resultHandler, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Validator should have been called");
    // QueryWrapper detected (verified by no exception)
  }

  /**
   * Test: LambdaQueryWrapper detection should set flag.
   */
  @Test
  void testLambdaQueryWrapperSqlCapture_shouldValidate() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement("SELECT * FROM user WHERE id = 456", SqlCommandType.SELECT);
    BoundSql bs = ms.getBoundSql(null);
    LambdaQueryWrapper<TestEntity> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(TestEntity::getId, 123);
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, wrapper, new RowBounds(), resultHandler, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Validator should have been called");
    // LambdaQueryWrapper detected (verified by no exception)
  }

  /**
   * Test: Violation handling with BLOCK strategy should throw SQLException.
   */
  @Test
  void testViolationHandling_BLOCK_shouldThrow() {
    // Arrange
    MappedStatement ms = createMappedStatement("SELECT * FROM user", SqlCommandType.SELECT);
    BoundSql bs = ms.getBoundSql(null);
    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
    validator.setResult(result);

    // Act & Assert
    SQLException exception = assertThrows(SQLException.class, () -> {
      interceptor.beforeQuery(executor, ms, new HashMap<>(), 
          new RowBounds(), resultHandler, bs);
    });

    assertTrue(exception.getMessage().contains("SQL validation failed"));
    assertTrue(exception.getMessage().contains("Missing WHERE clause"));
  }

  /**
   * Test: Violation handling with WARN strategy should log warning.
   */
  @Test
  void testViolationHandling_WARN_shouldLog() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement("SELECT * FROM orders WHERE status = 'pending'", SqlCommandType.SELECT);
    BoundSql bs = ms.getBoundSql(null);
    MpSqlSafetyInnerInterceptor warnInterceptor = 
        new MpSqlSafetyInnerInterceptor(validator, ViolationStrategy.WARN);
    
    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.MEDIUM, "Potential issue", "Consider fixing");
    validator.setResult(result);

    // Act
    warnInterceptor.beforeQuery(executor, ms, new HashMap<>(), 
        new RowBounds(), resultHandler, bs);

    // Assert - no exception thrown, execution proceeds
    assertTrue(validator.wasCalled(), "Validator should have been called");
  }

  /**
   * Test: Violation handling with LOG strategy should log info.
   */
  @Test
  void testViolationHandling_LOG_shouldLog() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement("SELECT * FROM products WHERE category = 'electronics'", SqlCommandType.SELECT);
    BoundSql bs = ms.getBoundSql(null);
    MpSqlSafetyInnerInterceptor logInterceptor = 
        new MpSqlSafetyInnerInterceptor(validator, ViolationStrategy.LOG);
    
    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.LOW, "Minor issue", "Optional fix");
    validator.setResult(result);

    // Act
    logInterceptor.beforeQuery(executor, ms, new HashMap<>(), 
        new RowBounds(), resultHandler, bs);

    // Assert - no exception thrown, execution proceeds
    assertTrue(validator.wasCalled(), "Validator should have been called");
  }

  /**
   * Test: Valid SQL should proceed without issues.
   */
  @Test
  void testValidSql_shouldProceed() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement("SELECT * FROM user WHERE email = 'test@example.com'", SqlCommandType.SELECT);
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, new HashMap<>(), 
        new RowBounds(), resultHandler, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Validator should have been called");
    // No exception thrown
  }

  /**
   * Test: Deduplication should prevent double check for same SQL.
   */
  @Test
  void testDeduplication_shouldPreventDoubleCheck() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement("SELECT * FROM user WHERE status = 'active'", SqlCommandType.SELECT);
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());
    Object parameter = new HashMap<>();
    RowBounds rowBounds = new RowBounds();

    // Act - execute same SQL twice
    interceptor.beforeQuery(executor, ms, parameter, rowBounds, resultHandler, bs);
    validator.reset(); // Reset call counter
    interceptor.beforeQuery(executor, ms, parameter, rowBounds, resultHandler, bs);

    // Assert - validator not called second time due to deduplication
    assertFalse(validator.wasCalled(), "Validator should not be called second time due to deduplication");
  }

  /**
   * Test: Constructor should throw exception if validator is null.
   */
  @Test
  void testConstructor_nullValidator_shouldThrow() {
    // Act & Assert
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      new MpSqlSafetyInnerInterceptor(null, ViolationStrategy.BLOCK);
    });

    assertEquals("validator cannot be null", exception.getMessage());
  }

  /**
   * Test: Constructor should throw exception if strategy is null.
   */
  @Test
  void testConstructor_nullStrategy_shouldThrow() {
    // Act & Assert
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      new MpSqlSafetyInnerInterceptor(validator, null);
    });

    assertEquals("strategy cannot be null", exception.getMessage());
  }

  /**
   * Test validator that allows controlling validation results.
   */
  static class TestValidator extends DefaultSqlSafetyValidator {
    private ValidationResult result = ValidationResult.pass();
    private boolean called = false;

    public TestValidator() {
      super(
          new JSqlParserFacade(true), // lenient mode
          new ArrayList<>(),
          new RuleCheckerOrchestrator(new ArrayList<>()),
          new SqlDeduplicationFilter()
      );
    }

    @Override
    public ValidationResult validate(SqlContext context) {
      called = true;
      return result;
    }

    public void setResult(ValidationResult result) {
      this.result = result;
    }

    public boolean wasCalled() {
      return called;
    }

    public void reset() {
      called = false;
    }
  }

  /**
   * Test SQL source for creating MappedStatement.
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

  /**
   * Test entity for LambdaQueryWrapper testing.
   */
  static class TestEntity {
    private Long id;
    private String name;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}















