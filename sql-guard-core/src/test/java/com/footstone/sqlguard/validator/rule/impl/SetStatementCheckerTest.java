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
 * Test class for SetStatementChecker.
 *
 * <p>Verifies that SetStatementChecker correctly detects session variable modification
 * statements (SET) while allowing UPDATE...SET column assignments.</p>
 *
 * <p><strong>Test Categories:</strong></p>
 * <ul>
 *   <li>PASS tests (≥5): SELECT/INSERT/DELETE statements, UPDATE table SET column, normal DML</li>
 *   <li>FAIL tests (≥10): SET autocommit, SET sql_mode, SET @variable, SET NAMES, SET CHARSET, case variations</li>
 *   <li>边界 tests (≥3): SET with different spacing, multiple SET in batch, UPDATE vs SET disambiguation</li>
 * </ul>
 *
 * <p><strong>Multi-Dialect Coverage:</strong></p>
 * <ul>
 *   <li>MySQL: SET autocommit, SET sql_mode, SET @variable, SET NAMES, SET CHARSET</li>
 *   <li>PostgreSQL: SET variants (SET search_path, SET statement_timeout)</li>
 * </ul>
 */
@DisplayName("SetStatementChecker Tests")
class SetStatementCheckerTest {

  private SetStatementChecker checker;
  private SetStatementConfig config;

  @BeforeEach
  void setUp() {
    config = new SetStatementConfig();
    checker = new SetStatementChecker(config);
  }

  // ==================== PASS Tests (≥5) ====================

  @Nested
  @DisplayName("PASS Tests - Normal SQL without SET statement")
  class PassTests {

    @Test
    @DisplayName("Simple SELECT statement should pass")
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
    @DisplayName("INSERT statement should pass")
    void testInsertStatement_shouldPass() {
      String sql = "INSERT INTO users (name, email) VALUES ('test', 'test@example.com')";
      SqlContext context = createContext(sql, SqlCommandType.INSERT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "INSERT statement should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("DELETE statement should pass")
    void testDeleteStatement_shouldPass() {
      String sql = "DELETE FROM users WHERE id = 1";
      SqlContext context = createContext(sql, SqlCommandType.DELETE);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "DELETE statement should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("UPDATE table SET column (column assignment) should pass - CRITICAL TEST")
    void testUpdateSetColumn_shouldPass() {
      // CRITICAL: This is UPDATE statement with SET for column assignment, NOT SET statement
      String sql = "UPDATE users SET name = 'test' WHERE id = 1";
      SqlContext context = createContext(sql, SqlCommandType.UPDATE);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "UPDATE table SET column should pass (this is UPDATE, not SET statement)");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("UPDATE with multiple SET columns should pass")
    void testUpdateMultipleSetColumns_shouldPass() {
      String sql = "UPDATE users SET name = 'test', email = 'test@example.com', status = 'active' WHERE id = 1";
      SqlContext context = createContext(sql, SqlCommandType.UPDATE);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "UPDATE with multiple SET columns should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("SELECT with JOIN should pass")
    void testSelectWithJoin_shouldPass() {
      String sql = "SELECT u.*, o.* FROM users u LEFT JOIN orders o ON u.id = o.user_id WHERE u.status = 'active'";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "SELECT with JOIN should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Disabled checker should skip validation")
    void testDisabledChecker_shouldSkip() {
      SetStatementConfig disabledConfig = new SetStatementConfig(false);
      SetStatementChecker disabledChecker = new SetStatementChecker(disabledConfig);

      String sql = "SET autocommit = 0";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      disabledChecker.check(context, result);

      assertTrue(result.isPassed(), "Disabled checker should skip validation");
      assertEquals(0, result.getViolations().size());
    }
  }

  // ==================== FAIL Tests (≥10) ====================

  @Nested
  @DisplayName("FAIL Tests - SET statement detection")
  class FailTests {

    @Test
    @DisplayName("SET autocommit = 0 should fail")
    void testSetAutocommit_shouldFail() {
      String sql = "SET autocommit = 0";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SET autocommit should fail");
      assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
      assertEquals(1, result.getViolations().size());
      assertTrue(result.getViolations().get(0).getMessage().contains("SET"));
    }

    @Test
    @DisplayName("SET autocommit = 1 should fail")
    void testSetAutocommitOn_shouldFail() {
      String sql = "SET autocommit = 1";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SET autocommit = 1 should fail");
      assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }

    @Test
    @DisplayName("SET sql_mode should fail")
    void testSetSqlMode_shouldFail() {
      String sql = "SET sql_mode = ''";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SET sql_mode should fail");
      assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("sql_mode"));
    }

    @Test
    @DisplayName("SET @variable should fail")
    void testSetUserVariable_shouldFail() {
      String sql = "SET @user_id = 123";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SET @variable should fail");
      assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }

    @Test
    @DisplayName("SET NAMES should fail")
    void testSetNames_shouldFail() {
      String sql = "SET NAMES utf8mb4";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SET NAMES should fail");
      assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }

    @Test
    @DisplayName("SET CHARSET should fail")
    void testSetCharset_shouldFail() {
      String sql = "SET CHARSET utf8";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SET CHARSET should fail");
      assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }

    @Test
    @DisplayName("SET SESSION should fail")
    void testSetSession_shouldFail() {
      String sql = "SET SESSION wait_timeout = 28800";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SET SESSION should fail");
      assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }

    @Test
    @DisplayName("SET GLOBAL should fail")
    void testSetGlobal_shouldFail() {
      String sql = "SET GLOBAL max_connections = 1000";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SET GLOBAL should fail");
      assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }

    @Test
    @DisplayName("SET TRANSACTION ISOLATION LEVEL should fail")
    void testSetTransactionIsolation_shouldFail() {
      String sql = "SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SET TRANSACTION ISOLATION LEVEL should fail");
      assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }

    @Test
    @DisplayName("PostgreSQL SET search_path should fail")
    void testPostgresSetSearchPath_shouldFail() {
      String sql = "SET search_path TO public, myschema";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "PostgreSQL SET search_path should fail");
      assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }

    @Test
    @DisplayName("PostgreSQL SET statement_timeout should fail")
    void testPostgresSetStatementTimeout_shouldFail() {
      String sql = "SET statement_timeout = '5s'";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "PostgreSQL SET statement_timeout should fail");
      assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }

    @Test
    @DisplayName("SET LOCAL should fail")
    void testSetLocal_shouldFail() {
      String sql = "SET LOCAL timezone = 'UTC'";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SET LOCAL should fail");
      assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }
  }

  // ==================== 边界 Tests (≥3) ====================

  @Nested
  @DisplayName("边界 Tests - Edge cases and boundary conditions")
  class EdgeCaseTests {

    @Test
    @DisplayName("SET with extra whitespace should be detected")
    void testSetWithExtraWhitespace_shouldFail() {
      String sql = "   SET   autocommit   =   0   ";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SET with extra whitespace should fail");
      assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }

    @Test
    @DisplayName("SET with newline before should be detected")
    void testSetWithNewline_shouldFail() {
      String sql = "\nSET autocommit = 0";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SET with newline should fail");
      assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }

    @Test
    @DisplayName("UPDATE vs SET disambiguation - CRITICAL TEST")
    void testUpdateVsSetDisambiguation_shouldDistinguish() {
      // UPDATE...SET should pass (column assignment)
      String updateSql = "UPDATE users SET name = 'test' WHERE id = 1";
      SqlContext updateContext = createContext(updateSql, SqlCommandType.UPDATE);
      ValidationResult updateResult = ValidationResult.pass();
      checker.check(updateContext, updateResult);
      assertTrue(updateResult.isPassed(), "UPDATE...SET should pass");

      // SET statement should fail
      String setSql = "SET autocommit = 0";
      SqlContext setContext = createRawContext(setSql);
      ValidationResult setResult = ValidationResult.pass();
      checker.check(setContext, setResult);
      assertFalse(setResult.isPassed(), "SET statement should fail");
    }

    @Test
    @DisplayName("Lowercase 'set' should be detected")
    void testLowercaseSet_shouldFail() {
      String sql = "set autocommit = 0";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Lowercase 'set' should fail");
      assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }

    @Test
    @DisplayName("Mixed case 'Set' should be detected")
    void testMixedCaseSet_shouldFail() {
      String sql = "Set autocommit = 0";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Mixed case 'Set' should fail");
      assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }

    @Test
    @DisplayName("SET with tab character should be detected")
    void testSetWithTab_shouldFail() {
      String sql = "\tSET autocommit = 0";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SET with tab should fail");
      assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }

    @Test
    @DisplayName("SET keyword in table name should pass")
    void testSetInTableName_shouldPass() {
      // Table name contains 'SET' but this is not a SET statement
      String sql = "SELECT * FROM user_settings WHERE id = 1";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "SET in table name should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("SET keyword in column name should pass")
    void testSetInColumnName_shouldPass() {
      // Column name contains 'SET' but this is not a SET statement
      String sql = "SELECT settings_value FROM config WHERE id = 1";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "SET in column name should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("SQL containing 'SET' in string literal should pass")
    void testSetInStringLiteral_shouldPass() {
      String sql = "SELECT * FROM users WHERE name = 'SET autocommit = 0'";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "SET in string literal should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("SQL containing 'OFFSET' should pass (contains SET substring)")
    void testOffsetKeyword_shouldPass() {
      String sql = "SELECT * FROM users LIMIT 10 OFFSET 20";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "OFFSET keyword should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("SQL containing 'RESET' should pass (contains SET substring)")
    void testResetKeyword_shouldPass() {
      // RESET is a different command, not SET
      String sql = "SELECT * FROM users WHERE status = 'RESET'";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "RESET in string should pass");
      assertEquals(0, result.getViolations().size());
    }
  }

  // ==================== Configuration Tests ====================

  @Nested
  @DisplayName("Configuration Tests")
  class ConfigurationTests {

    @Test
    @DisplayName("Default config should be enabled with MEDIUM risk level")
    void testDefaultConfig() {
      SetStatementConfig defaultConfig = new SetStatementConfig();
      assertTrue(defaultConfig.isEnabled(), "Should be enabled by default");
      assertEquals(RiskLevel.MEDIUM, defaultConfig.getRiskLevel(), "Should have MEDIUM risk level");
    }

    @Test
    @DisplayName("Config with enabled=false should disable checker")
    void testDisabledConfig() {
      SetStatementConfig disabledConfig = new SetStatementConfig(false);
      assertFalse(disabledConfig.isEnabled(), "Should be disabled");
    }

    @Test
    @DisplayName("isEnabled should return config state")
    void testIsEnabled() {
      SetStatementConfig enabledConfig = new SetStatementConfig(true);
      SetStatementChecker enabledChecker = new SetStatementChecker(enabledConfig);
      assertTrue(enabledChecker.isEnabled(), "Checker should be enabled");

      SetStatementConfig disabledConfig = new SetStatementConfig(false);
      SetStatementChecker disabledChecker = new SetStatementChecker(disabledConfig);
      assertFalse(disabledChecker.isEnabled(), "Checker should be disabled");
    }
  }

  // ==================== Violation Message Tests ====================

  @Nested
  @DisplayName("Violation Message Tests")
  class ViolationMessageTests {

    @Test
    @DisplayName("Violation message should contain variable name for autocommit")
    void testViolationMessageContainsVariableName_autocommit() {
      String sql = "SET autocommit = 0";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed());
      String message = result.getViolations().get(0).getMessage();
      assertTrue(message.contains("autocommit") || message.contains("SET"),
          "Violation message should contain variable name or SET keyword");
    }

    @Test
    @DisplayName("Violation message should contain variable name for sql_mode")
    void testViolationMessageContainsVariableName_sqlMode() {
      String sql = "SET sql_mode = 'STRICT_TRANS_TABLES'";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed());
      String message = result.getViolations().get(0).getMessage();
      assertTrue(message.contains("sql_mode") || message.contains("SET"),
          "Violation message should contain variable name or SET keyword");
    }

    @Test
    @DisplayName("Violation message should contain variable name for user variable")
    void testViolationMessageContainsVariableName_userVariable() {
      String sql = "SET @my_var = 'test'";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed());
      String message = result.getViolations().get(0).getMessage();
      assertTrue(message.contains("@my_var") || message.contains("SET"),
          "Violation message should contain variable name or SET keyword");
    }
  }

  // ==================== Multi-Dialect Tests ====================

  @Nested
  @DisplayName("Multi-Dialect Tests")
  class MultiDialectTests {

    @Test
    @DisplayName("MySQL: SET autocommit should fail")
    void testMySqlSetAutocommit_shouldFail() {
      String sql = "SET autocommit = 0";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "MySQL SET autocommit should fail");
    }

    @Test
    @DisplayName("MySQL: SET sql_mode should fail")
    void testMySqlSetSqlMode_shouldFail() {
      String sql = "SET sql_mode = ''";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "MySQL SET sql_mode should fail");
    }

    @Test
    @DisplayName("PostgreSQL: SET search_path should fail")
    void testPostgresSetSearchPath_shouldFail() {
      String sql = "SET search_path TO myschema";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "PostgreSQL SET search_path should fail");
    }

    @Test
    @DisplayName("PostgreSQL: SET client_encoding should fail")
    void testPostgresSetClientEncoding_shouldFail() {
      String sql = "SET client_encoding = 'UTF8'";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "PostgreSQL SET client_encoding should fail");
    }

    @Test
    @DisplayName("MySQL: UPDATE SET should pass (not SET statement)")
    void testMySqlUpdateSet_shouldPass() {
      String sql = "UPDATE users SET name = 'test', status = 'active' WHERE id = 1";
      SqlContext context = createContext(sql, SqlCommandType.UPDATE);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "MySQL UPDATE SET should pass");
    }

    @Test
    @DisplayName("PostgreSQL: UPDATE SET should pass (not SET statement)")
    void testPostgresUpdateSet_shouldPass() {
      String sql = "UPDATE users SET name = 'test' WHERE id = 1";
      SqlContext context = createContext(sql, SqlCommandType.UPDATE);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "PostgreSQL UPDATE SET should pass");
    }
  }

  // ==================== Helper Methods ====================

  /**
   * Creates a SqlContext with parsed statement for testing.
   * Used for SQL that JSqlParser can parse (SELECT, INSERT, UPDATE, DELETE).
   */
  private SqlContext createContext(String sql, SqlCommandType type) {
    try {
      Statement statement = CCJSqlParserUtil.parse(sql);
      return SqlContext.builder()
          .sql(sql)
          .type(type)
          .executionLayer(ExecutionLayer.MYBATIS)
          .statementId("com.example.TestMapper.testMethod")
          .statement(statement)
          .build();
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse SQL: " + sql, e);
    }
  }

  /**
   * Creates a SqlContext with raw SQL only (no parsed statement).
   * Used for SET statements that JSqlParser may not fully parse.
   * The checker uses pattern matching on the raw SQL string for detection.
   */
  private SqlContext createRawContext(String sql) {
    try {
      // Parse a simple SELECT to get a valid Statement object
      // The actual detection is done via pattern matching on the raw SQL string
      Statement statement = CCJSqlParserUtil.parse("SELECT 1");
      return SqlContext.builder()
          .sql(sql)  // Use the original SQL with SET statement
          .type(SqlCommandType.SELECT)  // Type doesn't matter for SET detection
          .executionLayer(ExecutionLayer.MYBATIS)
          .statementId("com.example.TestMapper.testMethod")
          .statement(statement)
          .build();
    } catch (Exception e) {
      throw new RuntimeException("Failed to create raw context", e);
    }
  }

}
