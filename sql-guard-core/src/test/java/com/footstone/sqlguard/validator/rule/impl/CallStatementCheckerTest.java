package com.footstone.sqlguard.validator.rule.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.config.ViolationStrategy;
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
 * Test class for CallStatementChecker.
 *
 * <p>Verifies that CallStatementChecker correctly detects stored procedure calls
 * (CALL/EXECUTE/EXEC) while allowing normal SQL statements with function calls.</p>
 *
 * <p><strong>Test Categories:</strong></p>
 * <ul>
 *   <li>PASS tests (≥5): Normal SELECT/INSERT/UPDATE/DELETE, function calls in expressions</li>
 *   <li>FAIL tests (≥10): MySQL CALL, Oracle EXECUTE, SQL Server EXEC with various parameters</li>
 *   <li>边界 tests (≥3): Case variations, special characters in procedure names, empty parameters</li>
 * </ul>
 *
 * <p><strong>Multi-Dialect Coverage:</strong></p>
 * <ul>
 *   <li>MySQL: CALL procedure_name(params)</li>
 *   <li>Oracle: EXECUTE procedure_name(params)</li>
 *   <li>SQL Server: EXEC procedure_name params</li>
 * </ul>
 */
@DisplayName("CallStatementChecker Tests")
class CallStatementCheckerTest {

  private CallStatementChecker checker;
  private CallStatementConfig config;

  @BeforeEach
  void setUp() {
    config = new CallStatementConfig();
    checker = new CallStatementChecker(config);
  }

  // ==================== PASS Tests (≥5) ====================

  @Nested
  @DisplayName("PASS Tests - Normal SQL without procedure calls")
  class PassTests {

    @Test
    @DisplayName("Simple SELECT should pass")
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
    @DisplayName("SELECT with aggregate functions should pass")
    void testSelectWithAggregateFunctions_shouldPass() {
      String sql = "SELECT MAX(id), MIN(id), COUNT(*), AVG(salary) FROM employees";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "SELECT with aggregate functions should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("SELECT with string functions should pass")
    void testSelectWithStringFunctions_shouldPass() {
      String sql = "SELECT UPPER(name), LOWER(email), CONCAT(first_name, ' ', last_name) FROM users";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "SELECT with string functions should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("INSERT statement should pass")
    void testInsertStatement_shouldPass() {
      String sql = "INSERT INTO users (name, email) VALUES ('test', 'test@example.com')";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "INSERT statement should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("UPDATE statement should pass")
    void testUpdateStatement_shouldPass() {
      String sql = "UPDATE users SET name = 'updated' WHERE id = 1";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "UPDATE statement should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("DELETE statement should pass")
    void testDeleteStatement_shouldPass() {
      String sql = "DELETE FROM users WHERE id = 1";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "DELETE statement should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("SELECT with date functions should pass")
    void testSelectWithDateFunctions_shouldPass() {
      String sql = "SELECT NOW(), CURDATE(), DATE_FORMAT(created_at, '%Y-%m-%d') FROM logs";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "SELECT with date functions should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Disabled checker should skip validation")
    void testDisabledChecker_shouldSkip() {
      CallStatementConfig disabledConfig = new CallStatementConfig(false);
      CallStatementChecker disabledChecker = new CallStatementChecker(disabledConfig);

      String sql = "CALL sp_dangerous_procedure()";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      disabledChecker.check(context, result);

      assertTrue(result.isPassed(), "Disabled checker should skip validation");
      assertEquals(0, result.getViolations().size());
    }
  }

  // ==================== FAIL Tests (≥10) ====================

  @Nested
  @DisplayName("FAIL Tests - Stored procedure calls")
  class FailTests {

    @Test
    @DisplayName("MySQL CALL without parameters should fail")
    void testMySqlCallWithoutParams_shouldFail() {
      String sql = "CALL sp_cleanup_old_data()";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "MySQL CALL should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
      assertEquals(1, result.getViolations().size());
      assertTrue(result.getViolations().get(0).getMessage().contains("CALL"));
      assertTrue(result.getViolations().get(0).getMessage().contains("sp_cleanup_old_data"));
    }

    @Test
    @DisplayName("MySQL CALL with parameters should fail")
    void testMySqlCallWithParams_shouldFail() {
      String sql = "CALL sp_user_create(1, 'test', 'test@example.com')";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "MySQL CALL with params should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("sp_user_create"));
    }

    @Test
    @DisplayName("Oracle EXECUTE without parameters should fail")
    void testOracleExecuteWithoutParams_shouldFail() {
      String sql = "EXECUTE sp_process_batch";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Oracle EXECUTE should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("EXECUTE"));
      assertTrue(result.getViolations().get(0).getMessage().contains("sp_process_batch"));
    }

    @Test
    @DisplayName("Oracle EXECUTE with parameters should fail")
    void testOracleExecuteWithParams_shouldFail() {
      String sql = "EXECUTE sp_update_inventory(100, 'SKU-001')";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Oracle EXECUTE with params should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("sp_update_inventory"));
    }

    @Test
    @DisplayName("SQL Server EXEC without parameters should fail")
    void testSqlServerExecWithoutParams_shouldFail() {
      String sql = "EXEC sp_refresh_cache";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SQL Server EXEC should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("EXEC"));
      assertTrue(result.getViolations().get(0).getMessage().contains("sp_refresh_cache"));
    }

    @Test
    @DisplayName("SQL Server EXEC with named parameters should fail")
    void testSqlServerExecWithNamedParams_shouldFail() {
      String sql = "EXEC sp_delete_user @user_id = 1";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SQL Server EXEC with named params should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("sp_delete_user"));
    }

    @Test
    @DisplayName("CALL with schema-qualified procedure name should fail")
    void testCallWithSchemaQualifiedName_shouldFail() {
      String sql = "CALL schema_name.sp_process_orders()";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "CALL with schema-qualified name should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("schema_name.sp_process_orders"));
    }

    @Test
    @DisplayName("EXECUTE with database.schema.procedure name should fail")
    void testExecuteWithFullyQualifiedName_shouldFail() {
      String sql = "EXECUTE db.dbo.sp_audit_log('action', 'user123')";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "EXECUTE with fully qualified name should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("db.dbo.sp_audit_log"));
    }

    @Test
    @DisplayName("CALL with numeric parameters should fail")
    void testCallWithNumericParams_shouldFail() {
      String sql = "CALL sp_calculate_discount(100.50, 0.15, 3)";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "CALL with numeric params should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("sp_calculate_discount"));
    }

    @Test
    @DisplayName("EXEC with multiple parameters should fail")
    void testExecWithMultipleParams_shouldFail() {
      String sql = "EXEC sp_send_notification @user_id = 1, @message = 'Hello', @priority = 'HIGH'";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "EXEC with multiple params should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("sp_send_notification"));
    }

    @Test
    @DisplayName("CALL with null parameter should fail")
    void testCallWithNullParam_shouldFail() {
      String sql = "CALL sp_update_record(NULL, 'value')";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "CALL with NULL param should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    @DisplayName("CALL with empty string parameter should fail")
    void testCallWithEmptyStringParam_shouldFail() {
      String sql = "CALL sp_log_event('')";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "CALL with empty string param should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }
  }

  // ==================== 边界 Tests (≥3) ====================

  @Nested
  @DisplayName("边界 Tests - Edge cases and boundary conditions")
  class EdgeCaseTests {

    @Test
    @DisplayName("Lowercase CALL should be detected")
    void testLowercaseCall_shouldFail() {
      String sql = "call sp_test_procedure()";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Lowercase CALL should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    @DisplayName("Mixed case Call should be detected")
    void testMixedCaseCall_shouldFail() {
      String sql = "Call Sp_Test_Procedure()";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Mixed case Call should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    @DisplayName("EXECUTE in lowercase should be detected")
    void testLowercaseExecute_shouldFail() {
      String sql = "execute sp_test";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Lowercase execute should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    @DisplayName("EXEC in lowercase should be detected")
    void testLowercaseExec_shouldFail() {
      String sql = "exec sp_test";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Lowercase exec should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    @DisplayName("Procedure name with underscores should be detected")
    void testProcedureNameWithUnderscores_shouldFail() {
      String sql = "CALL sp_user_account_management_v2()";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Procedure with underscores should fail");
      assertTrue(result.getViolations().get(0).getMessage().contains("sp_user_account_management_v2"));
    }

    @Test
    @DisplayName("Procedure name with numbers should be detected")
    void testProcedureNameWithNumbers_shouldFail() {
      String sql = "CALL proc123_test456()";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Procedure with numbers should fail");
      assertTrue(result.getViolations().get(0).getMessage().contains("proc123_test456"));
    }

    @Test
    @DisplayName("CALL with empty parameter list should fail")
    void testCallWithEmptyParamList_shouldFail() {
      String sql = "CALL sp_no_params()";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "CALL with empty () should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    @DisplayName("CALL without parentheses should fail")
    void testCallWithoutParentheses_shouldFail() {
      String sql = "CALL sp_simple_proc";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "CALL without () should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
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
    @DisplayName("CALL keyword in string literal should pass")
    void testCallInStringLiteral_shouldPass() {
      // CALL appears in a string, not as a statement
      String sql = "SELECT * FROM logs WHERE message = 'CALL sp_test()'";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "CALL in string literal should pass");
    }

    @Test
    @DisplayName("CALL with leading whitespace should be detected")
    void testCallWithLeadingWhitespace_shouldFail() {
      String sql = "   CALL sp_whitespace_test()";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "CALL with leading whitespace should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    @DisplayName("CALL with semicolon should be detected")
    void testCallWithSemicolon_shouldFail() {
      String sql = "CALL sp_test();";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "CALL with semicolon should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }
  }

  // ==================== Configuration Tests ====================

  @Nested
  @DisplayName("Configuration Tests")
  class ConfigurationTests {

    @Test
    @DisplayName("Default config should be enabled with HIGH risk level and WARN strategy")
    void testDefaultConfig() {
      CallStatementConfig defaultConfig = new CallStatementConfig();
      assertTrue(defaultConfig.isEnabled(), "Should be enabled by default");
      assertEquals(RiskLevel.HIGH, defaultConfig.getRiskLevel(), "Should have HIGH risk level");
      assertEquals(ViolationStrategy.WARN, defaultConfig.getViolationStrategy(), "Should have WARN strategy");
    }

    @Test
    @DisplayName("Config with enabled=false should disable checker")
    void testDisabledConfig() {
      CallStatementConfig disabledConfig = new CallStatementConfig(false);
      assertFalse(disabledConfig.isEnabled(), "Should be disabled");
      assertEquals(ViolationStrategy.WARN, disabledConfig.getViolationStrategy(), "Should still have WARN strategy");
    }

    @Test
    @DisplayName("Config with custom violation strategy")
    void testCustomViolationStrategy() {
      CallStatementConfig blockConfig = new CallStatementConfig(true, ViolationStrategy.BLOCK);
      assertTrue(blockConfig.isEnabled(), "Should be enabled");
      assertEquals(ViolationStrategy.BLOCK, blockConfig.getViolationStrategy(), "Should have BLOCK strategy");
    }

    @Test
    @DisplayName("isEnabled should return config state")
    void testIsEnabled() {
      CallStatementConfig enabledConfig = new CallStatementConfig(true);
      CallStatementChecker enabledChecker = new CallStatementChecker(enabledConfig);
      assertTrue(enabledChecker.isEnabled(), "Checker should be enabled");

      CallStatementConfig disabledConfig = new CallStatementConfig(false);
      CallStatementChecker disabledChecker = new CallStatementChecker(disabledConfig);
      assertFalse(disabledChecker.isEnabled(), "Checker should be disabled");
    }
  }

  // ==================== Multi-Dialect Tests ====================

  @Nested
  @DisplayName("Multi-Dialect Tests")
  class MultiDialectTests {

    @Test
    @DisplayName("MySQL: CALL procedure should fail")
    void testMySqlCall_shouldFail() {
      String sql = "CALL mysql_specific_procedure('param1', 'param2')";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "MySQL CALL should fail");
      assertTrue(result.getViolations().get(0).getMessage().contains("CALL"));
    }

    @Test
    @DisplayName("Oracle: EXECUTE procedure should fail")
    void testOracleExecute_shouldFail() {
      String sql = "EXECUTE oracle_package.procedure_name(p_id => 1)";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Oracle EXECUTE should fail");
      assertTrue(result.getViolations().get(0).getMessage().contains("EXECUTE"));
    }

    @Test
    @DisplayName("SQL Server: EXEC procedure should fail")
    void testSqlServerExec_shouldFail() {
      String sql = "EXEC dbo.sp_helpdb @dbname = 'master'";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SQL Server EXEC should fail");
      assertTrue(result.getViolations().get(0).getMessage().contains("EXEC"));
    }

    @Test
    @DisplayName("All dialects: Normal SELECT should pass")
    void testAllDialectsNormalSelect_shouldPass() {
      String sql = "SELECT id, name FROM users WHERE status = 'active'";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Normal SELECT should pass in all dialects");
    }
  }

  // ==================== Violation Message Tests ====================

  @Nested
  @DisplayName("Violation Message Tests")
  class ViolationMessageTests {

    @Test
    @DisplayName("Violation message should contain procedure name for CALL")
    void testViolationMessageContainsProcedureName_call() {
      String sql = "CALL sp_important_procedure()";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed());
      String message = result.getViolations().get(0).getMessage();
      assertTrue(message.contains("sp_important_procedure"),
          "Violation message should contain procedure name");
      assertTrue(message.contains("CALL"),
          "Violation message should contain CALL keyword");
    }

    @Test
    @DisplayName("Violation message should contain procedure name for EXECUTE")
    void testViolationMessageContainsProcedureName_execute() {
      String sql = "EXECUTE sp_oracle_proc";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed());
      String message = result.getViolations().get(0).getMessage();
      assertTrue(message.contains("sp_oracle_proc"),
          "Violation message should contain procedure name");
      assertTrue(message.contains("EXECUTE"),
          "Violation message should contain EXECUTE keyword");
    }

    @Test
    @DisplayName("Violation message should contain procedure name for EXEC")
    void testViolationMessageContainsProcedureName_exec() {
      String sql = "EXEC sp_sqlserver_proc";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed());
      String message = result.getViolations().get(0).getMessage();
      assertTrue(message.contains("sp_sqlserver_proc"),
          "Violation message should contain procedure name");
      assertTrue(message.contains("EXEC"),
          "Violation message should contain EXEC keyword");
    }

    @Test
    @DisplayName("Violation message should contain security warning")
    void testViolationMessageContainsSecurityWarning() {
      String sql = "CALL sp_test()";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed());
      String message = result.getViolations().get(0).getMessage();
      assertTrue(message.contains("不透明逻辑") || message.contains("权限提升"),
          "Violation message should contain security warning");
    }

    @Test
    @DisplayName("Violation should have suggestion")
    void testViolationHasSuggestion() {
      String sql = "CALL sp_test()";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed());
      String suggestion = result.getViolations().get(0).getSuggestion();
      assertTrue(suggestion != null && !suggestion.isEmpty(),
          "Violation should have a suggestion");
      assertTrue(suggestion.contains("应用层") || suggestion.contains("SQL语句"),
          "Suggestion should mention migrating to application layer");
    }
  }

  // ==================== Helper Methods ====================

  /**
   * Creates a SqlContext with parsed statement for testing.
   * Used for SQL that JSqlParser can parse (normal SELECT/INSERT/UPDATE/DELETE).
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
   * Used for CALL/EXECUTE/EXEC statements that JSqlParser cannot parse.
   * The checker uses regex pattern matching on the raw SQL string for detection.
   */
  private SqlContext createRawContext(String sql) {
    // Parse a simple SELECT to get a valid Statement object
    // The actual detection is done via regex on the raw SQL string
    try {
      Statement statement = CCJSqlParserUtil.parse("SELECT 1");
      return SqlContext.builder()
          .sql(sql)  // Use the original SQL with CALL/EXECUTE/EXEC
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
