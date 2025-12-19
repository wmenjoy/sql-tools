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
 * Unit tests for AnnotationParser basic parsing functionality.
 * Tests basic annotation extraction, namespace, and line number accuracy.
 */
@DisplayName("AnnotationParser Basic Tests")
class AnnotationParserTest {

  private AnnotationParser parser;
  private File testFile;

  @BeforeEach
  void setUp() {
    parser = new AnnotationParser();
    // Use test resources directory
    testFile = new File("src/test/resources/mappers/SimpleMapper.java");
  }

  @Test
  @DisplayName("Should parse @Select annotation and create SqlEntry with correct metadata")
  void testSelectAnnotation_shouldCreateSqlEntry() throws IOException, ParseException {
    // When
    List<SqlEntry> entries = parser.parse(testFile);

    // Then
    assertNotNull(entries);
    assertFalse(entries.isEmpty());

    // Find the @Select annotation entry
    SqlEntry selectEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.SELECT)
        .findFirst()
        .orElse(null);

    assertNotNull(selectEntry, "Should find @Select annotation");
    assertEquals(SourceType.ANNOTATION, selectEntry.getSource());
    assertEquals("com.example.SimpleMapper.getUserById", selectEntry.getMapperId());
    assertEquals(SqlCommandType.SELECT, selectEntry.getSqlType());
    assertEquals("SELECT * FROM user WHERE id = #{id}", selectEntry.getRawSql());
    assertTrue(selectEntry.getLineNumber() > 0, "Line number should be positive");
    assertFalse(selectEntry.isDynamic(), "Annotation SQL should not be dynamic");
    assertTrue(selectEntry.getSqlVariants().isEmpty(), "Annotation SQL should have no variants");
  }

  @Test
  @DisplayName("Should parse multiple annotations and create multiple SqlEntry instances")
  void testMultipleAnnotations_shouldCreateMultipleEntries() throws IOException, ParseException {
    // When
    List<SqlEntry> entries = parser.parse(testFile);

    // Then
    assertNotNull(entries);
    assertEquals(2, entries.size(), "Should find 2 SQL annotations (@Select and @Update)");

    // Verify @Select entry
    SqlEntry selectEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.SELECT)
        .findFirst()
        .orElse(null);
    assertNotNull(selectEntry);
    assertEquals("com.example.SimpleMapper.getUserById", selectEntry.getMapperId());

    // Verify @Update entry
    SqlEntry updateEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.UPDATE)
        .findFirst()
        .orElse(null);
    assertNotNull(updateEntry);
    assertEquals("com.example.SimpleMapper.updateUser", updateEntry.getMapperId());
    assertEquals("UPDATE user SET name = #{name} WHERE id = #{id}", updateEntry.getRawSql());
  }

  @Test
  @DisplayName("Should extract namespace using fully qualified class name")
  void testNamespaceExtraction_shouldUseClassName() throws IOException, ParseException {
    // When
    List<SqlEntry> entries = parser.parse(testFile);

    // Then
    assertFalse(entries.isEmpty());
    for (SqlEntry entry : entries) {
      assertTrue(entry.getMapperId().startsWith("com.example.SimpleMapper."),
          "MapperId should start with fully qualified class name");
    }
  }

  @Test
  @DisplayName("Should extract accurate line numbers matching Java file positions")
  void testLineNumberExtraction_shouldBeAccurate() throws IOException, ParseException {
    // When
    List<SqlEntry> entries = parser.parse(testFile);

    // Then
    assertFalse(entries.isEmpty());
    
    // @Select annotation is on line 6
    SqlEntry selectEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.SELECT)
        .findFirst()
        .orElse(null);
    assertNotNull(selectEntry);
    assertEquals(6, selectEntry.getLineNumber(), 
        "@Select annotation should be on line 6");

    // @Update annotation is on line 9
    SqlEntry updateEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.UPDATE)
        .findFirst()
        .orElse(null);
    assertNotNull(updateEntry);
    assertEquals(9, updateEntry.getLineNumber(), 
        "@Update annotation should be on line 9");
  }
}











