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
import java.util.List;

/**
 * Test class for MetadataStatementChecker.
 *
 * <p>Verifies that MetadataStatementChecker correctly detects metadata disclosure statements
 * (SHOW/DESCRIBE/USE) while allowing normal DML operations and INFORMATION_SCHEMA queries.</p>
 *
 * <p><strong>Test Categories:</strong></p>
 * <ul>
 *   <li>PASS tests (≥5): SELECT/INSERT/UPDATE/DELETE (DML operations), INFORMATION_SCHEMA queries,
 *       allowed metadata types when configured</li>
 *   <li>FAIL tests (≥10): SHOW TABLES, SHOW DATABASES, DESCRIBE table, DESC table, USE database,
 *       case variations, metadata commands with options</li>
 *   <li>边界 tests (≥3): empty allowedStatements blocks all, populated allowedStatements allows specific,
 *       INFORMATION_SCHEMA queries pass</li>
 * </ul>
 *
 * <p><strong>Multi-Dialect Coverage:</strong></p>
 * <ul>
 *   <li>MySQL: SHOW TABLES, SHOW DATABASES, DESCRIBE, DESC, USE</li>
 *   <li>PostgreSQL: \d commands (not directly supported, but similar patterns)</li>
 *   <li>Oracle: DESC variants</li>
 * </ul>
 */
@DisplayName("MetadataStatementChecker Tests")
class MetadataStatementCheckerTest {

  private MetadataStatementChecker checker;
  private MetadataStatementConfig config;

  @BeforeEach
  void setUp() {
    config = new MetadataStatementConfig();
    checker = new MetadataStatementChecker(config);
  }

  // ==================== PASS Tests (≥5) ====================

  @Nested
  @DisplayName("PASS Tests - Normal DML operations and allowed statements")
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
    @DisplayName("INSERT statement should pass")
    void testInsert_shouldPass() {
      String sql = "INSERT INTO users (name, email) VALUES ('test', 'test@example.com')";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "INSERT should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("UPDATE statement should pass")
    void testUpdate_shouldPass() {
      String sql = "UPDATE users SET name = 'new_name' WHERE id = 1";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "UPDATE should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("DELETE statement should pass")
    void testDelete_shouldPass() {
      String sql = "DELETE FROM users WHERE id = 1";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "DELETE should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("INFORMATION_SCHEMA query via SELECT should pass")
    void testInformationSchemaSelect_shouldPass() {
      String sql = "SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'mydb'";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "INFORMATION_SCHEMA query should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("INFORMATION_SCHEMA.COLUMNS query should pass")
    void testInformationSchemaColumns_shouldPass() {
      String sql = "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'users'";
      SqlContext context = createContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "INFORMATION_SCHEMA.COLUMNS query should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Allowed SHOW statement should pass when configured")
    void testAllowedShow_shouldPass() {
      MetadataStatementConfig allowShowConfig = new MetadataStatementConfig(true, Arrays.asList("SHOW"));
      MetadataStatementChecker allowShowChecker = new MetadataStatementChecker(allowShowConfig);

      String sql = "SHOW TABLES";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      allowShowChecker.check(context, result);

      assertTrue(result.isPassed(), "Allowed SHOW should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Allowed DESCRIBE statement should pass when configured")
    void testAllowedDescribe_shouldPass() {
      MetadataStatementConfig allowDescConfig = new MetadataStatementConfig(true, Arrays.asList("DESCRIBE"));
      MetadataStatementChecker allowDescChecker = new MetadataStatementChecker(allowDescConfig);

      String sql = "DESCRIBE users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      allowDescChecker.check(context, result);

      assertTrue(result.isPassed(), "Allowed DESCRIBE should pass");
      assertEquals(0, result.getViolations().size());
    }

    @Test
    @DisplayName("Disabled checker should skip validation")
    void testDisabledChecker_shouldSkip() {
      MetadataStatementConfig disabledConfig = new MetadataStatementConfig(false);
      MetadataStatementChecker disabledChecker = new MetadataStatementChecker(disabledConfig);

      String sql = "SHOW TABLES";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      disabledChecker.check(context, result);

      assertTrue(result.isPassed(), "Disabled checker should skip validation");
      assertEquals(0, result.getViolations().size());
    }
  }

  // ==================== FAIL Tests (≥10) ====================

  @Nested
  @DisplayName("FAIL Tests - Metadata disclosure statements")
  class FailTests {

    @Test
    @DisplayName("SHOW TABLES should fail")
    void testShowTables_shouldFail() {
      String sql = "SHOW TABLES";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SHOW TABLES should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
      assertEquals(1, result.getViolations().size());
      assertTrue(result.getViolations().get(0).getMessage().contains("SHOW"));
    }

    @Test
    @DisplayName("SHOW DATABASES should fail")
    void testShowDatabases_shouldFail() {
      String sql = "SHOW DATABASES";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SHOW DATABASES should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("SHOW"));
    }

    @Test
    @DisplayName("DESCRIBE table should fail")
    void testDescribeTable_shouldFail() {
      String sql = "DESCRIBE users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "DESCRIBE table should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("DESCRIBE"));
    }

    @Test
    @DisplayName("DESC table should fail")
    void testDescTable_shouldFail() {
      String sql = "DESC orders";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "DESC table should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("DESCRIBE"));
    }

    @Test
    @DisplayName("USE database should fail")
    void testUseDatabase_shouldFail() {
      String sql = "USE production_db";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "USE database should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
      assertTrue(result.getViolations().get(0).getMessage().contains("USE"));
    }

    @Test
    @DisplayName("Lowercase show tables should fail (case insensitive)")
    void testLowercaseShowTables_shouldFail() {
      String sql = "show tables";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Lowercase SHOW TABLES should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    @DisplayName("Mixed case DESCRIBE should fail")
    void testMixedCaseDescribe_shouldFail() {
      String sql = "DeScRiBe users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "Mixed case DESCRIBE should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    @DisplayName("SHOW TABLES LIKE pattern should fail")
    void testShowTablesLike_shouldFail() {
      String sql = "SHOW TABLES LIKE 'user%'";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SHOW TABLES LIKE should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    @DisplayName("SHOW COLUMNS FROM table should fail")
    void testShowColumns_shouldFail() {
      String sql = "SHOW COLUMNS FROM users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SHOW COLUMNS should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    @DisplayName("SHOW INDEX FROM table should fail")
    void testShowIndex_shouldFail() {
      String sql = "SHOW INDEX FROM users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SHOW INDEX should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    @DisplayName("SHOW CREATE TABLE should fail")
    void testShowCreateTable_shouldFail() {
      String sql = "SHOW CREATE TABLE users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SHOW CREATE TABLE should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    @DisplayName("SHOW GRANTS should fail")
    void testShowGrants_shouldFail() {
      String sql = "SHOW GRANTS FOR 'root'@'localhost'";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SHOW GRANTS should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    @DisplayName("SHOW STATUS should fail")
    void testShowStatus_shouldFail() {
      String sql = "SHOW STATUS";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SHOW STATUS should fail");
      assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }
  }

  // ==================== 边界 Tests (≥3) ====================

  @Nested
  @DisplayName("边界 Tests - Edge cases and boundary conditions")
  class EdgeCaseTests {

    @Test
    @DisplayName("Empty allowedStatements should block all metadata commands")
    void testEmptyAllowedStatements_blocksAll() {
      // Default config has empty allowedStatements
      MetadataStatementConfig emptyConfig = new MetadataStatementConfig();
      MetadataStatementChecker emptyChecker = new MetadataStatementChecker(emptyConfig);

      // Test SHOW
      ValidationResult result1 = ValidationResult.pass();
      emptyChecker.check(createRawContext("SHOW TABLES"), result1);
      assertFalse(result1.isPassed(), "Empty allowedStatements should block SHOW");

      // Test DESCRIBE
      ValidationResult result2 = ValidationResult.pass();
      emptyChecker.check(createRawContext("DESCRIBE users"), result2);
      assertFalse(result2.isPassed(), "Empty allowedStatements should block DESCRIBE");

      // Test USE
      ValidationResult result3 = ValidationResult.pass();
      emptyChecker.check(createRawContext("USE mydb"), result3);
      assertFalse(result3.isPassed(), "Empty allowedStatements should block USE");
    }

    @Test
    @DisplayName("Populated allowedStatements should allow only specified types")
    void testPopulatedAllowedStatements_allowsSpecific() {
      // Allow only SHOW
      MetadataStatementConfig partialConfig = new MetadataStatementConfig(true, Arrays.asList("SHOW"));
      MetadataStatementChecker partialChecker = new MetadataStatementChecker(partialConfig);

      // SHOW should pass
      ValidationResult result1 = ValidationResult.pass();
      partialChecker.check(createRawContext("SHOW TABLES"), result1);
      assertTrue(result1.isPassed(), "Allowed SHOW should pass");

      // DESCRIBE should still fail
      ValidationResult result2 = ValidationResult.pass();
      partialChecker.check(createRawContext("DESCRIBE users"), result2);
      assertFalse(result2.isPassed(), "Non-allowed DESCRIBE should fail");

      // USE should still fail
      ValidationResult result3 = ValidationResult.pass();
      partialChecker.check(createRawContext("USE mydb"), result3);
      assertFalse(result3.isPassed(), "Non-allowed USE should fail");
    }

    @Test
    @DisplayName("INFORMATION_SCHEMA queries should always pass (not metadata commands)")
    void testInformationSchemaQueries_alwaysPass() {
      // Even with empty allowedStatements, INFORMATION_SCHEMA queries should pass
      MetadataStatementConfig strictConfig = new MetadataStatementConfig();
      MetadataStatementChecker strictChecker = new MetadataStatementChecker(strictConfig);

      String[] infoSchemaQueries = {
          "SELECT * FROM INFORMATION_SCHEMA.TABLES",
          "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'mydb'",
          "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS",
          "SELECT * FROM information_schema.schemata"
      };

      for (String sql : infoSchemaQueries) {
        ValidationResult result = ValidationResult.pass();
        strictChecker.check(createContext(sql), result);
        assertTrue(result.isPassed(), "INFORMATION_SCHEMA query should pass: " + sql);
      }
    }

    @Test
    @DisplayName("Null SQL context should be handled gracefully")
    void testNullContext_shouldBeHandled() {
      ValidationResult result = ValidationResult.pass();

      // Should not throw exception
      checker.check(null, result);

      assertTrue(result.isPassed(), "Null context should pass without error");
    }

    @Test
    @DisplayName("Non-metadata SQL should pass")
    void testNonMetadataSql_shouldPass() {
      // Test that random non-metadata SQL passes
      SqlContext context = createRawContext("SELECT 1");
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertTrue(result.isPassed(), "Non-metadata SQL should pass");
    }

    @Test
    @DisplayName("SQL with leading whitespace should be detected")
    void testLeadingWhitespace_shouldDetect() {
      String sql = "   SHOW TABLES";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SQL with leading whitespace should be detected");
    }

    @Test
    @DisplayName("SQL with newlines should be detected")
    void testNewlines_shouldDetect() {
      String sql = "\n\nSHOW TABLES";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed(), "SQL with newlines should be detected");
    }
  }

  // ==================== Configuration Tests ====================

  @Nested
  @DisplayName("Configuration Tests")
  class ConfigurationTests {

    @Test
    @DisplayName("Default config should be enabled with HIGH risk level and empty allowedStatements")
    void testDefaultConfig() {
      MetadataStatementConfig defaultConfig = new MetadataStatementConfig();
      assertTrue(defaultConfig.isEnabled(), "Should be enabled by default");
      assertEquals(RiskLevel.HIGH, defaultConfig.getRiskLevel(), "Should have HIGH risk level");
      assertTrue(defaultConfig.getAllowedStatements().isEmpty(), "Should have empty allowedStatements");
    }

    @Test
    @DisplayName("Config with enabled=false should disable checker")
    void testDisabledConfig() {
      MetadataStatementConfig disabledConfig = new MetadataStatementConfig(false);
      assertFalse(disabledConfig.isEnabled(), "Should be disabled");
    }

    @Test
    @DisplayName("Config with allowedStatements should allow specified types")
    void testAllowedStatementsConfig() {
      List<String> allowed = Arrays.asList("SHOW", "DESCRIBE");
      MetadataStatementConfig config = new MetadataStatementConfig(true, allowed);

      assertTrue(config.isStatementAllowed("SHOW"), "SHOW should be allowed");
      assertTrue(config.isStatementAllowed("DESCRIBE"), "DESCRIBE should be allowed");
      assertFalse(config.isStatementAllowed("USE"), "USE should not be allowed");
    }

    @Test
    @DisplayName("Config should validate statement types")
    void testInvalidStatementType() {
      assertThrows(IllegalArgumentException.class, () -> {
        new MetadataStatementConfig(true, Arrays.asList("INVALID"));
      }, "Invalid statement type should throw exception");
    }

    @Test
    @DisplayName("Config should handle case-insensitive statement types")
    void testCaseInsensitiveStatementTypes() {
      MetadataStatementConfig config = new MetadataStatementConfig(true, Arrays.asList("show", "Describe"));

      assertTrue(config.isStatementAllowed("SHOW"), "Should allow SHOW (case-insensitive)");
      assertTrue(config.isStatementAllowed("show"), "Should allow show (case-insensitive)");
      assertTrue(config.isStatementAllowed("DESCRIBE"), "Should allow DESCRIBE (case-insensitive)");
    }

    @Test
    @DisplayName("isEnabled should return config state")
    void testIsEnabled() {
      MetadataStatementConfig enabledConfig = new MetadataStatementConfig(true);
      MetadataStatementChecker enabledChecker = new MetadataStatementChecker(enabledConfig);
      assertTrue(enabledChecker.isEnabled(), "Checker should be enabled");

      MetadataStatementConfig disabledConfig = new MetadataStatementConfig(false);
      MetadataStatementChecker disabledChecker = new MetadataStatementChecker(disabledConfig);
      assertFalse(disabledChecker.isEnabled(), "Checker should be disabled");
    }

    @Test
    @DisplayName("Setting null allowedStatements should result in empty list")
    void testNullAllowedStatements() {
      MetadataStatementConfig config = new MetadataStatementConfig();
      config.setAllowedStatements(null);
      assertTrue(config.getAllowedStatements().isEmpty(), "Null should result in empty list");
    }
  }

  // ==================== Violation Message Tests ====================

  @Nested
  @DisplayName("Violation Message Tests")
  class ViolationMessageTests {

    @Test
    @DisplayName("SHOW violation message should contain statement type")
    void testShowViolationMessage() {
      String sql = "SHOW TABLES";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed());
      String message = result.getViolations().get(0).getMessage();
      assertTrue(message.contains("SHOW") && message.contains("TABLES"),
          "Violation message should contain SHOW TABLES");
    }

    @Test
    @DisplayName("DESCRIBE violation message should contain table name")
    void testDescribeViolationMessage() {
      String sql = "DESCRIBE users";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed());
      String message = result.getViolations().get(0).getMessage();
      assertTrue(message.contains("DESCRIBE") && message.contains("users"),
          "Violation message should contain DESCRIBE and table name");
    }

    @Test
    @DisplayName("USE violation message should contain database name")
    void testUseViolationMessage() {
      String sql = "USE production_db";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed());
      String message = result.getViolations().get(0).getMessage();
      assertTrue(message.contains("USE") && message.contains("production_db"),
          "Violation message should contain USE and database name");
    }

    @Test
    @DisplayName("Violation should include suggestion")
    void testViolationSuggestion() {
      String sql = "SHOW TABLES";
      SqlContext context = createRawContext(sql);
      ValidationResult result = ValidationResult.pass();

      checker.check(context, result);

      assertFalse(result.isPassed());
      String suggestion = result.getViolations().get(0).getSuggestion();
      assertTrue(suggestion != null && !suggestion.isEmpty(),
          "Violation should include suggestion");
      assertTrue(suggestion.contains("INFORMATION_SCHEMA"),
          "Suggestion should mention INFORMATION_SCHEMA as alternative");
    }
  }

  // ==================== Helper Methods ====================

  /**
   * Creates a SqlContext with parsed statement for testing.
   * Used for SQL that JSqlParser can parse (normal DML, INFORMATION_SCHEMA queries).
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
      // If parsing fails, create raw context
      return createRawContext(sql);
    }
  }

  /**
   * Creates a SqlContext with raw SQL only (no parsed statement).
   * Used for metadata commands (SHOW/DESCRIBE/USE) that JSqlParser cannot parse.
   */
  private SqlContext createRawContext(String sql) {
    try {
      // Parse a simple SELECT to get a valid Statement object for the context
      Statement statement = CCJSqlParserUtil.parse("SELECT 1");
      return SqlContext.builder()
          .sql(sql)  // Use the original SQL with metadata command
          .type(SqlCommandType.SELECT)
          .executionLayer(ExecutionLayer.MYBATIS)
          .statementId("com.example.TestMapper.testMethod")
          .statement(statement)
          .build();
    } catch (Exception e) {
      throw new RuntimeException("Failed to create raw context", e);
    }
  }
}
