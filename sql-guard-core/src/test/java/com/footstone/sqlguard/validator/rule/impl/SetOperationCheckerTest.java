package com.footstone.sqlguard.validator.rule.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
 * Test class for SetOperationChecker.
 *
 * <p>Verifies that SetOperationChecker correctly detects SQL set operations (UNION, MINUS,
 * EXCEPT, INTERSECT) that can be used for SQL injection attacks, particularly data
 * exfiltration.</p>
 *
 * <p><strong>Test Categories:</strong></p>
 * <ul>
 *   <li>PASS tests (≥5): Normal SQL without set operations, allowed operations when configured</li>
 *   <li>FAIL tests (≥10): Various set operation injection attacks</li>
 *   <li>边界 tests (≥3): Empty allowedOperations, populated allowedOperations, case-insensitive matching</li>
 * </ul>
 *
 * <p><strong>Multi-Dialect Coverage:</strong></p>
 * <ul>
 *   <li>MySQL: UNION, UNION ALL</li>
 *   <li>Oracle: MINUS, INTERSECT</li>
 *   <li>PostgreSQL: EXCEPT</li>
 *   <li>SQL Server: All operations</li>
 * </ul>
 */
@DisplayName("SetOperationChecker Tests")
class SetOperationCheckerTest {

  private SetOperationChecker checker;
  private SetOperationConfig config;

  @BeforeEach
  void setUp() {
    config = new SetOperationConfig(true); // Explicitly enable checker for tests
    checker = new SetOperationChecker(config);
  }

  // ==================== PASS Tests (≥5) ====================

  @Nested
  @DisplayName("PASS Tests - Normal SQL without set operations")
  class PassTests {

    @Test
    @DisplayName("Simple SELECT without set operation should pass")
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
    @DisplayName("SELECT with subquery in WHERE should pass")
    void testSelectWithSubquery_shouldPass() {
      String sql = "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders WHERE status = 'completed')";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "SELECT with subquery should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("SELECT with derived table should pass")
    void testSelectWithDerivedTable_shouldPass() {
      String sql = "SELECT * FROM (SELECT id, name FROM users WHERE active = 1) t WHERE t.id > 100";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "SELECT with derived table should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("SELECT with CTE (WITH clause) should pass")
    void testSelectWithCTE_shouldPass() {
      String sql = "WITH active_users AS (SELECT * FROM users WHERE status = 'active') SELECT * FROM active_users WHERE id > 10";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "SELECT with CTE should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("UNION should pass when allowed in config")
    void testUnionAllowed_shouldPass() {
      SetOperationConfig allowConfig = new SetOperationConfig(true, Arrays.asList("UNION"));
      SetOperationChecker allowChecker = new SetOperationChecker(allowConfig);

      String sql = "SELECT id, name FROM users UNION SELECT id, name FROM admins";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      allowChecker.check(context, result);

      assertTrue(result.isPassed(), "UNION should pass when allowed");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Complex SELECT with GROUP BY and HAVING should pass")
    void testComplexSelectWithGroupBy_shouldPass() {
      String sql = "SELECT department, COUNT(*) as cnt FROM employees WHERE status = 'active' GROUP BY department HAVING COUNT(*) > 5 ORDER BY cnt DESC";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Complex SELECT with GROUP BY should pass");
      assertEquals(0, result.getViolations().size());
    }
  }

  // ==================== FAIL Tests (≥10) ====================

  @Nested
  @DisplayName("FAIL Tests - Set operation injection attacks")
  class FailTests {

    @Test
    @DisplayName("UNION injection should fail")
    void testUnionInjection_shouldFail() {
      String sql = "SELECT id, name FROM users UNION SELECT id, password FROM admin_users";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "UNION injection should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
      assertEquals(1, result.getViolations().size());
      assertTrue(result.getViolations().get(0).getMessage().contains("UNION"));
    }

    @Test
    @DisplayName("UNION ALL injection should fail")
    void testUnionAllInjection_shouldFail() {
      String sql = "SELECT * FROM products UNION ALL SELECT * FROM secret_data";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "UNION ALL injection should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("UNION"));
    }

    @Test
    @DisplayName("MySQL UNION data exfiltration should fail")
    void testMySqlUnionExfiltration_shouldFail() {
      String sql = "SELECT username, email FROM users WHERE id = 1 UNION SELECT table_name, column_name FROM information_schema.columns";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "MySQL UNION exfiltration should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("Oracle MINUS injection should fail")
    void testOracleMinusInjection_shouldFail() {
      String sql = "SELECT id FROM users MINUS SELECT id FROM blocked_users";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Oracle MINUS injection should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("MINUS"));
    }

    @Test
    @DisplayName("PostgreSQL EXCEPT injection should fail")
    void testPostgreSqlExceptInjection_shouldFail() {
      String sql = "SELECT email FROM users EXCEPT SELECT email FROM unsubscribed";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "PostgreSQL EXCEPT injection should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("EXCEPT"));
    }

    @Test
    @DisplayName("INTERSECT injection should fail")
    void testIntersectInjection_shouldFail() {
      String sql = "SELECT user_id FROM orders INTERSECT SELECT id FROM premium_users";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "INTERSECT injection should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("INTERSECT"));
    }

    @Test
    @DisplayName("Multiple UNION operations should fail with multiple violations")
    void testMultipleUnionOperations_shouldFail() {
      String sql = "SELECT a FROM t1 UNION SELECT b FROM t2 UNION SELECT c FROM t3";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Multiple UNION should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
      // Should have 2 violations (2 UNION operations)
      assertEquals(2, result.getViolations().size());
    }

    @Test
    @DisplayName("Nested set operations should fail")
    void testNestedSetOperations_shouldFail() {
      String sql = "(SELECT a FROM t1 UNION SELECT b FROM t2) INTERSECT SELECT c FROM t3";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Nested set operations should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("UNION with ORDER BY should fail")
    void testUnionWithOrderBy_shouldFail() {
      String sql = "SELECT id, name FROM users UNION SELECT id, name FROM admins ORDER BY name";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "UNION with ORDER BY should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("UNION with LIMIT should fail")
    void testUnionWithLimit_shouldFail() {
      String sql = "SELECT id FROM users UNION SELECT id FROM admins LIMIT 10";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "UNION with LIMIT should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("UNION with complex WHERE should fail")
    void testUnionWithComplexWhere_shouldFail() {
      String sql = "SELECT id FROM users WHERE status = 'active' AND role = 'user' UNION SELECT id FROM admins WHERE level > 5";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "UNION with complex WHERE should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("UNION injection with NULL columns should fail")
    void testUnionWithNullColumns_shouldFail() {
      String sql = "SELECT id, name, email FROM users UNION SELECT 1, username, password FROM admin_credentials";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "UNION with NULL columns should fail");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }
  }

  // ==================== 边界 Tests (≥3) ====================

  @Nested
  @DisplayName("边界 Tests - Edge cases and boundary conditions")
  class EdgeCaseTests {

    @Test
    @DisplayName("Empty allowedOperations should block all set operations")
    void testEmptyAllowedOperations_shouldBlockAll() {
      SetOperationConfig emptyConfig = new SetOperationConfig(true, Collections.emptyList());
      SetOperationChecker emptyChecker = new SetOperationChecker(emptyConfig);

      String sql = "SELECT a FROM t1 UNION SELECT b FROM t2";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      emptyChecker.check(context, result);

      assertFalse(result.isPassed(), "Empty allowedOperations should block all");
      assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    @DisplayName("Populated allowedOperations should allow specific operations only")
    void testPopulatedAllowedOperations_shouldAllowSpecific() {
      SetOperationConfig selectiveConfig = new SetOperationConfig(true, Arrays.asList("UNION", "INTERSECT"));
      SetOperationChecker selectiveChecker = new SetOperationChecker(selectiveConfig);

      // UNION should pass
      String unionSql = "SELECT a FROM t1 UNION SELECT b FROM t2";
      SqlContext unionContext = createContext(unionSql);
      ValidationResult unionResult = ValidationResult.pass();
      selectiveChecker.check(unionContext, unionResult);
      assertTrue(unionResult.isPassed(), "UNION should pass when in allowedOperations");

      // EXCEPT should fail (not in allowed list)
      String exceptSql = "SELECT a FROM t1 EXCEPT SELECT b FROM t2";
      SqlContext exceptContext = createContext(exceptSql);
      ValidationResult exceptResult = ValidationResult.pass();
      selectiveChecker.check(exceptContext, exceptResult);
      assertFalse(exceptResult.isPassed(), "EXCEPT should fail when not in allowedOperations");
    }

    @Test
    @DisplayName("Case-insensitive operation matching should work")
    void testCaseInsensitiveMatching() {
      // Config with lowercase
      SetOperationConfig lowerConfig = new SetOperationConfig(true, Arrays.asList("union", "minus"));
      SetOperationChecker lowerChecker = new SetOperationChecker(lowerConfig);

      String sql = "SELECT a FROM t1 UNION SELECT b FROM t2";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      lowerChecker.check(context, result);

      assertTrue(result.isPassed(), "Case-insensitive matching should allow UNION");
    }

    @Test
    @DisplayName("Disabled checker should skip validation")
    void testDisabledChecker_shouldSkip() {
      SetOperationConfig disabledConfig = new SetOperationConfig(false);
      SetOperationChecker disabledChecker = new SetOperationChecker(disabledConfig);

      String sql = "SELECT a FROM t1 UNION SELECT b FROM t2";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      disabledChecker.check(context, result);

      assertTrue(result.isPassed(), "Disabled checker should skip");
      assertEquals(0, result.getViolations().size());
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
    @DisplayName("Invalid operation name in config should throw exception")
    void testInvalidOperationName_shouldThrow() {
      assertThrows(IllegalArgumentException.class, () -> {
        new SetOperationConfig(true, Arrays.asList("INVALID_OP"));
      }, "Invalid operation name should throw exception");
    }

    @Test
    @DisplayName("UNION_ALL should be recognized correctly")
    void testUnionAllRecognition() {
      SetOperationConfig allowUnionAllConfig = new SetOperationConfig(true, Arrays.asList("UNION_ALL"));
      SetOperationChecker allowUnionAllChecker = new SetOperationChecker(allowUnionAllConfig);

      // UNION ALL should pass
      String unionAllSql = "SELECT a FROM t1 UNION ALL SELECT b FROM t2";
      SqlContext unionAllContext = createContext(unionAllSql);
      ValidationResult unionAllResult = ValidationResult.pass();
      allowUnionAllChecker.check(unionAllContext, unionAllResult);
      assertTrue(unionAllResult.isPassed(), "UNION ALL should pass when allowed");

      // Regular UNION should fail (not in allowed list)
      String unionSql = "SELECT a FROM t1 UNION SELECT b FROM t2";
      SqlContext unionContext = createContext(unionSql);
      ValidationResult unionResult = ValidationResult.pass();
      allowUnionAllChecker.check(unionContext, unionResult);
      assertFalse(unionResult.isPassed(), "UNION should fail when only UNION_ALL is allowed");
    }
  }

  // ==================== Configuration Tests ====================

  @Nested
  @DisplayName("Configuration Tests")
  class ConfigurationTests {

    @Test
    @DisplayName("Default config should be disabled (opt-in design)")
    void testDefaultConfig() {
      SetOperationConfig defaultConfig = new SetOperationConfig();
      assertFalse(defaultConfig.isEnabled(), "Should be disabled by default (opt-in design)");
      assertEquals(RiskLevel.CRITICAL, defaultConfig.getRiskLevel(), "Should have CRITICAL risk level");
      assertTrue(defaultConfig.getAllowedOperations().isEmpty(), "Should have empty allowedOperations");
    }

    @Test
    @DisplayName("Config should validate operation names")
    void testConfigValidation() {
      SetOperationConfig validConfig = new SetOperationConfig();
      
      // Valid operations should work
      validConfig.addAllowedOperation("UNION");
      validConfig.addAllowedOperation("union_all");
      validConfig.addAllowedOperation("MINUS");
      validConfig.addAllowedOperation("except");
      validConfig.addAllowedOperation("INTERSECT");

      assertEquals(5, validConfig.getAllowedOperations().size());

      // Invalid operation should throw
      assertThrows(IllegalArgumentException.class, () -> {
        validConfig.addAllowedOperation("INVALID");
      });
    }

    @Test
    @DisplayName("isOperationAllowed should work correctly")
    void testIsOperationAllowed() {
      SetOperationConfig testConfig = new SetOperationConfig(true, Arrays.asList("UNION", "INTERSECT"));

      assertTrue(testConfig.isOperationAllowed("UNION"));
      assertTrue(testConfig.isOperationAllowed("union")); // case-insensitive
      assertTrue(testConfig.isOperationAllowed("INTERSECT"));
      assertFalse(testConfig.isOperationAllowed("MINUS"));
      assertFalse(testConfig.isOperationAllowed("EXCEPT"));
      assertFalse(testConfig.isOperationAllowed(null));
    }
  }

  // ==================== Multi-Dialect Tests ====================

  @Nested
  @DisplayName("Multi-Dialect Tests")
  class MultiDialectTests {

    @Test
    @DisplayName("MySQL: UNION with LIMIT should fail")
    void testMySqlUnionWithLimit_shouldFail() {
      String sql = "SELECT id, name FROM users UNION SELECT id, name FROM admins LIMIT 100";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "MySQL UNION with LIMIT should fail");
    }

    @Test
    @DisplayName("Oracle: MINUS with ROWNUM should fail")
    void testOracleMinusWithRownum_shouldFail() {
      String sql = "SELECT id FROM users WHERE ROWNUM <= 10 MINUS SELECT id FROM blocked";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Oracle MINUS should fail");
    }

    @Test
    @DisplayName("PostgreSQL: EXCEPT with OFFSET should fail")
    void testPostgreSqlExceptWithOffset_shouldFail() {
      String sql = "SELECT email FROM users EXCEPT SELECT email FROM unsubscribed OFFSET 10";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "PostgreSQL EXCEPT should fail");
    }

    @Test
    @DisplayName("SQL Server: INTERSECT with TOP should fail")
    void testSqlServerIntersect_shouldFail() {
      String sql = "SELECT id FROM orders INTERSECT SELECT id FROM premium_orders";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SQL Server INTERSECT should fail");
    }
  }

  // ==================== Helper Methods ====================

  /**
   * Creates a SqlContext with parsed statement for testing.
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
