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
 * Unit tests for XmlMapperParser foreach-tag variant generation.
 * Tests empty/single/multiple item scenarios for foreach tags.
 */
@DisplayName("Foreach-Tag Variant Generation Tests")
class ForeachTagVariantGeneratorTest {

  private XmlMapperParser parser;

  @BeforeEach
  void setUp() {
    parser = new XmlMapperParser();
  }

  @Test
  @DisplayName("Foreach with empty collection should remove entire clause")
  void testForeachEmpty_shouldRemoveClause() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/foreach-basic.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertNotNull(entries);
    assertEquals(1, entries.size());

    SqlEntry entry = entries.get(0);
    assertTrue(entry.isDynamic(), "Should be marked as dynamic SQL");
    
    List<String> variants = entry.getSqlVariants();
    assertTrue(variants.size() >= 2, "Should generate at least 2 variants for foreach");

    // Should have variant without the IN clause (empty collection)
    boolean foundEmptyVariant = false;
    for (String variant : variants) {
      String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
      if (!cleanSql.contains("IN") && !cleanSql.contains("(")) {
        foundEmptyVariant = true;
        break;
      }
    }
    
    assertTrue(foundEmptyVariant, "Should have variant with empty collection (no IN clause)");
  }

  @Test
  @DisplayName("Foreach with single item should have no separator")
  void testForeachSingle_shouldNoSeparator() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/foreach-basic.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    SqlEntry entry = entries.get(0);
    List<String> variants = entry.getSqlVariants();

    // Should have variant with single item: WHERE id IN (?)
    boolean foundSingleItem = false;
    for (String variant : variants) {
      String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
      // Single item pattern: IN (?) with no comma
      if (cleanSql.contains("IN") && cleanSql.contains("(?)")) {
        foundSingleItem = true;
        break;
      }
    }
    
    assertTrue(foundSingleItem, "Should have variant with single item IN (?)");
  }

  @Test
  @DisplayName("Foreach with multiple items should use separator")
  void testForeachMultiple_shouldUseSeparator() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/foreach-basic.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    SqlEntry entry = entries.get(0);
    List<String> variants = entry.getSqlVariants();

    // Should have variant with multiple items: WHERE id IN (?, ?, ?)
    boolean foundMultipleItems = false;
    for (String variant : variants) {
      String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
      // Multiple items pattern: IN (?, ?, ?) with commas
      if (cleanSql.contains("IN") && cleanSql.matches(".*IN\\s*\\(\\?\\s*,\\s*\\?.*")) {
        foundMultipleItems = true;
        break;
      }
    }
    
    assertTrue(foundMultipleItems, "Should have variant with multiple items IN (?, ?, ?)");
  }

  @Test
  @DisplayName("Foreach in UPDATE SET clause should handle correctly")
  void testForeachInUpdate_shouldHandleSet() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/foreach-update.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertNotNull(entries);
    assertEquals(1, entries.size());

    SqlEntry entry = entries.get(0);
    assertTrue(entry.isDynamic());
    
    List<String> variants = entry.getSqlVariants();
    assertTrue(variants.size() >= 2, "Should generate variants for foreach in UPDATE");

    // Verify UPDATE statement structure is maintained
    for (String variant : variants) {
      String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
      assertTrue(cleanSql.toUpperCase().startsWith("UPDATE"), 
          "Should maintain UPDATE statement structure");
    }
  }

  @Test
  @DisplayName("Nested foreach should be limited to prevent explosion")
  void testNestedForeach_shouldLimit() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/foreach-nested.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertNotNull(entries);
    assertEquals(1, entries.size());

    SqlEntry entry = entries.get(0);
    assertTrue(entry.isDynamic());
    
    List<String> variants = entry.getSqlVariants();
    // Should be limited to prevent combinatorial explosion
    assertTrue(variants.size() <= 10, "Should limit variants to max 10");
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




