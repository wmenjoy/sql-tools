package com.footstone.sqlguard.validator;

import static org.junit.jupiter.api.Assertions.*;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator;
import java.util.Arrays;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for DefaultSqlSafetyValidator interface and basic validation flow.
 */
@DisplayName("DefaultSqlSafetyValidator - Interface Implementation")
class DefaultSqlSafetyValidatorTest {

  private JSqlParserFacade facade;
  private DefaultSqlSafetyValidator validator;

  @BeforeEach
  void setUp() {
    facade = new JSqlParserFacade(false); // fail-fast mode
    SqlDeduplicationFilter.clearThreadCache(); // Clear cache before each test
  }

  @Test
  @DisplayName("validate() should return ValidationResult with violations from enabled checkers")
  void testValidateInterface() {
    // Create a mock checker that always adds a HIGH violation
    RuleChecker mockChecker = new RuleChecker() {
      @Override
      public void check(SqlContext context, ValidationResult result) {
        result.addViolation(
            RiskLevel.HIGH,
            "Mock violation",
            "Fix the issue"
        );
      }

      @Override
      public boolean isEnabled() {
        return true;
      }
    };

    // Create validator with mock checker
    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(Arrays.asList(mockChecker));
    SqlDeduplicationFilter filter = new SqlDeduplicationFilter();
    validator = new DefaultSqlSafetyValidator(
        facade,
        Arrays.asList(mockChecker),
        orchestrator,
        filter
    );

    // Create test context
    SqlContext context = SqlContext.builder()
        .sql("SELECT * FROM users WHERE id = ?")
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectById")
        .build();

    // Execute validation
    ValidationResult result = validator.validate(context);

    // Verify results
    assertNotNull(result, "Result should not be null");
    assertFalse(result.isPassed(), "Result should fail when violations present");
    assertEquals(RiskLevel.HIGH, result.getRiskLevel(), "Risk level should be HIGH");
    assertEquals(1, result.getViolations().size(), "Should have 1 violation");
    assertEquals("Mock violation", result.getViolations().get(0).getMessage());
  }

  @Test
  @DisplayName("validate() should return pass when no violations found")
  void testValidatePass() {
    // Create a mock checker that never adds violations
    RuleChecker mockChecker = new RuleChecker() {
      @Override
      public void check(SqlContext context, ValidationResult result) {
        // No violations
      }

      @Override
      public boolean isEnabled() {
        return true;
      }
    };

    // Create validator
    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(Arrays.asList(mockChecker));
    SqlDeduplicationFilter filter = new SqlDeduplicationFilter();
    validator = new DefaultSqlSafetyValidator(
        facade,
        Arrays.asList(mockChecker),
        orchestrator,
        filter
    );

    // Create test context
    SqlContext context = SqlContext.builder()
        .sql("SELECT * FROM users WHERE id = ?")
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectById")
        .build();

    // Execute validation
    ValidationResult result = validator.validate(context);

    // Verify results
    assertNotNull(result);
    assertTrue(result.isPassed(), "Result should pass when no violations");
    assertEquals(RiskLevel.SAFE, result.getRiskLevel(), "Risk level should be SAFE");
    assertTrue(result.getViolations().isEmpty(), "Should have no violations");
  }

  @Test
  @DisplayName("validate() should aggregate multiple violations to highest risk level")
  void testMultipleViolationsAggregation() {
    // Create checkers with different risk levels
    RuleChecker lowChecker = new RuleChecker() {
      @Override
      public void check(SqlContext context, ValidationResult result) {
        result.addViolation(RiskLevel.LOW, "Low risk issue", "Fix it");
      }

      @Override
      public boolean isEnabled() {
        return true;
      }
    };

    RuleChecker criticalChecker = new RuleChecker() {
      @Override
      public void check(SqlContext context, ValidationResult result) {
        result.addViolation(RiskLevel.CRITICAL, "Critical issue", "Fix immediately");
      }

      @Override
      public boolean isEnabled() {
        return true;
      }
    };

    // Create validator
    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(
        Arrays.asList(lowChecker, criticalChecker)
    );
    SqlDeduplicationFilter filter = new SqlDeduplicationFilter();
    validator = new DefaultSqlSafetyValidator(
        facade,
        Arrays.asList(lowChecker, criticalChecker),
        orchestrator,
        filter
    );

    // Create test context
    SqlContext context = SqlContext.builder()
        .sql("SELECT * FROM users WHERE id = ?")
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectById")
        .build();

    // Execute validation
    ValidationResult result = validator.validate(context);

    // Verify results
    assertNotNull(result);
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel(), 
        "Risk level should aggregate to CRITICAL (highest)");
    assertEquals(2, result.getViolations().size(), "Should have 2 violations");
  }

  @Test
  @DisplayName("validate() should skip disabled checkers")
  void testDisabledCheckersSkipped() {
    // Create a disabled checker
    RuleChecker disabledChecker = new RuleChecker() {
      @Override
      public void check(SqlContext context, ValidationResult result) {
        result.addViolation(RiskLevel.HIGH, "Should not appear", "N/A");
      }

      @Override
      public boolean isEnabled() {
        return false; // Disabled
      }
    };

    // Create validator
    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(
        Arrays.asList(disabledChecker)
    );
    SqlDeduplicationFilter filter = new SqlDeduplicationFilter();
    validator = new DefaultSqlSafetyValidator(
        facade,
        Arrays.asList(disabledChecker),
        orchestrator,
        filter
    );

    // Create test context
    SqlContext context = SqlContext.builder()
        .sql("SELECT * FROM users WHERE id = ?")
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectById")
        .build();

    // Execute validation
    ValidationResult result = validator.validate(context);

    // Verify results
    assertTrue(result.isPassed(), "Should pass when all checkers disabled");
    assertTrue(result.getViolations().isEmpty(), "Should have no violations");
  }
}












