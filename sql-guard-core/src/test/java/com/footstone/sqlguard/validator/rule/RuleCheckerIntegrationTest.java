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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the complete rule checker framework.
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
@DisplayName("Rule Checker Framework Integration Tests")
class RuleCheckerIntegrationTest {

  private SqlContext testContext;

  /**
   * Mock checker 1 - adds LOW violation when enabled.
   */
  private static class MockChecker1 extends AbstractRuleChecker {
    private final CheckerConfig config;

    MockChecker1(CheckerConfig config) {
      this.config = config;
    }

    @Override
    public void check(SqlContext context, ValidationResult result) {
      result.addViolation(RiskLevel.LOW, "MockChecker1 violation", "Fix suggestion 1");
    }

    @Override
    public boolean isEnabled() {
      return config.isEnabled();
    }
  }

  /**
   * Mock checker 2 - adds HIGH violation when enabled.
   */
  private static class MockChecker2 extends AbstractRuleChecker {
    private final CheckerConfig config;

    MockChecker2(CheckerConfig config) {
      this.config = config;
    }

    @Override
    public void check(SqlContext context, ValidationResult result) {
      result.addViolation(RiskLevel.HIGH, "MockChecker2 violation", "Fix suggestion 2");
    }

    @Override
    public boolean isEnabled() {
      return config.isEnabled();
    }
  }

  /**
   * Mock checker 3 - disabled (isEnabled returns false).
   */
  private static class MockChecker3 extends AbstractRuleChecker {
    private final CheckerConfig config;

    MockChecker3(CheckerConfig config) {
      this.config = config;
    }

    @Override
    public void check(SqlContext context, ValidationResult result) {
      result.addViolation(RiskLevel.CRITICAL, "MockChecker3 should not appear",
          "Should not execute");
    }

    @Override
    public boolean isEnabled() {
      return config.isEnabled();
    }
  }

  /**
   * Mock checker that adds multiple violations.
   */
  private static class MultiViolationChecker extends AbstractRuleChecker {
    private final CheckerConfig config;

    MultiViolationChecker(CheckerConfig config) {
      this.config = config;
    }

    @Override
    public void check(SqlContext context, ValidationResult result) {
      result.addViolation(RiskLevel.LOW, "Violation 1", "Suggestion 1");
      result.addViolation(RiskLevel.MEDIUM, "Violation 2", "Suggestion 2");
      result.addViolation(RiskLevel.HIGH, "Violation 3", "Suggestion 3");
    }

    @Override
    public boolean isEnabled() {
      return config.isEnabled();
    }
  }

  /**
   * Mock checker for order testing.
   */
  private static class OrderedChecker extends AbstractRuleChecker {
    private final CheckerConfig config;
    private final int order;

    OrderedChecker(CheckerConfig config, int order) {
      this.config = config;
      this.order = order;
    }

    @Override
    public void check(SqlContext context, ValidationResult result) {
      result.addViolation(RiskLevel.LOW, "Checker " + order, "Suggestion " + order);
    }

    @Override
    public boolean isEnabled() {
      return config.isEnabled();
    }
  }

  @BeforeEach
  void setUp() {
    testContext = SqlContext.builder()
        .sql("SELECT * FROM users WHERE id = 1")
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectById")
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
    RuleChecker criticalChecker = new AbstractRuleChecker() {
      @Override
      public void check(SqlContext context, ValidationResult result) {
        result.addViolation(RiskLevel.CRITICAL, "Critical security issue",
            "Immediate action required");
      }

      @Override
      public boolean isEnabled() {
        return config2.isEnabled();
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








