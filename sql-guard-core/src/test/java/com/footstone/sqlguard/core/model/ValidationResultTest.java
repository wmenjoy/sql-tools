package com.footstone.sqlguard.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ValidationResult class.
 * Tests violation aggregation, risk level determination, and pass/fail logic.
 */
@DisplayName("ValidationResult Tests")
class ValidationResultTest {

  @Test
  @DisplayName("Initial creation with passed=true and SAFE risk level")
  void testInitialCreationPassed() {
    // Act
    ValidationResult result = ValidationResult.pass();

    // Assert
    assertNotNull(result);
    assertTrue(result.isPassed());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertTrue(result.getViolations().isEmpty());
  }

  @Test
  @DisplayName("Adding single violation changes passed to false")
  void testAddingSingleViolationChangesPassed() {
    // Arrange
    ValidationResult result = ValidationResult.pass();

    // Act
    result.addViolation(RiskLevel.LOW, "Missing index on WHERE clause", 
        "Consider adding index on user_id column");

    // Assert
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.LOW, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }

  @Test
  @DisplayName("Adding multiple violations aggregates to highest risk level")
  void testMultipleViolationsAggregateToHighestRisk() {
    // Arrange
    ValidationResult result = ValidationResult.pass();

    // Act - Add violations in increasing severity
    result.addViolation(RiskLevel.LOW, "Minor issue", "Fix suggestion");
    result.addViolation(RiskLevel.MEDIUM, "Medium issue", "Medium fix");
    result.addViolation(RiskLevel.CRITICAL, "Critical issue", "Urgent fix");
    result.addViolation(RiskLevel.HIGH, "High issue", "High fix");

    // Assert - Should aggregate to CRITICAL (highest)
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertEquals(4, result.getViolations().size());
  }

  @Test
  @DisplayName("Adding violations in reverse order still aggregates correctly")
  void testViolationsInReverseOrderAggregateCorrectly() {
    // Arrange
    ValidationResult result = ValidationResult.pass();

    // Act - Add violations in decreasing severity
    result.addViolation(RiskLevel.HIGH, "High issue", "High fix");
    result.addViolation(RiskLevel.MEDIUM, "Medium issue", "Medium fix");
    result.addViolation(RiskLevel.LOW, "Low issue", "Low fix");

    // Assert - Should remain HIGH (highest added)
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertEquals(3, result.getViolations().size());
  }

  @Test
  @DisplayName("Empty violations list means passed")
  void testEmptyViolationsListMeansPassed() {
    // Arrange
    ValidationResult result = ValidationResult.pass();

    // Assert
    assertTrue(result.isPassed());
    assertTrue(result.getViolations().isEmpty());
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
  }

  @Test
  @DisplayName("getRiskLevel returns SAFE when passed")
  void testGetRiskLevelReturnsSafeWhenPassed() {
    // Arrange
    ValidationResult result = ValidationResult.pass();

    // Assert
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
  }

  @Test
  @DisplayName("Details map can store additional context")
  void testDetailsMapStoresAdditionalContext() {
    // Arrange
    ValidationResult result = ValidationResult.pass();

    // Act
    result.getDetails().put("executionTime", 123L);
    result.getDetails().put("checkerName", "SqlInjectionChecker");

    // Assert
    assertEquals(123L, result.getDetails().get("executionTime"));
    assertEquals("SqlInjectionChecker", result.getDetails().get("checkerName"));
  }

  @Test
  @DisplayName("Violation info is properly stored with all fields")
  void testViolationInfoProperlyStored() {
    // Arrange
    ValidationResult result = ValidationResult.pass();

    // Act
    result.addViolation(RiskLevel.MEDIUM, "SQL injection risk detected", 
        "Use parameterized queries");

    // Assert
    assertEquals(1, result.getViolations().size());
    ViolationInfo violation = result.getViolations().get(0);
    assertEquals(RiskLevel.MEDIUM, violation.getRiskLevel());
    assertEquals("SQL injection risk detected", violation.getMessage());
    assertEquals("Use parameterized queries", violation.getSuggestion());
  }

  @Test
  @DisplayName("Multiple violations of same risk level are all stored")
  void testMultipleViolationsSameRiskLevel() {
    // Arrange
    ValidationResult result = ValidationResult.pass();

    // Act
    result.addViolation(RiskLevel.MEDIUM, "Issue 1", "Fix 1");
    result.addViolation(RiskLevel.MEDIUM, "Issue 2", "Fix 2");
    result.addViolation(RiskLevel.MEDIUM, "Issue 3", "Fix 3");

    // Assert
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(3, result.getViolations().size());
  }

  @Test
  @DisplayName("Risk level updates correctly when adding higher severity violation")
  void testRiskLevelUpdatesWithHigherSeverity() {
    // Arrange
    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.LOW, "Low issue", "Fix");

    // Act - Add higher severity
    result.addViolation(RiskLevel.CRITICAL, "Critical issue", "Urgent fix");

    // Assert
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
  }

  @Test
  @DisplayName("Risk level does not downgrade when adding lower severity violation")
  void testRiskLevelDoesNotDowngrade() {
    // Arrange
    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.CRITICAL, "Critical issue", "Urgent fix");

    // Act - Add lower severity
    result.addViolation(RiskLevel.LOW, "Low issue", "Fix");

    // Assert - Should remain CRITICAL
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertEquals(2, result.getViolations().size());
  }
}





