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
 * Unit tests for AnnotationParser annotation type detection.
 * Tests detection of @Select, @Update, @Delete, @Insert and filtering of non-SQL annotations.
 */
@DisplayName("Annotation Type Detection Tests")
class AnnotationTypeDetectionTest {

  private AnnotationParser parser;
  private File testFile;

  @BeforeEach
  void setUp() {
    parser = new AnnotationParser();
    testFile = new File("src/test/resources/mappers/ComplexMapper.java");
  }

  @Test
  @DisplayName("Should detect @Select annotation and map to SqlCommandType.SELECT")
  void testSelectAnnotation_shouldDetectSELECT() throws IOException, ParseException {
    // When
    List<SqlEntry> entries = parser.parse(testFile);

    // Then
    SqlEntry selectEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.SELECT)
        .findFirst()
        .orElse(null);

    assertNotNull(selectEntry, "Should detect @Select annotation");
    assertEquals(SqlCommandType.SELECT, selectEntry.getSqlType());
    assertEquals("SELECT * FROM user WHERE id = #{id}", selectEntry.getRawSql());
    assertEquals("com.example.ComplexMapper.getUserById", selectEntry.getMapperId());
  }

  @Test
  @DisplayName("Should detect @Update annotation and map to SqlCommandType.UPDATE")
  void testUpdateAnnotation_shouldDetectUPDATE() throws IOException, ParseException {
    // Create a test file with @Update
    File updateFile = new File("src/test/resources/mappers/SimpleMapper.java");
    
    // When
    List<SqlEntry> entries = parser.parse(updateFile);

    // Then
    SqlEntry updateEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.UPDATE)
        .findFirst()
        .orElse(null);

    assertNotNull(updateEntry, "Should detect @Update annotation");
    assertEquals(SqlCommandType.UPDATE, updateEntry.getSqlType());
  }

  @Test
  @DisplayName("Should detect @Delete annotation and map to SqlCommandType.DELETE")
  void testDeleteAnnotation_shouldDetectDELETE() throws IOException, ParseException {
    // When
    List<SqlEntry> entries = parser.parse(testFile);

    // Then
    SqlEntry deleteEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.DELETE)
        .findFirst()
        .orElse(null);

    assertNotNull(deleteEntry, "Should detect @Delete annotation");
    assertEquals(SqlCommandType.DELETE, deleteEntry.getSqlType());
    assertEquals("DELETE FROM user WHERE id = #{id}", deleteEntry.getRawSql());
    assertEquals("com.example.ComplexMapper.deleteUser", deleteEntry.getMapperId());
  }

  @Test
  @DisplayName("Should detect @Insert annotation and map to SqlCommandType.INSERT")
  void testInsertAnnotation_shouldDetectINSERT() throws IOException, ParseException {
    // When
    List<SqlEntry> entries = parser.parse(testFile);

    // Then
    SqlEntry insertEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.INSERT)
        .findFirst()
        .orElse(null);

    assertNotNull(insertEntry, "Should detect @Insert annotation");
    assertEquals(SqlCommandType.INSERT, insertEntry.getSqlType());
    assertEquals("INSERT INTO user (name, email) VALUES (#{name}, #{email})", insertEntry.getRawSql());
    assertEquals("com.example.ComplexMapper.insertUser", insertEntry.getMapperId());
  }

  @Test
  @DisplayName("Should skip non-SQL annotations like @Param, @ResultMap, @Options")
  void testNonSqlAnnotation_shouldSkip() throws IOException, ParseException {
    // When
    List<SqlEntry> entries = parser.parse(testFile);

    // Then - Should only have 3 SQL entries (@Select, @Delete, @Insert)
    // @ResultMap, @Param, @Options should not create entries
    assertEquals(3, entries.size(), "Should only extract SQL annotations");
    
    // Verify no entries contain annotation names in their SQL
    for (SqlEntry entry : entries) {
      assertFalse(entry.getRawSql().contains("@Param"));
      assertFalse(entry.getRawSql().contains("@ResultMap"));
      assertFalse(entry.getRawSql().contains("@Options"));
    }
  }

  @Test
  @DisplayName("Should extract all SQL annotations from method with multiple annotations")
  void testMultipleAnnotationsOnMethod_shouldExtractAll() throws IOException, ParseException {
    // When
    List<SqlEntry> entries = parser.parse(testFile);

    // Then - getUserById has both @Select and @ResultMap
    // Should extract only @Select
    long selectCount = entries.stream()
        .filter(e -> e.getMapperId().equals("com.example.ComplexMapper.getUserById"))
        .count();
    
    assertEquals(1, selectCount, "Should extract only SQL annotation from method with multiple annotations");

    // insertUser has both @Insert and @Options
    // Should extract only @Insert
    long insertCount = entries.stream()
        .filter(e -> e.getMapperId().equals("com.example.ComplexMapper.insertUser"))
        .count();
    
    assertEquals(1, insertCount, "Should extract only SQL annotation from method with multiple annotations");
  }
}











