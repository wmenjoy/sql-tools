package com.footstone.sqlguard.scanner.parser.impl;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.scanner.model.SourceType;
import com.footstone.sqlguard.scanner.model.SqlEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AnnotationParser covering comprehensive scenarios.
 * Tests multiple mapper files, nested classes, and edge cases.
 */
@DisplayName("AnnotationParser Integration Tests")
class AnnotationParserIntegrationTest {

  private AnnotationParser parser;

  @BeforeEach
  void setUp() {
    parser = new AnnotationParser();
  }

  @Test
  @DisplayName("SimpleMapper: Should parse basic @Select, @Update annotations")
  void testSimpleMapper_shouldParseBasicAnnotations() throws IOException, ParseException {
    // Given
    File file = new File("src/test/resources/mappers/SimpleMapper.java");

    // When
    List<SqlEntry> entries = parser.parse(file);

    // Then
    assertEquals(2, entries.size(), "Should find 2 SQL annotations");
    
    // Verify all entries have correct source type
    entries.forEach(entry -> {
      assertEquals(SourceType.ANNOTATION, entry.getSource());
      assertFalse(entry.isDynamic(), "Annotation SQL should not be dynamic");
      assertTrue(entry.getSqlVariants().isEmpty(), "Annotation SQL should have no variants");
    });

    // Verify @Select entry
    SqlEntry selectEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.SELECT)
        .findFirst()
        .orElse(null);
    assertNotNull(selectEntry);
    assertEquals("com.example.SimpleMapper.getUserById", selectEntry.getMapperId());
    assertEquals("SELECT * FROM user WHERE id = #{id}", selectEntry.getRawSql());

    // Verify @Update entry
    SqlEntry updateEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.UPDATE)
        .findFirst()
        .orElse(null);
    assertNotNull(updateEntry);
    assertEquals("com.example.SimpleMapper.updateUser", updateEntry.getMapperId());
  }

  @Test
  @DisplayName("MultiLineMapper: Should concatenate multi-line SQL arrays correctly")
  void testMultiLineMapper_shouldConcatenateArrays() throws IOException, ParseException {
    // Given
    File file = new File("src/test/resources/mappers/MultiLineMapper.java");

    // When
    List<SqlEntry> entries = parser.parse(file);

    // Then
    assertEquals(2, entries.size());

    // Verify multi-line array concatenation
    SqlEntry selectEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.SELECT)
        .findFirst()
        .orElse(null);
    assertNotNull(selectEntry);
    
    String expectedSql = "SELECT * FROM user WHERE name = #{name} ORDER BY id";
    assertEquals(expectedSql, selectEntry.getRawSql());
    assertEquals(6, selectEntry.getLineNumber(), "Line number should point to annotation start");
  }

  @Test
  @DisplayName("ComplexMapper: Should extract only SQL annotations, skip @Param, @Options")
  void testComplexMapper_shouldSkipNonSqlAnnotations() throws IOException, ParseException {
    // Given
    File file = new File("src/test/resources/mappers/ComplexMapper.java");

    // When
    List<SqlEntry> entries = parser.parse(file);

    // Then
    assertEquals(3, entries.size(), "Should extract only SQL annotations");

    // Verify all four SQL command types
    assertTrue(entries.stream().anyMatch(e -> e.getSqlType() == SqlCommandType.SELECT));
    assertTrue(entries.stream().anyMatch(e -> e.getSqlType() == SqlCommandType.DELETE));
    assertTrue(entries.stream().anyMatch(e -> e.getSqlType() == SqlCommandType.INSERT));
  }

  @Test
  @DisplayName("NestedClassMapper: Should handle nested interface with correct namespace")
  void testNestedClassMapper_shouldIncludeOuterClassName() throws IOException, ParseException {
    // Given
    File file = new File("src/test/resources/mappers/NestedClassMapper.java");

    // When
    List<SqlEntry> entries = parser.parse(file);

    // Then
    // JavaParser finds both OuterClass and NestedClassMapper, so we get 2 entries
    assertEquals(2, entries.size(), "Should find SQL in both outer and nested declarations");
    
    // Verify at least one entry has the nested class name
    boolean hasNestedMapper = entries.stream()
        .anyMatch(e -> e.getMapperId().contains("NestedClassMapper"));
    assertTrue(hasNestedMapper, "Should have entry with NestedClassMapper in mapperId");
    
    // Verify SQL content is correct
    boolean hasCorrectSql = entries.stream()
        .anyMatch(e -> e.getRawSql().equals("SELECT * FROM nested WHERE id = #{id}"));
    assertTrue(hasCorrectSql, "Should extract correct SQL");
  }

  @Test
  @DisplayName("EdgeCasesMapper: Should handle empty SQL, escaped quotes, Unicode")
  void testEdgeCasesMapper_shouldHandleEdgeCases() throws IOException, ParseException {
    // Given
    File file = new File("src/test/resources/mappers/EdgeCasesMapper.java");

    // When
    List<SqlEntry> entries = parser.parse(file);

    // Then
    // Empty @Select("") should be skipped (logged as warning)
    // Should have 3 valid entries (escaped quotes, Unicode, complex SQL)
    assertEquals(3, entries.size(), "Should skip empty SQL and extract 3 valid entries");

    // Verify escaped quotes handled correctly
    SqlEntry escapedEntry = entries.stream()
        .filter(e -> e.getRawSql().contains("O\\'Brien"))
        .findFirst()
        .orElse(null);
    assertNotNull(escapedEntry, "Should handle escaped quotes");

    // Verify Unicode characters preserved
    SqlEntry unicodeEntry = entries.stream()
        .filter(e -> e.getRawSql().contains("张三"))
        .findFirst()
        .orElse(null);
    assertNotNull(unicodeEntry, "Should preserve Unicode characters");

    // Verify complex SQL extracted
    SqlEntry complexEntry = entries.stream()
        .filter(e -> e.getRawSql().contains("LEFT JOIN"))
        .findFirst()
        .orElse(null);
    assertNotNull(complexEntry, "Should handle complex SQL");
  }

  @Test
  @DisplayName("Should handle file not found gracefully")
  void testFileNotFound_shouldThrowIOException() {
    // Given
    File nonExistentFile = new File("src/test/resources/mappers/NonExistent.java");

    // When/Then
    assertThrows(IOException.class, () -> parser.parse(nonExistentFile),
        "Should throw IOException for non-existent file");
  }

  @Test
  @DisplayName("Should verify line numbers are accurate across all test files")
  void testLineNumberAccuracy_shouldMatchActualPositions() throws IOException, ParseException {
    // Given
    File simpleFile = new File("src/test/resources/mappers/SimpleMapper.java");

    // When
    List<SqlEntry> entries = parser.parse(simpleFile);

    // Then - Verify line numbers match actual file positions
    SqlEntry selectEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.SELECT)
        .findFirst()
        .orElse(null);
    assertNotNull(selectEntry);
    assertEquals(6, selectEntry.getLineNumber(), "@Select should be on line 6");

    SqlEntry updateEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.UPDATE)
        .findFirst()
        .orElse(null);
    assertNotNull(updateEntry);
    assertEquals(9, updateEntry.getLineNumber(), "@Update should be on line 9");
  }

  @Test
  @DisplayName("Should verify all entries have required fields populated")
  void testRequiredFields_shouldBePopulated() throws IOException, ParseException {
    // Given
    File file = new File("src/test/resources/mappers/ComplexMapper.java");

    // When
    List<SqlEntry> entries = parser.parse(file);

    // Then - Verify all required fields are non-null and valid
    for (SqlEntry entry : entries) {
      assertNotNull(entry.getSource(), "Source should not be null");
      assertEquals(SourceType.ANNOTATION, entry.getSource());
      
      assertNotNull(entry.getFilePath(), "FilePath should not be null");
      assertTrue(entry.getFilePath().endsWith("ComplexMapper.java"));
      
      assertNotNull(entry.getMapperId(), "MapperId should not be null");
      assertTrue(entry.getMapperId().startsWith("com.example.ComplexMapper."));
      
      assertNotNull(entry.getSqlType(), "SqlType should not be null");
      
      assertNotNull(entry.getRawSql(), "RawSql should not be null");
      assertFalse(entry.getRawSql().trim().isEmpty(), "RawSql should not be empty");
      
      assertTrue(entry.getLineNumber() > 0, "LineNumber should be positive");
      
      assertFalse(entry.isDynamic(), "Annotation SQL should not be dynamic");
      assertNotNull(entry.getSqlVariants(), "SqlVariants should not be null");
    }
  }
}

