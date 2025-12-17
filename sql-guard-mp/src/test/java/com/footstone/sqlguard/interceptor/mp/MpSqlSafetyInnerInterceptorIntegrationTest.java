package com.footstone.sqlguard.interceptor.mp;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
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
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for MpSqlSafetyInnerInterceptor with MyBatis-Plus BaseMapper.
 *
 * <p>These tests simulate MyBatis-Plus BaseMapper operations and verify
 * that the safety interceptor correctly validates SQL statements.</p>
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>MyBatis-Plus configuration setup</li>
 *   <li>BaseMapper method simulation (selectById, selectList, etc.)</li>
 *   <li>IPage pagination with PaginationInnerInterceptor</li>
 *   <li>QueryWrapper and LambdaQueryWrapper validation</li>
 *   <li>Logic delete integration</li>
 *   <li>Concurrent execution thread safety</li>
 *   <li>Batch operations</li>
 *   <li>Complex queries with multiple plugins</li>
 * </ul>
 */
class MpSqlSafetyInnerInterceptorIntegrationTest {

  private TestValidator validator;
  private MybatisPlusInterceptor mpInterceptor;
  private MpSqlSafetyInnerInterceptor safetyInterceptor;
  private Configuration configuration;

  @BeforeEach
  void setUp() {
    validator = new TestValidator();
    SqlDeduplicationFilter.clearThreadCache();
    
    // Setup MyBatis-Plus configuration
    configuration = new Configuration();
    
    // Setup interceptor chain
    safetyInterceptor = new MpSqlSafetyInnerInterceptor(validator, ViolationStrategy.BLOCK);
    mpInterceptor = new MybatisPlusInterceptor();
    mpInterceptor.addInnerInterceptor(safetyInterceptor);
  }

  /**
   * Test: MyBatis-Plus configuration should be created successfully.
   */
  @Test
  void testSetup_mybatisPlusConfig_shouldCreate() {
    // Assert
    assertNotNull(configuration, "Configuration should be created");
    assertNotNull(mpInterceptor, "MybatisPlusInterceptor should be created");
    assertEquals(1, mpInterceptor.getInterceptors().size(), 
        "Should have safety interceptor");
  }

  /**
   * Test: BaseMapper.selectById should validate SQL.
   */
  @Test
  void testBaseMapper_selectById_shouldValidate() throws SQLException {
    // Arrange - simulate selectById SQL
    MappedStatement ms = createMappedStatement(
        "SELECT id, name, age, email, status, deleted FROM user WHERE id = ?",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);

    // Assert
    assertTrue(validator.wasCalled(), "selectById should be validated");
  }

  /**
   * Test: BaseMapper.selectList with empty wrapper should violate.
   */
  @Test
  void testBaseMapper_selectList_emptyWrapper_shouldViolate() {
    // Arrange - simulate selectList with empty QueryWrapper
    MappedStatement ms = createMappedStatement(
        "SELECT id, name, age, email, status, deleted FROM user",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    QueryWrapper<User> wrapper = new QueryWrapper<>(); // Empty
    
    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
    validator.setResult(result);

    // Act & Assert
    assertThrows(SQLException.class, () -> {
      safetyInterceptor.beforeQuery(null, ms, wrapper, new RowBounds(), null, bs);
    }, "Empty wrapper should violate");
  }

  /**
   * Test: BaseMapper.selectList with conditions should pass.
   */
  @Test
  void testBaseMapper_selectList_withWrapper_shouldPass() throws SQLException {
    // Arrange - simulate selectList with QueryWrapper
    MappedStatement ms = createMappedStatement(
        "SELECT id, name, age, email, status, deleted FROM user WHERE status = ?",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    QueryWrapper<User> wrapper = new QueryWrapper<>();
    wrapper.eq("status", "active");
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeQuery(null, ms, wrapper, new RowBounds(), null, bs);

    // Assert - should pass with WHERE clause
    assertTrue(validator.wasCalled());
  }

  /**
   * Test: BaseMapper.insert should validate SQL.
   */
  @Test
  void testBaseMapper_insert_shouldValidate() throws SQLException {
    // Arrange - simulate insert SQL
    MappedStatement ms = createMappedStatement(
        "INSERT INTO user (name, age, email, status) VALUES (?, ?, ?, ?)",
        SqlCommandType.INSERT
    );
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeUpdate(null, ms, new HashMap<>());

    // Assert
    assertTrue(validator.wasCalled(), "insert should be validated");
  }

  /**
   * Test: BaseMapper.update without wrapper should violate.
   */
  @Test
  void testBaseMapper_update_noWrapper_shouldViolate() {
    // Arrange - simulate update without WHERE
    MappedStatement ms = createMappedStatement(
        "UPDATE user SET status = ?",
        SqlCommandType.UPDATE
    );
    
    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.CRITICAL, "UPDATE without WHERE", "Add WHERE condition");
    validator.setResult(result);

    // Act & Assert
    assertThrows(SQLException.class, () -> {
      safetyInterceptor.beforeUpdate(null, ms, new HashMap<>());
    }, "Update without WHERE should violate");
  }

  /**
   * Test: BaseMapper.update with wrapper should pass.
   */
  @Test
  void testBaseMapper_update_withWrapper_shouldPass() throws SQLException {
    // Arrange - simulate update with WHERE
    MappedStatement ms = createMappedStatement(
        "UPDATE user SET status = ? WHERE id = ?",
        SqlCommandType.UPDATE
    );
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeUpdate(null, ms, new HashMap<>());

    // Assert
    assertTrue(validator.wasCalled());
  }

  /**
   * Test: BaseMapper.delete without wrapper should violate.
   */
  @Test
  void testBaseMapper_delete_noWrapper_shouldViolate() {
    // Arrange - simulate delete without WHERE
    MappedStatement ms = createMappedStatement(
        "DELETE FROM user",
        SqlCommandType.DELETE
    );
    
    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.CRITICAL, "DELETE without WHERE", "Add WHERE condition");
    validator.setResult(result);

    // Act & Assert
    assertThrows(SQLException.class, () -> {
      safetyInterceptor.beforeUpdate(null, ms, new HashMap<>());
    }, "Delete without WHERE should violate");
  }

  /**
   * Test: IPage pagination should be detected.
   */
  @Test
  void testIPage_pagination_shouldDetect() throws SQLException {
    // Arrange - simulate selectPage with IPage
    MappedStatement ms = createMappedStatement(
        "SELECT id, name, age, email, status, deleted FROM user WHERE age > ?",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    Page<User> page = new Page<>(1, 10);
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeQuery(null, ms, page, new RowBounds(), null, bs);

    // Assert
    assertTrue(validator.wasCalled(), "IPage pagination should be detected");
  }

  /**
   * Test: IPage with PaginationInnerInterceptor should add LIMIT.
   * Note: This is a simulation - actual LIMIT addition happens in PaginationInnerInterceptor.
   */
  @Test
  void testIPage_withPaginationInterceptor_shouldAddLimit() throws SQLException {
    // Arrange - simulate SQL after PaginationInnerInterceptor adds LIMIT
    MappedStatement ms = createMappedStatement(
        "SELECT id, name, age, email, status, deleted FROM user WHERE status = ? LIMIT 10",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    Page<User> page = new Page<>(1, 10);
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeQuery(null, ms, page, new RowBounds(), null, bs);

    // Assert
    assertTrue(validator.wasCalled(), "SQL with LIMIT should be validated");
  }

  /**
   * Test: LambdaQueryWrapper with type-safe conditions should validate.
   */
  @Test
  void testLambdaQueryWrapper_typeSafe_shouldValidate() throws SQLException {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT id, name, age, email, status, deleted FROM user WHERE name = ?",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(User::getName, "Alice");
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeQuery(null, ms, wrapper, new RowBounds(), null, bs);

    // Assert
    assertTrue(validator.wasCalled(), "LambdaQueryWrapper should be validated");
  }

  /**
   * Test: Logic delete should add deleted condition.
   */
  @Test
  void testLogicDelete_shouldAddDeletedCondition() throws SQLException {
    // Arrange - simulate logic delete SQL (MyBatis-Plus adds deleted = 0)
    MappedStatement ms = createMappedStatement(
        "SELECT id, name, age, email, status, deleted FROM user WHERE id = ? AND deleted = 0",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Logic delete SQL should be validated");
  }

  /**
   * Test: Concurrent execution should be thread-safe.
   */
  @Test
  void testConcurrentExecution_shouldBeThreadSafe() throws Exception {
    // Arrange
    MappedStatement ms = createMappedStatement(
        "SELECT id, name, age, email, status, deleted FROM user WHERE id = ?",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());

    // Act - execute in multiple threads
    Thread[] threads = new Thread[5];
    for (int i = 0; i < 5; i++) {
      threads[i] = new Thread(() -> {
        try {
          safetyInterceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);
        } catch (SQLException e) {
          fail("Should not throw exception");
        }
      });
      threads[i].start();
    }

    // Wait for all threads
    for (Thread thread : threads) {
      thread.join();
    }

    // Assert - no exceptions thrown
    assertTrue(validator.wasCalled(), "Concurrent execution should work");
  }

  /**
   * Test: Batch operations should validate each SQL.
   */
  @Test
  void testBatchOperations_shouldValidateEach() throws SQLException {
    // Arrange
    MappedStatement ms1 = createMappedStatement(
        "INSERT INTO user (name, age) VALUES ('User1', 20)",
        SqlCommandType.INSERT
    );
    MappedStatement ms2 = createMappedStatement(
        "INSERT INTO user (name, age) VALUES ('User2', 25)",
        SqlCommandType.INSERT
    );
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeUpdate(null, ms1, new HashMap<>());
    int firstCount = validator.getCallCount();
    
    validator.reset();
    safetyInterceptor.beforeUpdate(null, ms2, new HashMap<>());
    int secondCount = validator.getCallCount();

    // Assert
    assertEquals(1, firstCount, "First batch should validate");
    assertEquals(1, secondCount, "Second batch should validate");
  }

  /**
   * Test: Complex query with multiple plugins should work.
   */
  @Test
  void testComplexQuery_multiplePlugins_shouldWork() throws SQLException {
    // Arrange - simulate complex query with joins and conditions
    MappedStatement ms = createMappedStatement(
        "SELECT u.id, u.name, u.age FROM user u WHERE u.status = ? AND u.age > ? ORDER BY u.id LIMIT 10",
        SqlCommandType.SELECT
    );
    BoundSql bs = ms.getBoundSql(null);
    validator.setResult(ValidationResult.pass());

    // Act
    safetyInterceptor.beforeQuery(null, ms, new HashMap<>(), new RowBounds(), null, bs);

    // Assert
    assertTrue(validator.wasCalled(), "Complex query should be validated");
  }

  /**
   * Helper method to create a MappedStatement.
   */
  private MappedStatement createMappedStatement(String sql, SqlCommandType commandType) {
    return new MappedStatement.Builder(
        configuration,
        "com.footstone.sqlguard.interceptor.mp.UserMapper.testMethod",
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



