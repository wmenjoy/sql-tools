package com.footstone.sqlguard.scanner;

import com.footstone.sqlguard.scanner.model.*;
import com.footstone.sqlguard.scanner.parser.SqlParser;
import com.footstone.sqlguard.scanner.parser.WrapperScanner;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.config.SqlGuardConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SqlScanner orchestration class.
 * Tests aggregation, statistics calculation, and error handling.
 */
@DisplayName("SqlScanner Tests")
class SqlScannerTest {

  @TempDir
  Path tempDir;

  private SqlParser xmlParser;
  private SqlParser annotationParser;
  private WrapperScanner wrapperScanner;
  private SqlScanner scanner;

  @BeforeEach
  void setUp() {
    xmlParser = mock(SqlParser.class);
    annotationParser = mock(SqlParser.class);
    wrapperScanner = mock(WrapperScanner.class);
    scanner = new SqlScanner(xmlParser, annotationParser, wrapperScanner);
  }

  @Test
  @DisplayName("Should scan with all parsers and return complete ScanReport")
  void testScanWithAllParsersReturnsCompleteReport() throws Exception {
    // Given
    createTestFiles();
    ScanContext context = createScanContext();

    // Mock XML parser results
    when(xmlParser.parse(any(File.class))).thenReturn(createXmlEntries());

    // Mock annotation parser results
    when(annotationParser.parse(any(File.class))).thenReturn(createAnnotationEntries());

    // Mock wrapper scanner results
    when(wrapperScanner.scan(any(File.class))).thenReturn(createWrapperUsages());

    // When
    ScanReport report = scanner.scan(context);

    // Then
    assertNotNull(report);
    assertTrue(report.getEntries().size() > 0);
    assertTrue(report.getWrapperUsages().size() > 0);
  }

  @Test
  @DisplayName("Should aggregate entries from XML and annotation parsers")
  void testScanAggregatesEntriesFromParsers() throws Exception {
    // Given
    createTestFiles();
    ScanContext context = createScanContext();

    List<SqlEntry> xmlEntries = createXmlEntries();
    List<SqlEntry> annotationEntries = createAnnotationEntries();

    when(xmlParser.parse(any(File.class))).thenReturn(xmlEntries);
    when(annotationParser.parse(any(File.class))).thenReturn(annotationEntries);
    when(wrapperScanner.scan(any(File.class))).thenReturn(new ArrayList<>());

    // When
    ScanReport report = scanner.scan(context);

    // Then
    int expectedTotal = xmlEntries.size() + annotationEntries.size();
    assertTrue(report.getEntries().size() >= expectedTotal);
  }

  @Test
  @DisplayName("Should aggregate wrapper usages from wrapper scanner")
  void testScanAggregatesWrapperUsages() throws Exception {
    // Given
    createTestFiles();
    ScanContext context = createScanContext();

    List<WrapperUsage> wrapperUsages = createWrapperUsages();

    when(xmlParser.parse(any(File.class))).thenReturn(new ArrayList<>());
    when(annotationParser.parse(any(File.class))).thenReturn(new ArrayList<>());
    when(wrapperScanner.scan(any(File.class))).thenReturn(wrapperUsages);

    // When
    ScanReport report = scanner.scan(context);

    // Then
    assertEquals(wrapperUsages.size(), report.getWrapperUsages().size());
  }

  @Test
  @DisplayName("Should calculate statistics including total SQL count")
  void testScanCalculatesStatistics() throws Exception {
    // Given
    createTestFiles();
    ScanContext context = createScanContext();

    when(xmlParser.parse(any(File.class))).thenReturn(createXmlEntries());
    when(annotationParser.parse(any(File.class))).thenReturn(createAnnotationEntries());
    when(wrapperScanner.scan(any(File.class))).thenReturn(createWrapperUsages());

    // When
    ScanReport report = scanner.scan(context);

    // Then
    assertTrue(report.getStatistics().containsKey("totalSqlCount"));
    assertTrue(report.getStatistics().get("totalSqlCount") > 0);
  }

  @Test
  @DisplayName("Should calculate dynamic SQL count in statistics")
  void testScanCalculatesDynamicSqlCount() throws Exception {
    // Given
    createTestFiles();
    ScanContext context = createScanContext();

    List<SqlEntry> entries = createXmlEntries();
    entries.get(0).setDynamic(true);

    when(xmlParser.parse(any(File.class))).thenReturn(entries);
    when(annotationParser.parse(any(File.class))).thenReturn(new ArrayList<>());
    when(wrapperScanner.scan(any(File.class))).thenReturn(new ArrayList<>());

    // When
    ScanReport report = scanner.scan(context);

    // Then
    assertTrue(report.getStatistics().containsKey("dynamicSqlCount"));
    assertTrue(report.getStatistics().get("dynamicSqlCount") >= 1);
  }

  @Test
  @DisplayName("Should calculate wrapper count in statistics")
  void testScanCalculatesWrapperCount() throws Exception {
    // Given
    createTestFiles();
    ScanContext context = createScanContext();

    when(xmlParser.parse(any(File.class))).thenReturn(new ArrayList<>());
    when(annotationParser.parse(any(File.class))).thenReturn(new ArrayList<>());
    when(wrapperScanner.scan(any(File.class))).thenReturn(createWrapperUsages());

    // When
    ScanReport report = scanner.scan(context);

    // Then
    assertTrue(report.getStatistics().containsKey("wrapperUsageCount"));
    assertEquals(2, report.getStatistics().get("wrapperUsageCount"));
  }

  @Test
  @DisplayName("Should handle empty project and return empty report")
  void testScanHandlesEmptyProject() throws Exception {
    // Given
    ScanContext context = createScanContext();

    when(xmlParser.parse(any(File.class))).thenReturn(new ArrayList<>());
    when(annotationParser.parse(any(File.class))).thenReturn(new ArrayList<>());
    when(wrapperScanner.scan(any(File.class))).thenReturn(new ArrayList<>());

    // When
    ScanReport report = scanner.scan(context);

    // Then
    assertNotNull(report);
    assertEquals(0, report.getEntries().size());
    assertEquals(0, report.getWrapperUsages().size());
    assertEquals(0, report.getStatistics().get("totalSqlCount"));
  }

  @Test
  @DisplayName("Should handle parser exceptions gracefully and continue")
  void testScanHandlesParserExceptionsGracefully() throws Exception {
    // Given
    createTestFiles();
    ScanContext context = createScanContext();

    // Mock XML parser to throw exception
    when(xmlParser.parse(any(File.class))).thenThrow(new ParseException("Parse error", 0));

    // Mock annotation parser to succeed
    when(annotationParser.parse(any(File.class))).thenReturn(createAnnotationEntries());
    when(wrapperScanner.scan(any(File.class))).thenReturn(new ArrayList<>());

    // When
    ScanReport report = scanner.scan(context);

    // Then - should continue despite XML parser error
    assertNotNull(report);
    // Should have annotation entries despite XML parser failure
    assertTrue(report.getEntries().size() >= createAnnotationEntries().size());
  }

  // Helper Methods

  private void createTestFiles() throws IOException {
    // Create XML files
    Path resourcesDir = tempDir.resolve("src/main/resources");
    Files.createDirectories(resourcesDir);
    Files.createFile(resourcesDir.resolve("UserMapper.xml"));

    // Create Java files
    Path javaDir = tempDir.resolve("src/main/java");
    Files.createDirectories(javaDir);
    Files.createFile(javaDir.resolve("UserMapper.java"));
  }

  private ScanContext createScanContext() {
    SqlGuardConfig config = new SqlGuardConfig();
    config.setEnabled(true);
    return new ScanContext(tempDir, config);
  }

  private List<SqlEntry> createXmlEntries() {
    List<SqlEntry> entries = new ArrayList<>();
    entries.add(new SqlEntry(
        SourceType.XML,
        tempDir.resolve("src/main/resources/UserMapper.xml").toString(),
        "com.example.UserMapper.selectById",
        SqlCommandType.SELECT,
        "SELECT * FROM user WHERE id = #{id}",
        10
    ));
    entries.add(new SqlEntry(
        SourceType.XML,
        tempDir.resolve("src/main/resources/UserMapper.xml").toString(),
        "com.example.UserMapper.insert",
        SqlCommandType.INSERT,
        "INSERT INTO user VALUES (#{id}, #{name})",
        20
    ));
    return entries;
  }

  private List<SqlEntry> createAnnotationEntries() {
    List<SqlEntry> entries = new ArrayList<>();
    entries.add(new SqlEntry(
        SourceType.ANNOTATION,
        tempDir.resolve("src/main/java/UserMapper.java").toString(),
        "com.example.UserMapper.deleteById",
        SqlCommandType.DELETE,
        "DELETE FROM user WHERE id = #{id}",
        15
    ));
    return entries;
  }

  private List<WrapperUsage> createWrapperUsages() {
    List<WrapperUsage> usages = new ArrayList<>();
    usages.add(new WrapperUsage(
        tempDir.resolve("src/main/java/UserService.java").toString(),
        "getUserList",
        25,
        "QueryWrapper",
        true
    ));
    usages.add(new WrapperUsage(
        tempDir.resolve("src/main/java/UserService.java").toString(),
        "updateUser",
        35,
        "UpdateWrapper",
        true
    ));
    return usages;
  }
}





