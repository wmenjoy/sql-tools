package com.footstone.sqlguard.scanner.model;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ViolationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ScanReport violation statistics.
 *
 * <p>Tests the violation aggregation and statistics functionality added to ScanReport,
 * including counting violations by risk level and querying violation status.</p>
 */
@DisplayName("ScanReport Violation Statistics Tests")
class ScanReportViolationStatsTest {

  @Test
  @DisplayName("getTotalViolations() should return correct count")
  void testGetTotalViolations() {
    // Given
    ScanReport report = new ScanReport();
    report.addEntry(createEntryWithViolations(RiskLevel.CRITICAL, RiskLevel.HIGH));
    report.addEntry(createEntryWithViolations(RiskLevel.MEDIUM));
    report.calculateStatistics();

    // When
    int total = report.getTotalViolations();

    // Then
    assertEquals(3, total);
  }

  @Test
  @DisplayName("getViolationCount(CRITICAL) should return correct count")
  void testGetViolationCountCritical() {
    // Given
    ScanReport report = new ScanReport();
    report.addEntry(createEntryWithViolations(RiskLevel.CRITICAL));
    report.addEntry(createEntryWithViolations(RiskLevel.CRITICAL, RiskLevel.HIGH));
    report.calculateStatistics();

    // When
    int criticalCount = report.getViolationCount(RiskLevel.CRITICAL);

    // Then
    assertEquals(2, criticalCount);
  }

  @Test
  @DisplayName("getViolationCount(HIGH) should return correct count")
  void testGetViolationCountHigh() {
    // Given
    ScanReport report = new ScanReport();
    report.addEntry(createEntryWithViolations(RiskLevel.HIGH, RiskLevel.HIGH));
    report.addEntry(createEntryWithViolations(RiskLevel.MEDIUM));
    report.calculateStatistics();

    // When
    int highCount = report.getViolationCount(RiskLevel.HIGH);

    // Then
    assertEquals(2, highCount);
  }

  @Test
  @DisplayName("getViolationCount(MEDIUM) should return correct count")
  void testGetViolationCountMedium() {
    // Given
    ScanReport report = new ScanReport();
    report.addEntry(createEntryWithViolations(RiskLevel.MEDIUM));
    report.addEntry(createEntryWithViolations(RiskLevel.MEDIUM, RiskLevel.LOW));
    report.calculateStatistics();

    // When
    int mediumCount = report.getViolationCount(RiskLevel.MEDIUM);

    // Then
    assertEquals(2, mediumCount);
  }

  @Test
  @DisplayName("getViolationCount(LOW) should return correct count")
  void testGetViolationCountLow() {
    // Given
    ScanReport report = new ScanReport();
    report.addEntry(createEntryWithViolations(RiskLevel.LOW));
    report.addEntry(createEntryWithViolations(RiskLevel.LOW, RiskLevel.LOW));
    report.calculateStatistics();

    // When
    int lowCount = report.getViolationCount(RiskLevel.LOW);

    // Then
    assertEquals(3, lowCount);
  }

  @Test
  @DisplayName("hasViolations() should return true when violations exist")
  void testHasViolationsReturnsTrue() {
    // Given
    ScanReport report = new ScanReport();
    report.addEntry(createEntryWithViolations(RiskLevel.LOW));
    report.calculateStatistics();

    // When & Then
    assertTrue(report.hasViolations());
  }

  @Test
  @DisplayName("hasViolations() should return false when no violations")
  void testHasViolationsReturnsFalse() {
    // Given
    ScanReport report = new ScanReport();
    report.addEntry(createEntryWithoutViolations());
    report.calculateStatistics();

    // When & Then
    assertFalse(report.hasViolations());
  }

  @Test
  @DisplayName("hasCriticalViolations() should return true when CRITICAL exists")
  void testHasCriticalViolationsReturnsTrue() {
    // Given
    ScanReport report = new ScanReport();
    report.addEntry(createEntryWithViolations(RiskLevel.CRITICAL));
    report.calculateStatistics();

    // When & Then
    assertTrue(report.hasCriticalViolations());
  }

  @Test
  @DisplayName("hasCriticalViolations() should return false when no CRITICAL")
  void testHasCriticalViolationsReturnsFalse() {
    // Given
    ScanReport report = new ScanReport();
    report.addEntry(createEntryWithViolations(RiskLevel.HIGH, RiskLevel.MEDIUM));
    report.calculateStatistics();

    // When & Then
    assertFalse(report.hasCriticalViolations());
  }

  @Test
  @DisplayName("Empty report should have zero violations")
  void testEmptyReportHasZeroViolations() {
    // Given
    ScanReport report = new ScanReport();
    report.calculateStatistics();

    // When & Then
    assertEquals(0, report.getTotalViolations());
    assertFalse(report.hasViolations());
    assertFalse(report.hasCriticalViolations());
    assertEquals(0, report.getViolationCount(RiskLevel.CRITICAL));
    assertEquals(0, report.getViolationCount(RiskLevel.HIGH));
    assertEquals(0, report.getViolationCount(RiskLevel.MEDIUM));
    assertEquals(0, report.getViolationCount(RiskLevel.LOW));
  }

  @Test
  @DisplayName("Multiple entries with violations should aggregate correctly")
  void testMultipleEntriesAggregateCorrectly() {
    // Given
    ScanReport report = new ScanReport();
    report.addEntry(createEntryWithViolations(RiskLevel.CRITICAL, RiskLevel.HIGH));
    report.addEntry(createEntryWithViolations(RiskLevel.HIGH, RiskLevel.MEDIUM));
    report.addEntry(createEntryWithViolations(RiskLevel.MEDIUM, RiskLevel.LOW));
    report.calculateStatistics();

    // When & Then
    assertEquals(6, report.getTotalViolations());
    assertEquals(1, report.getViolationCount(RiskLevel.CRITICAL));
    assertEquals(2, report.getViolationCount(RiskLevel.HIGH));
    assertEquals(2, report.getViolationCount(RiskLevel.MEDIUM));
    assertEquals(1, report.getViolationCount(RiskLevel.LOW));
    assertTrue(report.hasViolations());
    assertTrue(report.hasCriticalViolations());
  }

  @Test
  @DisplayName("Violation statistics should be immutable")
  void testViolationStatisticsImmutable() {
    // Given
    ScanReport report = new ScanReport();
    report.addEntry(createEntryWithViolations(RiskLevel.HIGH));
    report.calculateStatistics();

    int originalCount = report.getTotalViolations();

    // When - add more entries after calculateStatistics()
    report.addEntry(createEntryWithViolations(RiskLevel.CRITICAL));

    // Then - statistics should not change until recalculated
    assertEquals(originalCount, report.getTotalViolations());
    assertEquals(0, report.getViolationCount(RiskLevel.CRITICAL));
  }

  /**
   * Helper method to create SqlEntry with violations.
   */
  private SqlEntry createEntryWithViolations(RiskLevel... riskLevels) {
    SqlEntry entry = new SqlEntry(
        SourceType.XML,
        "/test/Mapper.xml",
        "com.example.TestMapper.select",
        SqlCommandType.SELECT,
        "SELECT * FROM user",
        42
    );

    for (RiskLevel riskLevel : riskLevels) {
      entry.addViolation(new ViolationInfo(
          riskLevel,
          "Test violation at " + riskLevel,
          "Test suggestion"
      ));
    }

    return entry;
  }

  /**
   * Helper method to create SqlEntry without violations.
   */
  private SqlEntry createEntryWithoutViolations() {
    return new SqlEntry(
        SourceType.XML,
        "/test/Mapper.xml",
        "com.example.TestMapper.select",
        SqlCommandType.SELECT,
        "SELECT * FROM user WHERE id = #{id}",
        42
    );
  }
}



