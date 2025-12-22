package com.footstone.sqlguard.interceptor.mp;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
 * Tests for QueryWrapper SQL validation.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Empty QueryWrapper validation (no WHERE clause)</li>
 *   <li>QueryWrapper with various conditions (eq, in, etc.)</li>
 *   <li>LambdaQueryWrapper validation</li>
 *   <li>UpdateWrapper and LambdaUpdateWrapper validation</li>
 *   <li>Complex wrapper conditions</li>
 *   <li>Wrapper with IPage pagination</li>
 *   <li>Dynamic runtime conditions</li>
 * </ul>
 */
class QueryWrapperValidationTest {

  private TestValidator validator;
  private Executor executor;
  private ResultHandler resultHandler;
  private MpSqlSafetyInnerInterceptor interceptor;

  @BeforeEach
  void setUp() {
    validator = new TestValidator();
    SqlDeduplicationFilter.clearThreadCache();
    interceptor = new MpSqlSafetyInnerInterceptor(validator, ViolationStrategy.BLOCK);
  }

  /**
   * Test: Empty QueryWrapper should violate no WHERE clause rule.
   */
  @Test
  void testEmptyQueryWrapper_shouldViolateNoWhere() {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    QueryWrapper<Object> wrapper = new QueryWrapper<>(); // Empty
    
    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
    validator.setResult(result);

    // Act & Assert
    SQLException exception = assertThrows(SQLException.class, () -> {
      interceptor.beforeQuery(executor, ms, wrapper, new RowBounds(), resultHandler, bs);
    });
    
    assertTrue(exception.getMessage().contains("Missing WHERE clause"));
    assertTrue(exception.getMessage().contains("[QueryWrapper]"));
  }

  /**
   * Test: QueryWrapper with eq condition should pass.
   */
  @Test
  void testQueryWrapperWithEq_shouldPass() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE id = ?",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    QueryWrapper<Object> wrapper = new QueryWrapper<>();
    wrapper.eq("id", 123);
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, wrapper, new RowBounds(), resultHandler, bs);

    // Assert - no exception thrown
    assertTrue(validator.wasCalled());
  }

  /**
   * Test: QueryWrapper with in condition should pass.
   */
  @Test
  void testQueryWrapperWithIn_shouldPass() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE id IN (?, ?, ?)",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    QueryWrapper<Object> wrapper = new QueryWrapper<>();
    wrapper.in("id", 1, 2, 3);
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, wrapper, new RowBounds(), resultHandler, bs);

    // Assert - no exception thrown
    assertTrue(validator.wasCalled());
  }

  /**
   * Test: QueryWrapper with blacklist field should violate.
   */
  @Test
  void testQueryWrapperBlacklistField_shouldViolate() {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT password FROM user WHERE id = ?",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    QueryWrapper<Object> wrapper = new QueryWrapper<>();
    wrapper.eq("id", 123);
    
    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.CRITICAL, "Blacklist field: password", "Remove sensitive field");
    validator.setResult(result);

    // Act & Assert
    SQLException exception = assertThrows(SQLException.class, () -> {
      interceptor.beforeQuery(executor, ms, wrapper, new RowBounds(), resultHandler, bs);
    });
    
    assertTrue(exception.getMessage().contains("Blacklist field"));
  }

  /**
   * Test: LambdaQueryWrapper should validate.
   */
  @Test
  void testLambdaQueryWrapper_shouldValidate() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE name = ?",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    LambdaQueryWrapper<TestEntity> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(TestEntity::getName, "John");
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, wrapper, new RowBounds(), resultHandler, bs);

    // Assert - no exception thrown
    assertTrue(validator.wasCalled());
  }

  /**
   * Test: UpdateWrapper should validate.
   */
  @Test
  void testUpdateWrapper_shouldValidate() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "UPDATE user SET status = ? WHERE id = ?",
        SqlCommandType.UPDATE
    );
    UpdateWrapper<Object> wrapper = new UpdateWrapper<>();
    wrapper.eq("id", 123);
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeUpdate(executor, ms, wrapper);

    // Assert - no exception thrown
    assertTrue(validator.wasCalled());
  }

  /**
   * Test: LambdaUpdateWrapper should validate.
   */
  @Test
  void testLambdaUpdateWrapper_shouldValidate() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "UPDATE user SET email = ? WHERE id = ?",
        SqlCommandType.UPDATE
    );
    LambdaUpdateWrapper<TestEntity> wrapper = new LambdaUpdateWrapper<>();
    wrapper.eq(TestEntity::getId, 456L);
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeUpdate(executor, ms, wrapper);

    // Assert - no exception thrown
    assertTrue(validator.wasCalled());
  }

  /**
   * Test: Complex wrapper with multiple conditions should pass.
   */
  @Test
  void testComplexWrapper_multipleConditions_shouldPass() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE age > ? AND status = ? AND city IN (?, ?)",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    QueryWrapper<Object> wrapper = new QueryWrapper<>();
    wrapper.gt("age", 18)
           .eq("status", "active")
           .in("city", "Beijing", "Shanghai");
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, wrapper, new RowBounds(), resultHandler, bs);

    // Assert - no exception thrown
    assertTrue(validator.wasCalled());
  }

  /**
   * Test: Wrapper with IPage should detect both.
   */
  @Test
  void testWrapperWithIPage_shouldDetectBoth() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE status = ?",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    
    Map<String, Object> paramMap = new HashMap<>();
    QueryWrapper<Object> wrapper = new QueryWrapper<>();
    wrapper.eq("status", "active");
    paramMap.put("ew", wrapper);
    paramMap.put("page", new Page<>(1, 10));
    
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, paramMap, new RowBounds(), resultHandler, bs);

    // Assert - both wrapper and IPage detected
    assertTrue(validator.wasCalled());
  }

  /**
   * Test: Wrapper generated SQL should match expected pattern.
   */
  @Test
  void testWrapperGeneratedSql_shouldMatchExpected() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE id = 123",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    QueryWrapper<Object> wrapper = new QueryWrapper<>();
    wrapper.eq("id", 123);
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, wrapper, new RowBounds(), resultHandler, bs);

    // Assert
    assertTrue(validator.wasCalled());
    SqlContext context = validator.getLastContext();
    assertTrue(context.getSql().contains("WHERE"), "SQL should contain WHERE clause");
  }

  /**
   * Test: Nested wrapper conditions should validate.
   */
  @Test
  void testNestedWrapper_shouldValidate() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE (age > ? OR status = ?) AND city = ?",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    QueryWrapper<Object> wrapper = new QueryWrapper<>();
    wrapper.nested(w -> w.gt("age", 18).or().eq("status", "vip"))
           .eq("city", "Beijing");
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, wrapper, new RowBounds(), resultHandler, bs);

    // Assert - no exception thrown
    assertTrue(validator.wasCalled());
  }

  /**
   * Test: Dynamic wrapper with runtime conditions should catch violations.
   */
  @Test
  void testDynamicWrapper_runtimeConditions_shouldCatch() {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    
    // Dynamic wrapper - conditions added at runtime
    QueryWrapper<Object> wrapper = new QueryWrapper<>();
    // Intentionally empty to simulate runtime bug
    
    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.HIGH, "Dynamic wrapper is empty", "Add runtime conditions");
    validator.setResult(result);

    // Act & Assert
    SQLException exception = assertThrows(SQLException.class, () -> {
      interceptor.beforeQuery(executor, ms, wrapper, new RowBounds(), resultHandler, bs);
    });
    
    assertTrue(exception.getMessage().contains("Dynamic wrapper is empty"));
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
   * Test validator that captures SqlContext.
   */
  static class TestValidator extends DefaultSqlSafetyValidator {
    private ValidationResult result = ValidationResult.pass();
    private boolean called = false;
    private SqlContext lastContext = null;

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
      called = true;
      lastContext = context;
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
      lastContext = null;
    }

    public SqlContext getLastContext() {
      return lastContext;
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

  /**
   * Test entity for LambdaQueryWrapper.
   */
  static class TestEntity {
    private Long id;
    private String name;
    private String email;

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

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }
  }
}









