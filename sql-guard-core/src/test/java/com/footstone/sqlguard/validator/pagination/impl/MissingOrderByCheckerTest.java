package com.footstone.sqlguard.validator.pagination.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.rule.impl.MissingOrderByConfig;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for MissingOrderByChecker.
 *
 * <p>Verifies that MissingOrderByChecker correctly detects physical pagination queries
 * lacking ORDER BY clause, which causes unpredictable result ordering across pages.</p>
 */
class MissingOrderByCheckerTest {

  private JSqlParserFacade parser;
  private PaginationPluginDetector detector;
  private MissingOrderByChecker checker;
  private MissingOrderByConfig config;

  @BeforeEach
  void setUp() {
    parser = new JSqlParserFacade();
    detector = new PaginationPluginDetector(null, null);
    config = new MissingOrderByConfig();
    checker = new MissingOrderByChecker(detector, config);
  }

  // ==================== Basic ORDER BY Detection Tests ====================

  @Test
  void testPhysicalPaginationWithoutOrderBy_shouldViolate() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 10";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectWithoutOrderBy")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertFalse(result.isPassed());
    assertEquals(RiskLevel.LOW, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    
    String message = result.getViolations().get(0).getMessage();
    assertTrue(message.contains("ORDER BY") || message.contains("顺序"));
  }

  @Test
  void testPhysicalPaginationWithOrderBy_shouldPass() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 ORDER BY id LIMIT 10";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectWithOrderBy")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  // ==================== Multiple ORDER BY Columns Tests ====================

  @Test
  void testSingleOrderBy_shouldPass() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 ORDER BY id LIMIT 10";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectSingleOrderBy")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testMultipleOrderByColumns_shouldPass() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 ORDER BY create_time DESC, id ASC LIMIT 10";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectMultipleOrderBy")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  // ==================== ORDER BY with Expressions Tests ====================

  @Test
  void testOrderByWithExpression_shouldPass() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 ORDER BY LOWER(name) LIMIT 10";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectOrderByExpression")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  // ==================== Pagination Type Tests ====================

  @Test
  void testLogicalPagination_shouldSkip() throws Exception {
    String sql = "SELECT * FROM user";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectWithRowBounds")
        .rowBounds(new RowBounds(0, 10))
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testNoPagination_shouldSkip() throws Exception {
    String sql = "SELECT * FROM user WHERE id = ?";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectById")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  // ==================== Configuration Tests ====================

  @Test
  void testDisabledChecker_shouldSkip() throws Exception {
    config.setEnabled(false);
    
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 10";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectWithoutOrderBy")
        .build();
    ValidationResult result = ValidationResult.pass();

    assertFalse(checker.isEnabled());

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  // ==================== Violation Message Tests ====================

  @Test
  void testViolationMessage_shouldMentionStability() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 10";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectWithoutOrderBy")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertFalse(result.isPassed());
    assertEquals(1, result.getViolations().size());
    
    String message = result.getViolations().get(0).getMessage();
    assertTrue(message.contains("顺序不稳定"), 
        "Message should mention unstable ordering (顺序不稳定)");
  }

  @Test
  void testViolationSuggestion_shouldMentionOrderBy() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 10";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectWithoutOrderBy")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertFalse(result.isPassed());
    assertEquals(1, result.getViolations().size());
    
    String suggestion = result.getViolations().get(0).getSuggestion();
    assertTrue(suggestion.contains("添加ORDER BY子句"), 
        "Suggestion should mention adding ORDER BY clause (添加ORDER BY子句)");
  }

  // ==================== OFFSET with ORDER BY Tests ====================

  @Test
  void testOffsetWithOrderBy_shouldPass() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 ORDER BY id LIMIT 10 OFFSET 100";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectOffsetWithOrderBy")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testOffsetWithoutOrderBy_shouldViolate() throws Exception {
    String sql = "SELECT * FROM user WHERE id > 0 LIMIT 10 OFFSET 100";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectOffsetWithoutOrderBy")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertFalse(result.isPassed());
    assertEquals(RiskLevel.LOW, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }
}

