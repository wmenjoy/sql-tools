package com.footstone.sqlguard.scanner.parser.impl;

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
 * Unit tests for XmlMapperParser where-tag smart handling.
 * Tests WHERE keyword insertion and AND/OR removal logic.
 */
@DisplayName("Where-Tag Smart Handling Tests")
class WhereTagVariantGeneratorTest {

  private XmlMapperParser parser;

  @BeforeEach
  void setUp() {
    parser = new XmlMapperParser();
  }

  @Test
  @DisplayName("Where with content should add WHERE keyword and remove leading AND")
  void testWhereWithContent_shouldAddWhereKeyword() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/where-simple.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertNotNull(entries);
    assertEquals(1, entries.size());

    SqlEntry entry = entries.get(0);
    assertTrue(entry.isDynamic());
    
    List<String> variants = entry.getSqlVariants();
    
    // Find variant with condition
    boolean foundCorrectWhere = false;
    for (String variant : variants) {
      String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
      if (cleanSql.contains("id =")) {
        // Should have WHERE keyword
        assertTrue(cleanSql.contains("WHERE"), "Should add WHERE keyword");
        // Should not have "WHERE AND"
        assertFalse(cleanSql.matches(".*WHERE\\s+AND\\s+.*"), 
            "Should remove leading AND after WHERE");
        foundCorrectWhere = true;
      }
    }
    
    assertTrue(foundCorrectWhere, "Should have variant with WHERE condition");
  }

  @Test
  @DisplayName("Where without content should be removed entirely")
  void testWhereWithoutContent_shouldRemoveEntirely() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/where-simple.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    SqlEntry entry = entries.get(0);
    List<String> variants = entry.getSqlVariants();
    
    // Find variant without conditions
    boolean foundNoWhere = false;
    for (String variant : variants) {
      String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
      if (!cleanSql.contains("id =")) {
        // Should not have WHERE keyword
        assertFalse(cleanSql.contains("WHERE"), "Should remove WHERE when no content");
        foundNoWhere = true;
      }
    }
    
    assertTrue(foundNoWhere, "Should have variant without WHERE");
  }

  @Test
  @DisplayName("Where with multiple conditions should remove only leading AND/OR")
  void testWhereMultipleConditions_shouldRemoveOnlyLeadingAndOr() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/where-multiple.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    SqlEntry entry = entries.get(0);
    List<String> variants = entry.getSqlVariants();
    
    // Find variant with both conditions
    for (String variant : variants) {
      String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
      if (cleanSql.contains("name =") && cleanSql.contains("status =")) {
        // Should have WHERE keyword
        assertTrue(cleanSql.contains("WHERE"));
        // Should not start with WHERE AND
        assertFalse(cleanSql.matches(".*WHERE\\s+AND\\s+name.*"));
        // Should have AND between conditions
        assertTrue(cleanSql.contains("AND"), "Should keep internal AND between conditions");
      }
    }
  }

  @Test
  @DisplayName("Where with OR should handle correctly")
  void testWhereWithOr_shouldHandleCorrectly() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/where-or.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    SqlEntry entry = entries.get(0);
    List<String> variants = entry.getSqlVariants();
    
    // Find variant with condition
    for (String variant : variants) {
      String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
      if (cleanSql.contains("status =")) {
        // Should have WHERE keyword
        assertTrue(cleanSql.contains("WHERE"));
        // Should not have "WHERE OR"
        assertFalse(cleanSql.matches(".*WHERE\\s+OR\\s+.*"), 
            "Should remove leading OR after WHERE");
      }
    }
  }

  @Test
  @DisplayName("Nested where with if should combine correctly")
  void testNestedWhereIf_shouldCombine() throws IOException, ParseException {
    // Given - reuse if-multiple.xml which has where with nested if tags
    File xmlFile = getTestResourceFile("mappers/if-multiple.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    SqlEntry entry = entries.get(0);
    List<String> variants = entry.getSqlVariants();
    
    // All variants should have correct WHERE handling
    for (String variant : variants) {
      String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
      
      if (cleanSql.contains("name =") || cleanSql.contains("status =")) {
        // Has conditions - should have WHERE
        assertTrue(cleanSql.contains("WHERE"), "Should have WHERE with conditions");
        // Should not have WHERE AND/OR
        assertFalse(cleanSql.matches(".*WHERE\\s+(AND|OR)\\s+.*"), 
            "Should not have WHERE followed by AND/OR");
      } else {
        // No conditions - should not have WHERE
        assertFalse(cleanSql.contains("WHERE"), "Should not have WHERE without conditions");
      }
    }
  }

  /**
   * Helper method to get test resource file.
   */
  private File getTestResourceFile(String resourcePath) {
    URL resource = getClass().getClassLoader().getResource(resourcePath);
    if (resource == null) {
      throw new IllegalArgumentException("Test resource not found: " + resourcePath);
    }
    return new File(resource.getFile());
  }
}



















