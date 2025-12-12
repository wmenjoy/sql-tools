package com.footstone.sqlguard.validator.pagination.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Test class for DeepPaginationChecker.
 *
 * <p>Verifies that DeepPaginationChecker correctly detects deep pagination (high OFFSET values)
 * causing database to scan and skip large row counts before returning results. This is a MEDIUM
 * severity checker that runs after NoConditionPaginationChecker.</p>
 *
 * <p><strong>Test Coverage:</strong></p>
 * <ul>
 *   <li>Threshold boundary tests (below, above, equals, max±1)</li>
 *   <li>LIMIT syntax tests (OFFSET keyword, MySQL comma syntax, no offset)</li>
 *   <li>Pagination type tests (LOGICAL, PHYSICAL, NONE)</li>
 *   <li>Early-return flag tests (integration with Task 2.8)</li>
 *   <li>Configuration tests (custom threshold, enabled/disabled)</li>
 *   <li>Violation message tests (offset value, suggestion)</li>
 * </ul>
 */
class DeepPaginationCheckerTest {

  private JSqlParserFacade parser;
  private PaginationPluginDetector detector;
  private DeepPaginationChecker checker;
  private PaginationAbuseConfig config;

  @BeforeEach
  void setUp() {
    parser = new JSqlParserFacade();
    // Create detector without pagination plugin (to detect PHYSICAL pagination via LIMIT only)
    detector = new PaginationPluginDetector(null, null);
    config = new PaginationAbuseConfig(); // Default: maxOffset=10000
    checker = new DeepPaginationChecker(config, detector);
  }

  // ==================== Threshold Boundary Tests ====================

  @Test
  void testOffsetBelowThreshold_shouldPass() throws Exception {
    // OFFSET 9999 is below threshold 10000
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 20 OFFSET 9999";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectBelowThreshold")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify no violation
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testOffsetAboveThreshold_shouldViolate() throws Exception {
    // OFFSET 10001 is above threshold 10000
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 20 OFFSET 10001";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectAboveThreshold")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify MEDIUM violation
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    
    String message = result.getViolations().get(0).getMessage();
    assertTrue(message.contains("深分页"), "Message should contain '深分页'");
    assertTrue(message.contains("offset=10001"), "Message should contain offset value");
  }

  @Test
  void testOffsetEqualsThreshold_shouldPass() throws Exception {
    // OFFSET 10000 equals threshold 10000 - boundary inclusive on pass side
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 20 OFFSET 10000";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectEqualsThreshold")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify no violation (threshold inclusive on pass side)
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testOffsetMaxMinusOne_shouldPass() throws Exception {
    // OFFSET maxOffset-1 should pass
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 20 OFFSET 9999";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectMaxMinusOne")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify no violation
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testOffsetMaxPlusOne_shouldViolate() throws Exception {
    // OFFSET maxOffset+1 should violate
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 20 OFFSET 10001";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectMaxPlusOne")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify MEDIUM violation
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }

  // ==================== LIMIT Syntax Tests ====================

  @Test
  void testLimitOffsetSyntax_shouldCalculateCorrectly() throws Exception {
    // Standard "LIMIT n OFFSET m" syntax
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 20 OFFSET 5000";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectLimitOffset")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify no violation (5000 < 10000)
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testLimitCommaSyntax_shouldCalculateCorrectly() throws Exception {
    // MySQL "LIMIT offset,rowCount" syntax
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 5000,20";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectMySQLSyntax")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify no violation (offset=5000 < 10000)
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testOnlyLimitNoOffset_shouldPassWithZeroOffset() throws Exception {
    // LIMIT without OFFSET (offset=0)
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 100";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectLimitOnly")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify no violation (offset=0 < 10000)
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  // ==================== Pagination Type Tests ====================

  @Test
  void testLogicalPagination_shouldSkip() throws Exception {
    // SqlContext with RowBounds (LOGICAL type) - should be skipped by this checker
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
    // Plain query without LIMIT (NONE type)
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

  // ==================== Early-Return Tests ====================

  @Test
  void testNoConditionEarlyReturn_shouldSkipThisChecker() throws Exception {
    // Create ValidationResult with earlyReturn=true in details
    // This simulates Task 2.8 (NoConditionPaginationChecker) already violating
    String sql = "SELECT * FROM user LIMIT 20 OFFSET 50000";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectDeepOffsetNoWhere")
        .build();
    ValidationResult result = ValidationResult.pass();
    
    // Simulate Task 2.8 setting early-return flag
    result.getDetails().put("earlyReturn", true);

    checker.check(context, result);

    // Verify no violation from DeepPaginationChecker (Task 2.8 already violated)
    // Note: result.isPassed() might be false if Task 2.8 added violation, but
    // DeepPaginationChecker should NOT add additional violation
    assertEquals(0, result.getViolations().size(), 
        "DeepPaginationChecker should not add violation when earlyReturn=true");
  }

  @Test
  void testNoEarlyReturnFlag_shouldCheckNormally() throws Exception {
    // When earlyReturn flag is not set, checker should work normally
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 20 OFFSET 50000";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectDeepOffset")
        .build();
    ValidationResult result = ValidationResult.pass();
    // No earlyReturn flag set

    checker.check(context, result);

    // Verify MEDIUM violation (deep offset)
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }

  // ==================== Configuration Tests ====================

  @Test
  void testCustomMaxOffset_shouldRespect() throws Exception {
    // Custom config: maxOffset = 5000
    PaginationAbuseConfig customConfig = new PaginationAbuseConfig(true, 5000, 1000);
    DeepPaginationChecker customChecker = new DeepPaginationChecker(customConfig, detector);

    // SQL with OFFSET 6000 (above custom threshold 5000)
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 20 OFFSET 6000";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectCustomThreshold")
        .build();
    ValidationResult result = ValidationResult.pass();

    customChecker.check(context, result);

    // Verify MEDIUM violation (6000 > 5000)
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    
    String message = result.getViolations().get(0).getMessage();
    assertTrue(message.contains("offset=6000"), "Message should contain offset value");
  }

  @Test
  void testDisabledChecker_shouldSkip() throws Exception {
    // Configuration with enabled=false
    PaginationAbuseConfig disabledConfig = new PaginationAbuseConfig(false, 10000, 1000);
    DeepPaginationChecker disabledChecker = new DeepPaginationChecker(disabledConfig, detector);

    // SQL with deep offset
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 20 OFFSET 50000";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectDeepOffset")
        .build();
    ValidationResult result = ValidationResult.pass();

    // Verify checker is disabled
    assertFalse(disabledChecker.isEnabled());

    disabledChecker.check(context, result);

    // Verify no violations added when disabled
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  // ==================== Violation Message Tests ====================

  @Test
  void testViolationMessage_shouldContainOffsetValue() throws Exception {
    // SQL with OFFSET 15000
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 20 OFFSET 15000";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectOffset15000")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify violation message contains offset value
    assertFalse(result.isPassed());
    assertEquals(1, result.getViolations().size());
    
    String message = result.getViolations().get(0).getMessage();
    assertTrue(message.contains("offset=15000"), 
        "Message should contain 'offset=15000'");
    assertTrue(message.contains("扫描并跳过") || message.contains("性能"), 
        "Message should mention performance impact");
  }

  @Test
  void testViolationSuggestion_shouldMentionCursorPagination() throws Exception {
    // SQL with deep offset
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 20 OFFSET 15000";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectDeepOffset")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify suggestion mentions cursor pagination
    assertFalse(result.isPassed());
    assertEquals(1, result.getViolations().size());
    
    String suggestion = result.getViolations().get(0).getSuggestion();
    assertTrue(suggestion.contains("游标分页") || suggestion.contains("WHERE id > lastId"), 
        "Suggestion should mention cursor pagination or 'WHERE id > lastId'");
  }

  // ==================== MySQL Comma Syntax Deep Offset Test ====================

  @Test
  void testMySQLCommaSyntaxDeepOffset_shouldViolate() throws Exception {
    // MySQL "LIMIT offset,rowCount" syntax with deep offset
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 15000,20";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectMySQLDeepOffset")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Verify MEDIUM violation (offset=15000 > 10000)
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    
    String message = result.getViolations().get(0).getMessage();
    assertTrue(message.contains("offset=15000"), "Message should contain offset value");
  }
}

