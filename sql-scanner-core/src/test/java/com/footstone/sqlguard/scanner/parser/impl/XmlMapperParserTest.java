package com.footstone.sqlguard.scanner.parser.impl;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.scanner.model.SourceType;
import com.footstone.sqlguard.scanner.model.SqlEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XmlMapperParser basic XML parsing functionality.
 * Tests static SQL extraction, namespace handling, and line number accuracy.
 */
@DisplayName("XmlMapperParser Basic Parsing Tests")
class XmlMapperParserTest {

  private XmlMapperParser parser;

  @BeforeEach
  void setUp() {
    parser = new XmlMapperParser();
  }

  @Test
  @DisplayName("Should create SqlEntry from simple SELECT statement")
  void testSimpleSelect_shouldCreateSqlEntry() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/simple-static.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertNotNull(entries);
    assertFalse(entries.isEmpty());

    // Find the SELECT entry
    SqlEntry selectEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.SELECT)
        .findFirst()
        .orElseThrow(() -> new AssertionError("SELECT entry not found"));

    assertEquals(SourceType.XML, selectEntry.getSource());
    assertEquals("com.example.UserMapper.getUserById", selectEntry.getMapperId());
    assertEquals(SqlCommandType.SELECT, selectEntry.getSqlType());
    assertTrue(selectEntry.getRawSql().contains("SELECT * FROM user WHERE id = #{id}"));
    assertTrue(selectEntry.getLineNumber() > 0);
    assertFalse(selectEntry.isDynamic());
    assertTrue(selectEntry.getSqlVariants().isEmpty());
  }

  @Test
  @DisplayName("Should create multiple SqlEntry instances from multiple statements")
  void testMultipleStatements_shouldCreateMultipleEntries() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/simple-static.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertEquals(2, entries.size(), "Should extract both SELECT and UPDATE statements");

    // Verify SELECT entry
    SqlEntry selectEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.SELECT)
        .findFirst()
        .orElseThrow(() -> new AssertionError("SELECT entry not found"));
    assertEquals("com.example.UserMapper.getUserById", selectEntry.getMapperId());

    // Verify UPDATE entry
    SqlEntry updateEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.UPDATE)
        .findFirst()
        .orElseThrow(() -> new AssertionError("UPDATE entry not found"));
    assertEquals("com.example.UserMapper.updateUser", updateEntry.getMapperId());
    assertTrue(updateEntry.getRawSql().contains("UPDATE user SET name = #{name}"));
  }

  @Test
  @DisplayName("Should extract namespace and use as mapperId prefix")
  void testNamespaceExtraction_shouldPrefixMapperId() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/simple-static.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    for (SqlEntry entry : entries) {
      assertTrue(entry.getMapperId().startsWith("com.example.UserMapper."),
          "MapperId should start with namespace: " + entry.getMapperId());
    }
  }

  @Test
  @DisplayName("Should extract accurate line numbers from XML file")
  void testLineNumberExtraction_shouldBeAccurate() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/simple-static.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    for (SqlEntry entry : entries) {
      assertTrue(entry.getLineNumber() > 0, "Line number should be positive");
      // SELECT statement should be around line 5, UPDATE around line 8
      assertTrue(entry.getLineNumber() >= 4 && entry.getLineNumber() <= 10,
          "Line number should be within expected range: " + entry.getLineNumber());
    }

    // Verify SELECT comes before UPDATE in file
    SqlEntry selectEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.SELECT)
        .findFirst()
        .orElseThrow(() -> new AssertionError("SELECT entry not found"));
    SqlEntry updateEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.UPDATE)
        .findFirst()
        .orElseThrow(() -> new AssertionError("UPDATE entry not found"));

    assertTrue(selectEntry.getLineNumber() < updateEntry.getLineNumber(),
        "SELECT should appear before UPDATE in file");
  }

  @Test
  @DisplayName("Should handle missing namespace gracefully")
  void testMissingNamespace_shouldUseUnknownPrefix() throws IOException, ParseException {
    // Given - Create a temporary XML file without namespace
    File tempFile = File.createTempFile("test-mapper-", ".xml");
    tempFile.deleteOnExit();
    String xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n" +
        "<mapper>\n" +
        "    <select id=\"testSelect\" resultType=\"User\">\n" +
        "        SELECT * FROM user\n" +
        "    </select>\n" +
        "</mapper>";
    java.nio.file.Files.write(tempFile.toPath(), xmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    // When
    List<SqlEntry> entries = parser.parse(tempFile);

    // Then
    assertFalse(entries.isEmpty());
    SqlEntry entry = entries.get(0);
    assertTrue(entry.getMapperId().startsWith("unknown."),
        "MapperId should use 'unknown' prefix when namespace is missing: " + entry.getMapperId());
  }

  @Test
  @DisplayName("Should throw IOException for non-existent file")
  void testNonExistentFile_shouldThrowIOException() {
    // Given
    File nonExistentFile = new File("/path/to/nonexistent/file.xml");

    // When & Then
    assertThrows(IOException.class, () -> parser.parse(nonExistentFile));
  }

  @Test
  @DisplayName("Should throw ParseException for malformed XML")
  void testMalformedXml_shouldThrowParseException() throws IOException {
    // Given - Create a malformed XML file
    File tempFile = File.createTempFile("malformed-", ".xml");
    tempFile.deleteOnExit();
    String malformedXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<mapper namespace=\"test\">\n" +
        "    <select id=\"test\">SELECT * FROM user</select\n" + // Missing closing >
        "</mapper>";
    java.nio.file.Files.write(tempFile.toPath(), malformedXml.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    // When & Then
    assertThrows(ParseException.class, () -> parser.parse(tempFile));
  }

  /**
   * Helper method to get test resource file.
   */
  private File getTestResourceFile(String resourcePath) {
    URL resource = getClass().getClassLoader().getResource(resourcePath);
    assertNotNull(resource, "Test resource not found: " + resourcePath);
    return new File(resource.getFile());
  }
}

