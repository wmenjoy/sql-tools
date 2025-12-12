package com.footstone.sqlguard.validator.rule.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import java.util.Arrays;
import java.util.Collections;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for DummyConditionChecker.
 *
 * <p>Verifies detection of invalid/dummy WHERE conditions like "1=1", "true", "'a'='a'"
 * that effectively make the WHERE clause meaningless, resulting in full-table scans.</p>
 */
class DummyConditionCheckerTest {

  private DummyConditionChecker checker;
  private DummyConditionConfig config;

  @BeforeEach
  void setUp() {
    config = new DummyConditionConfig();
    checker = new DummyConditionChecker(config);
  }

  /**
   * Helper method to create SqlContext for testing.
   */
  private SqlContext createContext(String sql, Statement stmt, SqlCommandType type) {
    return SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(type)
        .mapperId("test.Mapper.testMethod")
        .build();
  }

  // ==================== Basic Pattern Tests ====================

  @Test
  void testOneEqualsOne_shouldViolate() throws Exception {
    String sql = "SELECT * FROM users WHERE 1=1";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }

  @Test
  void testOneEqualsOneWithSpaces_shouldViolate() throws Exception {
    String sql = "SELECT * FROM users WHERE 1 = 1";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }

  @Test
  void testStringConstantEquals_shouldViolate() throws Exception {
    // Test '1'='1'
    String sql1 = "SELECT * FROM users WHERE '1'='1'";
    Statement stmt1 = CCJSqlParserUtil.parse(sql1);
    SqlContext context1 = createContext(sql1, stmt1, SqlCommandType.SELECT);
    ValidationResult result1 = ValidationResult.pass();

    checker.check(context1, result1);

    assertEquals(RiskLevel.HIGH, result1.getRiskLevel());
    assertEquals(1, result1.getViolations().size());

    // Test 'a'='a'
    String sql2 = "SELECT * FROM users WHERE 'a'='a'";
    Statement stmt2 = CCJSqlParserUtil.parse(sql2);
    SqlContext context2 = createContext(sql2, stmt2, SqlCommandType.SELECT);
    ValidationResult result2 = ValidationResult.pass();

    checker.check(context2, result2);

    assertEquals(RiskLevel.HIGH, result2.getRiskLevel());
    assertEquals(1, result2.getViolations().size());
  }

  @Test
  void testTrue_shouldViolate() throws Exception {
    String sql = "SELECT * FROM users WHERE true";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }

  @Test
  void testConstantComparison_shouldViolate() throws Exception {
    // Test 2=2
    String sql1 = "SELECT * FROM users WHERE 2=2";
    Statement stmt1 = CCJSqlParserUtil.parse(sql1);
    SqlContext context1 = createContext(sql1, stmt1, SqlCommandType.SELECT);
    ValidationResult result1 = ValidationResult.pass();

    checker.check(context1, result1);

    assertEquals(RiskLevel.HIGH, result1.getRiskLevel());
    assertEquals(1, result1.getViolations().size());

    // Test 100=100
    String sql2 = "SELECT * FROM users WHERE 100=100";
    Statement stmt2 = CCJSqlParserUtil.parse(sql2);
    SqlContext context2 = createContext(sql2, stmt2, SqlCommandType.SELECT);
    ValidationResult result2 = ValidationResult.pass();

    checker.check(context2, result2);

    assertEquals(RiskLevel.HIGH, result2.getRiskLevel());
    assertEquals(1, result2.getViolations().size());
  }

  @Test
  void testFieldComparison_shouldPass() throws Exception {
    String sql = "SELECT * FROM users WHERE user_id=1";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testPlaceholder_shouldPass() throws Exception {
    String sql = "SELECT * FROM users WHERE id=?";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testEmbeddedDummy_shouldViolate() throws Exception {
    String sql = "SELECT * FROM users WHERE status='active' AND 1=1";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }

  // ==================== Advanced Pattern Tests ====================

  @Test
  void testAllDefaultPatterns_shouldDetect() throws Exception {
    // Test all default patterns: "1=1", "1 = 1", "'1'='1'", "true", "'a'='a'"
    String[] sqls = {
      "SELECT * FROM users WHERE 1=1",
      "SELECT * FROM users WHERE 1 = 1",
      "SELECT * FROM users WHERE '1'='1'",
      "SELECT * FROM users WHERE true",
      "SELECT * FROM users WHERE 'a'='a'"
    };

    for (String sql : sqls) {
      Statement stmt = CCJSqlParserUtil.parse(sql);
      SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertEquals(RiskLevel.HIGH, result.getRiskLevel(),
          "Failed to detect dummy condition in: " + sql);
      assertEquals(1, result.getViolations().size());
    }
  }

  @Test
  void testCaseInsensitiveMatching_shouldDetect() throws Exception {
    String[] sqls = {
      "SELECT * FROM users WHERE 1=1",
      "SELECT * FROM users where 1=1",
      "SELECT * FROM users WhErE 1=1"
    };

    for (String sql : sqls) {
      Statement stmt = CCJSqlParserUtil.parse(sql);
      SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertEquals(RiskLevel.HIGH, result.getRiskLevel(),
          "Failed case-insensitive detection for: " + sql);
      assertEquals(1, result.getViolations().size());
    }
  }

  @Test
  void testPatternInAndOr_shouldDetect() throws Exception {
    String sql = "SELECT * FROM users WHERE id>0 OR 1=1";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }

  @Test
  void testCustomPattern_shouldDetect() throws Exception {
    // Create config with custom pattern
    DummyConditionConfig customConfig = new DummyConditionConfig();
    customConfig.setCustomPatterns(Arrays.asList("0=0", "null is null"));
    DummyConditionChecker customChecker = new DummyConditionChecker(customConfig);

    String sql = "SELECT * FROM users WHERE 0=0";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    customChecker.check(context, result);

    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }

  @Test
  void testEmptyPattern_shouldPass() throws Exception {
    // Create config with empty patterns
    DummyConditionConfig emptyConfig = new DummyConditionConfig();
    emptyConfig.setPatterns(Collections.emptyList());
    emptyConfig.setCustomPatterns(Collections.emptyList());
    DummyConditionChecker emptyChecker = new DummyConditionChecker(emptyConfig);

    String sql = "SELECT * FROM users WHERE user_id=1";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    emptyChecker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testDisabledChecker_shouldSkip() throws Exception {
    // Create disabled config
    DummyConditionConfig disabledConfig = new DummyConditionConfig(false);
    DummyConditionChecker disabledChecker = new DummyConditionChecker(disabledConfig);

    String sql = "SELECT * FROM users WHERE 1=1";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    disabledChecker.check(context, result);

    // Should not execute check when disabled
    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testNoWhereClause_shouldPass() throws Exception {
    String sql = "SELECT * FROM users";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // No WHERE clause means nothing to check for dummy conditions
    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testUpdateWithDummyCondition_shouldViolate() throws Exception {
    String sql = "UPDATE users SET status='inactive' WHERE 1=1";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.UPDATE);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }

  @Test
  void testDeleteWithDummyCondition_shouldViolate() throws Exception {
    String sql = "DELETE FROM users WHERE true";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.DELETE);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }
}
