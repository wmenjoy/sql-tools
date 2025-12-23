package com.footstone.sqlguard.validator.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.sf.jsqlparser.statement.update.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the complete rule checker framework.
 *
 * <p>Phase 12 Migration: This test class has been updated to use the new
 * visitor-based architecture. Mock checkers now override visitXxx() methods
 * instead of check().</p>
 *
 * <p>Tests verify:</p>
 * <ul>
 *   <li>Full orchestration with multiple mock checkers</li>
 *   <li>Enabled/disabled toggling behavior</li>
 *   <li>Empty checker list handling</li>
 *   <li>Execution order preservation</li>
 *   <li>Multiple violations per checker</li>
 *   <li>Risk level aggregation to CRITICAL</li>
 * </ul>
 */
@DisplayName("Rule Checker Framework Integration Tests (Phase 12)")
class RuleCheckerIntegrationTest {

  private SqlContext testContext;

  /**
   * Mock checker 1 - adds LOW violation when enabled (via visitUpdate).
   */
  private static class MockChecker1 extends AbstractRuleChecker {

    MockChecker1(CheckerConfig config) {
      super(config);
    }

    @Override
    public void visitUpdate(Update update, SqlContext context) {
      addViolation(RiskLevel.LOW, "MockChecker1 violation", "Fix suggestion 1");
    }
  }

  /**
   * Mock checker 2 - adds HIGH violation when enabled (via visitUpdate).
   */
  private static class MockChecker2 extends AbstractRuleChecker {

    MockChecker2(CheckerConfig config) {
      super(config);
    }

    @Override
    public void visitUpdate(Update update, SqlContext context) {
      addViolation(RiskLevel.HIGH, "MockChecker2 violation", "Fix suggestion 2");
    }
  }

  /**
   * Mock checker 3 - adds CRITICAL violation when enabled (via visitUpdate).
   * Used to verify disabled checkers are skipped.
   */
  private static class MockChecker3 extends AbstractRuleChecker {

    MockChecker3(CheckerConfig config) {
      super(config);
    }

    @Override
    public void visitUpdate(Update update, SqlContext context) {
      addViolation(RiskLevel.CRITICAL, "MockChecker3 should not appear", "Should not execute");
    }
  }

  /**
   * Mock checker that adds multiple violations (via visitUpdate).
   */
  private static class MultiViolationChecker extends AbstractRuleChecker {

    MultiViolationChecker(CheckerConfig config) {
      super(config);
    }

    @Override
    public void visitUpdate(Update update, SqlContext context) {
      addViolation(RiskLevel.LOW, "Violation 1", "Suggestion 1");
      addViolation(RiskLevel.MEDIUM, "Violation 2", "Suggestion 2");
      addViolation(RiskLevel.HIGH, "Violation 3", "Suggestion 3");
    }
  }

  /**
   * Mock checker for order testing (via visitUpdate).
   */
  private static class OrderedChecker extends AbstractRuleChecker {
    private final int order;

    OrderedChecker(CheckerConfig config, int order) {
      super(config);
      this.order = order;
    }

    @Override
    public void visitUpdate(Update update, SqlContext context) {
      addViolation(RiskLevel.LOW, "Checker " + order, "Suggestion " + order);
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    // Use UPDATE statement since mock checkers use visitUpdate
    String sql = "UPDATE users SET name = 'test' WHERE id = 1";
    net.sf.jsqlparser.statement.Statement stmt = 
        net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql);
    
    testContext = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.UPDATE)
        .mapperId("test.Mapper.updateById")
        .statement(stmt)
        .build();
  }

  @Test
  @DisplayName("Full orchestration: all three checkers with checker3 disabled")
  void testFullOrchestration() {
    CheckerConfig config1 = new CheckerConfig(true);
    CheckerConfig config2 = new CheckerConfig(true);
    CheckerConfig config3 = new CheckerConfig(false); // Disabled

    List<RuleChecker> checkers = Arrays.asList(
        new MockChecker1(config1),
        new MockChecker2(config2),
        new MockChecker3(config3)
    );

    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    ValidationResult result = ValidationResult.pass();

    orchestrator.orchestrate(testContext, result);

    assertFalse(result.isPassed(), "Result should be failed");
    assertEquals(2, result.getViolations().size(),
        "Should have 2 violations (checker3 skipped)");
    assertEquals(RiskLevel.HIGH, result.getRiskLevel(),
        "Risk level should be HIGH (max of LOW and HIGH)");

    // Verify violations are from checker1 and checker2 only
    assertEquals("MockChecker1 violation", result.getViolations().get(0).getMessage());
    assertEquals("MockChecker2 violation", result.getViolations().get(1).getMessage());
  }

  @Test
  @DisplayName("Enabled/disabled toggling: disable checker1, only checker2 violation present")
  void testEnabledDisabledToggling() {
    CheckerConfig config1 = new CheckerConfig(false); // Disabled
    CheckerConfig config2 = new CheckerConfig(true);
    CheckerConfig config3 = new CheckerConfig(false);

    List<RuleChecker> checkers = Arrays.asList(
        new MockChecker1(config1),
        new MockChecker2(config2),
        new MockChecker3(config3)
    );

    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    ValidationResult result = ValidationResult.pass();

    orchestrator.orchestrate(testContext, result);

    assertFalse(result.isPassed(), "Result should be failed");
    assertEquals(1, result.getViolations().size(),
        "Should have 1 violation (only checker2 enabled)");
    assertEquals(RiskLevel.HIGH, result.getRiskLevel(), "Risk level should be HIGH");
    assertEquals("MockChecker2 violation", result.getViolations().get(0).getMessage());
  }

  @Test
  @DisplayName("Empty checker list: returns passed result")
  void testEmptyCheckerList() {
    List<RuleChecker> checkers = new ArrayList<>();
    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    ValidationResult result = ValidationResult.pass();

    orchestrator.orchestrate(testContext, result);

    assertTrue(result.isPassed(), "Result should remain passed");
    assertEquals(RiskLevel.SAFE, result.getRiskLevel(), "Risk level should be SAFE");
    assertEquals(0, result.getViolations().size(), "No violations should be present");
  }

  @Test
  @DisplayName("Execution order preservation: 5 checkers in sequence")
  void testExecutionOrderPreservation() {
    CheckerConfig config = new CheckerConfig(true);
    List<RuleChecker> checkers = new ArrayList<>();

    for (int i = 1; i <= 5; i++) {
      checkers.add(new OrderedChecker(config, i));
    }

    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    ValidationResult result = ValidationResult.pass();

    orchestrator.orchestrate(testContext, result);

    assertEquals(5, result.getViolations().size(), "Should have 5 violations");

    // Verify violations appear in same order
    for (int i = 0; i < 5; i++) {
      assertEquals("Checker " + (i + 1), result.getViolations().get(i).getMessage(),
          "Violation " + (i + 1) + " should be in correct order");
    }
  }

  @Test
  @DisplayName("Multiple violations per checker: all appear in result")
  void testMultipleViolationsPerChecker() {
    CheckerConfig config = new CheckerConfig(true);
    List<RuleChecker> checkers = Arrays.asList(new MultiViolationChecker(config));

    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    ValidationResult result = ValidationResult.pass();

    orchestrator.orchestrate(testContext, result);

    assertFalse(result.isPassed(), "Result should be failed");
    assertEquals(3, result.getViolations().size(),
        "Should have 3 violations from single checker");
    assertEquals(RiskLevel.HIGH, result.getRiskLevel(),
        "Risk level should be HIGH (max of LOW, MEDIUM, HIGH)");

    // Verify all violations present
    assertEquals("Violation 1", result.getViolations().get(0).getMessage());
    assertEquals("Violation 2", result.getViolations().get(1).getMessage());
    assertEquals("Violation 3", result.getViolations().get(2).getMessage());
  }

  @Test
  @DisplayName("Risk level aggregates to CRITICAL when any checker adds CRITICAL")
  void testRiskLevelAggregationToCritical() {
    CheckerConfig config1 = new CheckerConfig(true);
    CheckerConfig config2 = new CheckerConfig(true);

    // Checker that adds CRITICAL violation
    RuleChecker criticalChecker = new AbstractRuleChecker(config2) {
      @Override
      public void visitUpdate(Update update, SqlContext context) {
        addViolation(RiskLevel.CRITICAL, "Critical security issue", "Immediate action required");
      }
    };

    List<RuleChecker> checkers = Arrays.asList(
        new MockChecker1(config1), // LOW
        criticalChecker // CRITICAL
    );

    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    ValidationResult result = ValidationResult.pass();

    orchestrator.orchestrate(testContext, result);

    assertEquals(2, result.getViolations().size(), "Should have 2 violations");
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel(),
        "Risk level should be CRITICAL (max of LOW and CRITICAL)");
  }

  @Test
  @DisplayName("All checkers disabled: returns passed result")
  void testAllCheckersDisabled() {
    CheckerConfig config1 = new CheckerConfig(false);
    CheckerConfig config2 = new CheckerConfig(false);
    CheckerConfig config3 = new CheckerConfig(false);

    List<RuleChecker> checkers = Arrays.asList(
        new MockChecker1(config1),
        new MockChecker2(config2),
        new MockChecker3(config3)
    );

    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    ValidationResult result = ValidationResult.pass();

    orchestrator.orchestrate(testContext, result);

    assertTrue(result.isPassed(), "Result should remain passed (all disabled)");
    assertEquals(RiskLevel.SAFE, result.getRiskLevel(), "Risk level should be SAFE");
    assertEquals(0, result.getViolations().size(), "No violations should be present");
  }
}






