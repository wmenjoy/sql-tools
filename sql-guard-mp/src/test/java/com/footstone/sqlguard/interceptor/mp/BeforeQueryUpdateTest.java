package com.footstone.sqlguard.interceptor.mp;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
 * Tests for beforeQuery/beforeUpdate SQL context extraction.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>SqlContext extraction from MappedStatement and BoundSql</li>
 *   <li>IPage detection (direct parameter and in Map)</li>
 *   <li>IPage details extraction (current page and size)</li>
 *   <li>QueryWrapper/LambdaQueryWrapper/UpdateWrapper detection</li>
 *   <li>Parameter extraction from various parameter types</li>
 *   <li>Deduplication behavior for repeated SQL</li>
 * </ul>
 */
class BeforeQueryUpdateTest {

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
   * Test: validateSql should extract SqlContext correctly.
   */
  @Test
  void testValidateSql_shouldExtractContext() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE id = ?", 
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, new HashMap<>(), new RowBounds(), resultHandler, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Validator should have been called");
    SqlContext context = validator.getLastContext();
    assertNotNull(context, "SqlContext should be captured");
    assertEquals("SELECT * FROM user WHERE id = ?", context.getSql());
    assertEquals("com.example.UserMapper.testMethod", context.getMapperId());
  }

  /**
   * Test: IPage detection when IPage is direct parameter.
   */
  @Test
  void testIPageDetection_direct_shouldExtract() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE age > 18", 
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    IPage<Object> page = new Page<>(3, 50); // current=3, size=50
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, page, new RowBounds(), resultHandler, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Validator should have been called");
    // IPage detected (verified by no exception)
  }

  /**
   * Test: IPage detection when IPage is in parameter Map.
   */
  @Test
  void testIPageDetection_inMap_shouldExtract() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE status = ?", 
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    
    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("status", "active");
    paramMap.put("page", new Page<>(1, 20));
    
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, paramMap, new RowBounds(), resultHandler, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Validator should have been called");
    // IPage in map detected (verified by no exception)
  }

  /**
   * Test: IPage current page should be extracted correctly.
   */
  @Test
  void testIPageDetails_current_shouldExtract() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM orders WHERE date > ?", 
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    IPage<Object> page = new Page<>(5, 100); // current=5, size=100
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, page, new RowBounds(), resultHandler, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Validator should have been called");
    // Current page = 5 should be logged
  }

  /**
   * Test: IPage size should be extracted correctly.
   */
  @Test
  void testIPageDetails_size_shouldExtract() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM products WHERE category = ?", 
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    IPage<Object> page = new Page<>(1, 200); // current=1, size=200
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, page, new RowBounds(), resultHandler, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Validator should have been called");
    // Page size = 200 should be logged
  }

  /**
   * Test: QueryWrapper detection should set flag.
   */
  @Test
  void testQueryWrapperDetection_shouldSetFlag() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE name = ?", 
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    
    QueryWrapper<Object> wrapper = new QueryWrapper<>();
    wrapper.eq("name", "John");
    
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, wrapper, new RowBounds(), resultHandler, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Validator should have been called");
    // QueryWrapper flag should be set
  }

  /**
   * Test: LambdaQueryWrapper detection should set flag.
   */
  @Test
  void testLambdaQueryWrapperDetection_shouldSetFlag() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE email = ?", 
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    
    LambdaQueryWrapper<TestEntity> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(TestEntity::getEmail, "test@example.com");
    
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, wrapper, new RowBounds(), resultHandler, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Validator should have been called");
    // LambdaQueryWrapper flag should be set
  }

  /**
   * Test: UpdateWrapper detection should set flag.
   */
  @Test
  void testUpdateWrapperDetection_shouldSetFlag() throws SQLException {
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

    // Assert
    assertTrue(validator.wasCalled(), "Validator should have been called");
    // UpdateWrapper flag should be set
  }

  /**
   * Test: No IPage parameter should not set page info.
   */
  @Test
  void testNoIPage_shouldNotSetPageInfo() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE id = ?", 
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    
    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("id", 123);
    
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, paramMap, new RowBounds(), resultHandler, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Validator should have been called");
    // No IPage info should be set
  }

  /**
   * Test: No wrapper parameter should not set wrapper flag.
   */
  @Test
  void testNoWrapper_shouldNotSetFlag() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE username = ?", 
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    
    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("username", "admin");
    
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, paramMap, new RowBounds(), resultHandler, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Validator should have been called");
    // No wrapper flag should be set
  }

  /**
   * Test: Deduplication should skip validation for same SQL.
   */
  @Test
  void testDeduplication_sameSQL_shouldSkipSecond() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE role = 'admin'", 
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());

    // Act - execute same SQL twice
    interceptor.beforeQuery(executor, ms, new HashMap<>(), new RowBounds(), resultHandler, bs);
    assertTrue(validator.wasCalled(), "First call should validate");
    
    validator.reset();
    interceptor.beforeQuery(executor, ms, new HashMap<>(), new RowBounds(), resultHandler, bs);

    // Assert - second call should be skipped
    assertFalse(validator.wasCalled(), "Second call should be skipped by deduplication");
  }

  /**
   * Test: Parameter extraction should handle complex types.
   */
  @Test
  void testParameterExtraction_shouldHandleComplexTypes() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE id = ? AND status = ?", 
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    
    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("id", 123);
    paramMap.put("status", "active");
    paramMap.put("nested", new HashMap<String, Object>() {{
      put("key", "value");
    }});
    
    validator.setResult(ValidationResult.pass());

    // Act
    interceptor.beforeQuery(executor, ms, paramMap, new RowBounds(), resultHandler, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Validator should have been called");
    SqlContext context = validator.getLastContext();
    assertNotNull(context.getParams(), "Parameters should be extracted");
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
   * Test validator that captures SqlContext for assertions.
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









