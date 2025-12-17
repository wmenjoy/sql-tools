package com.footstone.sqlguard.scanner.report;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.scanner.model.ScanReport;
import com.footstone.sqlguard.scanner.model.SourceType;
import com.footstone.sqlguard.scanner.model.SqlEntry;
import com.footstone.sqlguard.scanner.model.WrapperUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for ReportProcessor.
 */
class ReportProcessorTest {

  private ReportProcessor processor;

  @BeforeEach
  void setUp() {
    processor = new ReportProcessor();
  }

  /**
   * Test helper to create a ScanReport with violations at different risk levels.
   */
  private ScanReport createTestReport() {
    ScanReport report = new ScanReport();

    // Add CRITICAL violation
    List<ViolationInfo> criticalViolations = Arrays.asList(
        new ViolationInfo(
            RiskLevel.CRITICAL,
            "Missing WHERE clause in DELETE statement",
            "Add WHERE clause to restrict affected rows"
        )
    );
    SqlEntry criticalEntry = new SqlEntry(
        SourceType.XML,
        "/path/to/UserMapper.xml",
        "com.example.UserMapper.deleteAll",
        SqlCommandType.DELETE,
        "DELETE FROM user",
        42,
        false,
        criticalViolations
    );
    report.addEntry(criticalEntry);

    // Add HIGH violation
    List<ViolationInfo> highViolations = Arrays.asList(
        new ViolationInfo(
            RiskLevel.HIGH,
            "Dummy condition '1=1' detected",
            "Remove dummy condition and use proper WHERE clause"
        )
    );
    SqlEntry highEntry = new SqlEntry(
        SourceType.XML,
        "/path/to/ProductMapper.xml",
        "com.example.ProductMapper.search",
        SqlCommandType.SELECT,
        "SELECT * FROM product WHERE 1=1 AND name = ?",
        156,
        false,
        highViolations
    );
    report.addEntry(highEntry);

    // Add MEDIUM violation
    List<ViolationInfo> mediumViolations = Arrays.asList(
        new ViolationInfo(
            RiskLevel.MEDIUM,
            "SELECT * may cause performance issues",
            "Specify explicit column names"
        )
    );
    SqlEntry mediumEntry = new SqlEntry(
        SourceType.ANNOTATION,
        "/path/to/OrderService.java",
        "com.example.OrderService.findAll",
        SqlCommandType.SELECT,
        "SELECT * FROM orders",
        78,
        false,
        mediumViolations
    );
    report.addEntry(mediumEntry);

    // Add entry without violations
    SqlEntry safeEntry = new SqlEntry(
        SourceType.XML,
        "/path/to/UserMapper.xml",
        "com.example.UserMapper.findById",
        SqlCommandType.SELECT,
        "SELECT id, name FROM user WHERE id = ?",
        10,
        false,
        new ArrayList<>()
    );
    report.addEntry(safeEntry);

    // Add wrapper usage
    WrapperUsage wrapperUsage = new WrapperUsage(
        "/path/to/UserService.java",
        "findUsers",
        50,
        "QueryWrapper",
        true
    );
    report.addWrapperUsage(wrapperUsage);

    return report;
  }

  @Test
  void testGroupByRiskLevel_shouldGroupCorrectly() {
    // Given
    ScanReport report = createTestReport();

    // When
    ProcessedReport processed = processor.process(report);

    // Then
    Map<RiskLevel, List<ViolationEntry>> violationsByLevel = processed.getViolationsByLevel();

    // Should have CRITICAL, HIGH, MEDIUM groups
    assertTrue(violationsByLevel.containsKey(RiskLevel.CRITICAL));
    assertTrue(violationsByLevel.containsKey(RiskLevel.HIGH));
    assertTrue(violationsByLevel.containsKey(RiskLevel.MEDIUM));

    // CRITICAL should have 1 violation
    assertEquals(1, violationsByLevel.get(RiskLevel.CRITICAL).size());

    // HIGH should have 1 violation
    assertEquals(1, violationsByLevel.get(RiskLevel.HIGH).size());

    // MEDIUM should have 1 violation
    assertEquals(1, violationsByLevel.get(RiskLevel.MEDIUM).size());

    // Should not have LOW or SAFE groups (no violations at those levels)
    assertFalse(violationsByLevel.containsKey(RiskLevel.LOW) &&
        !violationsByLevel.get(RiskLevel.LOW).isEmpty());
  }

  @Test
  void testSortBySeverity_shouldOrderCriticalFirst() {
    // Given
    ScanReport report = createTestReport();

    // When
    ProcessedReport processed = processor.process(report);

    // Then
    Map<RiskLevel, List<ViolationEntry>> violationsByLevel = processed.getViolationsByLevel();

    // Verify CRITICAL entry
    List<ViolationEntry> criticalViolations = violationsByLevel.get(RiskLevel.CRITICAL);
    assertNotNull(criticalViolations);
    assertEquals(1, criticalViolations.size());
    assertEquals("Missing WHERE clause in DELETE statement",
        criticalViolations.get(0).getMessage());

    // Verify HIGH entry
    List<ViolationEntry> highViolations = violationsByLevel.get(RiskLevel.HIGH);
    assertNotNull(highViolations);
    assertEquals(1, highViolations.size());
    assertEquals("Dummy condition '1=1' detected",
        highViolations.get(0).getMessage());

    // Verify MEDIUM entry
    List<ViolationEntry> mediumViolations = violationsByLevel.get(RiskLevel.MEDIUM);
    assertNotNull(mediumViolations);
    assertEquals(1, mediumViolations.size());
    assertEquals("SELECT * may cause performance issues",
        mediumViolations.get(0).getMessage());
  }

  @Test
  void testExtractStatistics_shouldCalculateCorrectly() {
    // Given
    ScanReport report = createTestReport();
    report.calculateStatistics();

    // When
    ProcessedReport processed = processor.process(report);

    // Then
    Map<String, Integer> stats = processed.getStatistics();

    // Total SQL count (4 entries: 3 with violations + 1 safe)
    assertEquals(4, stats.get("totalSqlCount"));

    // Violation counts by level
    assertEquals(1, stats.get("criticalCount"));
    assertEquals(1, stats.get("highCount"));
    assertEquals(1, stats.get("mediumCount"));
    assertEquals(0, stats.get("lowCount"));

    // Total violations
    assertEquals(3, stats.get("totalViolations"));

    // Wrapper usage count
    assertEquals(1, stats.get("wrapperUsageCount"));
  }

  @Test
  void testPrepareFormattedData_shouldCreateRenderStructure() {
    // Given
    ScanReport report = createTestReport();

    // When
    ProcessedReport processed = processor.process(report);

    // Then
    // Verify ViolationEntry structure
    Map<RiskLevel, List<ViolationEntry>> violationsByLevel = processed.getViolationsByLevel();
    ViolationEntry criticalEntry = violationsByLevel.get(RiskLevel.CRITICAL).get(0);

    assertEquals("/path/to/UserMapper.xml", criticalEntry.getFilePath());
    assertEquals(42, criticalEntry.getLineNumber());
    assertEquals("com.example.UserMapper.deleteAll", criticalEntry.getMapperId());
    assertEquals("DELETE FROM user", criticalEntry.getSqlSnippet());
    assertEquals(RiskLevel.CRITICAL, criticalEntry.getRiskLevel());
    assertEquals("Missing WHERE clause in DELETE statement", criticalEntry.getMessage());
    assertEquals("Add WHERE clause to restrict affected rows", criticalEntry.getSuggestion());
  }

  @Test
  void testTruncateSqlSnippet_shouldLimitLength() {
    // Given
    ScanReport report = new ScanReport();

    // Create SQL > 100 characters
    String longSql = "SELECT id, name, email, phone, address, city, state, zip, country, " +
        "created_at, updated_at, status FROM user WHERE id = ?";

    List<ViolationInfo> violations = Arrays.asList(
        new ViolationInfo(RiskLevel.LOW, "Test violation", "Test suggestion")
    );

    SqlEntry entry = new SqlEntry(
        SourceType.XML,
        "/path/to/test.xml",
        "test.mapper",
        SqlCommandType.SELECT,
        longSql,
        1,
        false,
        violations
    );
    report.addEntry(entry);

    // When
    ProcessedReport processed = processor.process(report);

    // Then
    ViolationEntry violationEntry = processed.getViolationsByLevel()
        .get(RiskLevel.LOW).get(0);

    // SQL should be truncated to 100 chars + "..."
    assertTrue(violationEntry.getSqlSnippet().length() <= 103);
    if (longSql.length() > 100) {
      assertTrue(violationEntry.getSqlSnippet().endsWith("..."));
    }
  }

  @Test
  void testEmptyReport_shouldHandleGracefully() {
    // Given
    ScanReport report = new ScanReport();
    report.calculateStatistics();

    // When
    ProcessedReport processed = processor.process(report);

    // Then
    assertFalse(processed.hasViolations());
    assertEquals(0, processed.getTotalViolations());

    Map<String, Integer> stats = processed.getStatistics();
    assertEquals(0, stats.get("totalSqlCount"));
    assertEquals(0, stats.get("totalViolations"));
  }

  @Test
  void testMultipleViolationsInSingleEntry_shouldFlattenCorrectly() {
    // Given
    ScanReport report = new ScanReport();

    // Entry with multiple violations
    List<ViolationInfo> violations = Arrays.asList(
        new ViolationInfo(RiskLevel.HIGH, "Violation 1", "Suggestion 1"),
        new ViolationInfo(RiskLevel.MEDIUM, "Violation 2", "Suggestion 2")
    );

    SqlEntry entry = new SqlEntry(
        SourceType.XML,
        "/path/to/test.xml",
        "test.mapper",
        SqlCommandType.SELECT,
        "SELECT * FROM test",
        1,
        false,
        violations
    );
    report.addEntry(entry);

    // When
    ProcessedReport processed = processor.process(report);

    // Then
    // Should create separate ViolationEntry for each violation
    Map<RiskLevel, List<ViolationEntry>> violationsByLevel = processed.getViolationsByLevel();

    assertTrue(violationsByLevel.containsKey(RiskLevel.HIGH));
    assertTrue(violationsByLevel.containsKey(RiskLevel.MEDIUM));

    assertEquals(1, violationsByLevel.get(RiskLevel.HIGH).size());
    assertEquals(1, violationsByLevel.get(RiskLevel.MEDIUM).size());
  }

  @Test
  void testSortWithinRiskLevel_shouldOrderByFilePathThenLineNumber() {
    // Given
    ScanReport report = new ScanReport();

    // Add violations in random order
    List<ViolationInfo> violation = Arrays.asList(
        new ViolationInfo(RiskLevel.HIGH, "Test", "Test")
    );

    // Entry 3: /b/file.xml:10
    report.addEntry(new SqlEntry(SourceType.XML, "/b/file.xml", "mapper3",
        SqlCommandType.SELECT, "SELECT 3", 10, false, violation));

    // Entry 1: /a/file.xml:5
    report.addEntry(new SqlEntry(SourceType.XML, "/a/file.xml", "mapper1",
        SqlCommandType.SELECT, "SELECT 1", 5, false, violation));

    // Entry 2: /a/file.xml:20
    report.addEntry(new SqlEntry(SourceType.XML, "/a/file.xml", "mapper2",
        SqlCommandType.SELECT, "SELECT 2", 20, false, violation));

    // When
    ProcessedReport processed = processor.process(report);

    // Then
    List<ViolationEntry> highViolations = processed.getViolationsByLevel().get(RiskLevel.HIGH);
    assertEquals(3, highViolations.size());

    // Should be sorted by file path, then line number
    assertEquals("/a/file.xml", highViolations.get(0).getFilePath());
    assertEquals(5, highViolations.get(0).getLineNumber());

    assertEquals("/a/file.xml", highViolations.get(1).getFilePath());
    assertEquals(20, highViolations.get(1).getLineNumber());

    assertEquals("/b/file.xml", highViolations.get(2).getFilePath());
    assertEquals(10, highViolations.get(2).getLineNumber());
  }

  @Test
  void testNullSuggestion_shouldHandleGracefully() {
    // Given
    ScanReport report = new ScanReport();

    List<ViolationInfo> violations = Arrays.asList(
        new ViolationInfo(RiskLevel.MEDIUM, "Test message", null)
    );

    SqlEntry entry = new SqlEntry(
        SourceType.XML,
        "/path/to/test.xml",
        "test.mapper",
        SqlCommandType.SELECT,
        "SELECT * FROM test",
        1,
        false,
        violations
    );
    report.addEntry(entry);

    // When
    ProcessedReport processed = processor.process(report);

    // Then
    ViolationEntry violationEntry = processed.getViolationsByLevel()
        .get(RiskLevel.MEDIUM).get(0);

    assertNull(violationEntry.getSuggestion());
  }
}




