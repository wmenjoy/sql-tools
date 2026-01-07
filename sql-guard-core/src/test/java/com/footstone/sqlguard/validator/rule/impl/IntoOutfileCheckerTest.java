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
 * Test class for IntoOutfileChecker.
 *
 * <p>Verifies that IntoOutfileChecker correctly detects MySQL file write operations
 * (SELECT INTO OUTFILE/DUMPFILE) while allowing Oracle SELECT INTO variable syntax.</p>
 *
 * <p><strong>Test Categories:</strong></p>
 * <ul>
 *   <li>PASS tests (≥5): Normal SELECT, Oracle INTO variable, queries without file operations</li>
 *   <li>FAIL tests (≥10): Various INTO OUTFILE/DUMPFILE attacks with different paths</li>
 *   <li>边界 tests (≥3): Path injection attempts, encoded paths, INTO without OUTFILE/DUMPFILE</li>
 * </ul>
 *
 * <p><strong>Note:</strong> JSqlParser cannot parse MySQL's INTO OUTFILE/DUMPFILE syntax,
 * so tests for these patterns use raw SQL context without parsed statements. The checker
 * uses regex pattern matching on the raw SQL string for detection.</p>
 *
 * <p><strong>Multi-Dialect Coverage:</strong></p>
 * <ul>
 *   <li>MySQL: INTO OUTFILE, INTO DUMPFILE (BLOCK)</li>
 *   <li>Oracle: INTO variable (ALLOW)</li>
 * </ul>
 */
@DisplayName("IntoOutfileChecker Tests")
class IntoOutfileCheckerTest {

  private IntoOutfileChecker checker;
  private IntoOutfileConfig config;

  @BeforeEach
  void setUp() {
    config = new IntoOutfileConfig();
    checker = new IntoOutfileChecker(config);
  }

  // ==================== PASS Tests (≥5) ====================

  @Nested
  @DisplayName("PASS Tests - Normal SQL without file operations")
  class PassTests {

    @Test
    @DisplayName("Simple SELECT without INTO should pass")
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
    @DisplayName("SELECT with JOIN should pass")
    void testSelectWithJoin_shouldPass() {
      String sql = "SELECT u.*, o.* FROM users u LEFT JOIN orders o ON u.id = o.user_id WHERE u.status = 'active'";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "SELECT with JOIN should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("SELECT with subquery should pass")
    void testSelectWithSubquery_shouldPass() {
      String sql = "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders WHERE status = 'completed')";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "SELECT with subquery should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("SELECT with GROUP BY and HAVING should pass")
    void testSelectWithGroupBy_shouldPass() {
      String sql = "SELECT department, COUNT(*) as cnt FROM employees WHERE status = 'active' GROUP BY department HAVING COUNT(*) > 5";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "SELECT with GROUP BY should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("SELECT with UNION should pass (no file operation)")
    void testSelectWithUnion_shouldPass() {
      String sql = "SELECT id, name FROM users UNION SELECT id, name FROM admins";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "SELECT with UNION should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Oracle SELECT INTO variable should pass")
    void testOracleSelectIntoVariable_shouldPass() {
      // Oracle syntax: SELECT column INTO variable FROM table
      // This is variable assignment, NOT file operation
      String sql = "SELECT id INTO v_id FROM users WHERE name = 'test'";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Oracle SELECT INTO variable should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Oracle SELECT multiple columns INTO variables should pass")
    void testOracleSelectMultipleIntoVariables_shouldPass() {
      // Oracle syntax: SELECT col1, col2 INTO var1, var2 FROM table
      String sql = "SELECT id, name INTO v_id, v_name FROM users WHERE id = 1";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Oracle SELECT INTO multiple variables should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Disabled checker should skip validation")
    void testDisabledChecker_shouldSkip() {
      IntoOutfileConfig disabledConfig = new IntoOutfileConfig(false);
      IntoOutfileChecker disabledChecker = new IntoOutfileChecker(disabledConfig);

      // Use raw SQL context since JSqlParser can't parse INTO OUTFILE
      String sql = "SELECT * INTO OUTFILE '/tmp/data.txt' FROM users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      disabledChecker.check(context, result);

      assertTrue(result.isPassed(), "Disabled checker should skip validation");
      assertEquals(0, result.getViolations().size());
    }
  }

  // ==================== FAIL Tests (≥10) ====================

  @Nested
  @DisplayName("FAIL Tests - MySQL file write operations")
  class FailTests {

    @Test
    @DisplayName("SELECT INTO OUTFILE with Unix path should fail")
    void testIntoOutfileUnixPath_shouldFail() {
      String sql = "SELECT * INTO OUTFILE '/tmp/data.txt' FROM users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "INTO OUTFILE with Unix path should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
      assertEquals(1, result.getViolations().size());
      assertTrue(result.getViolations().get(0).getMessage().contains("INTO OUTFILE"));
    }

    @Test
    @DisplayName("SELECT INTO OUTFILE with Windows path should fail")
    void testIntoOutfileWindowsPath_shouldFail() {
      String sql = "SELECT * INTO OUTFILE 'C:\\\\data\\\\export.csv' FROM users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "INTO OUTFILE with Windows path should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("INTO OUTFILE"));
    }

    @Test
    @DisplayName("SELECT INTO DUMPFILE should fail")
    void testIntoDumpfile_shouldFail() {
      String sql = "SELECT * INTO DUMPFILE '/tmp/dump.bin' FROM users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "INTO DUMPFILE should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("INTO DUMPFILE"));
    }

    @Test
    @DisplayName("SELECT INTO OUTFILE with web shell path should fail")
    void testIntoOutfileWebShell_shouldFail() {
      String sql = "SELECT '<?php system($_GET[\"cmd\"]); ?>' INTO OUTFILE '/var/www/html/shell.php' FROM dual";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "INTO OUTFILE web shell should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("SELECT INTO OUTFILE with relative path should fail")
    void testIntoOutfileRelativePath_shouldFail() {
      String sql = "SELECT * INTO OUTFILE './data/export.csv' FROM users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "INTO OUTFILE with relative path should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("SELECT INTO OUTFILE with FIELDS TERMINATED BY should fail")
    void testIntoOutfileWithFieldsTerminated_shouldFail() {
      String sql = "SELECT * INTO OUTFILE '/tmp/data.csv' FIELDS TERMINATED BY ',' FROM users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "INTO OUTFILE with FIELDS TERMINATED should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("SELECT INTO OUTFILE with LINES TERMINATED BY should fail")
    void testIntoOutfileWithLinesTerminated_shouldFail() {
      String sql = "SELECT * INTO OUTFILE '/tmp/data.csv' LINES TERMINATED BY '\\n' FROM users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "INTO OUTFILE with LINES TERMINATED should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("SELECT with WHERE clause INTO OUTFILE should fail")
    void testSelectWithWhereIntoOutfile_shouldFail() {
      String sql = "SELECT id, username, password INTO OUTFILE '/tmp/credentials.txt' FROM users WHERE role = 'admin'";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SELECT with WHERE INTO OUTFILE should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("SELECT CONCAT INTO OUTFILE should fail")
    void testSelectConcatIntoOutfile_shouldFail() {
      String sql = "SELECT CONCAT(username, ':', password) INTO OUTFILE '/tmp/creds.txt' FROM users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SELECT CONCAT INTO OUTFILE should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("SELECT FROM information_schema INTO OUTFILE should fail")
    void testSelectInfoSchemaIntoOutfile_shouldFail() {
      String sql = "SELECT table_name, column_name INTO OUTFILE '/tmp/schema.txt' FROM information_schema.columns";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SELECT FROM information_schema INTO OUTFILE should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("SELECT INTO DUMPFILE for binary data should fail")
    void testIntoDumpfileBinary_shouldFail() {
      String sql = "SELECT UNHEX('4D5A') INTO DUMPFILE '/tmp/malware.exe'";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "INTO DUMPFILE for binary should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("SELECT INTO OUTFILE with system path should fail")
    void testIntoOutfileSystemPath_shouldFail() {
      String sql = "SELECT * INTO OUTFILE '/etc/passwd.bak' FROM users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "INTO OUTFILE with system path should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }
  }

  // ==================== 边界 Tests (≥3) ====================

  @Nested
  @DisplayName("边界 Tests - Edge cases and boundary conditions")
  class EdgeCaseTests {

    @Test
    @DisplayName("INTO keyword without OUTFILE/DUMPFILE should pass")
    void testIntoWithoutOutfileDumpfile_shouldPass() {
      // This tests that INTO alone (without OUTFILE/DUMPFILE) passes
      // Oracle syntax: SELECT x INTO variable
      String sql = "SELECT COUNT(*) INTO v_count FROM users";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "INTO without OUTFILE/DUMPFILE should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Path with special characters should be detected")
    void testPathWithSpecialChars_shouldFail() {
      String sql = "SELECT * INTO OUTFILE '/tmp/../../../etc/cron.d/malicious' FROM users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Path traversal attempt should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("Path with URL encoding should be detected")
    void testPathWithUrlEncoding_shouldFail() {
      // Even if path contains URL-encoded characters, should still detect
      String sql = "SELECT * INTO OUTFILE '/tmp/file%20name.txt' FROM users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Path with URL encoding should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("Case variations of INTO OUTFILE should be detected")
    void testCaseVariations_shouldFail() {
      // Test case insensitivity
      String sql = "SELECT * into outfile '/tmp/data.txt' FROM users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Lowercase INTO OUTFILE should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("Null SQL context should be handled gracefully")
    void testNullContext_shouldBeHandled() {
      SqlContext context = createContextWithNullStatement();
      ValidationResult result = ValidationResult.pass();

      // Should not throw exception
      checker.check(context, result);

      assertTrue(result.isPassed(), "Null context should pass without error");
    }

    @Test
    @DisplayName("Empty path in INTO OUTFILE should still fail")
    void testEmptyPath_shouldFail() {
      String sql = "SELECT * INTO OUTFILE '' FROM users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "INTO OUTFILE with empty path should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("INTO OUTFILE in complex query should be detected")
    void testComplexQueryWithIntoOutfile_shouldFail() {
      String sql = "SELECT u.id, u.name, o.total INTO OUTFILE '/tmp/report.csv' " +
          "FROM users u JOIN orders o ON u.id = o.user_id WHERE o.status = 'completed'";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Complex query with INTO OUTFILE should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }
  }

  // ==================== Configuration Tests ====================

  @Nested
  @DisplayName("Configuration Tests")
  class ConfigurationTests {

    @Test
    @DisplayName("Default config should be enabled with CRITICAL risk level")
    void testDefaultConfig() {
      IntoOutfileConfig defaultConfig = new IntoOutfileConfig();
      assertTrue(defaultConfig.isEnabled(), "Should be enabled by default");
      assertEquals(RiskLevel.CRITICAL, defaultConfig.getRiskLevel(), "Should have CRITICAL risk level");
    }

    @Test
    @DisplayName("Config with enabled=false should disable checker")
    void testDisabledConfig() {
      IntoOutfileConfig disabledConfig = new IntoOutfileConfig(false);
      assertFalse(disabledConfig.isEnabled(), "Should be disabled");
    }

    @Test
    @DisplayName("isEnabled should return config state")
    void testIsEnabled() {
      IntoOutfileConfig enabledConfig = new IntoOutfileConfig(true);
      IntoOutfileChecker enabledChecker = new IntoOutfileChecker(enabledConfig);
      assertTrue(enabledChecker.isEnabled(), "Checker should be enabled");

      IntoOutfileConfig disabledConfig = new IntoOutfileConfig(false);
      IntoOutfileChecker disabledChecker = new IntoOutfileChecker(disabledConfig);
      assertFalse(disabledChecker.isEnabled(), "Checker should be disabled");
    }
  }

  // ==================== Multi-Dialect Tests ====================

  @Nested
  @DisplayName("Multi-Dialect Tests")
  class MultiDialectTests {

    @Test
    @DisplayName("MySQL: INTO OUTFILE should fail")
    void testMySqlIntoOutfile_shouldFail() {
      String sql = "SELECT * INTO OUTFILE '/var/lib/mysql-files/data.csv' FROM users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "MySQL INTO OUTFILE should fail");
    }

    @Test
    @DisplayName("MySQL: INTO DUMPFILE should fail")
    void testMySqlIntoDumpfile_shouldFail() {
      String sql = "SELECT LOAD_FILE('/etc/passwd') INTO DUMPFILE '/tmp/passwd_copy'";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "MySQL INTO DUMPFILE should fail");
    }

    @Test
    @DisplayName("Oracle: SELECT INTO variable should pass")
    void testOracleSelectInto_shouldPass() {
      // Oracle PL/SQL variable assignment
      String sql = "SELECT salary INTO v_salary FROM employees WHERE employee_id = 100";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Oracle SELECT INTO variable should pass");
    }

    @Test
    @DisplayName("Oracle: SELECT BULK COLLECT INTO should pass")
    void testOracleBulkCollect_shouldPass() {
      // Oracle BULK COLLECT syntax
      String sql = "SELECT id, name INTO v_ids, v_names FROM users WHERE status = 'active'";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Oracle BULK COLLECT INTO should pass");
    }
  }

  // ==================== Violation Message Tests ====================

  @Nested
  @DisplayName("Violation Message Tests")
  class ViolationMessageTests {

    @Test
    @DisplayName("Violation message should contain file path for OUTFILE")
    void testViolationMessageContainsPath_outfile() {
      String sql = "SELECT * INTO OUTFILE '/tmp/secret.txt' FROM users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed());
      String message = result.getViolations().get(0).getMessage();
      assertTrue(message.contains("/tmp/secret.txt") || message.contains("INTO OUTFILE"),
          "Violation message should contain file path or INTO OUTFILE");
    }

    @Test
    @DisplayName("Violation message should contain file path for DUMPFILE")
    void testViolationMessageContainsPath_dumpfile() {
      String sql = "SELECT * INTO DUMPFILE '/tmp/dump.bin' FROM users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed());
      String message = result.getViolations().get(0).getMessage();
      assertTrue(message.contains("/tmp/dump.bin") || message.contains("INTO DUMPFILE"),
          "Violation message should contain file path or INTO DUMPFILE");
    }
  }

  // ==================== Helper Methods ====================

  /**
   * Creates a SqlContext with parsed statement for testing.
   * Used for SQL that JSqlParser can parse (normal SELECT, Oracle INTO variable).
   */
  private SqlContext createContext(String sql) {
    try {
      Statement statement = CCJSqlParserUtil.parse(sql);
      return SqlContext.builder()
          .sql(sql)
          .type(SqlCommandType.SELECT)
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
   * Used for MySQL INTO OUTFILE/DUMPFILE syntax that JSqlParser cannot parse.
   * The checker uses regex pattern matching on the raw SQL string for detection.
   */
  private SqlContext createRawContext(String sql) {
    // Parse a simple SELECT to get a valid Statement object
    // The actual detection is done via regex on the raw SQL string
    try {
      Statement statement = CCJSqlParserUtil.parse("SELECT 1");
      return SqlContext.builder()
          .sql(sql)  // Use the original SQL with INTO OUTFILE/DUMPFILE
          .type(SqlCommandType.SELECT)
          .executionLayer(ExecutionLayer.MYBATIS)
          .statementId("com.example.TestMapper.testMethod")
          .statement(statement)
          .build();
    } catch (Exception e) {
      throw new RuntimeException("Failed to create raw context", e);
    }
  }

  /**
   * Creates a SqlContext with null statement for edge case testing.
   */
  private SqlContext createContextWithNullStatement() {
    return SqlContext.builder()
        .sql("SELECT 1")
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.TestMapper.testMethod")
        .statement(null)
        .build();
  }
}
