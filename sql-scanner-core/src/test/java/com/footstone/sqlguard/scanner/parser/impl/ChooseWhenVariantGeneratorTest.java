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
 * Unit tests for XmlMapperParser choose-when variant generation.
 * Tests mutually exclusive branch generation for choose/when/otherwise tags.
 */
@DisplayName("Choose-When Variant Generation Tests")
class ChooseWhenVariantGeneratorTest {

  private XmlMapperParser parser;

  @BeforeEach
  void setUp() {
    parser = new XmlMapperParser();
  }

  @Test
  @DisplayName("Choose with multiple when branches should generate per-branch variants")
  void testChooseMultipleWhen_shouldGeneratePerBranch() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/choose-basic.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertNotNull(entries);
    assertEquals(1, entries.size());

    SqlEntry entry = entries.get(0);
    assertTrue(entry.isDynamic());
    
    List<String> variants = entry.getSqlVariants();
    // 2 when branches + 1 otherwise = 3 variants
    assertEquals(3, variants.size(), "Should generate 3 variants (2 when + 1 otherwise)");

    // Verify each branch is represented
    boolean foundWhen1 = false;
    boolean foundWhen2 = false;
    boolean foundOtherwise = false;

    for (String variant : variants) {
      String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
      
      if (cleanSql.contains("'active'")) {
        foundWhen1 = true;
      } else if (cleanSql.contains("'inactive'")) {
        foundWhen2 = true;
      } else if (cleanSql.contains("'deleted'")) {
        foundOtherwise = true;
      }
    }

    assertTrue(foundWhen1, "Should have variant for first when branch");
    assertTrue(foundWhen2, "Should have variant for second when branch");
    assertTrue(foundOtherwise, "Should have variant for otherwise branch");
  }

  @Test
  @DisplayName("Choose without otherwise should handle gracefully")
  void testChooseNoOtherwise_shouldHandleGracefully() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/choose-no-otherwise.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    SqlEntry entry = entries.get(0);
    List<String> variants = entry.getSqlVariants();
    
    // Should have variants for when branches only (no otherwise)
    assertTrue(variants.size() >= 2, "Should generate variants for when branches");
    
    // Verify when branches are present
    boolean foundWhenBranch = false;
    for (String variant : variants) {
      if (variant.contains("status =")) {
        foundWhenBranch = true;
        break;
      }
    }
    
    assertTrue(foundWhenBranch, "Should have variants for when branches");
  }

  @Test
  @DisplayName("Each choose variant should include only ONE branch")
  void testChooseSingleBranch_shouldBeExclusive() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/choose-basic.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    SqlEntry entry = entries.get(0);
    List<String> variants = entry.getSqlVariants();
    
    // Each variant should have exactly one of the status values
    for (String variant : variants) {
      String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
      
      int branchCount = 0;
      if (cleanSql.contains("'active'")) branchCount++;
      if (cleanSql.contains("'inactive'")) branchCount++;
      if (cleanSql.contains("'deleted'")) branchCount++;
      
      assertEquals(1, branchCount, 
          "Each variant should include exactly ONE branch, but found " + branchCount + " in: " + cleanSql);
    }
  }

  @Test
  @DisplayName("Nested choose should be limited")
  void testNestedChoose_shouldLimit() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/choose-nested.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    SqlEntry entry = entries.get(0);
    List<String> variants = entry.getSqlVariants();
    
    // Should be limited to prevent explosion
    assertTrue(variants.size() <= 10, "Should limit variants to max 10");
  }

  @Test
  @DisplayName("Choose within where tag should combine correctly")
  void testChooseWithinWhere_shouldCombine() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/choose-basic.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    SqlEntry entry = entries.get(0);
    List<String> variants = entry.getSqlVariants();
    
    // All variants should have WHERE keyword (since choose is inside where tag)
    for (String variant : variants) {
      String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
      
      // Should have WHERE and one of the conditions
      assertTrue(cleanSql.contains("WHERE"), "Should have WHERE keyword");
      
      // Should not have "WHERE AND" or "WHERE OR"
      assertFalse(cleanSql.matches(".*WHERE\\s+(AND|OR)\\s+.*"), 
          "Should not have WHERE followed by AND/OR");
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


















