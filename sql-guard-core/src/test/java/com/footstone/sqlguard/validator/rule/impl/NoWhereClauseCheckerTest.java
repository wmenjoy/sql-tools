package com.footstone.sqlguard.validator.rule.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for NoWhereClauseChecker.
 *
 * <p>Verifies that NoWhereClauseChecker correctly detects SQL statements missing WHERE clauses
 * and adds CRITICAL violations for SELECT/UPDATE/DELETE operations that could cause catastrophic
 * full-table operations.</p>
 */
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
  void testDeleteWithoutWhere_shouldViolate() throws Exception {
    // DELETE without WHERE is CRITICAL - irreversible data loss
    String sql = "DELETE FROM user";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.DELETE)
        .mapperId("com.example.UserMapper.deleteAll")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    assertTrue(result.getViolations().get(0).getMessage().contains("缺少WHERE条件"));
    assertTrue(result.getViolations().get(0).getSuggestion().contains("添加WHERE条件"));
  }

  @Test
  void testUpdateWithoutWhere_shouldViolate() throws Exception {
    // UPDATE without WHERE is CRITICAL - irreversible data modification
    String sql = "UPDATE user SET status=1";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.UPDATE)
        .mapperId("com.example.UserMapper.updateAll")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    assertTrue(result.getViolations().get(0).getMessage().contains("缺少WHERE条件"));
    assertEquals(RiskLevel.CRITICAL, result.getViolations().get(0).getRiskLevel());
  }

  @Test
  void testSelectWithoutWhere_shouldViolate() throws Exception {
    // SELECT without WHERE is CRITICAL - memory exhaustion from full table scan
    String sql = "SELECT * FROM user";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectAll")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    assertTrue(result.getViolations().get(0).getMessage().contains("缺少WHERE条件"));
  }

  @Test
  void testSelectWithWhere_shouldPass() throws Exception {
    // SELECT with WHERE clause should pass
    String sql = "SELECT * FROM user WHERE id=1";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectById")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testInsertStatement_shouldSkip() throws Exception {
    // INSERT doesn't have WHERE clause by design - should pass
    String sql = "INSERT INTO user (name, email) VALUES ('John', 'john@example.com')";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.INSERT)
        .mapperId("com.example.UserMapper.insert")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testWhereWithDummyCondition_shouldPass() throws Exception {
    // WHERE 1=1 is a dummy condition but this checker should pass it
    // (DummyConditionChecker in Task 2.3 will handle this)
    String sql = "SELECT * FROM user WHERE 1=1";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectWithDummy")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testComplexWhere_shouldPass() throws Exception {
    // Complex WHERE clause should pass
    String sql = "SELECT * FROM user WHERE status='active' AND create_time > ?";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectComplex")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testDisabledChecker_shouldSkip() throws Exception {
    // When checker is disabled, no violations should be added
    NoWhereClauseConfig disabledConfig = new NoWhereClauseConfig(false);
    NoWhereClauseChecker disabledChecker = new NoWhereClauseChecker(disabledConfig);

    String sql = "DELETE FROM user";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.DELETE)
        .mapperId("com.example.UserMapper.deleteAll")
        .build();
    ValidationResult result = ValidationResult.pass();

    disabledChecker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }
}
