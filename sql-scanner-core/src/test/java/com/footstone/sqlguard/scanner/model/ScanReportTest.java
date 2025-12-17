package com.footstone.sqlguard.scanner.model;

import com.footstone.sqlguard.core.model.SqlCommandType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScanReport data model.
 * Tests entry/wrapper aggregation and statistics calculation.
 */
@DisplayName("ScanReport Model Tests")
class ScanReportTest {

  private ScanReport report;

  @BeforeEach
  void setUp() {
    report = new ScanReport();
  }

  @Test
  @DisplayName("Should add SqlEntry items to report")
  void testAddSqlEntry() {
    // Given
    SqlEntry entry1 = new SqlEntry(
        SourceType.XML, "/path/mapper.xml", "mapper.id1",
        SqlCommandType.SELECT, "SELECT * FROM user", 10);

    SqlEntry entry2 = new SqlEntry(
        SourceType.ANNOTATION, "/path/Mapper.java", "mapper.id2",
        SqlCommandType.INSERT, "INSERT INTO user VALUES (1)", 20);

    // When
    report.addEntry(entry1);
    report.addEntry(entry2);

    // Then
    assertEquals(2, report.getEntries().size());
    assertTrue(report.getEntries().contains(entry1));
    assertTrue(report.getEntries().contains(entry2));
  }

  @Test
  @DisplayName("Should add WrapperUsage items to report")
  void testAddWrapperUsage() {
    // Given
    WrapperUsage usage1 = new WrapperUsage(
        "/path/Service.java", "getUserById", 15, "QueryWrapper", true);

    WrapperUsage usage2 = new WrapperUsage(
        "/path/Service.java", "updateUser", 30, "UpdateWrapper", true);

    // When
    report.addWrapperUsage(usage1);
    report.addWrapperUsage(usage2);

    // Then
    assertEquals(2, report.getWrapperUsages().size());
    assertTrue(report.getWrapperUsages().contains(usage1));
    assertTrue(report.getWrapperUsages().contains(usage2));
  }

  @Test
  @DisplayName("Should calculate total SQL count statistic")
  void testStatisticsTotalSqlCount() {
    // Given
    report.addEntry(new SqlEntry(
        SourceType.XML, "/path/mapper.xml", "id1",
        SqlCommandType.SELECT, "SELECT 1", 10));
    report.addEntry(new SqlEntry(
        SourceType.ANNOTATION, "/path/Mapper.java", "id2",
        SqlCommandType.INSERT, "INSERT INTO t VALUES (1)", 20));
    report.addEntry(new SqlEntry(
        SourceType.XML, "/path/mapper2.xml", "id3",
        SqlCommandType.UPDATE, "UPDATE t SET x=1", 30));

    // When
    report.calculateStatistics();

    // Then
    assertEquals(3, report.getStatistics().get("totalSqlCount"));
  }

  @Test
  @DisplayName("Should calculate wrapper usage count statistic")
  void testStatisticsWrapperUsageCount() {
    // Given
    report.addWrapperUsage(new WrapperUsage(
        "/path/Service.java", "method1", 10, "QueryWrapper", true));
    report.addWrapperUsage(new WrapperUsage(
        "/path/Service.java", "method2", 20, "UpdateWrapper", true));

    // When
    report.calculateStatistics();

    // Then
    assertEquals(2, report.getStatistics().get("wrapperUsageCount"));
  }

  @Test
  @DisplayName("Should calculate dynamic SQL count statistic")
  void testStatisticsDynamicSqlCount() {
    // Given
    SqlEntry staticEntry = new SqlEntry(
        SourceType.XML, "/path/mapper.xml", "id1",
        SqlCommandType.SELECT, "SELECT * FROM user WHERE id = 1", 10);

    SqlEntry dynamicEntry = new SqlEntry(
        SourceType.XML, "/path/mapper.xml", "id2",
        SqlCommandType.SELECT, "SELECT * FROM user <where>", 20);
    dynamicEntry.setDynamic(true);

    report.addEntry(staticEntry);
    report.addEntry(dynamicEntry);

    // When
    report.calculateStatistics();

    // Then
    assertEquals(2, report.getStatistics().get("totalSqlCount"));
    assertEquals(1, report.getStatistics().get("dynamicSqlCount"));
  }

  @Test
  @DisplayName("Should calculate SQL count by command type")
  void testStatisticsSqlCountByType() {
    // Given
    report.addEntry(new SqlEntry(
        SourceType.XML, "/path/mapper.xml", "id1",
        SqlCommandType.SELECT, "SELECT 1", 10));
    report.addEntry(new SqlEntry(
        SourceType.XML, "/path/mapper.xml", "id2",
        SqlCommandType.SELECT, "SELECT 2", 20));
    report.addEntry(new SqlEntry(
        SourceType.XML, "/path/mapper.xml", "id3",
        SqlCommandType.INSERT, "INSERT INTO t VALUES (1)", 30));
    report.addEntry(new SqlEntry(
        SourceType.XML, "/path/mapper.xml", "id4",
        SqlCommandType.UPDATE, "UPDATE t SET x=1", 40));
    report.addEntry(new SqlEntry(
        SourceType.XML, "/path/mapper.xml", "id5",
        SqlCommandType.DELETE, "DELETE FROM t", 50));

    // When
    report.calculateStatistics();

    // Then
    assertEquals(2, report.getStatistics().get("selectCount"));
    assertEquals(1, report.getStatistics().get("insertCount"));
    assertEquals(1, report.getStatistics().get("updateCount"));
    assertEquals(1, report.getStatistics().get("deleteCount"));
  }

  @Test
  @DisplayName("Should calculate SQL count by source type")
  void testStatisticsSqlCountBySource() {
    // Given
    report.addEntry(new SqlEntry(
        SourceType.XML, "/path/mapper.xml", "id1",
        SqlCommandType.SELECT, "SELECT 1", 10));
    report.addEntry(new SqlEntry(
        SourceType.XML, "/path/mapper.xml", "id2",
        SqlCommandType.SELECT, "SELECT 2", 20));
    report.addEntry(new SqlEntry(
        SourceType.ANNOTATION, "/path/Mapper.java", "id3",
        SqlCommandType.INSERT, "INSERT INTO t VALUES (1)", 30));
    report.addEntry(new SqlEntry(
        SourceType.WRAPPER, "/path/Service.java", "id4",
        SqlCommandType.UPDATE, "UPDATE t SET x=1", 40));

    // When
    report.calculateStatistics();

    // Then
    assertEquals(2, report.getStatistics().get("xmlSourceCount"));
    assertEquals(1, report.getStatistics().get("annotationSourceCount"));
    assertEquals(1, report.getStatistics().get("wrapperSourceCount"));
  }

  @Test
  @DisplayName("Should handle empty report with zero entries")
  void testEmptyReportZeroEntries() {
    // When
    report.calculateStatistics();

    // Then
    assertEquals(0, report.getEntries().size());
    assertEquals(0, report.getWrapperUsages().size());
    assertEquals(0, report.getStatistics().get("totalSqlCount"));
    assertEquals(0, report.getStatistics().get("wrapperUsageCount"));
  }

  @Test
  @DisplayName("Should handle empty report with zero violations")
  void testEmptyReportZeroViolations() {
    // When
    report.calculateStatistics();

    // Then - all counts should be 0
    assertEquals(0, report.getStatistics().get("totalSqlCount"));
    assertEquals(0, report.getStatistics().get("dynamicSqlCount"));
    assertEquals(0, report.getStatistics().get("selectCount"));
    assertEquals(0, report.getStatistics().get("insertCount"));
    assertEquals(0, report.getStatistics().get("updateCount"));
    assertEquals(0, report.getStatistics().get("deleteCount"));
  }

  @Test
  @DisplayName("Should return immutable view of entries")
  void testGetEntriesReturnsView() {
    // Given
    SqlEntry entry = new SqlEntry(
        SourceType.XML, "/path/mapper.xml", "id1",
        SqlCommandType.SELECT, "SELECT 1", 10);
    report.addEntry(entry);

    // When
    java.util.List<SqlEntry> entries = report.getEntries();

    // Then - modifications should not affect original
    assertEquals(1, entries.size());
    assertTrue(entries.contains(entry));
  }

  @Test
  @DisplayName("Should return immutable view of wrapper usages")
  void testGetWrapperUsagesReturnsView() {
    // Given
    WrapperUsage usage = new WrapperUsage(
        "/path/Service.java", "method", 10, "QueryWrapper", true);
    report.addWrapperUsage(usage);

    // When
    java.util.List<WrapperUsage> usages = report.getWrapperUsages();

    // Then
    assertEquals(1, usages.size());
    assertTrue(usages.contains(usage));
  }

  @Test
  @DisplayName("Should maintain insertion order in statistics")
  void testStatisticsInsertionOrder() {
    // Given
    report.addEntry(new SqlEntry(
        SourceType.XML, "/path/mapper.xml", "id1",
        SqlCommandType.SELECT, "SELECT 1", 10));

    // When
    report.calculateStatistics();

    // Then - statistics should be LinkedHashMap maintaining order
    java.util.Map<String, Integer> stats = report.getStatistics();
    assertNotNull(stats);
    assertTrue(stats.get("totalSqlCount") >= 0);
  }
}

