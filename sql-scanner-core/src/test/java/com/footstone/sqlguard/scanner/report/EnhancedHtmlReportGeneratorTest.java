package com.footstone.sqlguard.scanner.report;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.scanner.model.ScanReport;
import com.footstone.sqlguard.scanner.model.SourceType;
import com.footstone.sqlguard.scanner.model.SqlEntry;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for EnhancedHtmlReportGenerator.
 */
class EnhancedHtmlReportGeneratorTest {

    @TempDir
    Path tempDir;

    private EnhancedHtmlReportGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new EnhancedHtmlReportGenerator();
    }

    private String readFile(Path path) throws IOException {
        return new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
    }

    private ScanReport createTestReport() {
        ScanReport report = new ScanReport();

        // SQL injection violation
        SqlEntry injectionEntry = new SqlEntry(
            SourceType.XML,
            "/path/to/UserMapper.xml",
            "com.example.UserMapper.search",
            SqlCommandType.SELECT,
            "SELECT * FROM user WHERE name = ${userName}",
            42,
            false,
            Arrays.asList(
                new ViolationInfo(
                    RiskLevel.CRITICAL,
                    "SQL 注入风险 - 检测到 ${userName} 占位符",
                    "使用 #{} 替换 ${}"
                )
            )
        );
        injectionEntry.setXmlSnippet("<select id=\"search\">\n  SELECT * FROM user WHERE name = ${userName}\n</select>");
        injectionEntry.setJavaMethodSignature("List<User> search(String userName)");
        report.addEntry(injectionEntry);

        // WHERE condition missing
        SqlEntry whereEntry = new SqlEntry(
            SourceType.XML,
            "/path/to/ProductMapper.xml",
            "com.example.ProductMapper.findAll",
            SqlCommandType.SELECT,
            "SELECT * FROM product",
            156,
            false,
            Arrays.asList(
                new ViolationInfo(
                    RiskLevel.HIGH,
                    "SELECT 查询缺少 WHERE 条件 - 将查询全表数据",
                    "添加 WHERE 条件限制结果集"
                )
            )
        );
        whereEntry.setXmlSnippet("<select id=\"findAll\">\n  SELECT * FROM product\n</select>");
        whereEntry.setJavaMethodSignature("List<Product> findAll()");
        report.addEntry(whereEntry);

        // Pagination issue
        SqlEntry paginationEntry = new SqlEntry(
            SourceType.XML,
            "/path/to/OrderMapper.xml",
            "com.example.OrderMapper.listAll",
            SqlCommandType.SELECT,
            "SELECT * FROM orders",
            78,
            false,
            Arrays.asList(
                new ViolationInfo(
                    RiskLevel.HIGH,
                    "SELECT 查询缺少物理分页 - 大数据量时可能导致内存溢出",
                    "使用 LIMIT 子句或 RowBounds"
                )
            )
        );
        paginationEntry.setXmlSnippet("<select id=\"listAll\">\n  SELECT * FROM orders\n</select>");
        paginationEntry.setJavaMethodSignature("List<Order> listAll()");
        report.addEntry(paginationEntry);

        // LIMIT parameter issue
        SqlEntry limitEntry = new SqlEntry(
            SourceType.XML,
            "/path/to/UserMapper.xml",
            "com.example.UserMapper.findByPage",
            SqlCommandType.SELECT,
            "SELECT * FROM user LIMIT ${offset}, ${limit}",
            90,
            false,
            Arrays.asList(
                new ViolationInfo(
                    RiskLevel.MEDIUM,
                    "LIMIT 分页参数 ${offset} 可能为空或值过大",
                    "在业务层保证分页参数不能为空"
                )
            )
        );
        limitEntry.setXmlSnippet("<select id=\"findByPage\">\n  SELECT * FROM user LIMIT ${offset}, ${limit}\n</select>");
        limitEntry.setJavaMethodSignature("List<User> findByPage(int offset, int limit)");
        report.addEntry(limitEntry);

        report.calculateStatistics();
        return report;
    }

    @Test
    void testEnhancedHtmlStructure_shouldBeValid() throws IOException {
        // Given
        ScanReport report = createTestReport();
        Path outputPath = tempDir.resolve("enhanced-report.html");

        // When
        generator.writeToFile(report, outputPath);

        // Then
        assertTrue(Files.exists(outputPath));

        String html = readFile(outputPath);
        Document doc = Jsoup.parse(html);

        assertNotNull(doc.head());
        assertNotNull(doc.body());
        assertTrue(doc.title().contains("SQL"));
    }

    @Test
    void testSidebar_shouldExist() throws IOException {
        // Given
        ScanReport report = createTestReport();
        Path outputPath = tempDir.resolve("report.html");

        // When
        generator.writeToFile(report, outputPath);

        // Then
        String html = readFile(outputPath);
        Document doc = Jsoup.parse(html);

        Element sidebar = doc.selectFirst(".sidebar");
        assertNotNull(sidebar, "Should have sidebar");

        // Check for filter sections
        assertTrue(html.contains("错误类型") || html.contains("type-filters"));
        assertTrue(html.contains("文件") || html.contains("file-filters"));
    }

    @Test
    void testTabs_shouldExist() throws IOException {
        // Given
        ScanReport report = createTestReport();
        Path outputPath = tempDir.resolve("report.html");

        // When
        generator.writeToFile(report, outputPath);

        // Then
        String html = readFile(outputPath);
        Document doc = Jsoup.parse(html);

        Elements tabs = doc.select(".tab");
        assertTrue(tabs.size() >= 4, "Should have at least 4 tabs");

        String tabsText = tabs.text();
        assertTrue(tabsText.contains("全部违规"));
        assertTrue(tabsText.contains("按严重程度"));
        assertTrue(tabsText.contains("按错误类型"));
        assertTrue(tabsText.contains("按文件"));
    }

    @Test
    void testViolationCards_shouldDisplay() throws IOException {
        // Given
        ScanReport report = createTestReport();
        Path outputPath = tempDir.resolve("report.html");

        // When
        generator.writeToFile(report, outputPath);

        // Then
        String html = readFile(outputPath);
        Document doc = Jsoup.parse(html);

        Elements cards = doc.select(".violation-card");
        // Enhanced report has 4 tabs, each showing all violations, so 4 * 4 = 16 cards
        assertTrue(cards.size() >= 4, "Should have at least 4 violation cards");

        // Check for data attributes
        Element firstCard = cards.first();
        assertTrue(firstCard.hasAttr("data-risk"));
        assertTrue(firstCard.hasAttr("data-type"));
        assertTrue(firstCard.hasAttr("data-file"));
        assertTrue(firstCard.hasAttr("data-mapper"));
    }

    @Test
    void testXmlContent_shouldDisplay() throws IOException {
        // Given
        ScanReport report = createTestReport();
        Path outputPath = tempDir.resolve("report.html");

        // When
        generator.writeToFile(report, outputPath);

        // Then
        String html = readFile(outputPath);
        Document doc = Jsoup.parse(html);

        // Should contain XML content
        assertTrue(html.contains("XML 语句") || html.contains("code-section"));
        assertTrue(html.contains("select id="));
    }

    @Test
    void testJavaMethodSignature_shouldDisplay() throws IOException {
        // Given
        ScanReport report = createTestReport();
        Path outputPath = tempDir.resolve("report.html");

        // When
        generator.writeToFile(report, outputPath);

        // Then
        String html = readFile(outputPath);
        Document doc = Jsoup.parse(html);

        // Should contain Java method signatures
        assertTrue(html.contains("Java 方法签名") || html.contains("List&lt;User&gt;"));
    }

    @Test
    void testErrorTypeClassification_shouldWork() throws IOException {
        // Given
        ScanReport report = createTestReport();
        Path outputPath = tempDir.resolve("report.html");

        // When
        generator.writeToFile(report, outputPath);

        // Then
        String html = readFile(outputPath);
        Document doc = Jsoup.parse(html);

        // Should contain error type badges
        Elements typeBadges = doc.select(".type-badge");
        assertTrue(typeBadges.size() > 0, "Should have type badges");

        String badgesText = typeBadges.text();
        assertTrue(badgesText.contains("SQL 注入") || 
                   badgesText.contains("WHERE") || 
                   badgesText.contains("分页"));
    }

    @Test
    void testSearchBox_shouldExist() throws IOException {
        // Given
        ScanReport report = createTestReport();
        Path outputPath = tempDir.resolve("report.html");

        // When
        generator.writeToFile(report, outputPath);

        // Then
        String html = readFile(outputPath);
        Document doc = Jsoup.parse(html);

        Element searchInput = doc.selectFirst("#searchInput");
        assertNotNull(searchInput, "Should have search input");
        assertTrue(searchInput.attr("placeholder").contains("搜索"));
    }

    @Test
    void testRiskFilters_shouldExist() throws IOException {
        // Given
        ScanReport report = createTestReport();
        Path outputPath = tempDir.resolve("report.html");

        // When
        generator.writeToFile(report, outputPath);

        // Then
        String html = readFile(outputPath);
        Document doc = Jsoup.parse(html);

        Elements riskFilters = doc.select(".risk-filter");
        assertTrue(riskFilters.size() >= 4, "Should have 4 risk level filters");
    }

    @Test
    void testJavaScriptFunctions_shouldBeIncluded() throws IOException {
        // Given
        ScanReport report = createTestReport();
        Path outputPath = tempDir.resolve("report.html");

        // When
        generator.writeToFile(report, outputPath);

        // Then
        String html = readFile(outputPath);

        // Check for key JavaScript functions
        assertTrue(html.contains("filterByType"));
        assertTrue(html.contains("filterByFile"));
        assertTrue(html.contains("filterByRisk"));
        assertTrue(html.contains("clearAllFilters"));
        assertTrue(html.contains("applyFilters"));
    }

    @Test
    void testGroupHeaders_shouldBeClickable() throws IOException {
        // Given
        ScanReport report = createTestReport();
        Path outputPath = tempDir.resolve("report.html");

        // When
        generator.writeToFile(report, outputPath);

        // Then
        String html = readFile(outputPath);
        Document doc = Jsoup.parse(html);

        Elements groupHeaders = doc.select(".group-header");
        assertTrue(groupHeaders.size() > 0, "Should have group headers");

        // Check for onclick attributes or cursor style
        String css = doc.select("style").html();
        assertTrue(css.contains("cursor: pointer") || css.contains("cursor:pointer"));
    }

    @Test
    void testChineseContent_shouldDisplay() throws IOException {
        // Given
        ScanReport report = createTestReport();
        Path outputPath = tempDir.resolve("report.html");

        // When
        generator.writeToFile(report, outputPath);

        // Then
        String html = readFile(outputPath);
        Document doc = Jsoup.parse(html);

        String bodyText = doc.body().text();
        assertTrue(bodyText.contains("严重") || bodyText.contains("高危"));
        assertTrue(bodyText.contains("SQL 注入") || bodyText.contains("分页"));
        assertTrue(bodyText.contains("建议"));
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

        String bodyText = doc.body().text();
        assertTrue(bodyText.contains("未发现违规") || bodyText.contains("0"));
    }

    @Test
    void testSpecialCharacters_shouldEscape() throws IOException {
        // Given
        ScanReport report = new ScanReport();

        String dangerousSql = "<script>alert('XSS')</script> SELECT * FROM user";

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
        entry.setXmlSnippet("<select><script>alert('XSS')</script></select>");
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
    }

    @Test
    void testFilterItems_shouldHaveCount() throws IOException {
        // Given
        ScanReport report = createTestReport();
        Path outputPath = tempDir.resolve("report.html");

        // When
        generator.writeToFile(report, outputPath);

        // Then
        String html = readFile(outputPath);
        Document doc = Jsoup.parse(html);

        Elements filterItems = doc.select(".filter-item");
        assertTrue(filterItems.size() > 0, "Should have filter items");

        // Each filter item should have a count
        for (Element item : filterItems) {
            Elements counts = item.select(".count");
            assertTrue(counts.size() > 0, "Filter item should have count");
        }
    }

    @Test
    void testClearFilterButton_shouldExist() throws IOException {
        // Given
        ScanReport report = createTestReport();
        Path outputPath = tempDir.resolve("report.html");

        // When
        generator.writeToFile(report, outputPath);

        // Then
        String html = readFile(outputPath);
        Document doc = Jsoup.parse(html);

        Element clearButton = doc.selectFirst(".clear-filter");
        assertNotNull(clearButton, "Should have clear filter button");
        assertTrue(clearButton.text().contains("清除"));
    }

    @Test
    void testResponsiveDesign_shouldHaveMediaQueries() throws IOException {
        // Given
        ScanReport report = createTestReport();
        Path outputPath = tempDir.resolve("report.html");

        // When
        generator.writeToFile(report, outputPath);

        // Then
        String html = readFile(outputPath);

        // Should contain media queries for responsive design
        assertTrue(html.contains("@media") || html.contains("max-width"));
    }
}

