package com.footstone.sqlguard.validator.rule.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test class for SqlCommentChecker.
 *
 * <p>Verifies that SqlCommentChecker correctly detects SQL comments that can be used
 * for SQL injection attacks, including single-line (--), multi-line (/* * /), and
 * MySQL (#) comments.</p>
 *
 * <p><strong>Test Categories:</strong></p>
 * <ul>
 *   <li>PASS tests (≥5): Normal SQL without comments, string literals containing comment markers</li>
 *   <li>FAIL tests (≥10): Various comment injection attacks</li>
 *   <li>边界 tests (≥3): Edge cases and boundary conditions</li>
 * </ul>
 *
 * <p><strong>Multi-Dialect Coverage:</strong></p>
 * <ul>
 *   <li>MySQL: # comments</li>
 *   <li>Oracle: /*+ hints * /</li>
 *   <li>PostgreSQL: -- and /* * / comments</li>
 *   <li>SQL Server: -- and /* * / comments</li>
 * </ul>
 */
@DisplayName("SqlCommentChecker Tests")
class SqlCommentCheckerTest {

  private SqlCommentChecker checker;
  private SqlCommentConfig config;

  @BeforeEach
  void setUp() {
    config = new SqlCommentConfig(true); // Explicitly enable for tests
    checker = new SqlCommentChecker(config);
  }

  // ==================== PASS Tests (≥5) ====================

  @Nested
  @DisplayName("PASS Tests - Normal SQL without comments")
  class PassTests {

    @Test
    @DisplayName("Simple SELECT without comments should pass")
    void testSimpleSelect_shouldPass() {
      String sql = "SELECT * FROM users WHERE id = 1";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Simple SELECT should pass");
      assertEquals(RiskLevel.SAFE, result.getRiskLevel());
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("String literal containing -- marker should pass")
    void testStringWithDoubleDash_shouldPass() {
      String sql = "SELECT * FROM users WHERE name = '--test'";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "String with -- should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("String literal containing /* */ marker should pass")
    void testStringWithBlockComment_shouldPass() {
      String sql = "SELECT * FROM users WHERE description = '/* this is not a comment */'";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "String with /* */ should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("String literal containing # marker should pass")
    void testStringWithHash_shouldPass() {
      String sql = "SELECT * FROM users WHERE tag = '#trending'";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "String with # should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Oracle hint should pass when allowHintComments=true")
    void testOracleHintAllowed_shouldPass() {
      SqlCommentConfig hintConfig = new SqlCommentConfig(true, true);
      SqlCommentChecker hintChecker = new SqlCommentChecker(hintConfig);

      String sql = "SELECT /*+ INDEX(users idx_email) */ * FROM users WHERE id = 1";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      hintChecker.check(context, result);

      assertTrue(result.isPassed(), "Oracle hint should pass when allowed");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Complex SQL with multiple string literals should pass")
    void testMultipleStringsWithMarkers_shouldPass() {
      String sql = "SELECT * FROM users WHERE name = '--admin' AND email = 'test/*comment*/@example.com' AND tag = '#user'";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Multiple strings with markers should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Disabled checker should pass all SQL")
    void testDisabledChecker_shouldPass() {
      SqlCommentConfig disabledConfig = new SqlCommentConfig(false);
      SqlCommentChecker disabledChecker = new SqlCommentChecker(disabledConfig);

      String sql = "SELECT * FROM users -- this comment should be ignored";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      disabledChecker.check(context, result);

      assertTrue(result.isPassed(), "Disabled checker should pass");
      assertEquals(0, result.getViolations().size());
    }
  }

  // ==================== FAIL Tests (≥10) ====================

  @Nested
  @DisplayName("FAIL Tests - Comment injection attacks")
  class FailTests {

    @Test
    @DisplayName("Single-line comment (--) should fail")
    void testSingleLineComment_shouldFail() {
      String sql = "SELECT * FROM users WHERE id = 1 -- AND password = 'secret'";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Single-line comment should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
      assertEquals(1, result.getViolations().size());
      assertTrue(result.getViolations().get(0).getMessage().contains("单行注释(--)"));
    }

    @Test
    @DisplayName("Multi-line comment (/* */) should fail")
    void testMultiLineComment_shouldFail() {
      String sql = "SELECT * FROM users /* WHERE id = 1 */";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Multi-line comment should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
      assertEquals(1, result.getViolations().size());
      assertTrue(result.getViolations().get(0).getMessage().contains("多行注释"));
    }

    @Test
    @DisplayName("MySQL hash comment (#) should fail")
    void testMySqlHashComment_shouldFail() {
      String sql = "SELECT * FROM users WHERE id = 1 # AND password = 'secret'";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "MySQL hash comment should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
      assertEquals(1, result.getViolations().size());
      assertTrue(result.getViolations().get(0).getMessage().contains("MySQL注释(#)"));
    }

    @Test
    @DisplayName("Comment at beginning of SQL should fail")
    void testCommentAtBeginning_shouldFail() {
      String sql = "/* malicious */ SELECT * FROM users";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Comment at beginning should fail");
      assertEquals(1, result.getViolations().size());
    }

    @Test
    @DisplayName("Comment in WHERE clause should fail")
    void testCommentInWhere_shouldFail() {
      String sql = "SELECT * FROM users WHERE id = 1 /* injection */ AND status = 'active'";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Comment in WHERE should fail");
      assertEquals(1, result.getViolations().size());
    }

    @Test
    @DisplayName("Comment in SELECT clause should fail")
    void testCommentInSelect_shouldFail() {
      String sql = "SELECT id, /* hidden */ name FROM users";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Comment in SELECT should fail");
      assertEquals(1, result.getViolations().size());
    }

    @Test
    @DisplayName("Comment in FROM clause should fail")
    void testCommentInFrom_shouldFail() {
      String sql = "SELECT * FROM /* users */ orders";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Comment in FROM should fail");
      assertEquals(1, result.getViolations().size());
    }

    @Test
    @DisplayName("Multiple comments should fail with multiple violations")
    void testMultipleComments_shouldFail() {
      String sql = "SELECT * FROM users -- comment1\n WHERE id = 1 /* comment2 */";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Multiple comments should fail");
      assertEquals(2, result.getViolations().size());
    }

    @Test
    @DisplayName("Comment to bypass authentication should fail")
    void testAuthBypassComment_shouldFail() {
      String sql = "SELECT * FROM users WHERE username = 'admin'--' AND password = 'wrong'";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Auth bypass comment should fail");
      assertEquals(1, result.getViolations().size());
    }

    @Test
    @DisplayName("Comment to hide UNION injection should fail")
    void testUnionHidingComment_shouldFail() {
      String sql = "SELECT * FROM users WHERE id = 1 /* UNION SELECT * FROM passwords */";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "UNION hiding comment should fail");
      assertEquals(1, result.getViolations().size());
    }

    @Test
    @DisplayName("Oracle hint should fail when allowHintComments=false (default)")
    void testOracleHintNotAllowed_shouldFail() {
      String sql = "SELECT /*+ INDEX(users idx_email) */ * FROM users WHERE id = 1";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Oracle hint should fail when not allowed");
      assertEquals(1, result.getViolations().size());
      assertTrue(result.getViolations().get(0).getMessage().contains("优化器提示"));
    }

    @Test
    @DisplayName("UPDATE with comment should fail")
    void testUpdateWithComment_shouldFail() {
      String sql = "UPDATE users SET name = 'test' -- WHERE id = 1";
      SqlContext context = createContextForUpdate(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "UPDATE with comment should fail");
      assertEquals(1, result.getViolations().size());
    }

    @Test
    @DisplayName("DELETE with comment should fail")
    void testDeleteWithComment_shouldFail() {
      String sql = "DELETE FROM users /* WHERE id = 1 */";
      SqlContext context = createContextForDelete(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "DELETE with comment should fail");
      assertEquals(1, result.getViolations().size());
    }
  }

  // ==================== 边界 Tests (≥3) ====================

  @Nested
  @DisplayName("Edge Case Tests - Boundary conditions")
  class EdgeCaseTests {

    @Test
    @DisplayName("Comment at different positions should be detected")
    void testCommentAtDifferentPositions() {
      // Beginning
      String sql1 = "-- comment\nSELECT * FROM users";
      SqlContext context1 = createContext(sql1);
      ValidationResult result1 = ValidationResult.pass();
      checker.check(context1, result1);
      assertFalse(result1.isPassed(), "Comment at beginning should be detected");

      // Middle
      String sql2 = "SELECT * /* comment */ FROM users";
      SqlContext context2 = createContext(sql2);
      ValidationResult result2 = ValidationResult.pass();
      checker.check(context2, result2);
      assertFalse(result2.isPassed(), "Comment in middle should be detected");

      // End
      String sql3 = "SELECT * FROM users -- comment";
      SqlContext context3 = createContext(sql3);
      ValidationResult result3 = ValidationResult.pass();
      checker.check(context3, result3);
      assertFalse(result3.isPassed(), "Comment at end should be detected");
    }

    @Test
    @DisplayName("Only comment (no SQL) should be detected")
    void testOnlyComment_shouldFail() {
      String sql = "-- this is just a comment";
      SqlContext context = createContextWithRawSql(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Only comment should be detected");
      assertEquals(1, result.getViolations().size());
    }

    @Test
    @DisplayName("Escaped quote with comment marker should pass")
    void testEscapedQuoteWithMarker_shouldPass() {
      String sql = "SELECT * FROM users WHERE name = 'O''Brien--test'";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Escaped quote with marker should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Double-quoted identifier with comment marker should pass")
    void testDoubleQuotedIdentifier_shouldPass() {
      String sql = "SELECT * FROM users WHERE \"column--name\" = 1";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Double-quoted identifier should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Backtick identifier with comment marker should pass (MySQL)")
    void testBacktickIdentifier_shouldPass() {
      String sql = "SELECT * FROM users WHERE `column--name` = 1";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Backtick identifier should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("SQL with only whitespace between comment markers should be detected")
    void testCommentWithWhitespace_shouldFail() {
      String sql = "SELECT * FROM users WHERE id = 1 --   ";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Comment with whitespace should be detected");
      assertEquals(1, result.getViolations().size());
    }

    @Test
    @DisplayName("Very long comment should be truncated in message")
    void testLongComment_shouldTruncate() {
      StringBuilder sb = new StringBuilder("-- ");
      for (int i = 0; i < 100; i++) {
        sb.append("a");
      }
      String sql = "SELECT * FROM users WHERE id = 1 " + sb.toString();
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Long comment should be detected");
      assertEquals(1, result.getViolations().size());
      // Message should contain truncated preview
      assertTrue(result.getViolations().get(0).getMessage().contains("..."));
    }

    @Test
    @DisplayName("Comment after valid SQL should be detected")
    void testCommentAfterValidSql_shouldFail() {
      String sql = "SELECT * FROM users WHERE id = 1; -- trailing comment";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Trailing comment should be detected");
      assertEquals(1, result.getViolations().size());
    }
  }

  // ==================== Oracle Hint Tests ====================

  @Nested
  @DisplayName("Oracle Hint Tests")
  class OracleHintTests {

    @Test
    @DisplayName("Multiple Oracle hints should pass when allowed")
    void testMultipleHints_shouldPass() {
      SqlCommentConfig hintConfig = new SqlCommentConfig(true, true);
      SqlCommentChecker hintChecker = new SqlCommentChecker(hintConfig);

      String sql = "SELECT /*+ FIRST_ROWS(10) INDEX(users idx_email) */ * FROM users WHERE id = 1";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      hintChecker.check(context, result);

      assertTrue(result.isPassed(), "Multiple hints should pass when allowed");
    }

    @Test
    @DisplayName("Hint in UPDATE should pass when allowed")
    void testHintInUpdate_shouldPass() {
      SqlCommentConfig hintConfig = new SqlCommentConfig(true, true);
      SqlCommentChecker hintChecker = new SqlCommentChecker(hintConfig);

      String sql = "UPDATE /*+ INDEX(users idx_email) */ users SET name = 'test' WHERE id = 1";
      SqlContext context = createContextForUpdate(sql);
      ValidationResult result = ValidationResult.pass();

      hintChecker.check(context, result);

      assertTrue(result.isPassed(), "Hint in UPDATE should pass when allowed");
    }

    @Test
    @DisplayName("Hint in DELETE should pass when allowed")
    void testHintInDelete_shouldPass() {
      SqlCommentConfig hintConfig = new SqlCommentConfig(true, true);
      SqlCommentChecker hintChecker = new SqlCommentChecker(hintConfig);

      String sql = "DELETE /*+ INDEX(users idx_email) */ FROM users WHERE id = 1";
      SqlContext context = createContextForDelete(sql);
      ValidationResult result = ValidationResult.pass();

      hintChecker.check(context, result);

      assertTrue(result.isPassed(), "Hint in DELETE should pass when allowed");
    }

    @Test
    @DisplayName("Non-hint comment with + should still fail")
    void testFakeHint_shouldFail() {
      SqlCommentConfig hintConfig = new SqlCommentConfig(true, true);
      SqlCommentChecker hintChecker = new SqlCommentChecker(hintConfig);

      // Space before + makes it not a valid hint
      String sql = "SELECT /* + INDEX(users idx_email) */ * FROM users WHERE id = 1";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      hintChecker.check(context, result);

      // JSqlParser doesn't recognize this as a hint, so it should fail
      assertFalse(result.isPassed(), "Fake hint with space should fail");
    }
  }

  // ==================== Config Tests ====================

  @Nested
  @DisplayName("Configuration Tests")
  class ConfigTests {

    @Test
    @DisplayName("Default config should have correct values")
    void testDefaultConfig() {
      SqlCommentConfig defaultConfig = new SqlCommentConfig();

      assertFalse(defaultConfig.isEnabled(), "Should be disabled by default (opt-in design)");
      assertEquals(RiskLevel.CRITICAL, defaultConfig.getRiskLevel(), "Risk level should be CRITICAL");
      assertFalse(defaultConfig.isAllowHintComments(), "Hints should not be allowed by default");
    }

    @Test
    @DisplayName("Config with enabled=false should disable checker")
    void testDisabledConfig() {
      SqlCommentConfig disabledConfig = new SqlCommentConfig(false);
      SqlCommentChecker disabledChecker = new SqlCommentChecker(disabledConfig);

      assertFalse(disabledChecker.isEnabled(), "Checker should be disabled");
    }

    @Test
    @DisplayName("Config with allowHintComments=true should allow hints")
    void testHintAllowedConfig() {
      SqlCommentConfig hintConfig = new SqlCommentConfig(true, true);

      assertTrue(hintConfig.isAllowHintComments(), "Hints should be allowed");
    }
  }

  // ==================== Helper Methods ====================

  /**
   * Creates a SqlContext for SELECT statements.
   */
  private SqlContext createContext(String sql) {
    try {
      Statement statement = CCJSqlParserUtil.parse(sql);
      return SqlContext.builder()
          .sql(sql)
          .type(SqlCommandType.SELECT)
          .statementId("TestMapper.select")
          .statement(statement)
          .executionLayer(ExecutionLayer.MYBATIS)
          .build();
    } catch (Exception e) {
      // For SQL that can't be parsed (e.g., with comments that break parsing),
      // create context with raw SQL only
      return createContextWithRawSql(sql);
    }
  }

  /**
   * Creates a SqlContext for UPDATE statements.
   */
  private SqlContext createContextForUpdate(String sql) {
    try {
      Statement statement = CCJSqlParserUtil.parse(sql);
      return SqlContext.builder()
          .sql(sql)
          .type(SqlCommandType.UPDATE)
          .statementId("TestMapper.update")
          .statement(statement)
          .executionLayer(ExecutionLayer.MYBATIS)
          .build();
    } catch (Exception e) {
      return SqlContext.builder()
          .sql(sql)
          .type(SqlCommandType.UPDATE)
          .statementId("TestMapper.update")
          .executionLayer(ExecutionLayer.MYBATIS)
          .build();
    }
  }

  /**
   * Creates a SqlContext for DELETE statements.
   */
  private SqlContext createContextForDelete(String sql) {
    try {
      Statement statement = CCJSqlParserUtil.parse(sql);
      return SqlContext.builder()
          .sql(sql)
          .type(SqlCommandType.DELETE)
          .statementId("TestMapper.delete")
          .statement(statement)
          .executionLayer(ExecutionLayer.MYBATIS)
          .build();
    } catch (Exception e) {
      return SqlContext.builder()
          .sql(sql)
          .type(SqlCommandType.DELETE)
          .statementId("TestMapper.delete")
          .executionLayer(ExecutionLayer.MYBATIS)
          .build();
    }
  }

  /**
   * Creates a SqlContext with raw SQL only (no parsed statement).
   */
  private SqlContext createContextWithRawSql(String sql) {
    return SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .statementId("TestMapper.rawSql")
        .executionLayer(ExecutionLayer.MYBATIS)
        .build();
  }
}
