package com.footstone.sqlguard.validator.pagination.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig;
import com.footstone.sqlguard.validator.rule.impl.NoPaginationConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * TDD tests for NoPaginationChecker migration to visitor pattern.
 *
 * <p>These tests verify that the NoPaginationChecker migration from check() to visitSelect()
 * maintains behavioral consistency while using the new architecture patterns:</p>
 * <ul>
 *   <li>Uses SqlContext.statement field instead of parsedSql</li>
 *   <li>Uses addViolation() helper method</li>
 *   <li>Uses direct JSqlParser API (plainSelect.getWhere())</li>
 *   <li>Uses local helper methods instead of AbstractRuleChecker utilities</li>
 * </ul>
 *
 * @since 1.1.0
 */
@DisplayName("NoPaginationChecker Migration Tests")
public class NoPaginationCheckerMigrationTest {

  private PaginationPluginDetector pluginDetector;
  private BlacklistFieldsConfig blacklistConfig;
  private NoPaginationConfig config;
  private NoPaginationChecker checker;

  @BeforeEach
  public void setUp() {
    pluginDetector = Mockito.mock(PaginationPluginDetector.class);

    blacklistConfig = new BlacklistFieldsConfig();
    blacklistConfig.setEnabled(true);
    Set<String> blacklist = new HashSet<>();
    blacklist.add("deleted");
    blacklist.add("status");
    blacklistConfig.setFields(blacklist);

    config = new NoPaginationConfig(); config.setEnabled(true); // Explicitly enable for tests
    config.setEnabled(true);

    checker = new NoPaginationChecker(pluginDetector, blacklistConfig, config);
  }

  @Nested
  @DisplayName("1. No Pagination Tests")
  class NoPaginationTests {

    /**
     * Test 1: SELECT without LIMIT should violate
     * <p>
     * Verifies that a SELECT query without any pagination (no LIMIT, no RowBounds, no IPage)
     * and without WHERE clause triggers a CRITICAL violation.
     * </p>
     */
    @Test
    @DisplayName("visitSelect() - no LIMIT - should add CRITICAL violation")
    public void testVisitSelect_noLimit_violates() throws JSQLParserException {
      String sql = "SELECT * FROM users";
      Statement stmt = CCJSqlParserUtil.parse(sql);

      SqlContext context = SqlContext.builder()
          .sql(sql)
          .statementId("test.noLimit")
          .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
          .statement(stmt)  // ✅ Uses statement field
          .build();

      ValidationResult result = ValidationResult.pass();
      checker.check(context, result);

      assertFalse(result.isPassed());
      assertEquals(1, result.getViolations().size());
      assertEquals(RiskLevel.CRITICAL, result.getViolations().get(0).getRiskLevel());
    }

    /**
     * Test 2: SELECT with LIMIT should pass
     * <p>
     * Verifies that pagination via LIMIT clause is correctly detected and
     * the query passes validation.
     * </p>
     */
    @Test
    @DisplayName("visitSelect() - with LIMIT - should pass")
    public void testVisitSelect_withLimit_passes() throws JSQLParserException {
      String sql = "SELECT * FROM users LIMIT 10";
      Statement stmt = CCJSqlParserUtil.parse(sql);

      SqlContext context = SqlContext.builder()
          .sql(sql)
          .statementId("test.withLimit")
          .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
          .statement(stmt)  // ✅ Uses statement field
          .build();

      ValidationResult result = ValidationResult.pass();
      checker.check(context, result);

      assertTrue(result.isPassed());
    }

    /**
     * Test 3: SELECT with RowBounds should pass
     * <p>
     * Verifies that pagination via RowBounds (non-DEFAULT) is correctly detected.
     * RowBounds presence is treated as pagination attempt, regardless of plugin availability.
     * </p>
     */
    @Test
    @DisplayName("visitSelect() - with RowBounds - should pass")
    public void testVisitSelect_withRowBounds_passes() throws JSQLParserException {
      String sql = "SELECT * FROM users";
      Statement stmt = CCJSqlParserUtil.parse(sql);

      SqlContext context = SqlContext.builder()
          .sql(sql)
          .statementId("test.withRowBounds")
          .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
          .statement(stmt)  // ✅ Uses statement field
          .rowBounds(new RowBounds(0, 10))  // Pagination present
          .build();

      ValidationResult result = ValidationResult.pass();
      checker.check(context, result);

      assertTrue(result.isPassed());
    }
  }

  @Nested
  @DisplayName("2. Risk Stratification Tests")
  class RiskStratificationTests {

    /**
     * Test 4: Blacklist-only WHERE should be HIGH risk
     * <p>
     * Verifies that a SELECT query with WHERE clause using ONLY blacklist fields
     * (e.g., deleted, status) triggers a HIGH risk violation.
     * </p>
     */
    @Test
    @DisplayName("visitSelect() - blacklist-only WHERE - should add HIGH violation")
    public void testVisitSelect_blacklistOnlyWhere_highRisk() throws JSQLParserException {
      String sql = "SELECT * FROM users WHERE deleted = 0";
      Statement stmt = CCJSqlParserUtil.parse(sql);

      SqlContext context = SqlContext.builder()
          .sql(sql)
          .statementId("test.blacklistOnly")
          .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
          .statement(stmt)  // ✅ Uses statement field
          .build();

      ValidationResult result = ValidationResult.pass();
      checker.check(context, result);

      assertFalse(result.isPassed());
      assertEquals(1, result.getViolations().size());
      assertEquals(RiskLevel.HIGH, result.getViolations().get(0).getRiskLevel());
    }

    /**
     * Test 5: Normal WHERE with enforceForAllQueries should be MEDIUM risk
     * <p>
     * Verifies that when enforceForAllQueries=true, a SELECT query with normal
     * business field WHERE clause (not blacklisted) triggers a MEDIUM violation.
     * </p>
     */
    @Test
    @DisplayName("visitSelect() - normal WHERE + enforceForAllQueries - should add MEDIUM violation")
    public void testVisitSelect_normalWhere_mediumRisk() throws JSQLParserException {
      config.setEnforceForAllQueries(true);
      NoPaginationChecker strictChecker = new NoPaginationChecker(
          pluginDetector, blacklistConfig, config);

      String sql = "SELECT * FROM users WHERE name = 'foo'";
      Statement stmt = CCJSqlParserUtil.parse(sql);

      SqlContext context = SqlContext.builder()
          .sql(sql)
          .statementId("test.normalWhere")
          .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
          .statement(stmt)  // ✅ Uses statement field
          .build();

      ValidationResult result = ValidationResult.pass();
      strictChecker.check(context, result);

      assertFalse(result.isPassed());
      assertEquals(1, result.getViolations().size());
      assertEquals(RiskLevel.MEDIUM, result.getViolations().get(0).getRiskLevel());
    }
  }

  @Nested
  @DisplayName("3. Whitelist Exemption Tests")
  class WhitelistExemptionTests {

    /**
     * Test 6: Whitelisted table should be skipped
     * <p>
     * Verifies that queries against whitelisted tables are exempt from pagination check.
     * </p>
     */
    @Test
    @DisplayName("visitSelect() - whitelisted table - should skip")
    public void testVisitSelect_whitelistedTable_skipped() throws JSQLParserException {
      List<String> whitelistTables = new ArrayList<>();
      whitelistTables.add("users");
      config.setWhitelistTables(whitelistTables);

      NoPaginationChecker whitelistChecker = new NoPaginationChecker(
          pluginDetector, blacklistConfig, config);

      String sql = "SELECT * FROM users";
      Statement stmt = CCJSqlParserUtil.parse(sql);

      SqlContext context = SqlContext.builder()
          .sql(sql)
          .statementId("test.whitelistedTable")
          .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
          .statement(stmt)  // ✅ Uses statement field
          .build();

      ValidationResult result = ValidationResult.pass();
      whitelistChecker.check(context, result);

      assertTrue(result.isPassed());
    }

    /**
     * Test 7: Unique key condition should be skipped
     * <p>
     * Verifies that queries with unique key equality conditions (e.g., WHERE id = 1)
     * are exempt from pagination check as they guarantee single-row result.
     * </p>
     */
    @Test
    @DisplayName("visitSelect() - unique key condition - should skip")
    public void testVisitSelect_uniqueKeyCondition_skipped() throws JSQLParserException {
      String sql = "SELECT * FROM users WHERE id = 1";
      Statement stmt = CCJSqlParserUtil.parse(sql);

      SqlContext context = SqlContext.builder()
          .sql(sql)
          .statementId("test.uniqueKey")
          .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
          .statement(stmt)  // ✅ Uses statement field
          .build();

      ValidationResult result = ValidationResult.pass();
      checker.check(context, result);

      assertTrue(result.isPassed());
    }
  }

  @Nested
  @DisplayName("4. Statement Field Migration Tests")
  class StatementFieldMigrationTests {

    /**
     * Test 8: Verify statement field is used correctly
     * <p>
     * Verifies that the SqlContext.statement field is properly used by the checker.
     * This test confirms the migration from parsedSql to statement field.
     * </p>
     */
    @Test
    @DisplayName("statement field - should work correctly")
    public void testStatementField_works() throws JSQLParserException {
      String sql = "SELECT * FROM users";
      Statement stmt = CCJSqlParserUtil.parse(sql);

      SqlContext context = SqlContext.builder()
          .sql(sql)
          .statementId("test.statementField")
          .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
          .statement(stmt)  // ✅ NEW statement field
          .build();

      // Verify statement field is set
      assertNotNull(context.getStatement());
      assertEquals(stmt, context.getStatement());

      // Verify checker works with statement field
      ValidationResult result = ValidationResult.pass();
      checker.check(context, result);

      // Should trigger CRITICAL violation (no WHERE, no pagination)
      assertFalse(result.isPassed());
      assertEquals(1, result.getViolations().size());
      assertEquals(RiskLevel.CRITICAL, result.getViolations().get(0).getRiskLevel());
    }
  }
}

