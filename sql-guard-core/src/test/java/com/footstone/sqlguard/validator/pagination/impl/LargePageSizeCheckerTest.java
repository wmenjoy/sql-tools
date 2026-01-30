package com.footstone.sqlguard.validator.pagination.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.rule.impl.PaginationAbuseConfig;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for LargePageSizeChecker.
 *
 * <p>This test class validates the LargePageSizeChecker implementation with 15+ test scenarios
 * covering threshold boundaries, LIMIT syntax variations, pagination type filtering, configuration
 * handling, and edge cases.</p>
 */
class LargePageSizeCheckerTest {

  private JSqlParserFacade parser;
  private PaginationPluginDetector detector;
  private LargePageSizeChecker checker;
  private PaginationAbuseConfig config;

  @BeforeEach
  void setUp() {
    parser = new JSqlParserFacade();
    detector = new PaginationPluginDetector(null, null);
    config = new PaginationAbuseConfig(); config.setEnabled(true); // Enabled for tests - Default: maxPageSize=1000
    checker = new LargePageSizeChecker(detector, config);
  }

  // ==================== Threshold Boundary Tests ====================

  @Test
  void testPageSizeBelowThreshold_shouldPass() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 999";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectBelowThreshold")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testPageSizeAboveThreshold_shouldViolate() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 1001";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectAboveThreshold")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertFalse(result.isPassed());
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    
    String message = result.getViolations().get(0).getMessage();
    assertTrue(message.contains("pageSize=1001"));
  }

  @Test
  void testPageSizeEqualsThreshold_shouldPass() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 1000";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectEqualsThreshold")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testPageSizeMaxMinusOne_shouldPass() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 999";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectMaxMinusOne")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testPageSizeMaxPlusOne_shouldViolate() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 1001";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectMaxPlusOne")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertFalse(result.isPassed());
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }

  // ==================== LIMIT Syntax Tests ====================

  @Test
  void testLimitOnlySyntax_shouldExtractCorrectly() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 500";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectLimitOnly")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testLimitCommaSyntax_shouldExtractRowCount() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 100,500";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectMySQLSyntax")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testLimitOffsetKeywordSyntax_shouldExtractRowCount() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 500 OFFSET 100";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectLimitOffset")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testUnionWithLimit_shouldDetectLargePageSize() throws Exception {
    String sql = "SELECT id FROM user WHERE id > 0 UNION SELECT id FROM user_bak LIMIT 5000";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectUnionLimit")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertFalse(result.isPassed());
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    assertTrue(result.getViolations().get(0).getMessage().contains("pageSize=5000"));
  }

  // ==================== Pagination Type Tests ====================

  @Test
  void testLogicalPagination_shouldSkip() throws Exception {
    String sql = "SELECT * FROM user";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectWithRowBounds")
        .rowBounds(new RowBounds(0, 10))
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testNoPagination_shouldSkip() throws Exception {
    String sql = "SELECT * FROM user WHERE id = ?";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectById")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  // ==================== Configuration Tests ====================

  @Test
  void testCustomMaxPageSize_shouldRespect() throws Exception {
    PaginationAbuseConfig customConfig = new PaginationAbuseConfig(true, 10000, 500);
    LargePageSizeChecker customChecker = new LargePageSizeChecker(detector, customConfig);

    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 600";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectCustomThreshold")
        .build();
    ValidationResult result = ValidationResult.pass();

    customChecker.check(context, result);

    assertFalse(result.isPassed());
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    
    String message = result.getViolations().get(0).getMessage();
    assertTrue(message.contains("pageSize=600"));
  }

  @Test
  void testDisabledChecker_shouldSkip() throws Exception {
    PaginationAbuseConfig disabledConfig = new PaginationAbuseConfig(false, 10000, 1000);
    LargePageSizeChecker disabledChecker = new LargePageSizeChecker(detector, disabledConfig);

    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 5000";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectLargePageSize")
        .build();
    ValidationResult result = ValidationResult.pass();

    assertFalse(disabledChecker.isEnabled());

    disabledChecker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  // ==================== Violation Message Tests ====================

  @Test
  void testViolationMessage_shouldContainPageSizeValue() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 5000";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectPageSize5000")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertFalse(result.isPassed());
    assertEquals(1, result.getViolations().size());
    
    String message = result.getViolations().get(0).getMessage();
    assertTrue(message.contains("pageSize=5000"));
    assertTrue(message.contains("单次查询数据量过多"));
  }

  @Test
  void testViolationSuggestion_shouldMentionThreshold() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 2000";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectLargePageSize")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertFalse(result.isPassed());
    assertEquals(1, result.getViolations().size());
    
    String suggestion = result.getViolations().get(0).getSuggestion();
    assertTrue(suggestion.contains("降低pageSize到1000以内"));
  }

  // ==================== Edge Case Tests ====================

  @Test
  void testVeryLargePageSize_shouldViolate() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 100000";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectVeryLarge")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertFalse(result.isPassed());
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    
    String message = result.getViolations().get(0).getMessage();
    assertTrue(message.contains("pageSize=100000"));
  }

  // ==================== Multi-Dialect Tests (SQL Server, DB2, Oracle) ====================

  /**
   * Test SQL Server TOP syntax with large page size.
   * 
   * <p>SQL Server uses TOP to limit rows: {@code SELECT TOP 5000 * FROM user}</p>
   * <p>Expected: Should detect pageSize=5000 > maxPageSize(1000) and trigger MEDIUM violation</p>
   */
  @Test
  void testSQLServerTop_LargePageSize_shouldViolate() throws Exception {
    String sql = "SELECT TOP 5000 * FROM user WHERE status = 'active'";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectSQLServerTop")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertFalse(result.isPassed(), "Should detect large pageSize in TOP clause");
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    
    String message = result.getViolations().get(0).getMessage();
    assertTrue(message.contains("pageSize=5000"), "Message should contain 'pageSize=5000'");
  }

  /**
   * Test SQL Server TOP syntax with small page size (within threshold).
   * 
   * <p>Expected: Should pass since pageSize=500 < maxPageSize(1000)</p>
   */
  @Test
  void testSQLServerTop_SmallPageSize_shouldPass() throws Exception {
    String sql = "SELECT TOP 500 * FROM user WHERE status = 'active'";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectSQLServerTopSmall")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed(), "Should pass when TOP pageSize is within threshold");
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  /**
   * Test DB2/Oracle 12c+ FETCH FIRST syntax with large page size.
   * 
   * <p>DB2/Oracle uses FETCH FIRST to limit rows: 
   * {@code SELECT * FROM user FETCH FIRST 5000 ROWS ONLY}</p>
   * <p>Expected: Should detect pageSize=5000 > maxPageSize(1000) and trigger MEDIUM violation</p>
   */
  @Test
  void testDB2FetchFirst_LargePageSize_shouldViolate() throws Exception {
    String sql = "SELECT * FROM user WHERE status = 'active' FETCH FIRST 5000 ROWS ONLY";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectDB2FetchFirst")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertFalse(result.isPassed(), "Should detect large pageSize in FETCH FIRST clause");
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    
    String message = result.getViolations().get(0).getMessage();
    assertTrue(message.contains("pageSize=5000"), "Message should contain 'pageSize=5000'");
  }

  /**
   * Test DB2 FETCH FIRST syntax with small page size (within threshold).
   * 
   * <p>Expected: Should pass since pageSize=500 < maxPageSize(1000)</p>
   */
  @Test
  void testDB2FetchFirst_SmallPageSize_shouldPass() throws Exception {
    String sql = "SELECT * FROM user WHERE status = 'active' FETCH FIRST 500 ROWS ONLY";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectDB2FetchFirstSmall")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed(), "Should pass when FETCH FIRST pageSize is within threshold");
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }
}
