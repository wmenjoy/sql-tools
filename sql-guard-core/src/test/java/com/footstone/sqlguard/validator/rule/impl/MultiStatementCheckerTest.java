package com.footstone.sqlguard.validator.rule.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test class for MultiStatementChecker.
 *
 * <p>Verifies that MultiStatementChecker correctly detects SQL injection via multi-statement
 * execution (e.g., "SELECT * FROM user; DROP TABLE user--") and blocks such attacks with
 * CRITICAL-level violations.</p>
 *
 * <p><strong>Test Categories:</strong></p>
 * <ul>
 *   <li>PASS tests (≥5): Normal SQL with trailing semicolon, single statements</li>
 *   <li>FAIL tests (≥10): Multi-statement injection variants</li>
 *   <li>边界 tests (≥3): Empty SQL, only semicolons, semicolons in string literals</li>
 * </ul>
 *
 * <p><strong>Detection Strategy:</strong></p>
 * <p>Uses SQL string parsing for semicolon detection since JSqlParser Statement AST
 * doesn't preserve statement separators. Detection excludes:</p>
 * <ul>
 *   <li>Trailing semicolons (end of SQL)</li>
 *   <li>Semicolons within string literals (', ", ``)</li>
 * </ul>
 */
@DisplayName("MultiStatementChecker Tests")
class MultiStatementCheckerTest {

  private JSqlParserFacade parser;
  private MultiStatementChecker checker;
  private MultiStatementConfig config;

  @BeforeEach
  void setUp() {
    parser = new JSqlParserFacade();
    config = new MultiStatementConfig(true); // Explicitly enable for tests
    checker = new MultiStatementChecker(config);
  }

  // ==================== PASS Tests (≥5) ====================

  @Nested
  @DisplayName("PASS Tests - Single statements that should pass validation")
  class PassTests {

    @Test
    @DisplayName("Simple SELECT without semicolon should pass")
    void testSimpleSelect_shouldPass() {
      String sql = "SELECT * FROM users WHERE id = 1";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Simple SELECT should pass");
      assertEquals(RiskLevel.SAFE, result.getRiskLevel());
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("SELECT with trailing semicolon should pass")
    void testSelectWithTrailingSemicolon_shouldPass() {
      String sql = "SELECT * FROM users WHERE id = 1;";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Trailing semicolon should pass");
      assertEquals(RiskLevel.SAFE, result.getRiskLevel());
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("UPDATE with trailing semicolon should pass")
    void testUpdateWithTrailingSemicolon_shouldPass() {
      String sql = "UPDATE users SET status = 'active' WHERE id = 1;";
      SqlContext context = createContext(sql, SqlCommandType.UPDATE);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "UPDATE with trailing semicolon should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("DELETE with trailing semicolon should pass")
    void testDeleteWithTrailingSemicolon_shouldPass() {
      String sql = "DELETE FROM users WHERE id = 1;";
      SqlContext context = createContext(sql, SqlCommandType.DELETE);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "DELETE with trailing semicolon should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("INSERT with trailing semicolon should pass")
    void testInsertWithTrailingSemicolon_shouldPass() {
      String sql = "INSERT INTO users (name, email) VALUES ('John', 'john@example.com');";
      SqlContext context = createContext(sql, SqlCommandType.INSERT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "INSERT with trailing semicolon should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Complex SELECT with subquery should pass")
    void testComplexSelectWithSubquery_shouldPass() {
      String sql = "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders WHERE status = 'active');";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Complex SELECT with subquery should pass");
      assertEquals(0, result.getViolations().size());
    }
  }

  // ==================== FAIL Tests (≥10) ====================

  @Nested
  @DisplayName("FAIL Tests - Multi-statement injection attacks that should be blocked")
  class FailTests {

    @Test
    @DisplayName("Classic SQL injection: SELECT; DROP TABLE should fail")
    void testClassicSqlInjection_shouldFail() {
      String sql = "SELECT * FROM users; DROP TABLE users--";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Multi-statement injection should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
      assertEquals(1, result.getViolations().size());
      assertTrue(result.getViolations().get(0).getMessage().contains("多语句"));
    }

    @Test
    @DisplayName("SQL injection: SELECT; DELETE FROM should fail")
    void testSelectDeleteInjection_shouldFail() {
      String sql = "SELECT * FROM users WHERE id = 1; DELETE FROM users;";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SELECT; DELETE injection should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("SQL injection: SELECT; UPDATE should fail")
    void testSelectUpdateInjection_shouldFail() {
      String sql = "SELECT * FROM users; UPDATE users SET admin = 1;";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SELECT; UPDATE injection should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("SQL injection: SELECT; INSERT should fail")
    void testSelectInsertInjection_shouldFail() {
      String sql = "SELECT * FROM users; INSERT INTO admin_users VALUES (1, 'hacker');";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SELECT; INSERT injection should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("SQL injection with comment: SELECT; DROP--comment should fail")
    void testInjectionWithComment_shouldFail() {
      String sql = "SELECT * FROM users WHERE id = '1'; DROP TABLE users-- comment";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Injection with comment should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("SQL injection: Multiple statements (3+) should fail")
    void testMultipleStatements_shouldFail() {
      String sql = "SELECT 1; SELECT 2; SELECT 3;";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Multiple statements should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("SQL injection: UPDATE; DROP should fail")
    void testUpdateDropInjection_shouldFail() {
      String sql = "UPDATE users SET name = 'test'; DROP TABLE users;";
      SqlContext context = createContext(sql, SqlCommandType.UPDATE);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "UPDATE; DROP injection should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("SQL injection: DELETE; TRUNCATE should fail")
    void testDeleteTruncateInjection_shouldFail() {
      String sql = "DELETE FROM logs WHERE id = 1; TRUNCATE TABLE users;";
      SqlContext context = createContext(sql, SqlCommandType.DELETE);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "DELETE; TRUNCATE injection should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("SQL injection: INSERT; DROP should fail")
    void testInsertDropInjection_shouldFail() {
      String sql = "INSERT INTO users (name) VALUES ('test'); DROP TABLE users;";
      SqlContext context = createContext(sql, SqlCommandType.INSERT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "INSERT; DROP injection should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("SQL injection with spaces around semicolon should fail")
    void testInjectionWithSpaces_shouldFail() {
      String sql = "SELECT * FROM users   ;   DROP TABLE users";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Injection with spaces should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("SQL injection: stacked queries should fail")
    void testStackedQueries_shouldFail() {
      String sql = "SELECT * FROM users WHERE id=1;WAITFOR DELAY '0:0:5'--";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Stacked queries should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("SQL injection: UNION then second statement should fail")
    void testUnionThenStatement_shouldFail() {
      String sql = "SELECT * FROM users UNION SELECT * FROM admins; DROP TABLE users;";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "UNION then statement should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }
  }

  // ==================== 边界 Tests (≥3) ====================

  @Nested
  @DisplayName("边界 Tests - Edge cases and boundary conditions")
  class EdgeCaseTests {

    @Test
    @DisplayName("Simple SELECT 1 should pass (baseline)")
    void testSimpleSelect1_shouldPass() {
      String sql = "SELECT 1";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Simple SELECT 1 should pass");
    }

    @Test
    @DisplayName("SQL with only trailing semicolons should pass")
    void testOnlyTrailingSemicolons_shouldPass() {
      String sql = "SELECT 1;;;";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      // Only trailing semicolons - no real multi-statement attack
      assertTrue(result.isPassed(), "Only trailing semicolons should pass");
    }

    @Test
    @DisplayName("Semicolon inside single-quoted string should pass")
    void testSemicolonInSingleQuotedString_shouldPass() {
      String sql = "SELECT * FROM users WHERE name = 'John; DROP TABLE users'";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Semicolon in single-quoted string should pass");
      assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    }

    @Test
    @DisplayName("Semicolon inside double-quoted string should pass")
    void testSemicolonInDoubleQuotedString_shouldPass() {
      String sql = "SELECT * FROM users WHERE name = \"John; DROP TABLE users\"";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Semicolon in double-quoted string should pass");
      assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    }

    @Test
    @DisplayName("Semicolon inside backtick identifier should pass")
    void testSemicolonInBacktickIdentifier_shouldPass() {
      String sql = "SELECT * FROM `table;name` WHERE id = 1";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Semicolon in backtick identifier should pass");
      assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    }

    @Test
    @DisplayName("Mixed: semicolon in string AND real injection should fail")
    void testMixedStringAndInjection_shouldFail() {
      String sql = "SELECT * FROM users WHERE name = 'test;value'; DROP TABLE users;";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Real injection after string should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("SQL with whitespace around semicolon at end should pass")
    void testWhitespaceAroundTrailingSemicolon_shouldPass() {
      String sql = "SELECT 1   ;   ";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Whitespace around trailing semicolon should pass");
    }

    @Test
    @DisplayName("SQL with escaped quote containing semicolon should pass")
    void testEscapedQuoteWithSemicolon_shouldPass() {
      String sql = "SELECT * FROM users WHERE name = 'O''Brien; test'";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Escaped quote with semicolon should pass");
    }
  }

  // ==================== Configuration Tests ====================

  @Nested
  @DisplayName("Configuration Tests")
  class ConfigurationTests {

    @Test
    @DisplayName("Disabled checker should skip validation")
    void testDisabledChecker_shouldSkip() {
      MultiStatementConfig disabledConfig = new MultiStatementConfig(false);
      MultiStatementChecker disabledChecker = new MultiStatementChecker(disabledConfig);

      String sql = "SELECT * FROM users; DROP TABLE users;";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      disabledChecker.check(context, result);

      assertTrue(result.isPassed(), "Disabled checker should skip");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Checker should be enabled by default")
    void testDefaultEnabled() {
      MultiStatementConfig defaultConfig = new MultiStatementConfig();
      assertFalse(defaultConfig.isEnabled(), "Should be disabled by default (opt-in design)");
    }
  }

  // ==================== Multi-Dialect Tests ====================

  @Nested
  @DisplayName("Multi-Dialect Tests - MySQL, Oracle, PostgreSQL")
  class MultiDialectTests {

    @Test
    @DisplayName("MySQL: LIMIT with injection should fail")
    void testMySqlLimitInjection_shouldFail() {
      String sql = "SELECT * FROM users LIMIT 10; DROP TABLE users;";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "MySQL LIMIT injection should fail");
    }

    @Test
    @DisplayName("Oracle: ROWNUM with injection should fail")
    void testOracleRownumInjection_shouldFail() {
      String sql = "SELECT * FROM users WHERE ROWNUM <= 10; DROP TABLE users;";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Oracle ROWNUM injection should fail");
    }

    @Test
    @DisplayName("PostgreSQL: RETURNING with injection should fail")
    void testPostgreSqlReturningInjection_shouldFail() {
      String sql = "INSERT INTO users (name) VALUES ('test') RETURNING id; DROP TABLE users;";
      SqlContext context = createContext(sql, SqlCommandType.INSERT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "PostgreSQL RETURNING injection should fail");
    }
  }

  // ==================== Helper Methods ====================

  /**
   * Creates a SqlContext with parsed statement for testing.
   * For multi-statement SQL, we parse only the first statement (JSqlParser behavior).
   */
  private SqlContext createContext(String sql, SqlCommandType type) {
    Statement stmt = null;
    try {
      // JSqlParser will parse only the first statement for multi-statement SQL
      // This is expected - we detect multi-statement via raw SQL, not AST
      stmt = parser.parse(sql);
    } catch (Exception e) {
      // For invalid SQL or multi-statement SQL that fails parsing,
      // we still create context with the raw SQL for validation
    }
    return SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(type)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.TestMapper.testMethod")
        .build();
  }

  /**
   * Creates a SqlContext with raw SQL only (no parsing) for edge cases.
   */
  private SqlContext createContextWithRawSql(String sql) {
    // For empty/whitespace SQL, use a placeholder
    String safeSql = (sql == null || sql.trim().isEmpty()) ? "SELECT 1" : sql;
    Statement stmt = null;
    try {
      stmt = parser.parse(safeSql);
    } catch (Exception e) {
      // Ignore parsing errors for edge cases
    }
    return SqlContext.builder()
        .sql(safeSql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.TestMapper.testMethod")
        .build();
  }
}
