package com.footstone.sqlguard.validator.pagination.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig;
import com.footstone.sqlguard.validator.rule.impl.NoPaginationConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for NoPaginationChecker.
 *
 * <p>Verifies that NoPaginationChecker correctly detects SELECT queries completely lacking
 * pagination limits (no LIMIT, no RowBounds, no IPage), with risk stratification
 * (CRITICAL for no WHERE, HIGH for blacklist-only WHERE, MEDIUM for others), whitelist
 * exemptions for known-safe queries, and unique key detection for single-row queries.</p>
 */
class NoPaginationCheckerTest {

  private JSqlParserFacade parser;
  private PaginationPluginDetector detectorWithoutPlugin;
  private PaginationPluginDetector detectorWithPageHelper;
  private NoPaginationChecker checker;
  private NoPaginationConfig config;
  private BlacklistFieldsConfig blacklistConfig;

  @BeforeEach
  void setUp() {
    parser = new JSqlParserFacade();
    detectorWithoutPlugin = new PaginationPluginDetector(null, null);
    
    // Create mock PageHelper interceptor
    List<Interceptor> interceptors = new ArrayList<>();
    interceptors.add(createMockPageInterceptor());
    detectorWithPageHelper = new PaginationPluginDetector(interceptors, null);
    
    blacklistConfig = new BlacklistFieldsConfig();
    config = new NoPaginationConfig(); config.setEnabled(true); // Explicitly enable for tests
    checker = new NoPaginationChecker(detectorWithoutPlugin, blacklistConfig, config);
  }

  /**
   * Creates a mock PageInterceptor for testing.
   */
  private Interceptor createMockPageInterceptor() {
    return new Interceptor() {
      @Override
      public Object intercept(org.apache.ibatis.plugin.Invocation invocation) {
        return null;
      }

      @Override
      public Object plugin(Object target) {
        return null;
      }

      @Override
      public void setProperties(java.util.Properties properties) {
      }

      @Override
      public String toString() {
        return "com.github.pagehelper.PageInterceptor";
      }
    };
  }

  // ==================== Pagination Detection Tests (4 tests) ====================

  @Test
  void testSqlWithLimit_shouldNotViolate() throws Exception {
    String sql = "SELECT * FROM user LIMIT 10";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectWithLimit")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testRowBoundsWithPlugin_shouldNotViolate() throws Exception {
    String sql = "SELECT * FROM user";
    
    // Create checker with plugin detector
    NoPaginationChecker checkerWithPlugin = new NoPaginationChecker(
        detectorWithPageHelper, blacklistConfig, config);
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectWithRowBounds")
        .rowBounds(new RowBounds(0, 10))
        .build();
    ValidationResult result = ValidationResult.pass();

    checkerWithPlugin.check(context, result);

    // Should not violate because RowBounds + plugin = physical pagination
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
  }

  @Test
  void testIPageWithPlugin_shouldNotViolate() throws Exception {
    String sql = "SELECT * FROM user";
    
    // Create checker with plugin detector
    NoPaginationChecker checkerWithPlugin = new NoPaginationChecker(
        detectorWithPageHelper, blacklistConfig, config);
    
    // Mock IPage parameter
    Map<String, Object> params = new HashMap<>();
    params.put("page", createMockIPage());
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectWithIPage")
        .params(params)
        .build();
    ValidationResult result = ValidationResult.pass();

    checkerWithPlugin.check(context, result);

    // Should not violate because IPage + plugin = physical pagination
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
  }

  @Test
  void testNoPaginationAtAll_shouldViolate() throws Exception {
    String sql = "SELECT * FROM user WHERE deleted=0";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectNoPagination")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Should violate with HIGH risk (blacklist-only WHERE)
    assertFalse(result.isPassed());
  }

  // ==================== Risk Stratification Tests (5 tests) ====================

  @Test
  void testNoWhereNoPagination_shouldBeCritical() throws Exception {
    String sql = "SELECT * FROM user";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectAll")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    
    String message = result.getViolations().get(0).getMessage();
    assertTrue(message.contains("无条件") || message.contains("全表"));
  }

  @Test
  void testDummyWhereNoPagination_shouldBeCritical() throws Exception {
    String sql = "SELECT * FROM user WHERE 1=1";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectDummy")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }

  @Test
  void testBlacklistOnlyWhereNoPagination_shouldBeHigh() throws Exception {
    String sql = "SELECT * FROM user WHERE deleted=0";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectByDeleted")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertFalse(result.isPassed());
    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    
    String message = result.getViolations().get(0).getMessage();
    assertTrue(message.contains("黑名单字段") || message.contains("blacklist"));
  }

  @Test
  void testMixedWhereNoPaginationNotEnforced_shouldPass() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 AND status='active'";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectMixed")
        .build();
    ValidationResult result = ValidationResult.pass();

    // Config with enforceForAllQueries=false (default)
    NoPaginationConfig configNotEnforced = new NoPaginationConfig();
    NoPaginationChecker checkerNotEnforced = new NoPaginationChecker(
        detectorWithoutPlugin, blacklistConfig, configNotEnforced);

    checkerNotEnforced.check(context, result);

    // Should pass because has business field 'id' and enforceForAllQueries=false
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
  }

  @Test
  void testMixedWhereNoPaginationEnforced_shouldBeMedium() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 AND status='active'";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectMixed")
        .build();
    ValidationResult result = ValidationResult.pass();

    // Config with enforceForAllQueries=true
    NoPaginationConfig configEnforced = new NoPaginationConfig(
        true, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), true);
    NoPaginationChecker checkerEnforced = new NoPaginationChecker(
        detectorWithoutPlugin, blacklistConfig, configEnforced);

    checkerEnforced.check(context, result);

    // Should violate with MEDIUM risk
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }

  // ==================== Whitelist Exemption Tests (8 tests) ====================

  @Test
  void testMapperIdExactMatch_shouldPass() throws Exception {
    String sql = "SELECT * FROM user";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("UserMapper.getById")
        .build();
    ValidationResult result = ValidationResult.pass();

    // Config with exact mapperId whitelist
    NoPaginationConfig configWithWhitelist = new NoPaginationConfig(
        true, Arrays.asList("UserMapper.getById"), new ArrayList<>(), new ArrayList<>(), false);
    NoPaginationChecker checkerWithWhitelist = new NoPaginationChecker(
        detectorWithoutPlugin, blacklistConfig, configWithWhitelist);

    checkerWithWhitelist.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
  }

  @Test
  void testMapperIdWildcardMatch_shouldPass() throws Exception {
    String sql = "SELECT * FROM user";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("UserMapper.getById")
        .build();
    ValidationResult result = ValidationResult.pass();

    // Config with wildcard pattern
    NoPaginationConfig configWithWildcard = new NoPaginationConfig(
        true, Arrays.asList("*.getById"), new ArrayList<>(), new ArrayList<>(), false);
    NoPaginationChecker checkerWithWildcard = new NoPaginationChecker(
        detectorWithoutPlugin, blacklistConfig, configWithWildcard);

    checkerWithWildcard.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
  }

  @Test
  void testMapperIdPrefixWildcard_shouldPass() throws Exception {
    String sql = "SELECT * FROM user";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("UserMapper.countAll")
        .build();
    ValidationResult result = ValidationResult.pass();

    // Config with prefix wildcard
    NoPaginationConfig configWithPrefixWildcard = new NoPaginationConfig(
        true, Arrays.asList("*.count*"), new ArrayList<>(), new ArrayList<>(), false);
    NoPaginationChecker checkerWithPrefixWildcard = new NoPaginationChecker(
        detectorWithoutPlugin, blacklistConfig, configWithPrefixWildcard);

    checkerWithPrefixWildcard.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
  }

  @Test
  void testTableWhitelist_shouldPass() throws Exception {
    String sql = "SELECT * FROM config_table";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("ConfigMapper.selectAll")
        .build();
    ValidationResult result = ValidationResult.pass();

    // Config with table whitelist
    NoPaginationConfig configWithTableWhitelist = new NoPaginationConfig(
        true, new ArrayList<>(), Arrays.asList("config_table"), new ArrayList<>(), false);
    NoPaginationChecker checkerWithTableWhitelist = new NoPaginationChecker(
        detectorWithoutPlugin, blacklistConfig, configWithTableWhitelist);

    checkerWithTableWhitelist.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
  }

  @Test
  void testUniqueKeyEquals_shouldPass() throws Exception {
    String sql = "SELECT * FROM user WHERE id=?";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("UserMapper.selectById")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Should pass because id=? is unique key condition
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
  }

  @Test
  void testUniqueKeyEqualsConstant_shouldPass() throws Exception {
    String sql = "SELECT * FROM user WHERE id=123";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("UserMapper.selectById")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Should pass because id=123 is unique key condition
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
  }

  @Test
  void testCustomUniqueKey_shouldPass() throws Exception {
    String sql = "SELECT * FROM user WHERE user_id=?";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("UserMapper.selectByUserId")
        .build();
    ValidationResult result = ValidationResult.pass();

    // Config with custom unique key
    NoPaginationConfig configWithCustomKey = new NoPaginationConfig(
        true, new ArrayList<>(), new ArrayList<>(), Arrays.asList("user_id"), false);
    NoPaginationChecker checkerWithCustomKey = new NoPaginationChecker(
        detectorWithoutPlugin, blacklistConfig, configWithCustomKey);

    checkerWithCustomKey.check(context, result);

    // Should pass because user_id=? is configured as unique key
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
  }

  @Test
  void testNonUniqueKeyEquals_shouldViolate() throws Exception {
    String sql = "SELECT * FROM user WHERE status=?";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("UserMapper.selectByStatus")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Should violate because status is not a unique key (and is blacklisted)
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
  }

  // ==================== Blacklist Field Detection Tests (4 tests) ====================

  @Test
  void testSingleBlacklistField_shouldBeHigh() throws Exception {
    String sql = "SELECT * FROM user WHERE deleted=0";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("UserMapper.selectNotDeleted")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertFalse(result.isPassed());
    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
  }

  @Test
  void testMultipleBlacklistFields_shouldBeHigh() throws Exception {
    String sql = "SELECT * FROM user WHERE deleted=0 AND status='active'";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("UserMapper.selectActiveNotDeleted")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertFalse(result.isPassed());
    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
  }

  @Test
  void testBlacklistPlusBusinessField_shouldNotBeHigh() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 AND deleted=0";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("UserMapper.selectByIdNotDeleted")
        .build();
    ValidationResult result = ValidationResult.pass();

    // Config with enforceForAllQueries=false
    NoPaginationConfig configNotEnforced = new NoPaginationConfig();
    NoPaginationChecker checkerNotEnforced = new NoPaginationChecker(
        detectorWithoutPlugin, blacklistConfig, configNotEnforced);

    checkerNotEnforced.check(context, result);

    // Should pass because has business field 'id'
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
  }

  @Test
  void testBlacklistPlusBusinessFieldEnforced_shouldBeMedium() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 AND deleted=0";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("UserMapper.selectByIdNotDeleted")
        .build();
    ValidationResult result = ValidationResult.pass();

    // Config with enforceForAllQueries=true
    NoPaginationConfig configEnforced = new NoPaginationConfig(
        true, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), true);
    NoPaginationChecker checkerEnforced = new NoPaginationChecker(
        detectorWithoutPlugin, blacklistConfig, configEnforced);

    checkerEnforced.check(context, result);

    // Should violate with MEDIUM risk (not HIGH)
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
  }

  // ==================== Configuration Tests (2 tests) ====================

  @Test
  void testDisabledChecker_shouldSkip() throws Exception {
    String sql = "SELECT * FROM user";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("UserMapper.selectAll")
        .build();
    ValidationResult result = ValidationResult.pass();

    // Config with enabled=false
    NoPaginationConfig configDisabled = new NoPaginationConfig(
        false, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), false);
    NoPaginationChecker checkerDisabled = new NoPaginationChecker(
        detectorWithoutPlugin, blacklistConfig, configDisabled);

    checkerDisabled.check(context, result);

    // Should skip check
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
  }

  @Test
  void testEnforceForAllQueries_shouldAddMediumViolations() throws Exception {
    String sql = "SELECT * FROM user WHERE create_time > ?";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("UserMapper.selectByCreateTime")
        .build();
    ValidationResult result = ValidationResult.pass();

    // Config with enforceForAllQueries=true
    NoPaginationConfig configEnforced = new NoPaginationConfig(
        true, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), true);
    NoPaginationChecker checkerEnforced = new NoPaginationChecker(
        detectorWithoutPlugin, blacklistConfig, configEnforced);

    checkerEnforced.check(context, result);

    // Should violate with MEDIUM risk
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
  }

  // ==================== Edge Case Tests (2 tests) ====================

  @Test
  void testNullRowBounds_shouldNotError() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("UserMapper.selectWithNullRowBounds")
        .rowBounds(null)
        .build();
    ValidationResult result = ValidationResult.pass();

    // Should not throw NPE
    checker.check(context, result);

    // Should pass (has WHERE clause, not enforced)
    assertTrue(result.isPassed());
  }

  @Test
  void testRowBoundsDefault_shouldTreatAsNoPagination() throws Exception {
    String sql = "SELECT * FROM user";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("UserMapper.selectWithDefaultRowBounds")
        .rowBounds(RowBounds.DEFAULT)
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Should treat RowBounds.DEFAULT as no pagination and violate
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
  }

  // ==================== Integration Tests (2 tests) ====================

  @Test
  void testInteractionWithLogicalPaginationChecker() throws Exception {
    String sql = "SELECT * FROM user";
    
    // RowBounds without plugin should trigger LogicalPaginationChecker
    // Should NOT trigger NoPaginationChecker
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("UserMapper.selectWithRowBounds")
        .rowBounds(new RowBounds(0, 10))
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Should not violate because RowBounds is present (even without plugin)
    // NoPaginationChecker should not trigger
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
  }

  @Test
  void testInteractionWithPhysicalPaginationCheckers() throws Exception {
    String sql = "SELECT * FROM user LIMIT 10 OFFSET 1000";
    
    // LIMIT clause should prevent NoPaginationChecker
    // Physical pagination checkers handle LIMIT queries
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("UserMapper.selectWithLimit")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Should not violate because LIMIT is present
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
  }

  // ==================== Multi-Dialect Pagination Detection Tests ====================

  /**
   * Test SQL Server TOP syntax should be recognized as pagination.
   * 
   * <p>SQL Server uses TOP to limit rows: {@code SELECT TOP 100 * FROM user}</p>
   * <p>Expected: Should recognize as paginated and NOT trigger violation</p>
   */
  @Test
  void testSQLServerTop_shouldRecognizeAsPaginated() throws Exception {
    String sql = "SELECT TOP 100 * FROM user";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectSQLServerTop")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed(), "Should recognize TOP as pagination and pass");
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  /**
   * Test DB2 FETCH FIRST syntax should be recognized as pagination.
   * 
   * <p>DB2 uses FETCH FIRST: {@code SELECT * FROM user FETCH FIRST 100 ROWS ONLY}</p>
   * <p>Expected: Should recognize as paginated and NOT trigger violation</p>
   */
  @Test
  void testDB2FetchFirst_shouldRecognizeAsPaginated() throws Exception {
    String sql = "SELECT * FROM user FETCH FIRST 100 ROWS ONLY";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectDB2FetchFirst")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed(), "Should recognize FETCH FIRST as pagination and pass");
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  /**
   * Test SQL Server OFFSET ROWS syntax should be recognized as pagination.
   * 
   * <p>SQL Server 2012+ uses OFFSET ROWS: 
   * {@code SELECT * FROM user ORDER BY id OFFSET 10 ROWS FETCH NEXT 100 ROWS ONLY}</p>
   * <p>Expected: Should recognize as paginated and NOT trigger violation</p>
   */
  @Test
  void testSQLServerOffsetRows_shouldRecognizeAsPaginated() throws Exception {
    String sql = "SELECT * FROM user ORDER BY id OFFSET 10 ROWS FETCH NEXT 100 ROWS ONLY";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectSQLServerOffsetRows")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed(), "Should recognize OFFSET ROWS as pagination and pass");
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  // ==================== Helper Methods ====================

  /**
   * Creates a mock IPage object for testing.
   * The class name must contain "IPage" for detection to work.
   */
  private Object createMockIPage() {
    // Create a class that implements a mock IPage interface
    class MockIPage {
      // Empty implementation - we only need the class name to contain "IPage"
    }
    return new MockIPage();
  }
}

