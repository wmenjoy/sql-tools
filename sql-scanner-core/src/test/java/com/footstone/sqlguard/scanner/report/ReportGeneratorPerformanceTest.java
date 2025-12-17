package com.footstone.sqlguard.scanner.report;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.scanner.model.ScanReport;
import com.footstone.sqlguard.scanner.model.SourceType;
import com.footstone.sqlguard.scanner.model.SqlEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for report generators.
 */
class ReportGeneratorPerformanceTest {

  @TempDir
  Path tempDir;

  private ConsoleReportGenerator consoleGenerator;
  private HtmlReportGenerator htmlGenerator;

  @BeforeEach
  void setUp() {
    consoleGenerator = new ConsoleReportGenerator();
    htmlGenerator = new HtmlReportGenerator();
  }

  @Test
  void testLargeReport_consolePerformance() {
    // Given: Report with 10000 violations
    ScanReport report = createLargeReport(10000);

    // When: Generate console output
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(outputStream));

    long startTime = System.currentTimeMillis();
    consoleGenerator.printToConsole(report);
    long duration = System.currentTimeMillis() - startTime;

    System.setOut(originalOut);

    // Then: Should complete within 1 second
    System.out.println("Console generation time for 10000 violations: " + duration + "ms");
    assertTrue(duration < 1000, "Console output should be fast (<1s), was: " + duration + "ms");

    // Verify output is not empty
    String output = outputStream.toString();
    assertTrue(output.length() > 0);
  }

  @Test
  void testLargeReport_htmlPerformance() throws IOException {
    // Given: Report with 10000 violations
    ScanReport report = createLargeReport(10000);

    // When: Generate HTML output
    Path htmlPath = tempDir.resolve("performance-test.html");

    long startTime = System.currentTimeMillis();
    htmlGenerator.writeToFile(report, htmlPath);
    long duration = System.currentTimeMillis() - startTime;

    // Then: Should complete within 3 seconds
    System.out.println("HTML generation time for 10000 violations: " + duration + "ms");
    assertTrue(duration < 3000, "HTML generation should be fast (<3s), was: " + duration + "ms");

    // Verify file exists and has reasonable size
    assertTrue(Files.exists(htmlPath));
    long fileSize = Files.size(htmlPath);
    System.out.println("HTML file size for 10000 violations: " + fileSize + " bytes (" +
                       (fileSize / 1024 / 1024) + " MB)");
    assertTrue(fileSize < 10 * 1024 * 1024, "HTML file should be <10MB, was: " +
               (fileSize / 1024 / 1024) + "MB");
  }

  @Test
  void testMediumReport_performance() throws IOException {
    // Given: Report with 1000 violations (more realistic scenario)
    ScanReport report = createLargeReport(1000);

    // When: Generate console output
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputStream));

    long consoleStart = System.currentTimeMillis();
    consoleGenerator.printToConsole(report);
    long consoleDuration = System.currentTimeMillis() - consoleStart;

    System.setOut(System.out);

    // When: Generate HTML output
    Path htmlPath = tempDir.resolve("medium-report.html");

    long htmlStart = System.currentTimeMillis();
    htmlGenerator.writeToFile(report, htmlPath);
    long htmlDuration = System.currentTimeMillis() - htmlStart;

    // Then: Both should be very fast
    System.out.println("Console generation time for 1000 violations: " + consoleDuration + "ms");
    System.out.println("HTML generation time for 1000 violations: " + htmlDuration + "ms");

    assertTrue(consoleDuration < 500, "Console should be <500ms for 1000 violations");
    assertTrue(htmlDuration < 1000, "HTML should be <1s for 1000 violations");
  }

  @Test
  void testSmallReport_performance() throws IOException {
    // Given: Report with 100 violations (typical scenario)
    ScanReport report = createLargeReport(100);

    // When: Generate console output
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputStream));

    long consoleStart = System.currentTimeMillis();
    consoleGenerator.printToConsole(report);
    long consoleDuration = System.currentTimeMillis() - consoleStart;

    System.setOut(System.out);

    // When: Generate HTML output
    Path htmlPath = tempDir.resolve("small-report.html");

    long htmlStart = System.currentTimeMillis();
    htmlGenerator.writeToFile(report, htmlPath);
    long htmlDuration = System.currentTimeMillis() - htmlStart;

    // Then: Both should be nearly instant
    System.out.println("Console generation time for 100 violations: " + consoleDuration + "ms");
    System.out.println("HTML generation time for 100 violations: " + htmlDuration + "ms");

    assertTrue(consoleDuration < 200, "Console should be <200ms for 100 violations");
    assertTrue(htmlDuration < 500, "HTML should be <500ms for 100 violations");
  }

  @Test
  void testReportProcessor_performance() {
    // Given: Large report
    ScanReport report = createLargeReport(5000);
    ReportProcessor processor = new ReportProcessor();

    // When: Process report
    long startTime = System.currentTimeMillis();
    ProcessedReport processed = processor.process(report);
    long duration = System.currentTimeMillis() - startTime;

    // Then: Should be fast
    System.out.println("Report processing time for 5000 violations: " + duration + "ms");
    assertTrue(duration < 500, "Report processing should be <500ms for 5000 violations");

    // Verify processed report is correct
    assertEquals(5000, processed.getTotalViolations());
  }

  @Test
  void testMemoryUsage_largeReport() throws IOException {
    // Given: Very large report
    Runtime runtime = Runtime.getRuntime();
    runtime.gc(); // Suggest garbage collection before test

    long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

    ScanReport report = createLargeReport(5000);

    // When: Generate both formats
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputStream));
    consoleGenerator.printToConsole(report);
    System.setOut(System.out);

    Path htmlPath = tempDir.resolve("memory-test.html");
    htmlGenerator.writeToFile(report, htmlPath);

    runtime.gc(); // Suggest garbage collection after generation

    long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
    long memoryUsed = memoryAfter - memoryBefore;

    // Then: Memory usage should be reasonable
    System.out.println("Memory used for 5000 violations: " + (memoryUsed / 1024 / 1024) + " MB");

    // This is a soft check - memory usage can vary
    assertTrue(memoryUsed < 100 * 1024 * 1024,
        "Memory usage should be reasonable (<100MB), was: " + (memoryUsed / 1024 / 1024) + "MB");
  }

  /**
   * Helper to create a large report with specified number of violations.
   */
  private ScanReport createLargeReport(int violationCount) {
    ScanReport report = new ScanReport();

    // Distribute violations across risk levels
    int criticalCount = violationCount / 4;
    int highCount = violationCount / 3;
    int mediumCount = violationCount / 3;
    int lowCount = violationCount - criticalCount - highCount - mediumCount;

    int id = 1;

    // Add CRITICAL violations
    for (int i = 0; i < criticalCount; i++) {
      report.addEntry(createViolationEntry(id++, RiskLevel.CRITICAL));
    }

    // Add HIGH violations
    for (int i = 0; i < highCount; i++) {
      report.addEntry(createViolationEntry(id++, RiskLevel.HIGH));
    }

    // Add MEDIUM violations
    for (int i = 0; i < mediumCount; i++) {
      report.addEntry(createViolationEntry(id++, RiskLevel.MEDIUM));
    }

    // Add LOW violations
    for (int i = 0; i < lowCount; i++) {
      report.addEntry(createViolationEntry(id++, RiskLevel.LOW));
    }

    report.calculateStatistics();
    return report;
  }

  /**
   * Helper to create a single violation entry.
   */
  private SqlEntry createViolationEntry(int id, RiskLevel level) {
    String sql = "SELECT id, name, email, status FROM table" + id + " WHERE id = ?";

    return new SqlEntry(
        SourceType.XML,
        "/path/to/mapper/Mapper" + id + ".xml",
        "com.example.mapper.Mapper" + id + ".method" + id,
        SqlCommandType.SELECT,
        sql,
        id % 1000 + 1, // Line number
        false,
        Arrays.asList(
            new ViolationInfo(
                level,
                level.name() + " violation " + id + ": Test message",
                "Suggestion for fixing violation " + id
            )
        )
    );
  }
}

