package com.footstone.sqlguard.interceptor.mp;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.footstone.sqlguard.validator.SqlDeduplicationFilter;
import com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * Tests for MpSqlSafetyInnerInterceptor compatibility with MyBatis-Plus ecosystem plugins.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>OptimisticLockerInnerInterceptor compatibility</li>
 *   <li>TenantLineInnerInterceptor compatibility (simulated)</li>
 *   <li>BlockAttackInnerInterceptor compatibility</li>
 *   <li>Multiple plugins execution order</li>
 *   <li>Batch operations validation</li>
 *   <li>Logic delete compatibility</li>
 *   <li>Dynamic table name compatibility</li>
 * </ul>
 */
class MpPluginCompatibilityTest {

  private TestValidator validator;
  private MybatisPlusInterceptor mpInterceptor;
  private MpSqlSafetyInnerInterceptor safetyInterceptor;

  @BeforeEach
  void setUp() {
    validator = new TestValidator();
    SqlDeduplicationFilter.clearThreadCache();
    safetyInterceptor = new MpSqlSafetyInnerInterceptor(validator, ViolationStrategy.BLOCK);
    mpInterceptor = new MybatisPlusInterceptor();
  }

  /**
   * Test: OptimisticLockerInnerInterceptor should coexist with safety interceptor.
   */
  @Test
  void testOptimisticLock_withSafety_shouldCoexist() {
    // Arrange
    mpInterceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    mpInterceptor.addInnerInterceptor(safetyInterceptor);

    // Assert
    assertEquals(2, mpInterceptor.getInterceptors().size());
    assertTrue(mpInterceptor.getInterceptors().get(0) instanceof OptimisticLockerInnerInterceptor);
    assertTrue(mpInterceptor.getInterceptors().get(1) instanceof MpSqlSafetyInnerInterceptor);
  }

  /**
   * Test: Optimistic lock version field should not interfere with validation.
   */
  @Test
  void testOptimisticLock_versionField_shouldNotInterfere() throws SQLException {
    // Arrange
    mpInterceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    mpInterceptor.addInnerInterceptor(safetyInterceptor);
    
    MappedStatement ms = createMappedStatement(
        "UPDATE user SET name = ? WHERE id = ? AND version = ?",
        SqlCommandType.UPDATE
    );
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeUpdate(null, ms, new HashMap<>());

    // Assert - version field should not cause validation issues
    assertTrue(validator.wasCalled());
  }

  /**
   * Test: TenantLineInnerInterceptor should coexist with safety interceptor.
   * Note: TenantLineInnerInterceptor requires handler configuration, so we simulate it.
   */
  @Test
  void testTenantLine_withSafety_shouldCoexist() {
    // Arrange
    // Simulated tenant interceptor (actual TenantLineInnerInterceptor requires handler)
    mpInterceptor.addInnerInterceptor(new PaginationInnerInterceptor()); // Use pagination as proxy
    mpInterceptor.addInnerInterceptor(safetyInterceptor);

    // Assert
    assertEquals(2, mpInterceptor.getInterceptors().size());
  }

  /**
   * Test: Tenant ID field should not interfere with validation.
   */
  @Test
  void testTenantLine_tenantId_shouldNotInterfere() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user WHERE tenant_id = ? AND status = ?",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);

    // Assert - tenant_id should not cause validation issues
    assertTrue(validator.wasCalled());
  }

  /**
   * Test: Illegal SQL should be caught by safety interceptor even with other plugins.
   */
  @Test
  void testIllegalSQL_withSafety_bothPlugins_shouldWork() {
    // Arrange
    mpInterceptor.addInnerInterceptor(new PaginationInnerInterceptor());
    mpInterceptor.addInnerInterceptor(safetyInterceptor);
    
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user", // No WHERE clause
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    
    ValidationResult result = ValidationResult.pass();
    result.addViolation(
        com.footstone.sqlguard.core.model.RiskLevel.HIGH,
        "Missing WHERE clause",
        "Add WHERE condition"
    );
    validator.setResult(result);

    // Act & Assert
    assertThrows(SQLException.class, () -> {
      safetyInterceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);
    });
  }

  /**
   * Test: Multiple plugins should execute in correct order.
   */
  @Test
  void testMultiplePlugins_executionOrder_correct() {
    // Arrange
    List<InnerInterceptor> interceptors = new ArrayList<>();
    interceptors.add(new PaginationInnerInterceptor());
    interceptors.add(new OptimisticLockerInnerInterceptor());
    interceptors.add(safetyInterceptor);
    
    for (InnerInterceptor interceptor : interceptors) {
      mpInterceptor.addInnerInterceptor(interceptor);
    }

    // Assert
    assertEquals(3, mpInterceptor.getInterceptors().size());
    assertEquals(PaginationInnerInterceptor.class, 
        mpInterceptor.getInterceptors().get(0).getClass());
    assertEquals(OptimisticLockerInnerInterceptor.class, 
        mpInterceptor.getInterceptors().get(1).getClass());
    assertEquals(MpSqlSafetyInnerInterceptor.class, 
        mpInterceptor.getInterceptors().get(2).getClass());
  }

  /**
   * Test: Batch operations should validate each SQL.
   */
  @Test
  void testMyBatisPlusBatchOperations_shouldValidate() throws SQLException {
    // Arrange - use different SQL to avoid deduplication
    MappedStatement ms1 = createMappedStatement(
        "INSERT INTO user (name, email) VALUES ('Alice', 'alice@example.com')",
        SqlCommandType.INSERT
    );
    MappedStatement ms2 = createMappedStatement(
        "INSERT INTO user (name, email) VALUES ('Bob', 'bob@example.com')",
        SqlCommandType.INSERT
    );
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeUpdate(null, ms1, new HashMap<>());
    int firstCount = validator.getCallCount();
    
    validator.reset();
    safetyInterceptor.beforeUpdate(null, ms2, new HashMap<>());
    int secondCount = validator.getCallCount();

    // Assert - both operations should be validated (different SQL)
    assertEquals(1, firstCount, "First batch operation should validate");
    assertEquals(1, secondCount, "Second batch operation should validate");
  }

  /**
   * Test: Logic delete should coexist with safety interceptor.
   */
  @Test
  void testLogicDelete_withSafety_shouldCoexist() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "UPDATE user SET deleted = 1 WHERE id = ?",
        SqlCommandType.UPDATE
    );
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeUpdate(null, ms, new HashMap<>());

    // Assert - logic delete should work with safety validation
    assertTrue(validator.wasCalled());
  }

  /**
   * Test: Dynamic table name should be validated correctly.
   */
  @Test
  void testDynamicTableName_withSafety_shouldValidate() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT * FROM user_2024 WHERE status = ?",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);

    // Assert - dynamic table name should not affect validation
    assertTrue(validator.wasCalled());
  }

  /**
   * Test: BlockAttackInnerInterceptor should work with safety interceptor.
   */
  @Test
  void testBlockAttackInnerInterceptor_withSafety_bothWork() {
    // Arrange
    mpInterceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
    mpInterceptor.addInnerInterceptor(safetyInterceptor);

    // Assert
    assertEquals(2, mpInterceptor.getInterceptors().size());
    assertTrue(mpInterceptor.getInterceptors().get(0) instanceof BlockAttackInnerInterceptor);
    assertTrue(mpInterceptor.getInterceptors().get(1) instanceof MpSqlSafetyInnerInterceptor);
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
   * Test validator.
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














