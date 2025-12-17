package com.footstone.sqlguard.scanner.model;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ViolationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for SqlEntry violations support.
 *
 * <p>Tests the violation tracking functionality added to SqlEntry model,
 * including adding violations, querying violation status, and determining
 * highest risk levels.</p>
 */
@DisplayName("SqlEntry Violations Support Tests")
class SqlEntryViolationsTest {

  private static final String TEST_FILE_PATH = "/path/to/test/Mapper.xml";
  private static final String TEST_MAPPER_ID = "com.example.TestMapper.selectById";
  private static final String TEST_SQL = "SELECT * FROM user WHERE id = #{id}";

  /**
   * Creates a test SqlEntry with no violations.
   */
  private SqlEntry createTestEntry() {
    return new SqlEntry(
        SourceType.XML,
        TEST_FILE_PATH,
        TEST_MAPPER_ID,
        SqlCommandType.SELECT,
        TEST_SQL,
        42
    );
  }

  /**
   * Creates a test violation with specified risk level.
   */
  private ViolationInfo createViolation(RiskLevel riskLevel, String message) {
    return new ViolationInfo(riskLevel, message, "Test suggestion");
  }

  @Test
  @DisplayName("Should add single violation successfully")
  void testAddSingleViolation() {
    // Given
    SqlEntry entry = createTestEntry();
    ViolationInfo violation = createViolation(RiskLevel.HIGH, "Test violation");

    // When
    entry.addViolation(violation);

    // Then
    assertTrue(entry.hasViolations());
    assertEquals(1, entry.getViolations().size());
    assertEquals(violation, entry.getViolations().get(0));
  }

  @Test
  @DisplayName("Should add multiple violations one by one")
  void testAddMultipleViolationsOneByOne() {
    // Given
    SqlEntry entry = createTestEntry();
    ViolationInfo violation1 = createViolation(RiskLevel.MEDIUM, "Violation 1");
    ViolationInfo violation2 = createViolation(RiskLevel.HIGH, "Violation 2");
    ViolationInfo violation3 = createViolation(RiskLevel.LOW, "Violation 3");

    // When
    entry.addViolation(violation1);
    entry.addViolation(violation2);
    entry.addViolation(violation3);

    // Then
    assertTrue(entry.hasViolations());
    assertEquals(3, entry.getViolations().size());
    assertEquals(Arrays.asList(violation1, violation2, violation3), entry.getViolations());
  }

  @Test
  @DisplayName("Should add violations list successfully")
  void testAddViolationsList() {
    // Given
    SqlEntry entry = createTestEntry();
    List<ViolationInfo> violations = Arrays.asList(
        createViolation(RiskLevel.CRITICAL, "Critical issue"),
        createViolation(RiskLevel.HIGH, "High issue"),
        createViolation(RiskLevel.MEDIUM, "Medium issue")
    );

    // When
    entry.addViolations(violations);

    // Then
    assertTrue(entry.hasViolations());
    assertEquals(3, entry.getViolations().size());
    assertEquals(violations, entry.getViolations());
  }

  @Test
  @DisplayName("Should get violations list correctly")
  void testGetViolationsList() {
    // Given
    SqlEntry entry = createTestEntry();
    ViolationInfo violation1 = createViolation(RiskLevel.LOW, "Issue 1");
    ViolationInfo violation2 = createViolation(RiskLevel.MEDIUM, "Issue 2");
    entry.addViolation(violation1);
    entry.addViolation(violation2);

    // When
    List<ViolationInfo> violations = entry.getViolations();

    // Then
    assertEquals(2, violations.size());
    assertEquals(Arrays.asList(violation1, violation2), violations);
  }

  @Test
  @DisplayName("hasViolations() should return true when violations exist")
  void testHasViolationsReturnsTrueWhenViolationsExist() {
    // Given
    SqlEntry entry = createTestEntry();
    entry.addViolation(createViolation(RiskLevel.LOW, "Test"));

    // When & Then
    assertTrue(entry.hasViolations());
  }

  @Test
  @DisplayName("hasViolations() should return false when no violations")
  void testHasViolationsReturnsFalseWhenNoViolations() {
    // Given
    SqlEntry entry = createTestEntry();

    // When & Then
    assertFalse(entry.hasViolations());
  }

  @Test
  @DisplayName("getHighestRiskLevel() should return CRITICAL when present")
  void testGetHighestRiskLevelReturnsCritical() {
    // Given
    SqlEntry entry = createTestEntry();
    entry.addViolation(createViolation(RiskLevel.MEDIUM, "Medium issue"));
    entry.addViolation(createViolation(RiskLevel.CRITICAL, "Critical issue"));
    entry.addViolation(createViolation(RiskLevel.LOW, "Low issue"));

    // When
    RiskLevel highest = entry.getHighestRiskLevel();

    // Then
    assertEquals(RiskLevel.CRITICAL, highest);
  }

  @Test
  @DisplayName("getHighestRiskLevel() should return HIGH when no CRITICAL")
  void testGetHighestRiskLevelReturnsHighWhenNoCritical() {
    // Given
    SqlEntry entry = createTestEntry();
    entry.addViolation(createViolation(RiskLevel.LOW, "Low issue"));
    entry.addViolation(createViolation(RiskLevel.HIGH, "High issue"));
    entry.addViolation(createViolation(RiskLevel.MEDIUM, "Medium issue"));

    // When
    RiskLevel highest = entry.getHighestRiskLevel();

    // Then
    assertEquals(RiskLevel.HIGH, highest);
  }

  @Test
  @DisplayName("getHighestRiskLevel() should return SAFE when no violations")
  void testGetHighestRiskLevelReturnsSafeWhenNoViolations() {
    // Given
    SqlEntry entry = createTestEntry();

    // When
    RiskLevel highest = entry.getHighestRiskLevel();

    // Then
    assertEquals(RiskLevel.SAFE, highest);
  }

  @Test
  @DisplayName("Violations should be immutable - defensive copy")
  void testViolationsAreImmutable_DefensiveCopy() {
    // Given
    SqlEntry entry = createTestEntry();
    ViolationInfo violation1 = createViolation(RiskLevel.HIGH, "Original violation");
    entry.addViolation(violation1);

    // When - get violations list
    List<ViolationInfo> violations = entry.getViolations();
    ViolationInfo violation2 = createViolation(RiskLevel.CRITICAL, "External violation");

    // Try to modify returned list
    violations.add(violation2);

    // Then - verify original entry is not modified
    assertEquals(1, entry.getViolations().size());
    assertFalse(entry.getViolations().contains(violation2));
  }

  @Test
  @DisplayName("Should handle adding empty violations list")
  void testAddViolationsWithEmptyList() {
    // Given
    SqlEntry entry = createTestEntry();
    List<ViolationInfo> emptyList = new ArrayList<>();

    // When
    entry.addViolations(emptyList);

    // Then
    assertFalse(entry.hasViolations());
    assertTrue(entry.getViolations().isEmpty());
  }

  @Test
  @DisplayName("Should handle adding null violations list gracefully")
  void testAddViolationsWithNull() {
    // Given
    SqlEntry entry = createTestEntry();

    // When
    entry.addViolations(null);

    // Then - should handle gracefully without throwing exception
    assertFalse(entry.hasViolations());
    assertTrue(entry.getViolations().isEmpty());
  }

  @Test
  @DisplayName("Should construct SqlEntry with violations")
  void testConstructorWithViolations() {
    // Given
    List<ViolationInfo> violations = Arrays.asList(
        createViolation(RiskLevel.HIGH, "Issue 1"),
        createViolation(RiskLevel.MEDIUM, "Issue 2")
    );

    // When
    SqlEntry entry = new SqlEntry(
        SourceType.XML,
        TEST_FILE_PATH,
        TEST_MAPPER_ID,
        SqlCommandType.SELECT,
        TEST_SQL,
        42,
        false,
        violations
    );

    // Then
    assertTrue(entry.hasViolations());
    assertEquals(2, entry.getViolations().size());
    assertEquals(RiskLevel.HIGH, entry.getHighestRiskLevel());
  }

  @Test
  @DisplayName("Should handle multiple violations with same risk level")
  void testMultipleRiskLevels() {
    // Given
    SqlEntry entry = createTestEntry();
    entry.addViolation(createViolation(RiskLevel.LOW, "Low 1"));
    entry.addViolation(createViolation(RiskLevel.LOW, "Low 2"));
    entry.addViolation(createViolation(RiskLevel.MEDIUM, "Medium 1"));

    // When
    RiskLevel highest = entry.getHighestRiskLevel();

    // Then
    assertEquals(RiskLevel.MEDIUM, highest);
    assertEquals(3, entry.getViolations().size());
  }

  @Test
  @DisplayName("Should throw exception when adding null violation")
  void testAddNullViolationThrowsException() {
    // Given
    SqlEntry entry = createTestEntry();

    // When & Then
    assertThrows(IllegalArgumentException.class, () -> entry.addViolation(null));
  }
}
