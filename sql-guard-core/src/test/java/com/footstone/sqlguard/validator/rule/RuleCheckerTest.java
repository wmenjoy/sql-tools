package com.footstone.sqlguard.validator.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test class for RuleChecker interface and CheckerConfig base class.
 *
 * <p>Tests verify:</p>
 * <ul>
 *   <li>Checker with no violations leaves ValidationResult passed</li>
 *   <li>Checker adding violation changes passed to false</li>
 *   <li>Disabled checker returns false from isEnabled()</li>
 *   <li>Check method signature accepts SqlContext and ValidationResult</li>
 * </ul>
 */
@DisplayName("RuleChecker Interface Tests")
class RuleCheckerTest {

  private SqlContext testContext;
  private ValidationResult testResult;

  @BeforeEach
  void setUp() {
    // Create minimal test context
    testContext = SqlContext.builder()
        .sql("SELECT * FROM users WHERE id = 1")
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectById")
        .build();

    testResult = ValidationResult.pass();
  }

  @Test
  @DisplayName("Checker with no violations leaves ValidationResult passed")
  void testCheckerNoViolations() {
    // Create a checker that does nothing
    RuleChecker noViolationChecker = new RuleChecker() {
      @Override
      public void check(SqlContext context, ValidationResult result) {
        // No violations added
      }

      @Override
      public boolean isEnabled() {
        return true;
      }
    };

    // Execute check
    noViolationChecker.check(testContext, testResult);

    // Verify result remains passed
    assertTrue(testResult.isPassed(), "Result should remain passed when no violations added");
    assertEquals(RiskLevel.SAFE, testResult.getRiskLevel(), "Risk level should remain SAFE");
    assertEquals(0, testResult.getViolations().size(), "No violations should be present");
  }

  @Test
  @DisplayName("Checker adding violation changes passed to false")
  void testCheckerAddsViolation() {
    // Create a checker that adds a violation
    RuleChecker violationChecker = new RuleChecker() {
      @Override
      public void check(SqlContext context, ValidationResult result) {
        result.addViolation(
            RiskLevel.MEDIUM,
            "Test violation",
            "Test suggestion"
        );
      }

      @Override
      public boolean isEnabled() {
        return true;
      }
    };

    // Execute check
    violationChecker.check(testContext, testResult);

    // Verify result is now failed
    assertFalse(testResult.isPassed(), "Result should be failed after violation added");
    assertEquals(RiskLevel.MEDIUM, testResult.getRiskLevel(), "Risk level should be MEDIUM");
    assertEquals(1, testResult.getViolations().size(), "One violation should be present");
  }

  @Test
  @DisplayName("Disabled checker returns false from isEnabled()")
  void testDisabledChecker() {
    // Create a disabled checker using CheckerConfig
    CheckerConfig disabledConfig = new CheckerConfig(false);

    RuleChecker disabledChecker = new RuleChecker() {
      @Override
      public void check(SqlContext context, ValidationResult result) {
        result.addViolation(
            RiskLevel.HIGH,
            "This should not be added",
            "Checker is disabled"
        );
      }

      @Override
      public boolean isEnabled() {
        return disabledConfig.isEnabled();
      }
    };

    // Verify isEnabled returns false
    assertFalse(disabledChecker.isEnabled(), "Disabled checker should return false");
  }

  @Test
  @DisplayName("Enabled checker returns true from isEnabled()")
  void testEnabledChecker() {
    // Create an enabled checker using CheckerConfig with default (true)
    CheckerConfig enabledConfig = new CheckerConfig();

    RuleChecker enabledChecker = new RuleChecker() {
      @Override
      public void check(SqlContext context, ValidationResult result) {
        // Implementation not relevant for this test
      }

      @Override
      public boolean isEnabled() {
        return enabledConfig.isEnabled();
      }
    };

    // Verify isEnabled returns true
    assertTrue(enabledChecker.isEnabled(), "Enabled checker should return true by default");
  }

  @Test
  @DisplayName("Check method accepts SqlContext and ValidationResult parameters")
  void testCheckMethodSignature() {
    // Create a checker that verifies parameter types
    RuleChecker signatureChecker = new RuleChecker() {
      @Override
      public void check(SqlContext context, ValidationResult result) {
        // Verify parameters are not null and have correct types
        assertTrue(context instanceof SqlContext, "First parameter should be SqlContext");
        assertTrue(result instanceof ValidationResult,
            "Second parameter should be ValidationResult");
        assertEquals("SELECT * FROM users WHERE id = 1", context.getSql(),
            "Context should contain correct SQL");
      }

      @Override
      public boolean isEnabled() {
        return true;
      }
    };

    // Execute check - assertions inside check method verify signature
    signatureChecker.check(testContext, testResult);
  }

  @Test
  @DisplayName("CheckerConfig with explicit enabled=true")
  void testCheckerConfigExplicitEnabled() {
    CheckerConfig config = new CheckerConfig(true);
    assertTrue(config.isEnabled(), "Config with enabled=true should return true");
  }

  @Test
  @DisplayName("CheckerConfig with explicit enabled=false")
  void testCheckerConfigExplicitDisabled() {
    CheckerConfig config = new CheckerConfig(false);
    assertFalse(config.isEnabled(), "Config with enabled=false should return false");
  }
}
