package com.footstone.sqlguard.validator.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.ValidationResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test class for RuleCheckerOrchestrator.
 *
 * <p>Tests verify:</p>
 * <ul>
 *   <li>Orchestrator with no checkers returns passed ValidationResult</li>
 *   <li>Single enabled checker adds violation correctly</li>
 *   <li>Multiple checkers add violations independently</li>
 *   <li>Disabled checker is skipped (isEnabled returns false)</li>
 *   <li>Final risk level is max of all violations</li>
 *   <li>Orchestrator preserves checker execution order</li>
 * </ul>
 */
@DisplayName("RuleCheckerOrchestrator Tests")
class RuleCheckerOrchestratorTest {

  private SqlContext testContext;

  @BeforeEach
  void setUp() {
    testContext = SqlContext.builder()
        .sql("SELECT * FROM users WHERE id = 1")
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("test.Mapper.selectById")
        .build();
  }

  @Test
  @DisplayName("Orchestrator with no checkers returns passed ValidationResult")
  void testEmptyCheckerList() {
    List<RuleChecker> checkers = new ArrayList<>();
    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    ValidationResult result = ValidationResult.pass();

    orchestrator.orchestrate(testContext, result);

    assertTrue(result.isPassed(), "Result should remain passed with no checkers");
    assertEquals(RiskLevel.SAFE, result.getRiskLevel(), "Risk level should be SAFE");
    assertEquals(0, result.getViolations().size(), "No violations should be present");
  }

  @Test
  @DisplayName("Single enabled checker adds violation correctly")
  void testSingleEnabledChecker() {
    RuleChecker checker = new MockChecker(true, RiskLevel.MEDIUM, "Test violation");
    List<RuleChecker> checkers = Arrays.asList(checker);
    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    ValidationResult result = ValidationResult.pass();

    orchestrator.orchestrate(testContext, result);

    assertFalse(result.isPassed(), "Result should be failed");
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel(), "Risk level should be MEDIUM");
    assertEquals(1, result.getViolations().size(), "Should have 1 violation");
    assertEquals("Test violation", result.getViolations().get(0).getMessage());
  }

  @Test
  @DisplayName("Multiple checkers add violations independently")
  void testMultipleCheckers() {
    RuleChecker checker1 = new MockChecker(true, RiskLevel.LOW, "Violation 1");
    RuleChecker checker2 = new MockChecker(true, RiskLevel.HIGH, "Violation 2");
    List<RuleChecker> checkers = Arrays.asList(checker1, checker2);
    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    ValidationResult result = ValidationResult.pass();

    orchestrator.orchestrate(testContext, result);

    assertFalse(result.isPassed(), "Result should be failed");
    assertEquals(RiskLevel.HIGH, result.getRiskLevel(),
        "Risk level should be HIGH (max of LOW and HIGH)");
    assertEquals(2, result.getViolations().size(), "Should have 2 violations");
  }

  @Test
  @DisplayName("Disabled checker is skipped")
  void testDisabledChecker() {
    RuleChecker enabledChecker = new MockChecker(true, RiskLevel.MEDIUM, "Enabled violation");
    RuleChecker disabledChecker = new MockChecker(false, RiskLevel.CRITICAL,
        "Should not appear");
    List<RuleChecker> checkers = Arrays.asList(enabledChecker, disabledChecker);
    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    ValidationResult result = ValidationResult.pass();

    orchestrator.orchestrate(testContext, result);

    assertFalse(result.isPassed(), "Result should be failed");
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel(),
        "Risk level should be MEDIUM (disabled checker skipped)");
    assertEquals(1, result.getViolations().size(), "Should have 1 violation (disabled skipped)");
    assertEquals("Enabled violation", result.getViolations().get(0).getMessage());
  }

  @Test
  @DisplayName("Final risk level is max of all violations - MEDIUM + CRITICAL = CRITICAL")
  void testRiskLevelAggregation() {
    RuleChecker checker1 = new MockChecker(true, RiskLevel.MEDIUM, "Medium violation");
    RuleChecker checker2 = new MockChecker(true, RiskLevel.CRITICAL, "Critical violation");
    List<RuleChecker> checkers = Arrays.asList(checker1, checker2);
    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    ValidationResult result = ValidationResult.pass();

    orchestrator.orchestrate(testContext, result);

    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel(),
        "Risk level should be CRITICAL (max of MEDIUM and CRITICAL)");
    assertEquals(2, result.getViolations().size(), "Should have 2 violations");
  }

  @Test
  @DisplayName("Orchestrator preserves checker execution order")
  void testExecutionOrder() {
    List<RuleChecker> checkers = new ArrayList<>();
    for (int i = 1; i <= 5; i++) {
      checkers.add(new MockChecker(true, RiskLevel.LOW, "Violation " + i));
    }
    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    ValidationResult result = ValidationResult.pass();

    orchestrator.orchestrate(testContext, result);

    assertEquals(5, result.getViolations().size(), "Should have 5 violations");
    for (int i = 0; i < 5; i++) {
      assertEquals("Violation " + (i + 1), result.getViolations().get(i).getMessage(),
          "Violations should appear in order");
    }
  }

  @Test
  @DisplayName("All enabled checkers execute without short-circuit")
  void testNoShortCircuit() {
    RuleChecker checker1 = new MockChecker(true, RiskLevel.CRITICAL, "Critical first");
    RuleChecker checker2 = new MockChecker(true, RiskLevel.LOW, "Low second");
    RuleChecker checker3 = new MockChecker(true, RiskLevel.MEDIUM, "Medium third");
    List<RuleChecker> checkers = Arrays.asList(checker1, checker2, checker3);
    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    ValidationResult result = ValidationResult.pass();

    orchestrator.orchestrate(testContext, result);

    assertEquals(3, result.getViolations().size(),
        "All 3 checkers should execute (no short-circuit)");
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel(),
        "Risk level should be CRITICAL");
  }

  @Test
  @DisplayName("Mixed enabled and disabled checkers")
  void testMixedEnabledDisabled() {
    List<RuleChecker> checkers = Arrays.asList(
        new MockChecker(true, RiskLevel.LOW, "Enabled 1"),
        new MockChecker(false, RiskLevel.HIGH, "Disabled 1"),
        new MockChecker(true, RiskLevel.MEDIUM, "Enabled 2"),
        new MockChecker(false, RiskLevel.CRITICAL, "Disabled 2"),
        new MockChecker(true, RiskLevel.HIGH, "Enabled 3")
    );
    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    ValidationResult result = ValidationResult.pass();

    orchestrator.orchestrate(testContext, result);

    assertEquals(3, result.getViolations().size(), "Should have 3 violations (3 enabled)");
    assertEquals(RiskLevel.HIGH, result.getRiskLevel(),
        "Risk level should be HIGH (max of LOW, MEDIUM, HIGH)");
    assertEquals("Enabled 1", result.getViolations().get(0).getMessage());
    assertEquals("Enabled 2", result.getViolations().get(1).getMessage());
    assertEquals("Enabled 3", result.getViolations().get(2).getMessage());
  }

  /**
   * Mock checker implementation for testing.
   */
  private static class MockChecker implements RuleChecker {
    private final boolean enabled;
    private final RiskLevel riskLevel;
    private final String message;

    MockChecker(boolean enabled, RiskLevel riskLevel, String message) {
      this.enabled = enabled;
      this.riskLevel = riskLevel;
      this.message = message;
    }

    @Override
    public void check(SqlContext context, ValidationResult result) {
      result.addViolation(riskLevel, message, "Test suggestion");
    }

    @Override
    public boolean isEnabled() {
      return enabled;
    }
  }
}



















