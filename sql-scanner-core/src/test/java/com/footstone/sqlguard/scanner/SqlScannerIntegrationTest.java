package com.footstone.sqlguard.scanner;

import com.footstone.sqlguard.scanner.model.*;
import com.footstone.sqlguard.scanner.parser.SqlParser;
import com.footstone.sqlguard.scanner.parser.WrapperScanner;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.config.SqlGuardConfig;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SqlScanner with mock parser implementations.
 * Tests end-to-end framework functionality.
 */
@DisplayName("SqlScanner Integration Tests")
class SqlScannerIntegrationTest {

  @TempDir
  Path tempDir;

  private SqlScanner scanner;

  @BeforeEach
  void setUp() {
    SqlParser xmlParser = new MockXmlMapperParser();
    SqlParser annotationParser = new MockAnnotationParser();
    WrapperScanner wrapperScanner = new MockWrapperScanner();
    scanner = new SqlScanner(xmlParser, annotationParser, wrapperScanner);
  }

  @Test
  @DisplayName("Should produce complete ScanReport with all parser results")
  void testIntegrationWithAllParsers() throws IOException {
    // Given
    createTestProjectStructure();
    ScanContext context = createScanContext();

    // When
    ScanReport report = scanner.scan(context);

    // Then - verify entries from XML parser
    long xmlEntries = report.getEntries().stream()
        .filter(e -> e.getSource() == SourceType.XML)
        .count();
    assertEquals(3, xmlEntries, "Should have 3 XML entries");

    // Then - verify entries from annotation parser
    long annotationEntries = report.getEntries().stream()
        .filter(e -> e.getSource() == SourceType.ANNOTATION)
        .count();
    assertEquals(2, annotationEntries, "Should have 2 annotation entries");

    // Then - verify wrapper usages
    assertEquals(5, report.getWrapperUsages().size(), "Should have 5 wrapper usages");

    // Then - verify total count
    assertEquals(5, report.getEntries().size(), "Should have 5 total SQL entries");
  }

  @Test
  @DisplayName("Should calculate accurate statistics")
  void testIntegrationStatisticsCalculation() throws IOException {
    // Given
    createTestProjectStructure();
    ScanContext context = createScanContext();

    // When
    ScanReport report = scanner.scan(context);

    // Then - verify statistics
    assertEquals(5, report.getStatistics().get("totalSqlCount"));
    assertEquals(5, report.getStatistics().get("wrapperUsageCount"));

    // Verify counts by SQL type
    assertTrue(report.getStatistics().get("selectCount") >= 1);
    assertTrue(report.getStatistics().get("insertCount") >= 1);
    assertTrue(report.getStatistics().get("updateCount") >= 1);
    assertTrue(report.getStatistics().get("deleteCount") >= 1);

    // Verify counts by source type
    assertEquals(3, report.getStatistics().get("xmlSourceCount"));
    assertEquals(2, report.getStatistics().get("annotationSourceCount"));
  }

  @Test
  @DisplayName("Should handle parser exceptions gracefully without crashing")
  void testIntegrationErrorHandlingGraceful() throws IOException {
    // Given
    createTestProjectStructure();
    SqlParser errorParser = new MockErrorThrowingParser();
    SqlParser goodParser = new MockAnnotationParser();
    WrapperScanner goodScanner = new MockWrapperScanner();
    SqlScanner errorScanner = new SqlScanner(errorParser, goodParser, goodScanner);

    ScanContext context = createScanContext();

    // When
    ScanReport report = errorScanner.scan(context);

    // Then - should have partial results despite XML parser error
    assertNotNull(report);
    assertTrue(report.getEntries().size() >= 2, "Should have at least annotation entries");
    assertEquals(5, report.getWrapperUsages().size(), "Should have wrapper usages");
  }

  @Test
  @DisplayName("Should log errors using SLF4J logger")
  void testIntegrationErrorLogging() throws IOException {
    // Given
    createTestProjectStructure();
    SqlParser errorParser = new MockErrorThrowingParser();
    SqlParser goodParser = new MockAnnotationParser();
    WrapperScanner goodScanner = new MockWrapperScanner();
    SqlScanner errorScanner = new SqlScanner(errorParser, goodParser, goodScanner);

    // Setup log appender to capture logs
    Logger logger = (Logger) LoggerFactory.getLogger(SqlScanner.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);

    ScanContext context = createScanContext();

    // When
    errorScanner.scan(context);

    // Then - verify error was logged
    List<ILoggingEvent> logsList = listAppender.list;
    boolean errorLogged = logsList.stream()
        .anyMatch(event -> event.getLevel() == Level.ERROR &&
                          event.getFormattedMessage().contains("Failed to parse"));

    assertTrue(errorLogged, "Error should be logged");

    // Cleanup
    logger.detachAppender(listAppender);
  }

  @Test
  @DisplayName("Should return partial results when some files fail")
  void testIntegrationPartialResults() throws IOException {
    // Given
    createTestProjectStructure();
    SqlParser partialParser = new MockPartialErrorParser();
    SqlParser goodParser = new MockAnnotationParser();
    WrapperScanner goodScanner = new MockWrapperScanner();
    SqlScanner partialScanner = new SqlScanner(partialParser, goodParser, goodScanner);

    ScanContext context = createScanContext();

    // When
    ScanReport report = partialScanner.scan(context);

    // Then - should have results from successful parses
    assertNotNull(report);
    assertTrue(report.getEntries().size() > 0, "Should have some entries");
    assertTrue(report.getWrapperUsages().size() > 0, "Should have wrapper usages");
  }

  @Test
  @DisplayName("Should handle empty project gracefully")
  void testIntegrationEmptyProject() {
    // Given - empty temp directory (no src structure)
    ScanContext context = createScanContext();

    // When
    ScanReport report = scanner.scan(context);

    // Then
    assertNotNull(report);
    assertEquals(0, report.getEntries().size());
    assertEquals(0, report.getWrapperUsages().size());
    assertEquals(0, report.getStatistics().get("totalSqlCount"));
  }

  // Helper Methods

  private void createTestProjectStructure() throws IOException {
    // Create src/main/resources directory with XML files
    Path resourcesDir = tempDir.resolve("src/main/resources");
    Files.createDirectories(resourcesDir);
    Files.createFile(resourcesDir.resolve("UserMapper.xml"));
    Files.createFile(resourcesDir.resolve("OrderMapper.xml"));

    // Create src/main/java directory with Java files
    Path javaDir = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(javaDir);
    Files.createFile(javaDir.resolve("UserMapper.java"));
    Files.createFile(javaDir.resolve("OrderMapper.java"));
  }

  private ScanContext createScanContext() {
    SqlGuardConfig config = new SqlGuardConfig();
    config.setEnabled(true);
    return new ScanContext(tempDir, config);
  }

  // Mock Parser Implementations

  /**
   * Mock XML parser returning 3 predefined entries (only for UserMapper.xml).
   */
  private class MockXmlMapperParser implements SqlParser {
    @Override
    public List<SqlEntry> parse(File file) throws IOException, ParseException {
      if (!file.exists()) {
        throw new IOException("File not found: " + file.getAbsolutePath());
      }

      List<SqlEntry> entries = new ArrayList<>();
      
      // Only return entries for UserMapper.xml
      if (file.getName().equals("UserMapper.xml")) {
        entries.add(new SqlEntry(
            SourceType.XML,
            file.getAbsolutePath(),
            "com.example.UserMapper.selectById",
            SqlCommandType.SELECT,
            "SELECT * FROM user WHERE id = #{id}",
            10
        ));
        entries.add(new SqlEntry(
            SourceType.XML,
            file.getAbsolutePath(),
            "com.example.UserMapper.insert",
            SqlCommandType.INSERT,
            "INSERT INTO user VALUES (#{id}, #{name})",
            20
        ));
        entries.add(new SqlEntry(
            SourceType.XML,
            file.getAbsolutePath(),
            "com.example.UserMapper.update",
            SqlCommandType.UPDATE,
            "UPDATE user SET name = #{name} WHERE id = #{id}",
            30
        ));
      }
      // Return empty list for other XML files
      return entries;
    }
  }

  /**
   * Mock annotation parser returning 2 predefined entries (only for OrderMapper.java).
   */
  private class MockAnnotationParser implements SqlParser {
    @Override
    public List<SqlEntry> parse(File file) throws IOException, ParseException {
      if (!file.exists()) {
        throw new IOException("File not found: " + file.getAbsolutePath());
      }

      List<SqlEntry> entries = new ArrayList<>();
      
      // Only return entries for OrderMapper.java
      if (file.getName().equals("OrderMapper.java")) {
        entries.add(new SqlEntry(
            SourceType.ANNOTATION,
            file.getAbsolutePath(),
            "com.example.OrderMapper.selectByUserId",
            SqlCommandType.SELECT,
            "SELECT * FROM orders WHERE user_id = #{userId}",
            15
        ));
        entries.add(new SqlEntry(
            SourceType.ANNOTATION,
            file.getAbsolutePath(),
            "com.example.OrderMapper.deleteById",
            SqlCommandType.DELETE,
            "DELETE FROM orders WHERE id = #{id}",
            25
        ));
      }
      // Return empty list for other Java files
      return entries;
    }
  }

  /**
   * Mock wrapper scanner returning 5 predefined usages (only if src/main/java exists).
   */
  private class MockWrapperScanner implements WrapperScanner {
    @Override
    public List<WrapperUsage> scan(File projectRoot) throws IOException {
      if (!projectRoot.exists()) {
        throw new IOException("Project root not found: " + projectRoot.getAbsolutePath());
      }

      List<WrapperUsage> usages = new ArrayList<>();
      
      // Only return usages if Java source directory exists
      File javaDir = new File(projectRoot, "src/main/java");
      if (!javaDir.exists()) {
        return usages; // Empty list for projects without Java sources
      }
      
      usages.add(new WrapperUsage(
          projectRoot.getAbsolutePath() + "/UserService.java",
          "getUserList",
          10,
          "QueryWrapper",
          true
      ));
      usages.add(new WrapperUsage(
          projectRoot.getAbsolutePath() + "/UserService.java",
          "getUserByName",
          20,
          "LambdaQueryWrapper",
          true
      ));
      usages.add(new WrapperUsage(
          projectRoot.getAbsolutePath() + "/UserService.java",
          "updateUser",
          30,
          "UpdateWrapper",
          true
      ));
      usages.add(new WrapperUsage(
          projectRoot.getAbsolutePath() + "/UserService.java",
          "updateUserLambda",
          40,
          "LambdaUpdateWrapper",
          true
      ));
      usages.add(new WrapperUsage(
          projectRoot.getAbsolutePath() + "/OrderService.java",
          "getOrders",
          15,
          "QueryWrapper",
          true
      ));
      return usages;
    }
  }

  /**
   * Mock parser that always throws ParseException.
   */
  private class MockErrorThrowingParser implements SqlParser {
    @Override
    public List<SqlEntry> parse(File file) throws IOException, ParseException {
      throw new ParseException("Simulated parse error", 0);
    }
  }

  /**
   * Mock parser that throws error for some files but succeeds for others.
   */
  private class MockPartialErrorParser implements SqlParser {
    private int callCount = 0;

    @Override
    public List<SqlEntry> parse(File file) throws IOException, ParseException {
      callCount++;
      if (callCount % 2 == 0) {
        throw new ParseException("Simulated parse error for file: " + file.getName(), 0);
      }

      List<SqlEntry> entries = new ArrayList<>();
      entries.add(new SqlEntry(
          SourceType.XML,
          file.getAbsolutePath(),
          "com.example.Mapper.select",
          SqlCommandType.SELECT,
          "SELECT * FROM table",
          10
      ));
      return entries;
    }
  }
}

