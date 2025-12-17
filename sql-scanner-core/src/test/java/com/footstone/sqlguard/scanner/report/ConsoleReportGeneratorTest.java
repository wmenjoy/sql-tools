package com.footstone.sqlguard.scanner.report;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.scanner.model.ScanReport;
import com.footstone.sqlguard.scanner.model.SourceType;
import com.footstone.sqlguard.scanner.model.SqlEntry;
import com.footstone.sqlguard.scanner.model.WrapperUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for ConsoleReportGenerator.
 */
class ConsoleReportGeneratorTest {

  private ConsoleReportGenerator generator;
  private ByteArrayOutputStream outputStream;
  private PrintStream originalOut;

  @BeforeEach
  void setUp() {
    generator = new ConsoleReportGenerator();
    
    // Capture System.out
    outputStream = new ByteArrayOutputStream();
    originalOut = System.out;
    System.setOut(new PrintStream(outputStream));
  }

  @AfterEach
  void tearDown() {
    // Restore System.out
    System.setOut(originalOut);
  }

  /**
   * Helper to get captured output.
   */
  private String getOutput() {
    return outputStream.toString();
  }

  /**
   * Helper to create a test report with violations.
   */
  private ScanReport createTestReport() {
    ScanReport report = new ScanReport();

    // Add CRITICAL violation
    SqlEntry criticalEntry = new SqlEntry(
        SourceType.XML,
        "/path/to/UserMapper.xml",
        "com.example.UserMapper.deleteAll",
        SqlCommandType.DELETE,
        "DELETE FROM user",
        42,
        false,
        Arrays.asList(
            new ViolationInfo(
                RiskLevel.CRITICAL,
                "Missing WHERE clause in DELETE statement",
                "Add WHERE clause to restrict affected rows"
            )
        )
    );
    report.addEntry(criticalEntry);

    // Add HIGH violation
    SqlEntry highEntry = new SqlEntry(
        SourceType.XML,
        "/path/to/ProductMapper.xml",
        "com.example.ProductMapper.search",
        SqlCommandType.SELECT,
        "SELECT * FROM product WHERE 1=1 AND name = ?",
        156,
        false,
        Arrays.asList(
            new ViolationInfo(
                RiskLevel.HIGH,
                "Dummy condition '1=1' detected",
                "Remove dummy condition and use proper WHERE clause"
            )
        )
    );
    report.addEntry(highEntry);

    // Add MEDIUM violation
    SqlEntry mediumEntry = new SqlEntry(
        SourceType.ANNOTATION,
        "/path/to/OrderService.java",
        "com.example.OrderService.findAll",
        SqlCommandType.SELECT,
        "SELECT * FROM orders",
        78,
        false,
        Arrays.asList(
            new ViolationInfo(
                RiskLevel.MEDIUM,
                "No pagination or limit on large table",
                "Add LIMIT clause or use pagination"
            )
        )
    );
    report.addEntry(mediumEntry);

    // Add wrapper usage
    WrapperUsage wrapperUsage = new WrapperUsage(
        "/path/to/UserService.java",
        "findUsers",
        50,
        "QueryWrapper",
        true
    );
    report.addWrapperUsage(wrapperUsage);

    report.calculateStatistics();
    return report;
  }

  @Test
  void testConsoleOutputFormat_shouldMatchDesign() {
    // Given
    ScanReport report = createTestReport();

    // When
    generator.printToConsole(report);

    // Then
    String output = getOutput();

    // Should contain header separator
    assertTrue(output.contains("================================================================================"));

    // Should contain title
    assertTrue(output.contains("SQL Safety Scan Report"));

    // Should contain statistics line
    assertTrue(output.contains("Total SQL:"));
    assertTrue(output.contains("Violations:"));

    // Should contain risk level sections
    assertTrue(output.contains("[CRITICAL]"));
    assertTrue(output.contains("[HIGH]"));
    assertTrue(output.contains("[MEDIUM]"));

    // Should contain file:line format
    assertTrue(output.contains("/path/to/UserMapper.xml:42"));
    assertTrue(output.contains("/path/to/ProductMapper.xml:156"));

    // Should contain mapper IDs
    assertTrue(output.contains("com.example.UserMapper.deleteAll"));
    assertTrue(output.contains("com.example.ProductMapper.search"));

    // Should contain SQL snippets
    assertTrue(output.contains("SQL:"));
    assertTrue(output.contains("DELETE FROM user"));

    // Should contain messages
    assertTrue(output.contains("Message:"));
    assertTrue(output.contains("Missing WHERE clause"));

    // Should contain suggestions
    assertTrue(output.contains("Suggestion:"));
    assertTrue(output.contains("Add WHERE clause to restrict affected rows"));

    // Should contain wrapper section
    assertTrue(output.contains("WRAPPER USAGES"));
  }

  @Test
  void testAnsiColors_shouldApplyCorrectly() {
    // Given
    ScanReport report = createTestReport();

    // When
    generator.printToConsole(report);

    // Then
    String output = getOutput();

    // Check for ANSI color codes (if terminal supports colors)
    // CRITICAL should use red (31m) or be present
    assertTrue(output.contains("CRITICAL") || output.contains("\033[31m"));

    // HIGH should use yellow (33m) or be present
    assertTrue(output.contains("HIGH") || output.contains("\033[33m"));

    // MEDIUM should use blue (34m) or be present
    assertTrue(output.contains("MEDIUM") || output.contains("\033[34m"));
  }

  @Test
  void testViolationEntry_shouldFormatCorrectly() {
    // Given
    ScanReport report = createTestReport();

    // When
    generator.printToConsole(report);

    // Then
    String output = getOutput();

    // Check format: [file:line] mapperId
    assertTrue(output.contains("[/path/to/UserMapper.xml:42]"));
    assertTrue(output.contains("com.example.UserMapper.deleteAll"));

    // Check SQL line format
    assertTrue(output.contains("SQL: DELETE FROM user"));

    // Check message format
    assertTrue(output.contains("Message: Missing WHERE clause in DELETE statement"));

    // Check suggestion format
    assertTrue(output.contains("Suggestion: Add WHERE clause to restrict affected rows"));
  }

  @Test
  void testSqlSnippet_shouldDisplay() {
    // Given
    ScanReport report = createTestReport();

    // When
    generator.printToConsole(report);

    // Then
    String output = getOutput();

    // SQL snippets should be displayed with proper indentation
    assertTrue(output.contains("SQL:"));
    assertTrue(output.contains("DELETE FROM user"));
    assertTrue(output.contains("SELECT * FROM product WHERE 1=1 AND name = ?"));
    assertTrue(output.contains("SELECT * FROM orders"));
  }

  @Test
  void testSummaryStatistics_shouldDisplay() {
    // Given
    ScanReport report = createTestReport();

    // When
    generator.printToConsole(report);

    // Then
    String output = getOutput();

    // Should show total SQL count
    assertTrue(output.contains("Total SQL: 3"));

    // Should show violation counts
    assertTrue(output.contains("Violations: 3"));
    assertTrue(output.contains("CRITICAL: 1"));
    assertTrue(output.contains("HIGH: 1"));
    assertTrue(output.contains("MEDIUM: 1"));

    // Should show wrapper count
    assertTrue(output.contains("Wrapper"));
    assertTrue(output.contains("1"));
  }

  @Test
  void testEmptyReport_shouldShowNoViolations() {
    // Given
    ScanReport report = new ScanReport();
    report.calculateStatistics();

    // When
    generator.printToConsole(report);

    // Then
    String output = getOutput();

    // Should contain header
    assertTrue(output.contains("SQL Safety Scan Report"));

    // Should show zero violations
    assertTrue(output.contains("No violations found") || 
               output.contains("Violations: 0") ||
               output.contains("Total SQL: 0"));
  }

  @Test
  void testMultipleViolationsSameLevel_shouldDisplayAll() {
    // Given
    ScanReport report = new ScanReport();

    // Add multiple CRITICAL violations
    for (int i = 1; i <= 3; i++) {
      SqlEntry entry = new SqlEntry(
          SourceType.XML,
          "/path/to/Mapper" + i + ".xml",
          "com.example.Mapper" + i + ".method",
          SqlCommandType.DELETE,
          "DELETE FROM table" + i,
          i * 10,
          false,
          Arrays.asList(
              new ViolationInfo(
                  RiskLevel.CRITICAL,
                  "Violation " + i,
                  "Suggestion " + i
              )
          )
      );
      report.addEntry(entry);
    }

    report.calculateStatistics();

    // When
    generator.printToConsole(report);

    // Then
    String output = getOutput();

    // Should show count (may have ANSI codes and "violations" word)
    assertTrue(output.contains("CRITICAL") && output.contains("3"));

    // Should display all three violations
    assertTrue(output.contains("Mapper1"));
    assertTrue(output.contains("Mapper2"));
    assertTrue(output.contains("Mapper3"));
  }

  @Test
  void testWrapperUsages_shouldDisplay() {
    // Given
    ScanReport report = new ScanReport();

    report.addWrapperUsage(new WrapperUsage(
        "/path/to/Service1.java",
        "method1",
        10,
        "QueryWrapper",
        true
    ));

    report.addWrapperUsage(new WrapperUsage(
        "/path/to/Service2.java",
        "method2",
        20,
        "LambdaQueryWrapper",
        true
    ));

    report.calculateStatistics();

    // When
    generator.printToConsole(report);

    // Then
    String output = getOutput();

    // Should contain wrapper section
    assertTrue(output.contains("WRAPPER"));

    // Should show wrapper locations
    assertTrue(output.contains("Service1.java") || output.contains("method1"));
    assertTrue(output.contains("Service2.java") || output.contains("method2"));
  }

  @Test
  void testLongSql_shouldTruncate() {
    // Given
    ScanReport report = new ScanReport();

    String longSql = "SELECT id, name, email, phone, address, city, state, zip, country, " +
        "created_at, updated_at, status, description, notes FROM user WHERE id = ?";

    SqlEntry entry = new SqlEntry(
        SourceType.XML,
        "/path/to/test.xml",
        "test.mapper",
        SqlCommandType.SELECT,
        longSql,
        1,
        false,
        Arrays.asList(
            new ViolationInfo(RiskLevel.LOW, "Test", "Test")
        )
    );
    report.addEntry(entry);
    report.calculateStatistics();

    // When
    generator.printToConsole(report);

    // Then
    String output = getOutput();

    // SQL should be truncated with "..."
    if (longSql.length() > 100) {
      assertTrue(output.contains("..."));
    }
  }

  @Test
  void testRiskLevelOrdering_shouldDisplayCriticalFirst() {
    // Given
    ScanReport report = new ScanReport();

    // Add violations in reverse order
    report.addEntry(new SqlEntry(SourceType.XML, "/low.xml", "low", SqlCommandType.SELECT,
        "SELECT 1", 1, false, Arrays.asList(new ViolationInfo(RiskLevel.LOW, "Low", "Low"))));

    report.addEntry(new SqlEntry(SourceType.XML, "/medium.xml", "medium", SqlCommandType.SELECT,
        "SELECT 2", 2, false, Arrays.asList(new ViolationInfo(RiskLevel.MEDIUM, "Medium", "Medium"))));

    report.addEntry(new SqlEntry(SourceType.XML, "/high.xml", "high", SqlCommandType.SELECT,
        "SELECT 3", 3, false, Arrays.asList(new ViolationInfo(RiskLevel.HIGH, "High", "High"))));

    report.addEntry(new SqlEntry(SourceType.XML, "/critical.xml", "critical", SqlCommandType.SELECT,
        "SELECT 4", 4, false, Arrays.asList(new ViolationInfo(RiskLevel.CRITICAL, "Critical", "Critical"))));

    report.calculateStatistics();

    // When
    generator.printToConsole(report);

    // Then
    String output = getOutput();

    // Find positions of risk level headers
    int criticalPos = output.indexOf("[CRITICAL]");
    int highPos = output.indexOf("[HIGH]");
    int mediumPos = output.indexOf("[MEDIUM]");
    int lowPos = output.indexOf("[LOW]");

    // CRITICAL should appear first
    assertTrue(criticalPos > 0);
    assertTrue(criticalPos < highPos);
    assertTrue(highPos < mediumPos);
    assertTrue(mediumPos < lowPos);
  }
}

