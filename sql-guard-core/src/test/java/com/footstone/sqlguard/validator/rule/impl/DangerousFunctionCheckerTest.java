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

import java.util.Arrays;
import java.util.Collections;

/**
 * Test class for DangerousFunctionChecker.
 *
 * <p>Verifies that DangerousFunctionChecker correctly detects dangerous database functions
 * (load_file, sys_exec, sleep, etc.) that enable file operations, OS command execution,
 * and DoS attacks.</p>
 *
 * <p><strong>Test Categories:</strong></p>
 * <ul>
 *   <li>PASS tests (≥5): Safe functions like MAX/SUM/CONCAT/COUNT, normal queries</li>
 *   <li>FAIL tests (≥10): Dangerous functions in various positions (SELECT, WHERE, nested)</li>
 *   <li>边界 tests (≥3): Empty deniedFunctions, nested functions, subqueries</li>
 * </ul>
 *
 * <p><strong>Multi-Database Coverage:</strong></p>
 * <ul>
 *   <li>MySQL: load_file, sleep, benchmark</li>
 *   <li>Oracle: sys_exec (UDF)</li>
 *   <li>PostgreSQL: pg_sleep</li>
 * </ul>
 */
@DisplayName("DangerousFunctionChecker Tests")
class DangerousFunctionCheckerTest {

  private DangerousFunctionChecker checker;
  private DangerousFunctionConfig config;

  @BeforeEach
  void setUp() {
    config = new DangerousFunctionConfig();
    checker = new DangerousFunctionChecker(config);
  }

  // ==================== PASS Tests (≥5) ====================

  @Nested
  @DisplayName("PASS Tests - Safe functions that should be allowed")
  class PassTests {

    @Test
    @DisplayName("Aggregate function MAX should pass")
    void testMaxFunction_shouldPass() {
      String sql = "SELECT MAX(id) FROM users";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "MAX function should pass");
      assertEquals(RiskLevel.SAFE, result.getRiskLevel());
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Aggregate function SUM should pass")
    void testSumFunction_shouldPass() {
      String sql = "SELECT SUM(amount) FROM orders WHERE status = 'completed'";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "SUM function should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Aggregate function COUNT should pass")
    void testCountFunction_shouldPass() {
      String sql = "SELECT COUNT(*) FROM users WHERE status = 'active'";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "COUNT function should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("String function CONCAT should pass")
    void testConcatFunction_shouldPass() {
      String sql = "SELECT CONCAT(first_name, ' ', last_name) AS full_name FROM users";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "CONCAT function should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Date function NOW should pass")
    void testNowFunction_shouldPass() {
      String sql = "SELECT NOW(), created_at FROM users WHERE id = 1";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "NOW function should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Multiple safe functions should pass")
    void testMultipleSafeFunctions_shouldPass() {
      String sql = "SELECT MAX(id), MIN(id), AVG(age), COUNT(*) FROM users GROUP BY department";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Multiple safe functions should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Query without functions should pass")
    void testQueryWithoutFunctions_shouldPass() {
      String sql = "SELECT id, name, email FROM users WHERE status = 'active'";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Query without functions should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Disabled checker should skip validation")
    void testDisabledChecker_shouldSkip() {
      DangerousFunctionConfig disabledConfig = new DangerousFunctionConfig(false);
      DangerousFunctionChecker disabledChecker = new DangerousFunctionChecker(disabledConfig);

      String sql = "SELECT load_file('/etc/passwd')";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      disabledChecker.check(context, result);

      assertTrue(result.isPassed(), "Disabled checker should skip validation");
      assertEquals(0, result.getViolations().size());
    }
  }

  // ==================== FAIL Tests (≥10) ====================

  @Nested
  @DisplayName("FAIL Tests - Dangerous functions that should be blocked")
  class FailTests {

    @Test
    @DisplayName("load_file in SELECT clause should fail")
    void testLoadFileInSelect_shouldFail() {
      String sql = "SELECT load_file('/etc/passwd')";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "load_file should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
      assertEquals(1, result.getViolations().size());
      assertTrue(result.getViolations().get(0).getMessage().toLowerCase().contains("load_file"));
    }

    @Test
    @DisplayName("sleep in WHERE clause should fail")
    void testSleepInWhere_shouldFail() {
      String sql = "SELECT * FROM users WHERE sleep(5) = 0";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "sleep in WHERE should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().toLowerCase().contains("sleep"));
    }

    @Test
    @DisplayName("benchmark function should fail")
    void testBenchmark_shouldFail() {
      String sql = "SELECT benchmark(10000000, SHA1('test'))";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "benchmark should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("sys_exec function should fail")
    void testSysExec_shouldFail() {
      String sql = "SELECT sys_exec('whoami')";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "sys_exec should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("sys_eval function should fail")
    void testSysEval_shouldFail() {
      String sql = "SELECT sys_eval('cat /etc/passwd')";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "sys_eval should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("Nested load_file in CONCAT should fail")
    void testNestedLoadFileInConcat_shouldFail() {
      String sql = "SELECT CONCAT(load_file('/etc/passwd'), 'suffix')";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Nested load_file should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("LOAD_FILE uppercase should fail (case insensitive)")
    void testLoadFileUppercase_shouldFail() {
      String sql = "SELECT LOAD_FILE('/etc/passwd')";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "LOAD_FILE uppercase should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("Load_File mixed case should fail (case insensitive)")
    void testLoadFileMixedCase_shouldFail() {
      String sql = "SELECT Load_File('/etc/passwd')";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Load_File mixed case should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("sleep in UPDATE WHERE should fail")
    void testSleepInUpdateWhere_shouldFail() {
      String sql = "UPDATE users SET status = 'inactive' WHERE sleep(10) = 0";
      SqlContext context = createContext(sql, SqlCommandType.UPDATE);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "sleep in UPDATE WHERE should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("sleep in DELETE WHERE should fail")
    void testSleepInDeleteWhere_shouldFail() {
      String sql = "DELETE FROM users WHERE sleep(5) = 0";
      SqlContext context = createContext(sql, SqlCommandType.DELETE);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "sleep in DELETE WHERE should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("pg_sleep (PostgreSQL) should fail")
    void testPgSleep_shouldFail() {
      String sql = "SELECT pg_sleep(10)";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "pg_sleep should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("Dangerous function in HAVING clause should fail")
    void testDangerousFunctionInHaving_shouldFail() {
      String sql = "SELECT department, COUNT(*) FROM employees GROUP BY department HAVING sleep(1) = 0";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Dangerous function in HAVING should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }
  }

  // ==================== 边界 Tests (≥3) ====================

  @Nested
  @DisplayName("边界 Tests - Edge cases and boundary conditions")
  class EdgeCaseTests {

    @Test
    @DisplayName("Empty deniedFunctions list should allow all functions")
    void testEmptyDeniedFunctions_shouldAllowAll() {
      DangerousFunctionConfig emptyConfig = new DangerousFunctionConfig(true, Collections.emptyList());
      DangerousFunctionChecker emptyChecker = new DangerousFunctionChecker(emptyConfig);

      String sql = "SELECT load_file('/etc/passwd')";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      emptyChecker.check(context, result);

      assertTrue(result.isPassed(), "Empty deniedFunctions should allow all");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Deeply nested dangerous function should fail")
    void testDeeplyNestedDangerousFunction_shouldFail() {
      // Function nested inside multiple levels
      String sql = "SELECT UPPER(LOWER(CONCAT(load_file('/etc/passwd'), 'x')))";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Deeply nested dangerous function should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("Dangerous function in subquery should fail")
    void testDangerousFunctionInSubquery_shouldFail() {
      String sql = "SELECT * FROM users WHERE id IN (SELECT load_file('/etc/passwd'))";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Dangerous function in subquery should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("Dangerous function in CASE WHEN should fail")
    void testDangerousFunctionInCaseWhen_shouldFail() {
      String sql = "SELECT CASE WHEN sleep(1) > 0 THEN 'a' ELSE 'b' END FROM users";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Dangerous function in CASE WHEN should fail");
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
    @DisplayName("Custom deniedFunctions list should work")
    void testCustomDeniedFunctions_shouldWork() {
      DangerousFunctionConfig customConfig = new DangerousFunctionConfig(
          true,
          Arrays.asList("custom_danger", "another_bad")
      );
      DangerousFunctionChecker customChecker = new DangerousFunctionChecker(customConfig);

      String sql = "SELECT custom_danger()";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      customChecker.check(context, result);

      assertFalse(result.isPassed(), "Custom dangerous function should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("Multiple dangerous functions should report multiple violations")
    void testMultipleDangerousFunctions_shouldReportMultiple() {
      String sql = "SELECT load_file('/etc/passwd'), sleep(5)";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Multiple dangerous functions should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
      assertEquals(2, result.getViolations().size(), "Should report 2 violations");
    }
  }

  // ==================== Configuration Tests ====================

  @Nested
  @DisplayName("Configuration Tests")
  class ConfigurationTests {

    @Test
    @DisplayName("Default config should be enabled with CRITICAL risk level")
    void testDefaultConfig() {
      DangerousFunctionConfig defaultConfig = new DangerousFunctionConfig();
      assertTrue(defaultConfig.isEnabled(), "Should be enabled by default");
      assertEquals(RiskLevel.CRITICAL, defaultConfig.getRiskLevel(), "Should have CRITICAL risk level");
      assertFalse(defaultConfig.getDeniedFunctions().isEmpty(), "Should have default denied functions");
    }

    @Test
    @DisplayName("Default denied functions should include load_file")
    void testDefaultDeniedFunctionsIncludesLoadFile() {
      DangerousFunctionConfig defaultConfig = new DangerousFunctionConfig();
      assertTrue(defaultConfig.isDenied("load_file"), "Should deny load_file");
      assertTrue(defaultConfig.isDenied("LOAD_FILE"), "Should deny LOAD_FILE (case insensitive)");
    }

    @Test
    @DisplayName("Default denied functions should include sleep")
    void testDefaultDeniedFunctionsIncludesSleep() {
      DangerousFunctionConfig defaultConfig = new DangerousFunctionConfig();
      assertTrue(defaultConfig.isDenied("sleep"), "Should deny sleep");
    }

    @Test
    @DisplayName("Default denied functions should include benchmark")
    void testDefaultDeniedFunctionsIncludesBenchmark() {
      DangerousFunctionConfig defaultConfig = new DangerousFunctionConfig();
      assertTrue(defaultConfig.isDenied("benchmark"), "Should deny benchmark");
    }

    @Test
    @DisplayName("addDeniedFunction should work")
    void testAddDeniedFunction() {
      DangerousFunctionConfig testConfig = new DangerousFunctionConfig();
      testConfig.addDeniedFunction("custom_func");
      assertTrue(testConfig.isDenied("custom_func"), "Should deny added function");
      assertTrue(testConfig.isDenied("CUSTOM_FUNC"), "Should deny added function (case insensitive)");
    }

    @Test
    @DisplayName("removeDeniedFunction should work")
    void testRemoveDeniedFunction() {
      DangerousFunctionConfig testConfig = new DangerousFunctionConfig();
      assertTrue(testConfig.isDenied("sleep"), "sleep should be denied initially");
      testConfig.removeDeniedFunction("sleep");
      assertFalse(testConfig.isDenied("sleep"), "sleep should not be denied after removal");
    }

    @Test
    @DisplayName("Config with enabled=false should disable checker")
    void testDisabledConfig() {
      DangerousFunctionConfig disabledConfig = new DangerousFunctionConfig(false);
      assertFalse(disabledConfig.isEnabled(), "Should be disabled");
    }

    @Test
    @DisplayName("isEnabled should return config state")
    void testIsEnabled() {
      DangerousFunctionConfig enabledConfig = new DangerousFunctionConfig(true);
      DangerousFunctionChecker enabledChecker = new DangerousFunctionChecker(enabledConfig);
      assertTrue(enabledChecker.isEnabled(), "Checker should be enabled");

      DangerousFunctionConfig disabledConfig = new DangerousFunctionConfig(false);
      DangerousFunctionChecker disabledChecker = new DangerousFunctionChecker(disabledConfig);
      assertFalse(disabledChecker.isEnabled(), "Checker should be disabled");
    }
  }

  // ==================== Multi-Database Tests ====================

  @Nested
  @DisplayName("Multi-Database Tests")
  class MultiDatabaseTests {

    @Test
    @DisplayName("MySQL: load_file should fail")
    void testMySqlLoadFile_shouldFail() {
      String sql = "SELECT load_file('/var/lib/mysql-files/data.csv')";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "MySQL load_file should fail");
    }

    @Test
    @DisplayName("MySQL: sleep should fail")
    void testMySqlSleep_shouldFail() {
      String sql = "SELECT sleep(10)";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "MySQL sleep should fail");
    }

    @Test
    @DisplayName("PostgreSQL: pg_sleep should fail")
    void testPostgreSqlPgSleep_shouldFail() {
      String sql = "SELECT pg_sleep(10)";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "PostgreSQL pg_sleep should fail");
    }

    @Test
    @DisplayName("Oracle UDF: sys_exec should fail")
    void testOracleSysExec_shouldFail() {
      String sql = "SELECT sys_exec('id')";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Oracle sys_exec should fail");
    }
  }

  // ==================== Violation Message Tests ====================

  @Nested
  @DisplayName("Violation Message Tests")
  class ViolationMessageTests {

    @Test
    @DisplayName("Violation message should contain function name")
    void testViolationMessageContainsFunctionName() {
      String sql = "SELECT load_file('/etc/passwd')";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed());
      String message = result.getViolations().get(0).getMessage();
      assertTrue(message.toLowerCase().contains("load_file"),
          "Violation message should contain function name");
    }

    @Test
    @DisplayName("Violation should have suggestion")
    void testViolationHasSuggestion() {
      String sql = "SELECT sleep(5)";
      SqlContext context = createContext(sql, SqlCommandType.SELECT);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed());
      String suggestion = result.getViolations().get(0).getSuggestion();
      assertTrue(suggestion != null && !suggestion.isEmpty(),
          "Violation should have a suggestion");
    }
  }

  // ==================== Helper Methods ====================

  /**
   * Creates a SqlContext with parsed statement for testing.
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
