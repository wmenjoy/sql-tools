package com.footstone.sqlguard.validator.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test class for AbstractRuleChecker template method pattern.
 *
 * <p>Phase 12 Migration: This test class has been updated to verify the new
 * visitor-based architecture instead of the old utility methods.</p>
 *
 * <p>Tests verify:</p>
 * <ul>
 *   <li>Template method dispatches to correct visitXxx() methods</li>
 *   <li>Direct JSqlParser API usage (getWhere(), getTable(), etc.)</li>
 *   <li>addViolation() helper method works correctly</li>
 *   <li>ThreadLocal context and result are properly managed</li>
 * </ul>
 *
 * <p><strong>NOTE:</strong> The old utility methods (extractWhere, extractTableName,
 * extractFields, isDummyCondition, isConstant) have been removed in Phase 12.
 * Tests for these methods are no longer applicable.</p>
 */
@DisplayName("AbstractRuleChecker Template Method Tests (Phase 12)")
class AbstractRuleCheckerTest {

  private JSqlParserFacade parser;
  private TestRuleChecker checker;
  private CheckerConfig config;

  /**
   * Concrete implementation of AbstractRuleChecker for testing template method.
   */
  private static class TestRuleChecker extends AbstractRuleChecker {
    // Track which visitXxx() methods were called
    private boolean visitSelectCalled = false;
    private boolean visitUpdateCalled = false;
    private boolean visitDeleteCalled = false;
    private boolean visitInsertCalled = false;
    private Statement lastStatement = null;

    TestRuleChecker(CheckerConfig config) {
      super(config);
    }

    @Override
    public void visitSelect(Select select, SqlContext context) {
      visitSelectCalled = true;
      lastStatement = select;
    }

    @Override
    public void visitUpdate(Update update, SqlContext context) {
      visitUpdateCalled = true;
      lastStatement = update;
      // Test: Use direct JSqlParser API
      if (update.getWhere() == null) {
        addViolation(RiskLevel.CRITICAL, "UPDATE without WHERE clause");
      }
    }

    @Override
    public void visitDelete(Delete delete, SqlContext context) {
      visitDeleteCalled = true;
      lastStatement = delete;
      // Test: Use direct JSqlParser API
      if (delete.getWhere() == null) {
        addViolation(RiskLevel.CRITICAL, "DELETE without WHERE clause");
      }
    }

    // Getters for test assertions
    public boolean isVisitSelectCalled() { return visitSelectCalled; }
    public boolean isVisitUpdateCalled() { return visitUpdateCalled; }
    public boolean isVisitDeleteCalled() { return visitDeleteCalled; }
    public boolean isVisitInsertCalled() { return visitInsertCalled; }
    public Statement getLastStatement() { return lastStatement; }

    public void reset() {
      visitSelectCalled = false;
      visitUpdateCalled = false;
      visitDeleteCalled = false;
      visitInsertCalled = false;
      lastStatement = null;
    }
  }

  @BeforeEach
  void setUp() {
    parser = new JSqlParserFacade();
    config = new CheckerConfig(true);
    checker = new TestRuleChecker(config);
  }

  @Nested
  @DisplayName("Template Method Dispatch Tests")
  class TemplateMethodDispatchTests {

    @Test
    @DisplayName("check() dispatches SELECT to visitSelect()")
    void testDispatchSelect() throws Exception {
      String sql = "SELECT * FROM users WHERE id = 1";
      Statement stmt = parser.parse(sql);

      SqlContext context = SqlContext.builder()
          .sql(sql)
          .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
          .statementId("test.selectById")
          .statement(stmt)
          .build();

      ValidationResult result = ValidationResult.pass();
      checker.check(context, result);

      assertTrue(checker.isVisitSelectCalled(), "visitSelect() should be called");
      assertFalse(checker.isVisitUpdateCalled(), "visitUpdate() should NOT be called");
      assertFalse(checker.isVisitDeleteCalled(), "visitDelete() should NOT be called");
      assertFalse(checker.isVisitInsertCalled(), "visitInsert() should NOT be called");
      assertNotNull(checker.getLastStatement(), "Statement should be captured");
      assertTrue(checker.getLastStatement() instanceof Select, "Should be Select statement");
    }

    @Test
    @DisplayName("check() dispatches UPDATE to visitUpdate()")
    void testDispatchUpdate() throws Exception {
      String sql = "UPDATE users SET name = 'John' WHERE id = 1";
      Statement stmt = parser.parse(sql);

      SqlContext context = SqlContext.builder()
          .sql(sql)
          .type(SqlCommandType.UPDATE)
        .executionLayer(ExecutionLayer.MYBATIS)
          .statementId("test.updateById")
          .statement(stmt)
          .build();

      ValidationResult result = ValidationResult.pass();
      checker.check(context, result);

      assertFalse(checker.isVisitSelectCalled(), "visitSelect() should NOT be called");
      assertTrue(checker.isVisitUpdateCalled(), "visitUpdate() should be called");
      assertFalse(checker.isVisitDeleteCalled(), "visitDelete() should NOT be called");
      assertFalse(checker.isVisitInsertCalled(), "visitInsert() should NOT be called");
      assertTrue(checker.getLastStatement() instanceof Update, "Should be Update statement");
    }

    @Test
    @DisplayName("check() dispatches DELETE to visitDelete()")
    void testDispatchDelete() throws Exception {
      String sql = "DELETE FROM users WHERE id = 1";
      Statement stmt = parser.parse(sql);

      SqlContext context = SqlContext.builder()
          .sql(sql)
          .type(SqlCommandType.DELETE)
        .executionLayer(ExecutionLayer.MYBATIS)
          .statementId("test.deleteById")
          .statement(stmt)
          .build();

      ValidationResult result = ValidationResult.pass();
      checker.check(context, result);

      assertFalse(checker.isVisitSelectCalled(), "visitSelect() should NOT be called");
      assertFalse(checker.isVisitUpdateCalled(), "visitUpdate() should NOT be called");
      assertTrue(checker.isVisitDeleteCalled(), "visitDelete() should be called");
      assertFalse(checker.isVisitInsertCalled(), "visitInsert() should NOT be called");
      assertTrue(checker.getLastStatement() instanceof Delete, "Should be Delete statement");
    }
  }

  @Nested
  @DisplayName("Direct API Usage Tests")
  class DirectApiUsageTests {

    @Test
    @DisplayName("visitUpdate() can use update.getWhere() directly")
    void testDirectApiUpdateGetWhere() throws Exception {
      // UPDATE with WHERE - should pass
      String sql = "UPDATE users SET name = 'John' WHERE id = 1";
      Statement stmt = parser.parse(sql);

      SqlContext context = SqlContext.builder()
          .sql(sql)
          .type(SqlCommandType.UPDATE)
        .executionLayer(ExecutionLayer.MYBATIS)
          .statementId("test.updateWithWhere")
          .statement(stmt)
          .build();

      ValidationResult result = ValidationResult.pass();
      checker.check(context, result);

      assertTrue(result.isPassed(), "UPDATE with WHERE should pass");
      assertEquals(0, result.getViolations().size(), "No violations expected");
    }

    @Test
    @DisplayName("visitUpdate() detects missing WHERE via update.getWhere()")
    void testDirectApiUpdateNoWhere() throws Exception {
      // UPDATE without WHERE - should fail
      String sql = "UPDATE users SET name = 'John'";
      Statement stmt = parser.parse(sql);

      SqlContext context = SqlContext.builder()
          .sql(sql)
          .type(SqlCommandType.UPDATE)
        .executionLayer(ExecutionLayer.MYBATIS)
          .statementId("test.updateNoWhere")
          .statement(stmt)
          .build();

      ValidationResult result = ValidationResult.pass();
      checker.check(context, result);

      assertFalse(result.isPassed(), "UPDATE without WHERE should fail");
      assertEquals(1, result.getViolations().size(), "One violation expected");
      assertEquals(RiskLevel.CRITICAL, result.getViolations().get(0).getRiskLevel());
    }

    @Test
    @DisplayName("visitDelete() can use delete.getWhere() directly")
    void testDirectApiDeleteGetWhere() throws Exception {
      // DELETE with WHERE - should pass
      String sql = "DELETE FROM users WHERE id = 1";
      Statement stmt = parser.parse(sql);

      SqlContext context = SqlContext.builder()
          .sql(sql)
          .type(SqlCommandType.DELETE)
        .executionLayer(ExecutionLayer.MYBATIS)
          .statementId("test.deleteWithWhere")
          .statement(stmt)
          .build();

      ValidationResult result = ValidationResult.pass();
      checker.check(context, result);

      assertTrue(result.isPassed(), "DELETE with WHERE should pass");
      assertEquals(0, result.getViolations().size(), "No violations expected");
    }

    @Test
    @DisplayName("visitDelete() detects missing WHERE via delete.getWhere()")
    void testDirectApiDeleteNoWhere() throws Exception {
      // DELETE without WHERE - should fail
      String sql = "DELETE FROM users";
      Statement stmt = parser.parse(sql);

      SqlContext context = SqlContext.builder()
          .sql(sql)
          .type(SqlCommandType.DELETE)
        .executionLayer(ExecutionLayer.MYBATIS)
          .statementId("test.deleteNoWhere")
          .statement(stmt)
          .build();

      ValidationResult result = ValidationResult.pass();
      checker.check(context, result);

      assertFalse(result.isPassed(), "DELETE without WHERE should fail");
      assertEquals(1, result.getViolations().size(), "One violation expected");
      assertEquals(RiskLevel.CRITICAL, result.getViolations().get(0).getRiskLevel());
    }

    @Test
    @DisplayName("Direct API: select.getSelectBody() for SELECT items")
    void testDirectApiSelectGetSelectBody() throws Exception {
      String sql = "SELECT id, name FROM users WHERE id = 1";
      Statement stmt = parser.parse(sql);

      // Direct API test
      assertTrue(stmt instanceof Select, "Should be Select");
      Select select = (Select) stmt;

      // Use direct JSqlParser API
      assertTrue(select.getSelectBody() instanceof PlainSelect);
      PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
      assertNotNull(plainSelect.getSelectItems());
      assertEquals(2, plainSelect.getSelectItems().size(), "Should have 2 select items");

      // WHERE clause
      Expression where = plainSelect.getWhere();
      assertNotNull(where, "WHERE should not be null");
    }
  }

  @Nested
  @DisplayName("addViolation Helper Method Tests")
  class AddViolationHelperTests {

    @Test
    @DisplayName("addViolation() with RiskLevel and message")
    void testAddViolationTwoParams() throws Exception {
      String sql = "UPDATE users SET name = 'test'"; // No WHERE
      Statement stmt = parser.parse(sql);

      SqlContext context = SqlContext.builder()
          .sql(sql)
          .type(SqlCommandType.UPDATE)
        .executionLayer(ExecutionLayer.MYBATIS)
          .statementId("test.updateNoWhere")
          .statement(stmt)
          .build();

      ValidationResult result = ValidationResult.pass();
      checker.check(context, result);

      // Verify addViolation was called via visitUpdate
      assertEquals(1, result.getViolations().size());
      assertEquals(RiskLevel.CRITICAL, result.getViolations().get(0).getRiskLevel());
      assertEquals("UPDATE without WHERE clause", result.getViolations().get(0).getMessage());
    }
  }

  @Nested
  @DisplayName("Enabled/Disabled Tests")
  class EnabledDisabledTests {

    @Test
    @DisplayName("Checker respects isEnabled() from config")
    void testIsEnabledFromConfig() {
      CheckerConfig enabledConfig = new CheckerConfig(true);
      CheckerConfig disabledConfig = new CheckerConfig(false);

      TestRuleChecker enabledChecker = new TestRuleChecker(enabledConfig);
      TestRuleChecker disabledChecker = new TestRuleChecker(disabledConfig);

      assertTrue(enabledChecker.isEnabled(), "Enabled checker should return true");
      assertFalse(disabledChecker.isEnabled(), "Disabled checker should return false");
    }
  }

  @Nested
  @DisplayName("Null Handling Tests")
  class NullHandlingTests {

    @Test
    @DisplayName("check() handles null statement gracefully")
    void testNullStatement() {
      SqlContext context = SqlContext.builder()
          .sql("SELECT * FROM users")
          .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
          .statementId("test.select")
          // No statement set - will be null
          .build();

      ValidationResult result = ValidationResult.pass();

      // Should not throw exception
      checker.check(context, result);

      // No visitXxx should be called
      assertFalse(checker.isVisitSelectCalled());
      assertFalse(checker.isVisitUpdateCalled());
      assertFalse(checker.isVisitDeleteCalled());
      assertFalse(checker.isVisitInsertCalled());
    }
  }
}
