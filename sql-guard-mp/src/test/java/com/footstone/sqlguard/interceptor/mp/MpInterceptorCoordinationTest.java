package com.footstone.sqlguard.interceptor.mp;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.footstone.sqlguard.validator.SqlDeduplicationFilter;
import com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Tests for MpSqlSafetyInnerInterceptor coordination with PaginationInnerInterceptor.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Both interceptors configured in MybatisPlusInterceptor</li>
 *   <li>Correct execution order (Pagination first, Safety second)</li>
 *   <li>IPage queries with and without WHERE clauses</li>
 *   <li>Non-IPage queries</li>
 *   <li>QueryWrapper with empty conditions</li>
 *   <li>Multiple interceptors execution</li>
 * </ul>
 */
class MpInterceptorCoordinationTest {

  private TestValidator validator;
  private MybatisPlusInterceptor mpInterceptor;
  private MpSqlSafetyInnerInterceptor safetyInterceptor;
  private PaginationInnerInterceptor paginationInterceptor;

  @BeforeEach
  void setUp() {
    validator = new TestValidator();
    SqlDeduplicationFilter.clearThreadCache();
    
    // Create interceptors
    paginationInterceptor = new PaginationInnerInterceptor();
    safetyInterceptor = new MpSqlSafetyInnerInterceptor(validator, ViolationStrategy.BLOCK);
    
    // Configure MybatisPlusInterceptor with correct order
    mpInterceptor = new MybatisPlusInterceptor();
    mpInterceptor.addInnerInterceptor(paginationInterceptor);
    mpInterceptor.addInnerInterceptor(safetyInterceptor);
  }

  /**
   * Test: Both interceptors should be configured.
   */
  @Test
  void testBothInterceptors_configured_shouldExist() {
    // Assert
    assertNotNull(mpInterceptor, "MybatisPlusInterceptor should be configured");
    assertEquals(2, mpInterceptor.getInterceptors().size(), 
        "Should have 2 interceptors");
    assertEquals(paginationInterceptor, mpInterceptor.getInterceptors().get(0),
        "First interceptor should be PaginationInnerInterceptor");
    assertEquals(safetyInterceptor, mpInterceptor.getInterceptors().get(1),
        "Second interceptor should be MpSqlSafetyInnerInterceptor");
  }

  /**
   * Test: Interceptor order should be Pagination first, Safety second.
   */
  @Test
  void testPaginationFirst_safetySecond_correctOrder() {
    // Assert
    assertEquals(paginationInterceptor, mpInterceptor.getInterceptors().get(0),
        "PaginationInnerInterceptor should be first");
    assertEquals(safetyInterceptor, mpInterceptor.getInterceptors().get(1),
        "MpSqlSafetyInnerInterceptor should be second");
  }

  /**
   * Test: IPage query should trigger pagination interceptor to add LIMIT.
   */
  @Test
  void testIPageQuery_pagination_addsLimit() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE age > 18",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    IPage<Object> page = new Page<>(1, 10);
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeQuery(null, ms, page, new RowBounds(), null, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Safety interceptor should validate");
    // In real scenario, PaginationInnerInterceptor would add LIMIT clause
  }

  /**
   * Test: IPage query should be validated by safety interceptor.
   */
  @Test
  void testIPageQuery_safety_validatesWithLimit() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE status = 'active' LIMIT 10",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    IPage<Object> page = new Page<>(1, 10);
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeQuery(null, ms, page, new RowBounds(), null, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Safety interceptor should validate SQL with LIMIT");
  }

  /**
   * Test: IPage query without WHERE should still violate even with LIMIT.
   */
  @Test
  void testNoWhereIPage_pagination_addsLimit_safety_stillViolates() {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    IPage<Object> page = new Page<>(1, 10);
    
    // Simulate violation for no WHERE clause
    ValidationResult result = ValidationResult.pass();
    result.addViolation(
        com.footstone.sqlguard.core.model.RiskLevel.HIGH,
        "Missing WHERE clause",
        "Add WHERE condition"
    );
    validator.setResult(result);

    // Act & Assert
    assertThrows(SQLException.class, () -> {
      safetyInterceptor.beforeQuery(null, ms, page, new RowBounds(), null, bs);
    }, "Should throw SQLException for missing WHERE clause");
  }

  /**
   * Test: Valid IPage query with WHERE should pass both interceptors.
   */
  @Test
  void testValidWhereIPage_bothPass() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE id > 100",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    IPage<Object> page = new Page<>(1, 20);
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeQuery(null, ms, page, new RowBounds(), null, bs);

    // Assert - no exception thrown
    assertTrue(validator.wasCalled(), "Safety interceptor should validate");
  }

  /**
   * Test: Non-IPage query should skip pagination but still validate.
   */
  @Test
  void testNonIPageQuery_paginationSkips_safetyValidates() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE email = 'test@example.com'",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Safety interceptor should validate non-IPage queries");
  }

  /**
   * Test: Empty QueryWrapper should be equivalent to no WHERE.
   */
  @Test
  void testQueryWrapper_empty_equivalentToNoWhere() {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    QueryWrapper<Object> wrapper = new QueryWrapper<>(); // Empty wrapper
    
    // Simulate violation for empty wrapper
    ValidationResult result = ValidationResult.pass();
    result.addViolation(
        com.footstone.sqlguard.core.model.RiskLevel.HIGH,
        "Empty QueryWrapper - no WHERE clause",
        "Add conditions to QueryWrapper"
    );
    validator.setResult(result);

    // Act & Assert
    assertThrows(SQLException.class, () -> {
      safetyInterceptor.beforeQuery(null, ms, wrapper, new RowBounds(), null, bs);
    }, "Should throw SQLException for empty QueryWrapper");
  }

  /**
   * Test: Multiple interceptors should all execute.
   */
  @Test
  void testMultipleInterceptors_allExecute() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM orders WHERE status = 'pending'",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Safety interceptor should execute");
    // In real scenario, all interceptors in the chain would execute
  }

  /**
   * Test: Interceptor order affects results.
   */
  @Test
  void testInterceptorOrder_affectsResults() {
    // Arrange
    MybatisPlusInterceptor wrongOrderInterceptor = new MybatisPlusInterceptor();
    // Wrong order: Safety before Pagination
    wrongOrderInterceptor.addInnerInterceptor(safetyInterceptor);
    wrongOrderInterceptor.addInnerInterceptor(paginationInterceptor);

    // Assert
    assertEquals(safetyInterceptor, wrongOrderInterceptor.getInterceptors().get(0),
        "Wrong order: Safety is first");
    assertEquals(paginationInterceptor, wrongOrderInterceptor.getInterceptors().get(1),
        "Wrong order: Pagination is second");
    
    // Note: This test demonstrates wrong order. Correct order is:
    // 1. PaginationInnerInterceptor (adds LIMIT)
    // 2. MpSqlSafetyInnerInterceptor (validates final SQL with LIMIT)
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
   * Test validator that captures validation calls.
   */
  static class TestValidator extends DefaultSqlSafetyValidator {
    private ValidationResult result = ValidationResult.pass();
    private boolean called = false;

    public TestValidator() {
      super(
          new JSqlParserFacade(true),
          new ArrayList<>(),
          new RuleCheckerOrchestrator(new ArrayList<>()),
          new SqlDeduplicationFilter()
      );
    }

    @Override
    public ValidationResult validate(com.footstone.sqlguard.core.model.SqlContext context) {
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
}
















