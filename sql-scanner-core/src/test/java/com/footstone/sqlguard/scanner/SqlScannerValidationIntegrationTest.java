package com.footstone.sqlguard.scanner;

import com.footstone.sqlguard.config.SqlGuardConfig;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.scanner.model.ScanContext;
import com.footstone.sqlguard.scanner.model.ScanReport;
import com.footstone.sqlguard.scanner.model.SqlEntry;
import com.footstone.sqlguard.scanner.parser.SqlParser;
import com.footstone.sqlguard.scanner.parser.WrapperScanner;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for SqlScanner with DefaultSqlSafetyValidator.
 *
 * <p>Tests the integration between SQL extraction (SqlScanner) and
 * SQL validation (DefaultSqlSafetyValidator), ensuring violations
 * are properly detected and populated in SqlEntry objects.</p>
 */
@DisplayName("SqlScanner Validation Integration Tests")
class SqlScannerValidationIntegrationTest {

  @TempDir
  Path tempDir;

  private SqlParser mockXmlParser;
  private SqlParser mockAnnotationParser;
  private WrapperScanner mockWrapperScanner;
  private DefaultSqlSafetyValidator mockValidator;
  private SqlGuardConfig config;

  @BeforeEach
  void setUp() {
    mockXmlParser = mock(SqlParser.class);
    mockAnnotationParser = mock(SqlParser.class);
    mockWrapperScanner = mock(WrapperScanner.class);
    mockValidator = mock(DefaultSqlSafetyValidator.class);
    
    config = new SqlGuardConfig();
    config.setEnabled(true);
  }

  /**
   * Helper method to create ScanContext.
   */
  private ScanContext createScanContext() {
    return new ScanContext(tempDir, config);
  }

  @Test
  @DisplayName("Scanner with validator should validate SQL correctly")
  void testScannerWithValidatorValidatesSql() throws Exception {
    // Given - create actual XML file
    createTestXmlFile();
    
    SqlEntry entry = createSqlEntry("SELECT * FROM user WHERE id = #{id}");
    when(mockXmlParser.parse(any(File.class))).thenReturn(Collections.singletonList(entry));
    when(mockAnnotationParser.parse(any(File.class))).thenReturn(Collections.emptyList());
    when(mockWrapperScanner.scan(any(File.class))).thenReturn(Collections.emptyList());
    when(mockValidator.validate(any())).thenReturn(com.footstone.sqlguard.core.model.ValidationResult.pass());

    SqlScanner scanner = new SqlScanner(mockXmlParser, mockAnnotationParser, mockWrapperScanner, mockValidator);
    ScanContext context = createScanContext();

    // When
    ScanReport report = scanner.scan(context);

    // Then
    assertNotNull(report);
    verify(mockValidator, times(1)).validate(any());
  }

  /**
   * Helper method to create test XML file in temp directory.
   */
  private void createTestXmlFile() throws Exception {
    java.nio.file.Path resourcesDir = tempDir.resolve("src/main/resources");
    java.nio.file.Files.createDirectories(resourcesDir);
    java.nio.file.Path xmlFile = resourcesDir.resolve("TestMapper.xml");
    java.nio.file.Files.write(xmlFile, "<?xml version=\"1.0\"?>".getBytes());
  }

  @Test
  @DisplayName("Should detect CRITICAL violations (SQL injection)")
  void testDetectCriticalViolations() throws Exception {
    // Given - create actual XML file and SQL with ${} injection
    createTestXmlFile();
    
    SqlEntry entry = createSqlEntry("SELECT * FROM user ORDER BY ${orderBy}");
    when(mockXmlParser.parse(any(File.class))).thenReturn(Collections.singletonList(entry));
    when(mockAnnotationParser.parse(any(File.class))).thenReturn(Collections.emptyList());
    when(mockWrapperScanner.scan(any(File.class))).thenReturn(Collections.emptyList());

    // Mock validator to add CRITICAL violation
    when(mockValidator.validate(any())).thenAnswer(invocation -> {
      com.footstone.sqlguard.core.model.ValidationResult result = com.footstone.sqlguard.core.model.ValidationResult.pass();
      result.addViolation(
          RiskLevel.CRITICAL,
          "SQL injection risk - ${} string interpolation detected",
          "Use #{} parameterized query instead"
      );
      return result;
    });

    SqlScanner scanner = new SqlScanner(mockXmlParser, mockAnnotationParser, mockWrapperScanner, mockValidator);
    ScanContext context = createScanContext();

    // When
    ScanReport report = scanner.scan(context);

    // Then
    assertEquals(1, report.getEntries().size());
    SqlEntry resultEntry = report.getEntries().get(0);
    assertTrue(resultEntry.hasViolations());
    assertEquals(RiskLevel.CRITICAL, resultEntry.getHighestRiskLevel());
  }

  @Test
  @DisplayName("Should detect HIGH violations (blacklist fields)")
  void testDetectHighViolations() throws Exception {
    // Given - create actual XML file and SQL with blacklist field
    createTestXmlFile();
    
    SqlEntry entry = createSqlEntry("SELECT * FROM user WHERE is_deleted = 1");
    when(mockXmlParser.parse(any(File.class))).thenReturn(Collections.singletonList(entry));
    when(mockAnnotationParser.parse(any(File.class))).thenReturn(Collections.emptyList());
    when(mockWrapperScanner.scan(any(File.class))).thenReturn(Collections.emptyList());

    // Mock validator to add HIGH violation
    when(mockValidator.validate(any())).thenAnswer(invocation -> {
      com.footstone.sqlguard.core.model.ValidationResult result = com.footstone.sqlguard.core.model.ValidationResult.pass();
      result.addViolation(
          RiskLevel.HIGH,
          "Blacklist field 'is_deleted' used in WHERE clause",
          "Use proper soft-delete mechanism"
      );
      return result;
    });

    SqlScanner scanner = new SqlScanner(mockXmlParser, mockAnnotationParser, mockWrapperScanner, mockValidator);
    ScanContext context = createScanContext();

    // When
    ScanReport report = scanner.scan(context);

    // Then
    assertEquals(1, report.getEntries().size());
    SqlEntry resultEntry = report.getEntries().get(0);
    assertTrue(resultEntry.hasViolations());
    assertEquals(RiskLevel.HIGH, resultEntry.getHighestRiskLevel());
  }

  @Test
  @DisplayName("Should detect MEDIUM violations (deep pagination)")
  void testDetectMediumViolations() throws Exception {
    // Given - create actual XML file and SQL with deep pagination
    createTestXmlFile();
    
    SqlEntry entry = createSqlEntry("SELECT * FROM user LIMIT 100000, 20");
    when(mockXmlParser.parse(any(File.class))).thenReturn(Collections.singletonList(entry));
    when(mockAnnotationParser.parse(any(File.class))).thenReturn(Collections.emptyList());
    when(mockWrapperScanner.scan(any(File.class))).thenReturn(Collections.emptyList());

    // Mock validator to add MEDIUM violation
    when(mockValidator.validate(any())).thenAnswer(invocation -> {
      com.footstone.sqlguard.core.model.ValidationResult result = com.footstone.sqlguard.core.model.ValidationResult.pass();
      result.addViolation(
          RiskLevel.MEDIUM,
          "Deep pagination detected (offset > 10000)",
          "Use cursor-based pagination instead"
      );
      return result;
    });

    SqlScanner scanner = new SqlScanner(mockXmlParser, mockAnnotationParser, mockWrapperScanner, mockValidator);
    ScanContext context = createScanContext();

    // When
    ScanReport report = scanner.scan(context);

    // Then
    assertEquals(1, report.getEntries().size());
    SqlEntry resultEntry = report.getEntries().get(0);
    assertTrue(resultEntry.hasViolations());
    assertEquals(RiskLevel.MEDIUM, resultEntry.getHighestRiskLevel());
  }

  @Test
  @DisplayName("Should detect LOW violations (missing ORDER BY)")
  void testDetectLowViolations() throws Exception {
    // Given - create actual XML file and SQL with missing ORDER BY
    createTestXmlFile();
    
    SqlEntry entry = createSqlEntry("SELECT * FROM user LIMIT 10");
    when(mockXmlParser.parse(any(File.class))).thenReturn(Collections.singletonList(entry));
    when(mockAnnotationParser.parse(any(File.class))).thenReturn(Collections.emptyList());
    when(mockWrapperScanner.scan(any(File.class))).thenReturn(Collections.emptyList());

    // Mock validator to add LOW violation
    when(mockValidator.validate(any())).thenAnswer(invocation -> {
      com.footstone.sqlguard.core.model.ValidationResult result = com.footstone.sqlguard.core.model.ValidationResult.pass();
      result.addViolation(
          RiskLevel.LOW,
          "LIMIT without ORDER BY may produce inconsistent results",
          "Add ORDER BY clause for deterministic pagination"
      );
      return result;
    });

    SqlScanner scanner = new SqlScanner(mockXmlParser, mockAnnotationParser, mockWrapperScanner, mockValidator);
    ScanContext context = createScanContext();

    // When
    ScanReport report = scanner.scan(context);

    // Then
    assertEquals(1, report.getEntries().size());
    SqlEntry resultEntry = report.getEntries().get(0);
    assertTrue(resultEntry.hasViolations());
    assertEquals(RiskLevel.LOW, resultEntry.getHighestRiskLevel());
  }

  @Test
  @DisplayName("Should handle multiple violations per SQL statement")
  void testMultipleViolationsPerStatement() throws Exception {
    // Given - create actual XML file and SQL with multiple issues
    createTestXmlFile();
    
    SqlEntry entry = createSqlEntry("UPDATE user SET status = 'active'");
    when(mockXmlParser.parse(any(File.class))).thenReturn(Collections.singletonList(entry));
    when(mockAnnotationParser.parse(any(File.class))).thenReturn(Collections.emptyList());
    when(mockWrapperScanner.scan(any(File.class))).thenReturn(Collections.emptyList());

    // Mock validator to add multiple violations
    when(mockValidator.validate(any())).thenAnswer(invocation -> {
      com.footstone.sqlguard.core.model.ValidationResult result = com.footstone.sqlguard.core.model.ValidationResult.pass();
      result.addViolation(
          RiskLevel.CRITICAL,
          "UPDATE without WHERE clause - affects all rows",
          "Add WHERE clause to limit scope"
      );
      result.addViolation(
          RiskLevel.HIGH,
          "Mass update operation detected",
          "Consider batch processing"
      );
      return result;
    });

    SqlScanner scanner = new SqlScanner(mockXmlParser, mockAnnotationParser, mockWrapperScanner, mockValidator);
    ScanContext context = createScanContext();

    // When
    ScanReport report = scanner.scan(context);

    // Then
    assertEquals(1, report.getEntries().size());
    SqlEntry resultEntry = report.getEntries().get(0);
    assertTrue(resultEntry.hasViolations());
    assertEquals(2, resultEntry.getViolations().size());
    assertEquals(RiskLevel.CRITICAL, resultEntry.getHighestRiskLevel());
  }

  @Test
  @DisplayName("Violations should be populated in SqlEntry correctly")
  void testViolationsPopulatedCorrectly() throws Exception {
    // Given - create actual XML file
    createTestXmlFile();
    
    SqlEntry entry = createSqlEntry("SELECT * FROM user");
    when(mockXmlParser.parse(any(File.class))).thenReturn(Collections.singletonList(entry));
    when(mockAnnotationParser.parse(any(File.class))).thenReturn(Collections.emptyList());
    when(mockWrapperScanner.scan(any(File.class))).thenReturn(Collections.emptyList());

    // Mock validator to add violation
    when(mockValidator.validate(any())).thenAnswer(invocation -> {
      com.footstone.sqlguard.core.model.ValidationResult result = com.footstone.sqlguard.core.model.ValidationResult.pass();
      result.addViolation(
          RiskLevel.MEDIUM,
          "Test violation message",
          "Test suggestion"
      );
      return result;
    });

    SqlScanner scanner = new SqlScanner(mockXmlParser, mockAnnotationParser, mockWrapperScanner, mockValidator);
    ScanContext context = createScanContext();

    // When
    ScanReport report = scanner.scan(context);

    // Then
    SqlEntry resultEntry = report.getEntries().get(0);
    assertEquals(1, resultEntry.getViolations().size());
    assertEquals("Test violation message", resultEntry.getViolations().get(0).getMessage());
    assertEquals("Test suggestion", resultEntry.getViolations().get(0).getSuggestion());
    assertEquals(RiskLevel.MEDIUM, resultEntry.getViolations().get(0).getRiskLevel());
  }

  @Test
  @DisplayName("Valid SQL should produce no violations")
  void testValidSqlProducesNoViolations() throws Exception {
    // Given - create actual XML file and valid SQL
    createTestXmlFile();
    
    SqlEntry entry = createSqlEntry("SELECT * FROM user WHERE id = #{id} ORDER BY id");
    when(mockXmlParser.parse(any(File.class))).thenReturn(Collections.singletonList(entry));
    when(mockAnnotationParser.parse(any(File.class))).thenReturn(Collections.emptyList());
    when(mockWrapperScanner.scan(any(File.class))).thenReturn(Collections.emptyList());

    // Mock validator - return passed result with no violations
    when(mockValidator.validate(any())).thenReturn(com.footstone.sqlguard.core.model.ValidationResult.pass());

    SqlScanner scanner = new SqlScanner(mockXmlParser, mockAnnotationParser, mockWrapperScanner, mockValidator);
    ScanContext context = createScanContext();

    // When
    ScanReport report = scanner.scan(context);

    // Then
    assertEquals(1, report.getEntries().size());
    SqlEntry resultEntry = report.getEntries().get(0);
    assertFalse(resultEntry.hasViolations());
    assertEquals(RiskLevel.SAFE, resultEntry.getHighestRiskLevel());
  }

  @Test
  @DisplayName("Empty project should produce empty report")
  void testEmptyProjectProducesEmptyReport() throws Exception {
    // Given - no SQL entries
    when(mockXmlParser.parse(any(File.class))).thenReturn(Collections.emptyList());
    when(mockAnnotationParser.parse(any(File.class))).thenReturn(Collections.emptyList());
    when(mockWrapperScanner.scan(any(File.class))).thenReturn(Collections.emptyList());

    SqlScanner scanner = new SqlScanner(mockXmlParser, mockAnnotationParser, mockWrapperScanner, mockValidator);
    ScanContext context = createScanContext();

    // When
    ScanReport report = scanner.scan(context);

    // Then
    assertNotNull(report);
    assertTrue(report.getEntries().isEmpty());
    verify(mockValidator, never()).validate(any());
  }

  @Test
  @DisplayName("Validator should be called for each SqlEntry")
  void testValidatorCalledForEachEntry() throws Exception {
    // Given - create actual XML file and multiple SQL entries
    createTestXmlFile();
    
    List<SqlEntry> entries = new ArrayList<>();
    entries.add(createSqlEntry("SELECT * FROM user WHERE id = 1"));
    entries.add(createSqlEntry("SELECT * FROM order WHERE id = 2"));
    entries.add(createSqlEntry("SELECT * FROM product WHERE id = 3"));

    when(mockXmlParser.parse(any(File.class))).thenReturn(entries);
    when(mockAnnotationParser.parse(any(File.class))).thenReturn(Collections.emptyList());
    when(mockWrapperScanner.scan(any(File.class))).thenReturn(Collections.emptyList());
    when(mockValidator.validate(any())).thenReturn(com.footstone.sqlguard.core.model.ValidationResult.pass());

    SqlScanner scanner = new SqlScanner(mockXmlParser, mockAnnotationParser, mockWrapperScanner, mockValidator);
    ScanContext context = createScanContext();

    // When
    ScanReport report = scanner.scan(context);

    // Then
    assertEquals(3, report.getEntries().size());
    verify(mockValidator, times(3)).validate(any());
  }

  @Test
  @DisplayName("Performance: 100 SQL entries validated in <1 second")
  void testPerformance100Entries() throws Exception {
    // Given - create actual XML file and 100 SQL entries
    createTestXmlFile();
    
    List<SqlEntry> entries = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      entries.add(createSqlEntry("SELECT * FROM user WHERE id = " + i));
    }

    when(mockXmlParser.parse(any(File.class))).thenReturn(entries);
    when(mockAnnotationParser.parse(any(File.class))).thenReturn(Collections.emptyList());
    when(mockWrapperScanner.scan(any(File.class))).thenReturn(Collections.emptyList());
    when(mockValidator.validate(any())).thenReturn(com.footstone.sqlguard.core.model.ValidationResult.pass());

    SqlScanner scanner = new SqlScanner(mockXmlParser, mockAnnotationParser, mockWrapperScanner, mockValidator);
    ScanContext context = createScanContext();

    // When
    long startTime = System.currentTimeMillis();
    ScanReport report = scanner.scan(context);
    long duration = System.currentTimeMillis() - startTime;

    // Then
    assertEquals(100, report.getEntries().size());
    assertTrue(duration < 1000, "Validation took " + duration + "ms, expected <1000ms");
  }

  /**
   * Helper method to create a test SqlEntry.
   */
  private SqlEntry createSqlEntry(String sql) {
    return new SqlEntry(
        com.footstone.sqlguard.scanner.model.SourceType.XML,
        "/test/Mapper.xml",
        "com.example.TestMapper.selectById",
        SqlCommandType.SELECT,
        sql,
        42
    );
  }
}

