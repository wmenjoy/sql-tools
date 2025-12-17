package com.footstone.sqlguard.scanner.report;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.scanner.model.ScanReport;
import com.footstone.sqlguard.scanner.model.SourceType;
import com.footstone.sqlguard.scanner.model.SqlEntry;
import com.footstone.sqlguard.scanner.model.WrapperUsage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
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
 * Integration tests for report generators.
 */
class ReportGeneratorIntegrationTest {

  @TempDir
  Path tempDir;

  private ConsoleReportGenerator consoleGenerator;
  private HtmlReportGenerator htmlGenerator;

  @BeforeEach
  void setUp() {
    consoleGenerator = new ConsoleReportGenerator();
    htmlGenerator = new HtmlReportGenerator();
  }

  /**
   * Helper to read file content (Java 8 compatible).
   */
  private String readFile(Path path) throws IOException {
    return new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
  }

  @Test
  void testLargeReport_shouldHandleCorrectly() throws IOException {
    // Given: Large report with 100 violations
    ScanReport report = new ScanReport();

    // Add 25 CRITICAL violations
    for (int i = 1; i <= 25; i++) {
      report.addEntry(createViolationEntry(i, RiskLevel.CRITICAL, "critical"));
    }

    // Add 30 HIGH violations
    for (int i = 26; i <= 55; i++) {
      report.addEntry(createViolationEntry(i, RiskLevel.HIGH, "high"));
    }

    // Add 25 MEDIUM violations
    for (int i = 56; i <= 80; i++) {
      report.addEntry(createViolationEntry(i, RiskLevel.MEDIUM, "medium"));
    }

    // Add 20 LOW violations
    for (int i = 81; i <= 100; i++) {
      report.addEntry(createViolationEntry(i, RiskLevel.LOW, "low"));
    }

    // Add 100 safe entries
    for (int i = 1; i <= 100; i++) {
      SqlEntry safeEntry = new SqlEntry(
          SourceType.XML,
          "/path/to/Safe" + i + ".xml",
          "com.example.Safe" + i + ".method",
          SqlCommandType.SELECT,
          "SELECT id FROM table" + i + " WHERE id = ?",
          i,
          false,
          Arrays.asList()
      );
      report.addEntry(safeEntry);
    }

    // Add 20 wrapper usages
    for (int i = 1; i <= 20; i++) {
      report.addWrapperUsage(new WrapperUsage(
          "/path/to/Service" + i + ".java",
          "method" + i,
          i * 10,
          "QueryWrapper",
          true
      ));
    }

    report.calculateStatistics();

    // When: Generate console output
    ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(consoleOutput));

    long consoleStart = System.currentTimeMillis();
    consoleGenerator.printToConsole(report);
    long consoleTime = System.currentTimeMillis() - consoleStart;

    System.setOut(originalOut);

    // Then: Verify console output
    String console = consoleOutput.toString();
    assertTrue(console.contains("Total SQL: 200"));
    assertTrue(console.contains("Violations: 100"));
    assertTrue(console.contains("CRITICAL"));
    assertTrue(console.contains("HIGH"));
    assertTrue(console.contains("MEDIUM"));
    assertTrue(consoleTime < 1000, "Console output should be fast (<1s), was: " + consoleTime + "ms");

    // When: Generate HTML output
    Path htmlPath = tempDir.resolve("large-report.html");
    long htmlStart = System.currentTimeMillis();
    htmlGenerator.writeToFile(report, htmlPath);
    long htmlTime = System.currentTimeMillis() - htmlStart;

    // Then: Verify HTML output
    assertTrue(Files.exists(htmlPath));
    String html = readFile(htmlPath);
    Document doc = Jsoup.parse(html);

    Elements rows = doc.select("table#violations-table tbody tr");
    assertEquals(100, rows.size(), "Should have 100 violation rows");

    assertTrue(htmlTime < 3000, "HTML generation should be fast (<3s), was: " + htmlTime + "ms");
  }

  @Test
  void testSpecialCharacters_shouldEscapeInBothFormats() throws IOException {
    // Given: Report with special characters
    ScanReport report = new ScanReport();

    String dangerousSql = "<script>alert('XSS')</script> SELECT * FROM user WHERE name='O''Brien' && id > 100";
    String dangerousMessage = "SQL injection risk: <danger> & \"quotes\"";
    String dangerousSuggestion = "Use parameterized queries & escape <html>";

    SqlEntry entry = new SqlEntry(
        SourceType.XML,
        "/path/to/<test>.xml",
        "com.example.<Test>.method",
        SqlCommandType.SELECT,
        dangerousSql,
        1,
        false,
        Arrays.asList(
            new ViolationInfo(RiskLevel.HIGH, dangerousMessage, dangerousSuggestion)
        )
    );
    report.addEntry(entry);
    report.calculateStatistics();

    // When: Generate console output
    ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
    System.setOut(new PrintStream(consoleOutput));
    consoleGenerator.printToConsole(report);
    System.setOut(System.out);

    String console = consoleOutput.toString();

    // Then: Console should display correctly (no escaping needed for terminal)
    assertTrue(console.contains("alert"));
    assertTrue(console.contains("O''Brien"));

    // When: Generate HTML output
    Path htmlPath = tempDir.resolve("special-chars.html");
    htmlGenerator.writeToFile(report, htmlPath);

    String html = readFile(htmlPath);

    // Then: HTML should escape dangerous characters
    assertFalse(html.contains("<script>alert('XSS')</script>"));
    assertTrue(html.contains("&lt;script&gt;") || html.contains("&lt;"));
    assertTrue(html.contains("&amp;") || html.contains("&amp"));

    // Verify HTML is valid
    Document doc = Jsoup.parse(html);
    assertNotNull(doc);
  }

  @Test
  void testEmptyReport_shouldHandleGracefullyInBothFormats() throws IOException {
    // Given: Empty report
    ScanReport report = new ScanReport();
    report.calculateStatistics();

    // When: Generate console output
    ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
    System.setOut(new PrintStream(consoleOutput));
    consoleGenerator.printToConsole(report);
    System.setOut(System.out);

    String console = consoleOutput.toString();

    // Then: Console should show no violations
    assertTrue(console.contains("No violations") || console.contains("Total SQL: 0"));

    // When: Generate HTML output
    Path htmlPath = tempDir.resolve("empty-report.html");
    htmlGenerator.writeToFile(report, htmlPath);

    String html = readFile(htmlPath);
    Document doc = Jsoup.parse(html);

    // Then: HTML should show success state
    String bodyText = doc.body().text();
    assertTrue(bodyText.contains("No violations") || bodyText.contains("0"));
  }

  @Test
  void testLongSql_shouldDisplayInHtml() throws IOException {
    // Given: Entry with very long SQL
    ScanReport report = new ScanReport();

    StringBuilder longSql = new StringBuilder("SELECT ");
    for (int i = 1; i <= 50; i++) {
      longSql.append("column").append(i).append(", ");
    }
    longSql.append("id FROM user WHERE id = ?");

    SqlEntry entry = new SqlEntry(
        SourceType.XML,
        "/path/to/test.xml",
        "test.mapper",
        SqlCommandType.SELECT,
        longSql.toString(),
        1,
        false,
        Arrays.asList(
            new ViolationInfo(RiskLevel.LOW, "Test violation", "Test suggestion")
        )
    );
    report.addEntry(entry);
    report.calculateStatistics();

    // When: Generate HTML output
    Path htmlPath = tempDir.resolve("long-sql.html");
    htmlGenerator.writeToFile(report, htmlPath);

    String html = readFile(htmlPath);

    // Then: HTML should contain SQL
    assertTrue(longSql.length() > 100, "Test SQL should be longer than 100 chars");
    assertTrue(html.contains("column1"), "HTML should contain SQL content");
  }

  @Test
  void testEdgeCases_shouldHandleGracefully() throws IOException {
    // Given: Report with edge cases
    ScanReport report = new ScanReport();

    // Null suggestion
    report.addEntry(new SqlEntry(
        SourceType.XML,
        "/null-suggestion.xml",
        "test.nullSuggestion",
        SqlCommandType.SELECT,
        "SELECT * FROM test",
        1,
        false,
        Arrays.asList(new ViolationInfo(RiskLevel.LOW, "Test message", null))
    ));

    // Very long file path
    StringBuilder longPathBuilder = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      longPathBuilder.append("/very/long/path/");
    }
    String longPath = longPathBuilder.append("file.xml").toString();
    report.addEntry(new SqlEntry(
        SourceType.XML,
        longPath,
        "test.longPath",
        SqlCommandType.SELECT,
        "SELECT * FROM test",
        1,
        false,
        Arrays.asList(new ViolationInfo(RiskLevel.MEDIUM, "Test", "Test"))
    ));

    // Unicode characters
    report.addEntry(new SqlEntry(
        SourceType.XML,
        "/path/to/测试.xml",
        "测试.mapper",
        SqlCommandType.SELECT,
        "SELECT * FROM user WHERE name = '李明'",
        1,
        false,
        Arrays.asList(new ViolationInfo(RiskLevel.HIGH, "测试消息", "测试建议"))
    ));

    report.calculateStatistics();

    // When: Generate both formats
    ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
    System.setOut(new PrintStream(consoleOutput));
    consoleGenerator.printToConsole(report);
    System.setOut(System.out);

    Path htmlPath = tempDir.resolve("edge-cases.html");
    htmlGenerator.writeToFile(report, htmlPath);

    // Then: Both should complete without errors
    String console = consoleOutput.toString();
    assertNotNull(console);
    assertTrue(console.length() > 0);

    String html = readFile(htmlPath);
    Document doc = Jsoup.parse(html);
    assertNotNull(doc);

    // Verify Unicode is preserved
    assertTrue(html.contains("测试") || html.contains("李明"));
  }

  @Test
  void testMultipleViolationsPerEntry_shouldFlattenCorrectly() throws IOException {
    // Given: Entries with multiple violations each
    ScanReport report = new ScanReport();

    SqlEntry multiViolation = new SqlEntry(
        SourceType.XML,
        "/path/to/multi.xml",
        "test.multi",
        SqlCommandType.SELECT,
        "SELECT * FROM user WHERE 1=1",
        1,
        false,
        Arrays.asList(
            new ViolationInfo(RiskLevel.CRITICAL, "Violation 1", "Suggestion 1"),
            new ViolationInfo(RiskLevel.HIGH, "Violation 2", "Suggestion 2"),
            new ViolationInfo(RiskLevel.MEDIUM, "Violation 3", "Suggestion 3")
        )
    );
    report.addEntry(multiViolation);
    report.calculateStatistics();

    // When: Generate console output
    ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
    System.setOut(new PrintStream(consoleOutput));
    consoleGenerator.printToConsole(report);
    System.setOut(System.out);

    String console = consoleOutput.toString();

    // Then: All violations should be displayed
    assertTrue(console.contains("Violation 1"));
    assertTrue(console.contains("Violation 2"));
    assertTrue(console.contains("Violation 3"));

    // When: Generate HTML output
    Path htmlPath = tempDir.resolve("multi-violations.html");
    htmlGenerator.writeToFile(report, htmlPath);

    String html = readFile(htmlPath);
    Document doc = Jsoup.parse(html);

    // Then: Should have 3 separate rows
    Elements rows = doc.select("table#violations-table tbody tr");
    assertEquals(3, rows.size());
  }

  @Test
  void testWrapperUsages_shouldDisplayInBothFormats() throws IOException {
    // Given: Report with only wrapper usages
    ScanReport report = new ScanReport();

    for (int i = 1; i <= 5; i++) {
      report.addWrapperUsage(new WrapperUsage(
          "/path/to/Service" + i + ".java",
          "method" + i,
          i * 10,
          i % 2 == 0 ? "QueryWrapper" : "LambdaQueryWrapper",
          true
      ));
    }

    report.calculateStatistics();

    // When: Generate console output
    ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
    System.setOut(new PrintStream(consoleOutput));
    consoleGenerator.printToConsole(report);
    System.setOut(System.out);

    String console = consoleOutput.toString();

    // Then: Console should show wrapper section
    assertTrue(console.contains("WRAPPER"));
    assertTrue(console.contains("Service1"));
    assertTrue(console.contains("method1"));

    // When: Generate HTML output
    Path htmlPath = tempDir.resolve("wrappers.html");
    htmlGenerator.writeToFile(report, htmlPath);

    String html = readFile(htmlPath);

    // Then: HTML should show wrapper section
    assertTrue(html.contains("Wrapper") || html.contains("wrapper"));
    assertTrue(html.contains("Service1"));
    assertTrue(html.contains("method1"));
  }

  /**
   * Helper to create a violation entry.
   */
  private SqlEntry createViolationEntry(int id, RiskLevel level, String prefix) {
    return new SqlEntry(
        SourceType.XML,
        "/path/to/" + prefix + "/" + "File" + id + ".xml",
        "com.example." + prefix + ".Mapper" + id + ".method",
        SqlCommandType.SELECT,
        "SELECT * FROM table" + id + " WHERE id = ?",
        id,
        false,
        Arrays.asList(
            new ViolationInfo(
                level,
                prefix + " violation " + id,
                prefix + " suggestion " + id
            )
        )
    );
  }
}

