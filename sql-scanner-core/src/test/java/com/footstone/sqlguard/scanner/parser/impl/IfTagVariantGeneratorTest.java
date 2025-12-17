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
 * Unit tests for XmlMapperParser if-tag variant generation.
 * Tests combinatorial if-tag handling with intelligent variant limiting.
 */
@DisplayName("If-Tag Variant Generation Tests")
class IfTagVariantGeneratorTest {

  private XmlMapperParser parser;

  @BeforeEach
  void setUp() {
    parser = new XmlMapperParser();
  }

  @Test
  @DisplayName("Simple if tag should generate two variants (include/exclude)")
  void testSimpleIf_shouldGenerateTwoVariants() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/if-simple.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertNotNull(entries);
    assertEquals(1, entries.size());

    SqlEntry entry = entries.get(0);
    assertTrue(entry.isDynamic(), "Should be marked as dynamic SQL");
    
    List<String> variants = entry.getSqlVariants();
    assertEquals(2, variants.size(), "Should generate 2 variants for single if tag");

    // Verify we have both variants (order may vary)
    boolean foundWithCondition = false;
    boolean foundWithoutCondition = false;
    
    for (String variant : variants) {
      if (variant.contains("WHERE")) {
        foundWithCondition = true;
      } else if (!variant.contains("WHERE")) {
        foundWithoutCondition = true;
      }
    }
    
    assertTrue(foundWithCondition, "Should have variant with WHERE condition");
    assertTrue(foundWithoutCondition, "Should have variant without WHERE condition");
  }

  @Test
  @DisplayName("Multiple independent if tags should generate combinations (2^n)")
  void testMultipleIndependentIf_shouldGenerateCombinations() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/if-multiple.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertNotNull(entries);
    assertEquals(1, entries.size());

    SqlEntry entry = entries.get(0);
    assertTrue(entry.isDynamic());
    
    List<String> variants = entry.getSqlVariants();
    assertEquals(4, variants.size(), "Should generate 4 variants for 2 if tags (2^2)");

    // Verify all combinations are present
    boolean foundBothConditions = false;
    boolean foundOnlyFirst = false;
    boolean foundOnlySecond = false;
    boolean foundNoConditions = false;

    for (String variant : variants) {
      String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
      
      boolean hasName = cleanSql.contains("name =");
      boolean hasStatus = cleanSql.contains("status =");

      if (hasName && hasStatus) {
        foundBothConditions = true;
        assertTrue(cleanSql.contains("WHERE"), "Both conditions should have WHERE");
      } else if (hasName && !hasStatus) {
        foundOnlyFirst = true;
        assertTrue(cleanSql.contains("WHERE"), "Name-only should have WHERE");
      } else if (!hasName && hasStatus) {
        foundOnlySecond = true;
        assertTrue(cleanSql.contains("WHERE"), "Status-only should have WHERE");
      } else {
        foundNoConditions = true;
        assertFalse(cleanSql.contains("WHERE"), "No conditions should not have WHERE");
      }
    }

    assertTrue(foundBothConditions, "Should have variant with both conditions");
    assertTrue(foundOnlyFirst, "Should have variant with only first condition");
    assertTrue(foundOnlySecond, "Should have variant with only second condition");
    assertTrue(foundNoConditions, "Should have variant with no conditions");
  }

  @Test
  @DisplayName("Three if tags should limit to max variants (prevent explosion)")
  void testThreeIf_shouldLimitToMaxVariants() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/if-three.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertNotNull(entries);
    assertEquals(1, entries.size());

    SqlEntry entry = entries.get(0);
    assertTrue(entry.isDynamic());
    
    List<String> variants = entry.getSqlVariants();
    // 3 if tags would generate 8 variants (2^3), but should be limited
    assertTrue(variants.size() <= 10, "Should limit variants to max 10");
    assertEquals(8, variants.size(), "For 3 if tags, should generate 8 variants (2^3)");
  }

  @Test
  @DisplayName("Nested if tags should handle recursively")
  void testNestedIf_shouldHandleRecursively() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/if-nested.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertNotNull(entries);
    assertEquals(1, entries.size());

    SqlEntry entry = entries.get(0);
    assertTrue(entry.isDynamic());
    
    List<String> variants = entry.getSqlVariants();
    assertTrue(variants.size() >= 2, "Should generate at least 2 variants for nested if");
    
    // Verify nested structure is handled
    boolean foundWithOuter = false;
    boolean foundWithoutOuter = false;

    for (String variant : variants) {
      String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
      
      if (cleanSql.contains("name =")) {
        foundWithOuter = true;
      } else if (!cleanSql.contains("name =") && !cleanSql.contains("status =")) {
        foundWithoutOuter = true;
      }
    }

    assertTrue(foundWithOuter, "Should have variant with outer if included");
    assertTrue(foundWithoutOuter, "Should have variant with outer if excluded");
  }

  @Test
  @DisplayName("If within where tag should combine correctly")
  void testIfWithinWhere_shouldCombineCorrectly() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/if-multiple.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertNotNull(entries);
    SqlEntry entry = entries.get(0);
    
    List<String> variants = entry.getSqlVariants();
    
    for (String variant : variants) {
      String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
      
      // If there are conditions, WHERE should be present and properly formatted
      if (cleanSql.contains("name =") || cleanSql.contains("status =")) {
        assertTrue(cleanSql.contains("WHERE"), "Should have WHERE keyword");
        
        // WHERE should not be followed immediately by AND/OR
        assertFalse(cleanSql.matches(".*WHERE\\s+(AND|OR)\\s+.*"), 
            "WHERE should not be followed by AND/OR: " + cleanSql);
      } else {
        // No conditions means no WHERE clause
        assertFalse(cleanSql.contains("WHERE"), "Should not have WHERE when no conditions");
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

