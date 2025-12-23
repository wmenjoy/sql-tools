package com.footstone.sqlguard.scanner.parser.impl;

import com.footstone.sqlguard.core.model.SqlCommandType;
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
 * Integration tests for XmlMapperParser with comprehensive test scenarios.
 * Tests edge cases, real-world mappers, and complex XML structures.
 */
@DisplayName("XmlMapperParser Integration Tests")
class XmlMapperParserIntegrationTest {

  private XmlMapperParser parser;

  @BeforeEach
  void setUp() {
    parser = new XmlMapperParser();
  }

  @Test
  @DisplayName("Should parse simple static SQL without dynamic tags")
  void testSimpleStatic_shouldParseCorrectly() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/simple-static.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertEquals(2, entries.size(), "Should extract 2 SQL entries (SELECT + UPDATE)");
    
    // Verify all are static
    for (SqlEntry entry : entries) {
      assertFalse(entry.isDynamic(), "Static SQL should not be dynamic");
      assertTrue(entry.getSqlVariants().isEmpty(), "Static SQL should have empty variants");
    }
    
    // Verify SELECT entry
    SqlEntry selectEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.SELECT)
        .findFirst()
        .orElseThrow(() -> new AssertionError("SELECT not found"));
    assertEquals("com.example.UserMapper.getUserById", selectEntry.getMapperId());
    
    // Verify UPDATE entry
    SqlEntry updateEntry = entries.stream()
        .filter(e -> e.getSqlType() == SqlCommandType.UPDATE)
        .findFirst()
        .orElseThrow(() -> new AssertionError("UPDATE not found"));
    assertEquals("com.example.UserMapper.updateUser", updateEntry.getMapperId());
  }

  @Test
  @DisplayName("Should parse SQL with <if> condition and generate variants")
  void testIfCondition_shouldGenerateVariants() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/if-condition.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertEquals(1, entries.size());
    SqlEntry entry = entries.get(0);
    
    assertTrue(entry.isDynamic(), "SQL with <if> should be dynamic");
    assertFalse(entry.getSqlVariants().isEmpty(), "Should generate variants");
    assertEquals(2, entry.getSqlVariants().size(), "Should generate 2 variants for <if>");
  }

  @Test
  @DisplayName("Should parse SQL with <foreach> and generate variants")
  void testForeachLoop_shouldGenerateVariants() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/foreach-loop.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertEquals(1, entries.size());
    SqlEntry entry = entries.get(0);
    
    assertTrue(entry.isDynamic(), "SQL with <foreach> should be dynamic");
    assertTrue(entry.getSqlVariants().size() >= 2, "Should generate representative variants");
  }

  @Test
  @DisplayName("Should parse SQL with <where> tag")
  void testWhereTag_shouldHandleCorrectly() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/where-tag.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertEquals(1, entries.size());
    SqlEntry entry = entries.get(0);
    
    assertTrue(entry.isDynamic(), "SQL with <where> should be dynamic");
    assertTrue(entry.getRawSql().toUpperCase().contains("SELECT"), "Should contain SQL");
  }

  @Test
  @DisplayName("Should parse SQL with nested dynamic tags")
  void testNestedDynamic_shouldDetectAndLimit() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/nested-dynamic.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertEquals(1, entries.size());
    SqlEntry entry = entries.get(0);
    
    assertTrue(entry.isDynamic(), "Nested dynamic SQL should be marked as dynamic");
    assertTrue(entry.getSqlVariants().size() <= 10, 
        "Variant count should be limited to 10: " + entry.getSqlVariants().size());
  }

  @Test
  @DisplayName("Should parse complex real-world mapper with multiple statements")
  void testComplexRealWorld_shouldParseAll() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/complex-real-world.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertEquals(5, entries.size(), "Should extract all 5 SQL statements");
    
    // Verify all statements parsed
    long selectCount = entries.stream().filter(e -> e.getSqlType() == SqlCommandType.SELECT).count();
    long insertCount = entries.stream().filter(e -> e.getSqlType() == SqlCommandType.INSERT).count();
    long updateCount = entries.stream().filter(e -> e.getSqlType() == SqlCommandType.UPDATE).count();
    long deleteCount = entries.stream().filter(e -> e.getSqlType() == SqlCommandType.DELETE).count();
    
    assertEquals(2, selectCount, "Should have 2 SELECT statements");
    assertEquals(1, insertCount, "Should have 1 INSERT statement");
    assertEquals(1, updateCount, "Should have 1 UPDATE statement");
    assertEquals(1, deleteCount, "Should have 1 DELETE statement");
    
    // Verify line numbers are accurate
    for (SqlEntry entry : entries) {
      assertTrue(entry.getLineNumber() > 0, "Line number should be positive");
    }
    
    // Verify mapperIds are correct
    for (SqlEntry entry : entries) {
      assertTrue(entry.getMapperId().startsWith("com.example.OrderMapper."),
          "MapperId should start with namespace: " + entry.getMapperId());
    }
  }

  @Test
  @DisplayName("Should handle CDATA sections correctly")
  void testCdataSection_shouldParse() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/cdata-section.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertEquals(1, entries.size());
    SqlEntry entry = entries.get(0);
    
    // CDATA content should be extracted
    assertTrue(entry.getRawSql().contains("SELECT"), "Should extract SQL from CDATA");
    assertTrue(entry.getRawSql().contains("<"), "Should preserve < character from CDATA");
    assertFalse(entry.isDynamic(), "CDATA SQL without dynamic tags should not be dynamic");
  }

  @Test
  @DisplayName("Should handle XML comments correctly")
  void testXmlComments_shouldIgnore() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/xml-comments.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertEquals(1, entries.size());
    SqlEntry entry = entries.get(0);
    
    // Comments should not affect parsing
    assertTrue(entry.getRawSql().contains("SELECT"), "Should extract SQL");
    assertEquals("com.example.UserMapper.findUsers", entry.getMapperId());
  }

  @Test
  @DisplayName("Should handle multi-line SQL correctly")
  void testMultilineSQL_shouldPreserve() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/multiline-sql.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertEquals(1, entries.size());
    SqlEntry entry = entries.get(0);
    
    // Multi-line SQL should be extracted
    String sql = entry.getRawSql();
    assertTrue(sql.contains("SELECT"), "Should contain SELECT");
    assertTrue(sql.contains("FROM"), "Should contain FROM");
    assertTrue(sql.contains("WHERE"), "Should contain WHERE");
    assertTrue(sql.contains("ORDER BY"), "Should contain ORDER BY");
  }

  @Test
  @DisplayName("Should handle special characters in SQL")
  void testSpecialCharacters_shouldHandle() throws IOException, ParseException {
    // Given - Create temporary XML with special characters
    File tempFile = File.createTempFile("test-special-", ".xml");
    tempFile.deleteOnExit();
    
    String xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n" +
        "<mapper namespace=\"test\">\n" +
        "    <select id=\"test\" resultType=\"User\">\n" +
        "        <![CDATA[\n" +
        "        SELECT * FROM user WHERE name LIKE '%test%' AND age > 18 AND status <> 'deleted'\n" +
        "        ]]>\n" +
        "    </select>\n" +
        "</mapper>";
    
    java.nio.file.Files.write(tempFile.toPath(), 
        xmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    // When
    List<SqlEntry> entries = parser.parse(tempFile);

    // Then
    assertEquals(1, entries.size());
    SqlEntry entry = entries.get(0);
    
    // Special characters should be preserved
    assertTrue(entry.getRawSql().contains("%"), "Should preserve % character");
    assertTrue(entry.getRawSql().contains(">"), "Should preserve > character");
    assertTrue(entry.getRawSql().contains("<>"), "Should preserve <> operator");
  }

  @Test
  @DisplayName("Should run all XML mapper tests successfully")
  void testAllXmlMappers_shouldPassAll() throws IOException, ParseException {
    // Given - All test mapper files
    String[] mapperFiles = {
        "mappers/simple-static.xml",
        "mappers/if-condition.xml",
        "mappers/foreach-loop.xml",
        "mappers/where-tag.xml",
        "mappers/choose-when.xml",
        "mappers/nested-dynamic.xml",
        "mappers/complex-real-world.xml",
        "mappers/cdata-section.xml",
        "mappers/xml-comments.xml",
        "mappers/multiline-sql.xml"
    };

    int totalEntries = 0;
    int dynamicEntries = 0;
    int staticEntries = 0;

    // When - Parse all mappers
    for (String mapperFile : mapperFiles) {
      File xmlFile = getTestResourceFile(mapperFile);
      List<SqlEntry> entries = parser.parse(xmlFile);
      
      totalEntries += entries.size();
      for (SqlEntry entry : entries) {
        if (entry.isDynamic()) {
          dynamicEntries++;
        } else {
          staticEntries++;
        }
      }
    }

    // Then - Verify overall statistics
    assertTrue(totalEntries >= 15, "Should extract at least 15 SQL entries total");
    assertTrue(dynamicEntries > 0, "Should have some dynamic SQL entries");
    assertTrue(staticEntries > 0, "Should have some static SQL entries");
    
    System.out.println("Total SQL entries parsed: " + totalEntries);
    System.out.println("Dynamic SQL entries: " + dynamicEntries);
    System.out.println("Static SQL entries: " + staticEntries);
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

















