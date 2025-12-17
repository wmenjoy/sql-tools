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
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for HtmlReportGenerator.
 */
class HtmlReportGeneratorTest {

  @TempDir
  Path tempDir;

  private HtmlReportGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new HtmlReportGenerator();
  }

  /**
   * Helper to read file content (Java 8 compatible).
   */
  private String readFile(Path path) throws IOException {
    return new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
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
  void testHtmlStructure_shouldBeValid() throws IOException {
    // Given
    ScanReport report = createTestReport();
    Path outputPath = tempDir.resolve("report.html");

    // When
    generator.writeToFile(report, outputPath);

    // Then
    assertTrue(Files.exists(outputPath));

    String html = readFile(outputPath);
    Document doc = Jsoup.parse(html);

    // Should have valid HTML structure
    assertNotNull(doc.head());
    assertNotNull(doc.body());
    assertEquals("SQL Safety Scan Report", doc.title());
  }

  @Test
  void testSortableTable_shouldIncludeColumns() throws IOException {
    // Given
    ScanReport report = createTestReport();
    Path outputPath = tempDir.resolve("report.html");

    // When
    generator.writeToFile(report, outputPath);

    // Then
    String html = readFile(outputPath);
    Document doc = Jsoup.parse(html);

    // Find violations table
    Element table = doc.selectFirst("table#violations-table");
    assertNotNull(table, "Should have violations table");

    // Check table headers
    Elements headers = table.select("thead th");
    assertTrue(headers.size() >= 5, "Should have at least 5 columns");

    // Check column names
    String headerText = headers.text();
    assertTrue(headerText.contains("Risk Level"));
    assertTrue(headerText.contains("File"));
    assertTrue(headerText.contains("Mapper"));
    assertTrue(headerText.contains("Message"));
  }

  @Test
  void testCollapsibleSqlSections_shouldWork() throws IOException {
    // Given
    ScanReport report = createTestReport();
    Path outputPath = tempDir.resolve("report.html");

    // When
    generator.writeToFile(report, outputPath);

    // Then
    String html = readFile(outputPath);
    Document doc = Jsoup.parse(html);

    // Check for details/summary elements
    Elements details = doc.select("details");
    assertTrue(details.size() > 0, "Should have collapsible sections");

    Elements summaries = doc.select("summary");
    assertTrue(summaries.size() > 0, "Should have summary elements");
  }

  @Test
  void testStatisticsDashboard_shouldDisplay() throws IOException {
    // Given
    ScanReport report = createTestReport();
    Path outputPath = tempDir.resolve("report.html");

    // When
    generator.writeToFile(report, outputPath);

    // Then
    String html = readFile(outputPath);
    Document doc = Jsoup.parse(html);

    // Find dashboard
    Element dashboard = doc.selectFirst(".dashboard");
    assertNotNull(dashboard, "Should have dashboard");

    // Check for stat boxes
    Elements statBoxes = dashboard.select(".stat-box");
    assertTrue(statBoxes.size() >= 4, "Should have multiple stat boxes");

    // Check content
    String dashboardText = dashboard.text();
    assertTrue(dashboardText.contains("Total SQL"));
    assertTrue(dashboardText.contains("CRITICAL"));
    assertTrue(dashboardText.contains("HIGH"));
    assertTrue(dashboardText.contains("MEDIUM"));
  }

  @Test
  void testSpecialCharacters_shouldEscape() throws IOException {
    // Given
    ScanReport report = new ScanReport();

    // SQL with special characters
    String dangerousSql = "<script>alert('XSS')</script> SELECT * FROM user WHERE name='O'Brien' && id > 100";

    SqlEntry entry = new SqlEntry(
        SourceType.XML,
        "/path/to/test.xml",
        "test.mapper",
        SqlCommandType.SELECT,
        dangerousSql,
        1,
        false,
        Arrays.asList(
            new ViolationInfo(RiskLevel.HIGH, "Test <danger>", "Test & suggestion")
        )
    );
    report.addEntry(entry);
    report.calculateStatistics();

    Path outputPath = tempDir.resolve("report.html");

    // When
    generator.writeToFile(report, outputPath);

    // Then
    String html = readFile(outputPath);

    // Should NOT contain raw script tags
    assertFalse(html.contains("<script>alert('XSS')</script>"));

    // Should contain escaped versions
    assertTrue(html.contains("&lt;script&gt;") || html.contains("&lt;"));
    assertTrue(html.contains("&amp;") || html.contains("&amp"));

    // Parse to verify valid HTML
    Document doc = Jsoup.parse(html);
    assertNotNull(doc);
  }

  @Test
  void testEmptyReport_shouldHandleGracefully() throws IOException {
    // Given
    ScanReport report = new ScanReport();
    report.calculateStatistics();
    Path outputPath = tempDir.resolve("report.html");

    // When
    generator.writeToFile(report, outputPath);

    // Then
    assertTrue(Files.exists(outputPath));

    String html = readFile(outputPath);
    Document doc = Jsoup.parse(html);

    // Should contain success message or empty state
    String bodyText = doc.body().text();
    assertTrue(bodyText.contains("No violations") || 
               bodyText.contains("0") ||
               bodyText.contains("safe"));
  }

  @Test
  void testCssStyles_shouldBeIncluded() throws IOException {
    // Given
    ScanReport report = createTestReport();
    Path outputPath = tempDir.resolve("report.html");

    // When
    generator.writeToFile(report, outputPath);

    // Then
    String html = readFile(outputPath);
    Document doc = Jsoup.parse(html);

    // Should have style tag
    Elements styles = doc.select("style");
    assertTrue(styles.size() > 0, "Should have CSS styles");

    String css = styles.first().html();

    // Check for key CSS classes
    assertTrue(css.contains(".dashboard") || css.contains("dashboard"));
    assertTrue(css.contains(".stat-box") || css.contains("stat-box"));
    assertTrue(css.contains("table") || css.contains("TABLE"));
  }

  @Test
  void testTableRows_shouldMatchViolationCount() throws IOException {
    // Given
    ScanReport report = createTestReport();
    Path outputPath = tempDir.resolve("report.html");

    // When
    generator.writeToFile(report, outputPath);

    // Then
    String html = readFile(outputPath);
    Document doc = Jsoup.parse(html);

    Element table = doc.selectFirst("table#violations-table");
    assertNotNull(table);

    Elements rows = table.select("tbody tr");
    assertEquals(3, rows.size(), "Should have 3 violation rows");
  }

  @Test
  void testRiskLevelColors_shouldBeApplied() throws IOException {
    // Given
    ScanReport report = createTestReport();
    Path outputPath = tempDir.resolve("report.html");

    // When
    generator.writeToFile(report, outputPath);

    // Then
    String html = readFile(outputPath);
    Document doc = Jsoup.parse(html);

    Element table = doc.selectFirst("table#violations-table");
    assertNotNull(table);

    // Check for risk level classes
    Elements criticalRows = table.select("tr.critical");
    Elements highRows = table.select("tr.high");
    Elements mediumRows = table.select("tr.medium");

    assertTrue(criticalRows.size() > 0 || highRows.size() > 0 || mediumRows.size() > 0,
        "Should have color-coded risk level rows");
  }

  @Test
  void testJavaScriptSorting_shouldBeIncluded() throws IOException {
    // Given
    ScanReport report = createTestReport();
    Path outputPath = tempDir.resolve("report.html");

    // When
    generator.writeToFile(report, outputPath);

    // Then
    String html = readFile(outputPath);
    Document doc = Jsoup.parse(html);

    // Should have script tag
    Elements scripts = doc.select("script");
    assertTrue(scripts.size() > 0, "Should have JavaScript");

    String js = scripts.first().html();

    // Check for sorting function
    assertTrue(js.contains("sort") || js.contains("Sort"));
  }

  @Test
  void testLongSql_shouldDisplayInCollapsible() throws IOException {
    // Given
    ScanReport report = new ScanReport();

    String longSql = "SELECT id, name, email, phone, address, city, state, zip, country, " +
        "created_at, updated_at, status, description, notes, metadata, tags FROM user WHERE id = ?";

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

    Path outputPath = tempDir.resolve("report.html");

    // When
    generator.writeToFile(report, outputPath);

    // Then
    String html = readFile(outputPath);
    Document doc = Jsoup.parse(html);

    // SQL should be in details/summary
    Elements details = doc.select("details");
    assertTrue(details.size() > 0);

    // Full SQL should be present somewhere in the HTML
    assertTrue(html.contains("SELECT id, name, email"));
  }

  @Test
  void testWrapperSection_shouldDisplay() throws IOException {
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

    Path outputPath = tempDir.resolve("report.html");

    // When
    generator.writeToFile(report, outputPath);

    // Then
    String html = readFile(outputPath);
    Document doc = Jsoup.parse(html);

    // Should contain wrapper information
    String bodyText = doc.body().text();
    assertTrue(bodyText.contains("Wrapper") || bodyText.contains("wrapper"));
    assertTrue(bodyText.contains("Service1") || bodyText.contains("method1"));
  }

  @Test
  void testUnicodeCharacters_shouldDisplay() throws IOException {
    // Given
    ScanReport report = new ScanReport();

    SqlEntry entry = new SqlEntry(
        SourceType.XML,
        "/path/to/测试.xml",
        "测试.mapper",
        SqlCommandType.SELECT,
        "SELECT * FROM user WHERE name = '李明'",
        1,
        false,
        Arrays.asList(
            new ViolationInfo(RiskLevel.LOW, "测试消息", "测试建议")
        )
    );
    report.addEntry(entry);
    report.calculateStatistics();

    Path outputPath = tempDir.resolve("report.html");

    // When
    generator.writeToFile(report, outputPath);

    // Then
    String html = readFile(outputPath);
    Document doc = Jsoup.parse(html);

    // Should contain Unicode characters
    String bodyText = doc.body().text();
    assertTrue(bodyText.contains("测试") || bodyText.contains("李明"));
  }
}

