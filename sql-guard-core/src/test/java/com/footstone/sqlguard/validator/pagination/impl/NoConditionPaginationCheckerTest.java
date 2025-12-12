package com.footstone.sqlguard.validator.pagination.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.pagination.PaginationType;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for NoConditionPaginationChecker.
 *
 * <p>Verifies that NoConditionPaginationChecker correctly detects unconditioned LIMIT queries
 * (physical pagination without WHERE clause) which still perform full table scans despite
 * pagination. This is the highest-priority physical pagination checker with CRITICAL risk level.</p>
 *
 * <p><strong>Test Coverage:</strong></p>
 * <ul>
 *   <li>Basic no-condition tests (no WHERE, dummy WHERE)</li>
 *   <li>Valid WHERE clause tests (simple, complex)</li>
 *   <li>Pagination type tests (LOGICAL, PHYSICAL, NONE)</li>
 *   <li>LIMIT details extraction tests (offset, rowCount, MySQL syntax)</li>
 *   <li>Early-return flag tests</li>
 *   <li>Configuration tests (enabled/disabled)</li>
 *   <li>Dummy condition tests (WHERE true, WHERE 'a'='a')</li>
 * </ul>
 */
class NoConditionPaginationCheckerTest {

  private JSqlParserFacade parser;
  private PaginationPluginDetector detector;
  private NoConditionPaginationChecker checker;
  private NoConditionPaginationConfig config;

  @BeforeEach
  void setUp() {
    parser = new JSqlParserFacade();
    // Create detector without pagination plugin (to detect PHYSICAL pagination via LIMIT only)
    detector = new PaginationPluginDetector(null, null);
    config = new NoConditionPaginationConfig();
    checker = new NoConditionPaginationChecker(config, detector);
  }

  // ==================== Basic No-Condition Tests ====================

  @Test
  void testPhysicalPaginationNoWhere_shouldViolate() throws Exception {
    // SQL with LIMIT but no WHERE - still scans entire table
    String sql = "SELECT * FROM user LIMIT 10";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectWithLimit")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify CRITICAL violation
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    
    // Verify violation message
    String message = result.getViolations().get(0).getMessage();
    assertTrue(message.contains("无条件"), "Message should contain '无条件'");
    assertTrue(message.contains("全表扫描"), "Message should contain '全表扫描'");
    
    // Verify suggestion
    String suggestion = result.getViolations().get(0).getSuggestion();
    assertTrue(suggestion.contains("WHERE"), "Suggestion should mention WHERE");
  }

  @Test
  void testPhysicalPaginationDummyWhere_shouldViolate() throws Exception {
    // SQL with LIMIT and dummy WHERE (1=1) - equivalent to no condition
    String sql = "SELECT * FROM user WHERE 1=1 LIMIT 10";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectWithDummyWhere")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify CRITICAL violation (dummy condition equivalent to no condition)
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }

  @Test
  void testPhysicalPaginationValidWhere_shouldPass() throws Exception {
    // SQL with LIMIT and proper WHERE condition
    String sql = "SELECT * FROM user WHERE id > ? LIMIT 10";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectWithValidWhere")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify no violation
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testPhysicalPaginationComplexValidWhere_shouldPass() throws Exception {
    // SQL with LIMIT and complex WHERE condition
    String sql = "SELECT * FROM user WHERE status='active' AND create_time > ? LIMIT 20";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectActiveUsers")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify no violation
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  // ==================== Pagination Type Tests ====================

  @Test
  void testLogicalPagination_shouldSkip() throws Exception {
    // SqlContext with RowBounds without plugin (LOGICAL type)
    // This should be handled by LogicalPaginationChecker, not this checker
    String sql = "SELECT * FROM user";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectWithRowBounds")
        .rowBounds(new RowBounds(0, 10))
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify no violation from this checker (LOGICAL pagination, not PHYSICAL)
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testNoPagination_shouldSkip() throws Exception {
    // Plain query without LIMIT or RowBounds (NONE type)
    String sql = "SELECT * FROM user WHERE id = ?";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectById")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify no violation from this checker (no pagination detected)
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  // ==================== LIMIT Details Extraction Tests ====================

  @Test
  void testLimitWithOffset_shouldIncludeDetailsInViolation() throws Exception {
    // SQL with LIMIT and OFFSET
    String sql = "SELECT * FROM user LIMIT 10 OFFSET 100";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectWithOffset")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify violation
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    
    // Verify LIMIT details in violation details
    assertNotNull(result.getDetails().get("limit"), "Details should contain limit");
    assertNotNull(result.getDetails().get("offset"), "Details should contain offset");
  }

  @Test
  void testLimitWithoutOffset_shouldIncludeDetailsInViolation() throws Exception {
    // SQL with LIMIT only (no OFFSET)
    String sql = "SELECT * FROM user LIMIT 50";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectFirst50")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify violation
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    
    // Verify LIMIT details
    assertNotNull(result.getDetails().get("limit"), "Details should contain limit");
  }

  @Test
  void testMySQLCommaSyntax_shouldIncludeDetails() throws Exception {
    // MySQL LIMIT syntax: LIMIT offset, rowCount
    String sql = "SELECT * FROM user LIMIT 100, 20";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectMySQLSyntax")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify violation
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    
    // Verify LIMIT details extracted correctly
    assertNotNull(result.getDetails().get("limit"), "Details should contain limit");
  }

  // ==================== Early-Return Flag Tests ====================

  @Test
  void testEarlyReturnFlag_shouldBeSetInViolationDetails() throws Exception {
    // SQL with no WHERE + LIMIT
    String sql = "SELECT * FROM user LIMIT 10";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectWithLimit")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify early-return flag is set
    assertTrue(result.getDetails().containsKey("earlyReturn"), 
        "Details should contain earlyReturn flag");
    assertEquals(true, result.getDetails().get("earlyReturn"), 
        "earlyReturn flag should be true");
  }

  // ==================== Configuration Tests ====================

  @Test
  void testDisabledChecker_shouldSkip() throws Exception {
    // Configuration with enabled=false
    config.setEnabled(false);
    
    String sql = "SELECT * FROM user LIMIT 10";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectWithLimit")
        .build();
    ValidationResult result = ValidationResult.pass();

    // Verify checker is disabled
    assertFalse(checker.isEnabled());
    
    checker.check(context, result);

    // Verify no violations added when disabled
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  // ==================== Dummy Condition Tests ====================

  @Test
  void testDummyConditionWhereTrue_shouldViolate() throws Exception {
    // SQL with "WHERE true" dummy condition
    String sql = "SELECT * FROM user WHERE true LIMIT 10";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectWithWhereTrue")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify CRITICAL violation (WHERE true is dummy condition)
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }

  @Test
  void testDummyConditionStringEquals_shouldViolate() throws Exception {
    // SQL with "WHERE 'a'='a'" dummy condition
    String sql = "SELECT * FROM user WHERE 'a'='a' LIMIT 10";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectWithStringEquals")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify CRITICAL violation (constant equality is dummy condition)
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }

  // ==================== Pagination Type Detection Verification ====================

  @Test
  void testPaginationTypeDetection_physicalWithLimit() throws Exception {
    // Verify that LIMIT clause is detected as PHYSICAL pagination
    String sql = "SELECT * FROM user LIMIT 10";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectWithLimit")
        .build();

    PaginationType type = detector.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, type, 
        "LIMIT clause should be detected as PHYSICAL pagination");
  }

  @Test
  void testPaginationTypeDetection_logicalWithRowBounds() throws Exception {
    // Verify that RowBounds without plugin is detected as LOGICAL pagination
    String sql = "SELECT * FROM user";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectWithRowBounds")
        .rowBounds(new RowBounds(0, 10))
        .build();

    PaginationType type = detector.detectPaginationType(context);
    assertEquals(PaginationType.LOGICAL, type, 
        "RowBounds without plugin should be detected as LOGICAL pagination");
  }
}

