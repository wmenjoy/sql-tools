package com.footstone.sqlguard.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ViolationInfo value object.
 * Tests immutability, equals/hashCode contract, and validation.
 */
@DisplayName("ViolationInfo Tests")
class ViolationInfoTest {

  @Test
  @DisplayName("Creation with all fields succeeds")
  void testCreationWithAllFields() {
    // Act
    ViolationInfo violation = new ViolationInfo(
        RiskLevel.HIGH,
        "SQL injection risk detected",
        "Use parameterized queries"
    );

    // Assert
    assertNotNull(violation);
    assertEquals(RiskLevel.HIGH, violation.getRiskLevel());
    assertEquals("SQL injection risk detected", violation.getMessage());
    assertEquals("Use parameterized queries", violation.getSuggestion());
  }

  @Test
  @DisplayName("Creation with null suggestion succeeds")
  void testCreationWithNullSuggestion() {
    // Act
    ViolationInfo violation = new ViolationInfo(
        RiskLevel.MEDIUM,
        "Missing WHERE clause",
        null
    );

    // Assert
    assertNotNull(violation);
    assertEquals(RiskLevel.MEDIUM, violation.getRiskLevel());
    assertEquals("Missing WHERE clause", violation.getMessage());
    assertEquals(null, violation.getSuggestion());
  }

  @Test
  @DisplayName("Creation with null riskLevel throws IllegalArgumentException")
  void testCreationWithNullRiskLevel() {
    // Act & Assert
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> new ViolationInfo(null, "Message", "Suggestion")
    );
    assertTrue(exception.getMessage().contains("riskLevel"));
  }

  @Test
  @DisplayName("Creation with null message throws IllegalArgumentException")
  void testCreationWithNullMessage() {
    // Act & Assert
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> new ViolationInfo(RiskLevel.LOW, null, "Suggestion")
    );
    assertTrue(exception.getMessage().contains("message"));
  }

  @Test
  @DisplayName("Creation with empty message throws IllegalArgumentException")
  void testCreationWithEmptyMessage() {
    // Act & Assert
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> new ViolationInfo(RiskLevel.LOW, "", "Suggestion")
    );
    assertTrue(exception.getMessage().contains("message"));
  }

  @Test
  @DisplayName("equals() compares riskLevel and message only")
  void testEqualsComparesRiskLevelAndMessage() {
    // Arrange
    ViolationInfo v1 = new ViolationInfo(RiskLevel.HIGH, "Issue", "Fix 1");
    ViolationInfo v2 = new ViolationInfo(RiskLevel.HIGH, "Issue", "Fix 2");
    ViolationInfo v3 = new ViolationInfo(RiskLevel.HIGH, "Issue", null);

    // Assert - same riskLevel and message, different suggestions
    assertEquals(v1, v2);
    assertEquals(v1, v3);
    assertEquals(v2, v3);
  }

  @Test
  @DisplayName("equals() returns false for different riskLevel")
  void testEqualsReturnsFalseForDifferentRiskLevel() {
    // Arrange
    ViolationInfo v1 = new ViolationInfo(RiskLevel.HIGH, "Issue", "Fix");
    ViolationInfo v2 = new ViolationInfo(RiskLevel.LOW, "Issue", "Fix");

    // Assert
    assertNotEquals(v1, v2);
  }

  @Test
  @DisplayName("equals() returns false for different message")
  void testEqualsReturnsFalseForDifferentMessage() {
    // Arrange
    ViolationInfo v1 = new ViolationInfo(RiskLevel.HIGH, "Issue 1", "Fix");
    ViolationInfo v2 = new ViolationInfo(RiskLevel.HIGH, "Issue 2", "Fix");

    // Assert
    assertNotEquals(v1, v2);
  }

  @Test
  @DisplayName("equals() is reflexive")
  void testEqualsIsReflexive() {
    // Arrange
    ViolationInfo v1 = new ViolationInfo(RiskLevel.MEDIUM, "Issue", "Fix");

    // Assert
    assertEquals(v1, v1);
  }

  @Test
  @DisplayName("equals() is symmetric")
  void testEqualsIsSymmetric() {
    // Arrange
    ViolationInfo v1 = new ViolationInfo(RiskLevel.MEDIUM, "Issue", "Fix");
    ViolationInfo v2 = new ViolationInfo(RiskLevel.MEDIUM, "Issue", "Fix");

    // Assert
    assertEquals(v1, v2);
    assertEquals(v2, v1);
  }

  @Test
  @DisplayName("equals() is transitive")
  void testEqualsIsTransitive() {
    // Arrange
    ViolationInfo v1 = new ViolationInfo(RiskLevel.MEDIUM, "Issue", "Fix 1");
    ViolationInfo v2 = new ViolationInfo(RiskLevel.MEDIUM, "Issue", "Fix 2");
    ViolationInfo v3 = new ViolationInfo(RiskLevel.MEDIUM, "Issue", "Fix 3");

    // Assert
    assertEquals(v1, v2);
    assertEquals(v2, v3);
    assertEquals(v1, v3);
  }

  @Test
  @DisplayName("equals() returns false for null")
  void testEqualsReturnsFalseForNull() {
    // Arrange
    ViolationInfo v1 = new ViolationInfo(RiskLevel.MEDIUM, "Issue", "Fix");

    // Assert
    assertNotEquals(v1, null);
  }

  @Test
  @DisplayName("equals() returns false for different class")
  void testEqualsReturnsFalseForDifferentClass() {
    // Arrange
    ViolationInfo v1 = new ViolationInfo(RiskLevel.MEDIUM, "Issue", "Fix");
    String other = "Not a ViolationInfo";

    // Assert
    assertNotEquals(v1, other);
  }

  @Test
  @DisplayName("hashCode() is consistent with equals")
  void testHashCodeConsistentWithEquals() {
    // Arrange
    ViolationInfo v1 = new ViolationInfo(RiskLevel.HIGH, "Issue", "Fix 1");
    ViolationInfo v2 = new ViolationInfo(RiskLevel.HIGH, "Issue", "Fix 2");

    // Assert - equal objects must have equal hash codes
    assertEquals(v1, v2);
    assertEquals(v1.hashCode(), v2.hashCode());
  }

  @Test
  @DisplayName("hashCode() differs for different riskLevel")
  void testHashCodeDiffersForDifferentRiskLevel() {
    // Arrange
    ViolationInfo v1 = new ViolationInfo(RiskLevel.HIGH, "Issue", "Fix");
    ViolationInfo v2 = new ViolationInfo(RiskLevel.LOW, "Issue", "Fix");

    // Assert - different objects should (usually) have different hash codes
    assertNotEquals(v1.hashCode(), v2.hashCode());
  }

  @Test
  @DisplayName("hashCode() differs for different message")
  void testHashCodeDiffersForDifferentMessage() {
    // Arrange
    ViolationInfo v1 = new ViolationInfo(RiskLevel.HIGH, "Issue 1", "Fix");
    ViolationInfo v2 = new ViolationInfo(RiskLevel.HIGH, "Issue 2", "Fix");

    // Assert
    assertNotEquals(v1.hashCode(), v2.hashCode());
  }

  @Test
  @DisplayName("toString() includes all fields")
  void testToStringIncludesAllFields() {
    // Arrange
    ViolationInfo violation = new ViolationInfo(
        RiskLevel.CRITICAL,
        "Critical issue",
        "Urgent fix"
    );

    // Act
    String result = violation.toString();

    // Assert
    assertNotNull(result);
    assertTrue(result.contains("CRITICAL"));
    assertTrue(result.contains("Critical issue"));
    assertTrue(result.contains("Urgent fix"));
    assertTrue(result.contains("ViolationInfo"));
  }

  @Test
  @DisplayName("toString() handles null suggestion")
  void testToStringHandlesNullSuggestion() {
    // Arrange
    ViolationInfo violation = new ViolationInfo(
        RiskLevel.LOW,
        "Minor issue",
        null
    );

    // Act
    String result = violation.toString();

    // Assert
    assertNotNull(result);
    assertTrue(result.contains("LOW"));
    assertTrue(result.contains("Minor issue"));
    assertTrue(result.contains("null") || result.contains("suggestion=null"));
  }

  @Test
  @DisplayName("ViolationInfo is immutable - fields cannot be modified")
  void testImmutability() {
    // Arrange
    ViolationInfo violation = new ViolationInfo(
        RiskLevel.HIGH,
        "Issue",
        "Fix"
    );

    // Assert - all getters return same values
    assertEquals(RiskLevel.HIGH, violation.getRiskLevel());
    assertEquals("Issue", violation.getMessage());
    assertEquals("Fix", violation.getSuggestion());

    // Note: Since fields are final, we can't test modification directly
    // This test verifies that getters consistently return the same values
    assertEquals(RiskLevel.HIGH, violation.getRiskLevel());
    assertEquals("Issue", violation.getMessage());
    assertEquals("Fix", violation.getSuggestion());
  }
}
