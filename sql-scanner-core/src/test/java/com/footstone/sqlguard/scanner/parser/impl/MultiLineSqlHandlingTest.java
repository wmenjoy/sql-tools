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
 * Unit tests for AnnotationParser multi-line SQL string handling.
 * Tests single-line strings, multi-line arrays, value attributes, and mixed parameters.
 */
@DisplayName("Multi-Line SQL Handling Tests")
class MultiLineSqlHandlingTest {

  private AnnotationParser parser;
  private File testFile;

  @BeforeEach
  void setUp() {
    parser = new AnnotationParser();
    testFile = new File("src/test/resources/mappers/MultiLineMapper.java");
  }

  @Test
  @DisplayName("Should extract single-line string SQL correctly")
  void testSingleLineString_shouldExtract() throws IOException, ParseException {
    // When
    List<SqlEntry> entries = parser.parse(testFile);

    // Then - Find the @Update annotation with single-line SQL
    SqlEntry updateEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.UPDATE)
        .findFirst()
        .orElse(null);

    assertNotNull(updateEntry, "Should find @Update annotation");
    assertEquals("UPDATE user SET name = #{name}", updateEntry.getRawSql());
    assertEquals("com.example.MultiLineMapper.updateUserName", updateEntry.getMapperId());
  }

  @Test
  @DisplayName("Should concatenate multi-line array SQL to single SQL string")
  void testMultiLineArray_shouldConcatenate() throws IOException, ParseException {
    // When
    List<SqlEntry> entries = parser.parse(testFile);

    // Then - Find the @Select annotation with multi-line array
    SqlEntry selectEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.SELECT)
        .findFirst()
        .orElse(null);

    assertNotNull(selectEntry, "Should find @Select annotation");
    
    // Verify SQL is concatenated with spaces
    String expectedSql = "SELECT * FROM user WHERE name = #{name} ORDER BY id";
    assertEquals(expectedSql, selectEntry.getRawSql());
    assertEquals("com.example.MultiLineMapper.findByName", selectEntry.getMapperId());
  }

  @Test
  @DisplayName("Should extract SQL from value attribute")
  void testValueAttribute_shouldExtract() throws IOException, ParseException {
    // When
    List<SqlEntry> entries = parser.parse(testFile);

    // Then - @Update uses value="..." attribute
    SqlEntry updateEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.UPDATE)
        .findFirst()
        .orElse(null);

    assertNotNull(updateEntry);
    assertEquals("UPDATE user SET name = #{name}", updateEntry.getRawSql());
  }

  @Test
  @DisplayName("Should extract only SQL value from mixed parameters")
  void testMixedParameters_shouldExtractValue() throws IOException, ParseException {
    // When
    List<SqlEntry> entries = parser.parse(testFile);

    // Then - @Update has both value and timeout parameters
    SqlEntry updateEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.UPDATE)
        .findFirst()
        .orElse(null);

    assertNotNull(updateEntry);
    // Should extract only SQL, not timeout parameter
    assertEquals("UPDATE user SET name = #{name}", updateEntry.getRawSql());
    // Should not contain "timeout" in SQL
    assertFalse(updateEntry.getRawSql().contains("timeout"));
  }
}






