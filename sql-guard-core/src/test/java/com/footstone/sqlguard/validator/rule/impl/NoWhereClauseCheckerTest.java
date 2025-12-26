package com.footstone.sqlguard.validator.rule.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test class for NoWhereClauseChecker.
 *
 * <p>Phase 12 Migration: This test class has been updated to reflect the new
 * visitor-based architecture:</p>
 * <ul>
 *   <li>NoWhereClauseChecker only checks UPDATE and DELETE statements</li>
 *   <li>SELECT statements are handled by NoPaginationChecker (with risk stratification)</li>
 *   <li>Uses statement() instead of parsedSql() for building SqlContext</li>
 *   <li>addViolation uses 2-parameter version (no suggestion)</li>
 * </ul>
 *
 * <p>Verifies that NoWhereClauseChecker correctly detects SQL statements missing WHERE clauses
 * and adds CRITICAL violations for UPDATE/DELETE operations that could cause catastrophic
 * full-table operations.</p>
 */
@DisplayName("NoWhereClauseChecker Tests (Phase 12)")
class NoWhereClauseCheckerTest {

  private JSqlParserFacade parser;
  private NoWhereClauseChecker checker;
  private NoWhereClauseConfig config;

  @BeforeEach
  void setUp() {
    parser = new JSqlParserFacade();
    config = new NoWhereClauseConfig();
    checker = new NoWhereClauseChecker(config);
  }

  @Test
  @DisplayName("DELETE without WHERE should add CRITICAL violation")
  void testDeleteWithoutWhere_shouldViolate() throws Exception {
    // DELETE without WHERE is CRITICAL - irreversible data loss
    String sql = "DELETE FROM user";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)  // Use statement() instead of parsedSql()
        .type(SqlCommandType.DELETE)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.deleteAll")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertFalse(result.isPassed(), "Should have violation");
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    assertTrue(result.getViolations().get(0).getMessage().contains("DELETE"));
    assertTrue(result.getViolations().get(0).getMessage().contains("WHERE"));
  }

  @Test
  @DisplayName("UPDATE without WHERE should add CRITICAL violation")
  void testUpdateWithoutWhere_shouldViolate() throws Exception {
    // UPDATE without WHERE is CRITICAL - irreversible data modification
    String sql = "UPDATE user SET status=1";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.UPDATE)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.updateAll")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertFalse(result.isPassed(), "Should have violation");
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    assertTrue(result.getViolations().get(0).getMessage().contains("UPDATE"));
    assertTrue(result.getViolations().get(0).getMessage().contains("WHERE"));
    assertEquals(RiskLevel.CRITICAL, result.getViolations().get(0).getRiskLevel());
  }

  @Test
  @DisplayName("SELECT without WHERE should be skipped (handled by NoPaginationChecker)")
  void testSelectWithoutWhere_shouldBeSkipped() throws Exception {
    // Phase 12 Change: SELECT is now handled by NoPaginationChecker with risk stratification
    // NoWhereClauseChecker only handles UPDATE and DELETE
    String sql = "SELECT * FROM user";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectAll")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // SELECT should pass (not checked by NoWhereClauseChecker)
    assertTrue(result.isPassed(), "SELECT should be skipped by NoWhereClauseChecker");
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  @DisplayName("SELECT with WHERE should pass")
  void testSelectWithWhere_shouldPass() throws Exception {
    // SELECT with WHERE clause should pass
    String sql = "SELECT * FROM user WHERE id=1";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectById")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  @DisplayName("DELETE with WHERE should pass")
  void testDeleteWithWhere_shouldPass() throws Exception {
    // DELETE with WHERE should pass
    String sql = "DELETE FROM user WHERE id = 1";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.DELETE)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.deleteById")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  @DisplayName("UPDATE with WHERE should pass")
  void testUpdateWithWhere_shouldPass() throws Exception {
    // UPDATE with WHERE should pass
    String sql = "UPDATE user SET status = 1 WHERE id = 1";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.UPDATE)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.updateById")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  @DisplayName("INSERT should be skipped (no WHERE by design)")
  void testInsertStatement_shouldSkip() throws Exception {
    // INSERT doesn't have WHERE clause by design - should pass
    String sql = "INSERT INTO user (name, email) VALUES ('John', 'john@example.com')";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.INSERT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.insert")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  @DisplayName("WHERE 1=1 dummy condition should pass (handled by DummyConditionChecker)")
  void testWhereWithDummyCondition_shouldPass() throws Exception {
    // WHERE 1=1 is a dummy condition but this checker should pass it
    // (DummyConditionChecker handles this)
    String sql = "UPDATE user SET status = 1 WHERE 1=1";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.UPDATE)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.updateWithDummy")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  @DisplayName("Complex WHERE should pass")
  void testComplexWhere_shouldPass() throws Exception {
    // Complex WHERE clause should pass
    String sql = "DELETE FROM user WHERE status='inactive' AND create_time < '2020-01-01'";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.DELETE)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.deleteComplex")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  @DisplayName("Disabled checker should skip validation")
  void testDisabledChecker_shouldSkip() throws Exception {
    // When checker is disabled, no violations should be added
    NoWhereClauseConfig disabledConfig = new NoWhereClauseConfig(false);
    NoWhereClauseChecker disabledChecker = new NoWhereClauseChecker(disabledConfig);

    String sql = "DELETE FROM user";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.DELETE)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.deleteAll")
        .build();
    ValidationResult result = ValidationResult.pass();

    disabledChecker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }
}
