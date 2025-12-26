package com.footstone.sqlguard.validator.rule.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.ValidationResult;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for BlacklistFieldChecker.
 *
 * <p>Verifies detection of WHERE conditions using only blacklisted fields (deleted, del_flag,
 * status, is_deleted, etc.) which are typically state flags with low cardinality causing
 * excessive row matches and near-full-table scans.</p>
 */
class BlacklistFieldCheckerTest {

  private BlacklistFieldChecker checker;
  private BlacklistFieldsConfig config;

  @BeforeEach
  void setUp() {
    config = new BlacklistFieldsConfig();
    checker = new BlacklistFieldChecker(config);
  }

  /**
   * Helper method to create SqlContext for testing.
   */
  private SqlContext createContext(String sql, Statement stmt, SqlCommandType type) {
    return SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(type)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("test.Mapper.testMethod")
        .build();
  }

  // ==================== Blacklist Detection Tests ====================

  @Test
  void testDeletedOnly_shouldViolate() throws Exception {
    String sql = "SELECT * FROM users WHERE deleted=0";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    assertTrue(result.getViolations().get(0).getMessage().contains("黑名单字段"));
    assertTrue(result.getViolations().get(0).getMessage().contains("deleted"));
  }

  @Test
  void testStatusOnly_shouldViolate() throws Exception {
    String sql = "SELECT * FROM orders WHERE status='active'";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    assertTrue(result.getViolations().get(0).getMessage().contains("黑名单字段"));
    assertTrue(result.getViolations().get(0).getMessage().contains("status"));
  }

  @Test
  void testMultipleBlacklistFields_shouldViolate() throws Exception {
    String sql = "SELECT * FROM users WHERE deleted=0 AND enabled=1";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    assertTrue(result.getViolations().get(0).getMessage().contains("黑名单字段"));
    // Should mention both fields
    String message = result.getViolations().get(0).getMessage();
    assertTrue(message.contains("deleted") || message.contains("enabled"));
  }

  @Test
  void testMixedConditions_shouldPass() throws Exception {
    // id is NOT in blacklist, so this should pass
    String sql = "SELECT * FROM users WHERE id=1 AND deleted=0";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testNonBlacklistOnly_shouldPass() throws Exception {
    String sql = "SELECT * FROM orders WHERE user_id=? AND order_id=?";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testNoWhereClause_shouldPass() throws Exception {
    // No WHERE clause - should be skipped (handled by NoWhereClauseChecker)
    String sql = "SELECT * FROM users";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  // ==================== Edge Case Tests ====================

  @Test
  void testWildcardPattern_shouldMatch() throws Exception {
    // Add "create_*" to blacklist
    Set<String> blacklist = new HashSet<>(config.getFields());
    blacklist.add("create_*");
    config = new BlacklistFieldsConfig(true, blacklist);
    checker = new BlacklistFieldChecker(config);

    // WHERE create_time > ? should violate (matches create_* pattern)
    String sql = "SELECT * FROM users WHERE create_time > '2024-01-01'";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    assertTrue(result.getViolations().get(0).getMessage().contains("黑名单字段"));
  }

  @Test
  void testCaseInsensitive_shouldMatch() throws Exception {
    // Test uppercase DELETED
    String sql1 = "SELECT * FROM users WHERE DELETED=0";
    Statement stmt1 = CCJSqlParserUtil.parse(sql1);
    SqlContext context1 = createContext(sql1, stmt1, SqlCommandType.SELECT);
    ValidationResult result1 = ValidationResult.pass();

    checker.check(context1, result1);

    assertEquals(RiskLevel.HIGH, result1.getRiskLevel());
    assertEquals(1, result1.getViolations().size());

    // Test lowercase deleted
    String sql2 = "SELECT * FROM users WHERE deleted=0";
    Statement stmt2 = CCJSqlParserUtil.parse(sql2);
    SqlContext context2 = createContext(sql2, stmt2, SqlCommandType.SELECT);
    ValidationResult result2 = ValidationResult.pass();

    checker.check(context2, result2);

    assertEquals(RiskLevel.HIGH, result2.getRiskLevel());
    assertEquals(1, result2.getViolations().size());
  }

  @Test
  void testEmptyBlacklist_shouldPassAll() throws Exception {
    // Empty blacklist should not violate anything
    config = new BlacklistFieldsConfig(true, Collections.emptySet());
    checker = new BlacklistFieldChecker(config);

    String sql = "SELECT * FROM users WHERE deleted=0";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testBlacklistPlusDummy_shouldBothViolate() throws Exception {
    // WHERE deleted=0 AND 1=1 should trigger both BlacklistFieldChecker and DummyConditionChecker
    // This test verifies BlacklistFieldChecker detects the blacklist-only condition
    String sql = "SELECT * FROM users WHERE deleted=0 AND 1=1";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // BlacklistFieldChecker should detect deleted as blacklist-only
    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    assertTrue(result.getViolations().get(0).getMessage().contains("黑名单字段"));
  }

  @Test
  void testDisabledChecker_shouldSkip() throws Exception {
    // Disabled checker should not add violations
    config = new BlacklistFieldsConfig(false, config.getFields());
    checker = new BlacklistFieldChecker(config);

    String sql = "SELECT * FROM users WHERE deleted=0";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  // ==================== UPDATE/DELETE Statement Tests ====================
  // NOTE: After Phase 12 migration, BlacklistFieldChecker only checks SELECT statements.
  // UPDATE and DELETE statements are skipped (handled by NoWhereClauseChecker for safety).

  @Test
  void testUpdateStatement_shouldSkip() throws Exception {
    String sql = "UPDATE users SET name='test' WHERE status='active'";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.UPDATE);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // UPDATE is skipped - BlacklistFieldChecker only checks SELECT statements
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testDeleteStatement_shouldSkip() throws Exception {
    String sql = "DELETE FROM users WHERE del_flag=1";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.DELETE);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // DELETE is skipped - BlacklistFieldChecker only checks SELECT statements
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  // ==================== Additional Blacklist Field Tests ====================

  @Test
  void testAllDefaultBlacklistFields_shouldViolate() throws Exception {
    // Test each default blacklist field individually
    String[] blacklistFields = {"deleted", "del_flag", "status", "is_deleted", "enabled", "type"};

    for (String field : blacklistFields) {
      String sql = "SELECT * FROM users WHERE " + field + "=1";
      Statement stmt = CCJSqlParserUtil.parse(sql);
      SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertEquals(RiskLevel.HIGH, result.getRiskLevel(),
          "Field '" + field + "' should trigger violation");
      assertEquals(1, result.getViolations().size());
      assertTrue(result.getViolations().get(0).getMessage().contains("黑名单字段"));
    }
  }

  @Test
  void testCustomBlacklist_shouldViolate() throws Exception {
    // Custom blacklist with different fields
    Set<String> customBlacklist = new HashSet<>(Arrays.asList("is_active", "flag", "state"));
    config = new BlacklistFieldsConfig(true, customBlacklist);
    checker = new BlacklistFieldChecker(config);

    String sql = "SELECT * FROM users WHERE is_active=1";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    assertTrue(result.getViolations().get(0).getMessage().contains("黑名单字段"));
  }

  @Test
  void testWildcardUpdatePattern_shouldMatch() throws Exception {
    // Add "update_*" to blacklist
    Set<String> blacklist = new HashSet<>(config.getFields());
    blacklist.add("update_*");
    config = new BlacklistFieldsConfig(true, blacklist);
    checker = new BlacklistFieldChecker(config);

    // WHERE update_time < ? AND update_by = ? should violate (both match update_* pattern)
    String sql = "SELECT * FROM users WHERE update_time < '2024-01-01' AND update_by = 'admin'";
    Statement stmt = CCJSqlParserUtil.parse(sql);
    SqlContext context = createContext(sql, stmt, SqlCommandType.SELECT);
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    assertTrue(result.getViolations().get(0).getMessage().contains("黑名单字段"));
  }
}
