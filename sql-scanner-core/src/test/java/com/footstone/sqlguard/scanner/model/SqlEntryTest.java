package com.footstone.sqlguard.scanner.model;

import com.footstone.sqlguard.core.model.SqlCommandType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SqlEntry data model.
 * Tests creation, validation, equals/hashCode, and SqlVariants manipulation.
 */
@DisplayName("SqlEntry Model Tests")
class SqlEntryTest {

  @Test
  @DisplayName("Should create SqlEntry with all required fields")
  void testCreateWithAllFields() {
    // Given
    SourceType source = SourceType.XML;
    String filePath = "/path/to/mapper.xml";
    String mapperId = "com.example.UserMapper.selectById";
    SqlCommandType sqlType = SqlCommandType.SELECT;
    String rawSql = "SELECT * FROM user WHERE id = #{id}";
    int lineNumber = 42;

    // When
    SqlEntry entry = new SqlEntry(source, filePath, mapperId, sqlType, rawSql, lineNumber);

    // Then
    assertEquals(source, entry.getSource());
    assertEquals(filePath, entry.getFilePath());
    assertEquals(mapperId, entry.getMapperId());
    assertEquals(sqlType, entry.getSqlType());
    assertEquals(rawSql, entry.getRawSql());
    assertEquals(lineNumber, entry.getLineNumber());
    assertFalse(entry.isDynamic()); // Default false
    assertNotNull(entry.getSqlVariants());
    assertTrue(entry.getSqlVariants().isEmpty());
  }

  @Test
  @DisplayName("Should validate source type enum values")
  void testSourceTypeValidation() {
    // Test all valid source types
    assertDoesNotThrow(() -> new SqlEntry(
        SourceType.XML, "/path/file.xml", "mapper.id", 
        SqlCommandType.SELECT, "SELECT 1", 1));
    
    assertDoesNotThrow(() -> new SqlEntry(
        SourceType.ANNOTATION, "/path/File.java", "mapper.id", 
        SqlCommandType.INSERT, "INSERT INTO t VALUES (1)", 1));
    
    assertDoesNotThrow(() -> new SqlEntry(
        SourceType.WRAPPER, "/path/Service.java", "method.name", 
        SqlCommandType.UPDATE, "UPDATE t SET x=1", 1));
  }

  @Test
  @DisplayName("Should toggle dynamic flag")
  void testDynamicFlagToggling() {
    // Given
    SqlEntry entry = new SqlEntry(
        SourceType.XML, "/path/mapper.xml", "mapper.id",
        SqlCommandType.SELECT, "SELECT * FROM user", 10);

    // When - default is false
    assertFalse(entry.isDynamic());

    // When - set to true
    entry.setDynamic(true);

    // Then
    assertTrue(entry.isDynamic());

    // When - set back to false
    entry.setDynamic(false);

    // Then
    assertFalse(entry.isDynamic());
  }

  @Test
  @DisplayName("Should manipulate SqlVariants list")
  void testSqlVariantsManipulation() {
    // Given
    SqlEntry entry = new SqlEntry(
        SourceType.XML, "/path/mapper.xml", "mapper.id",
        SqlCommandType.SELECT, "SELECT * FROM user <where>", 10);

    // When - initially empty
    assertTrue(entry.getSqlVariants().isEmpty());

    // When - add variants
    entry.getSqlVariants().add("SELECT * FROM user WHERE name = ?");
    entry.getSqlVariants().add("SELECT * FROM user WHERE age > ?");

    // Then
    assertEquals(2, entry.getSqlVariants().size());
    assertEquals("SELECT * FROM user WHERE name = ?", entry.getSqlVariants().get(0));
    assertEquals("SELECT * FROM user WHERE age > ?", entry.getSqlVariants().get(1));
  }

  @Test
  @DisplayName("Should implement equals based on filePath and lineNumber")
  void testEqualsBasedOnFilePathAndLineNumber() {
    // Given
    SqlEntry entry1 = new SqlEntry(
        SourceType.XML, "/path/mapper.xml", "mapper.id1",
        SqlCommandType.SELECT, "SELECT * FROM user", 10);

    SqlEntry entry2 = new SqlEntry(
        SourceType.ANNOTATION, "/path/mapper.xml", "mapper.id2",
        SqlCommandType.INSERT, "INSERT INTO user VALUES (1)", 10);

    SqlEntry entry3 = new SqlEntry(
        SourceType.XML, "/path/mapper.xml", "mapper.id1",
        SqlCommandType.SELECT, "SELECT * FROM user", 20);

    SqlEntry entry4 = new SqlEntry(
        SourceType.XML, "/path/other.xml", "mapper.id1",
        SqlCommandType.SELECT, "SELECT * FROM user", 10);

    // Then - same file and line should be equal
    assertEquals(entry1, entry2);

    // Then - different line should not be equal
    assertNotEquals(entry1, entry3);

    // Then - different file should not be equal
    assertNotEquals(entry1, entry4);

    // Then - reflexive
    assertEquals(entry1, entry1);

    // Then - null comparison
    assertNotEquals(null, entry1);
  }

  @Test
  @DisplayName("Should implement hashCode based on filePath and lineNumber")
  void testHashCodeBasedOnFilePathAndLineNumber() {
    // Given
    SqlEntry entry1 = new SqlEntry(
        SourceType.XML, "/path/mapper.xml", "mapper.id1",
        SqlCommandType.SELECT, "SELECT * FROM user", 10);

    SqlEntry entry2 = new SqlEntry(
        SourceType.ANNOTATION, "/path/mapper.xml", "mapper.id2",
        SqlCommandType.INSERT, "INSERT INTO user VALUES (1)", 10);

    SqlEntry entry3 = new SqlEntry(
        SourceType.XML, "/path/mapper.xml", "mapper.id1",
        SqlCommandType.SELECT, "SELECT * FROM user", 20);

    // Then - same file and line should have same hashCode
    assertEquals(entry1.hashCode(), entry2.hashCode());

    // Then - different line should likely have different hashCode
    assertNotEquals(entry1.hashCode(), entry3.hashCode());
  }

  @Test
  @DisplayName("Should throw exception when filePath is null")
  void testValidationFilePathNull() {
    // When & Then
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new SqlEntry(SourceType.XML, null, "mapper.id", 
            SqlCommandType.SELECT, "SELECT 1", 1));

    assertTrue(exception.getMessage().contains("filePath"));
  }

  @Test
  @DisplayName("Should throw exception when filePath is empty")
  void testValidationFilePathEmpty() {
    // When & Then
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new SqlEntry(SourceType.XML, "", "mapper.id", 
            SqlCommandType.SELECT, "SELECT 1", 1));

    assertTrue(exception.getMessage().contains("filePath"));
  }

  @Test
  @DisplayName("Should throw exception when rawSql is null")
  void testValidationRawSqlNull() {
    // When & Then
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new SqlEntry(SourceType.XML, "/path/file.xml", "mapper.id", 
            SqlCommandType.SELECT, null, 1));

    assertTrue(exception.getMessage().contains("rawSql"));
  }

  @Test
  @DisplayName("Should throw exception when rawSql is empty")
  void testValidationRawSqlEmpty() {
    // When & Then
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new SqlEntry(SourceType.XML, "/path/file.xml", "mapper.id", 
            SqlCommandType.SELECT, "   ", 1));

    assertTrue(exception.getMessage().contains("rawSql"));
  }

  @Test
  @DisplayName("Should throw exception when lineNumber is zero")
  void testValidationLineNumberZero() {
    // When & Then
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new SqlEntry(SourceType.XML, "/path/file.xml", "mapper.id", 
            SqlCommandType.SELECT, "SELECT 1", 0));

    assertTrue(exception.getMessage().contains("lineNumber"));
  }

  @Test
  @DisplayName("Should throw exception when lineNumber is negative")
  void testValidationLineNumberNegative() {
    // When & Then
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new SqlEntry(SourceType.XML, "/path/file.xml", "mapper.id", 
            SqlCommandType.SELECT, "SELECT 1", -5));

    assertTrue(exception.getMessage().contains("lineNumber"));
  }

  @Test
  @DisplayName("Should throw exception when source is null")
  void testValidationSourceNull() {
    // When & Then
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new SqlEntry(null, "/path/file.xml", "mapper.id", 
            SqlCommandType.SELECT, "SELECT 1", 1));

    assertTrue(exception.getMessage().contains("source"));
  }

  @Test
  @DisplayName("Should throw exception when mapperId is null")
  void testValidationMapperIdNull() {
    // When & Then
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new SqlEntry(SourceType.XML, "/path/file.xml", null, 
            SqlCommandType.SELECT, "SELECT 1", 1));

    assertTrue(exception.getMessage().contains("mapperId"));
  }

  @Test
  @DisplayName("Should throw exception when sqlType is null")
  void testValidationSqlTypeNull() {
    // When & Then
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new SqlEntry(SourceType.XML, "/path/file.xml", "mapper.id", 
            null, "SELECT 1", 1));

    assertTrue(exception.getMessage().contains("sqlType"));
  }
}













