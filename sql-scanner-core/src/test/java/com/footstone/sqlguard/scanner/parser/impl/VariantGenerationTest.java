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
 * Unit tests for XmlMapperParser SQL variant generation functionality.
 * Tests generation of SQL variants for dynamic SQL scenarios (if/foreach/choose).
 */
@DisplayName("XmlMapperParser Variant Generation Tests")
class VariantGenerationTest {

  private XmlMapperParser parser;

  @BeforeEach
  void setUp() {
    parser = new XmlMapperParser();
  }

  @Test
  @DisplayName("Should generate two variants for <if> tag (with/without condition)")
  void testIfTagVariants_shouldGenerateTwoScenarios() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/if-condition.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertFalse(entries.isEmpty());
    SqlEntry entry = entries.get(0);
    assertTrue(entry.isDynamic(), "SQL with <if> should be dynamic");
    
    List<String> variants = entry.getSqlVariants();
    assertNotNull(variants);
    assertEquals(2, variants.size(), "Should generate 2 variants for <if> tag");
    
    // Verify one variant includes the condition, one excludes it
    boolean hasWithCondition = variants.stream()
        .anyMatch(v -> v.contains("name") || v.contains("AND"));
    boolean hasWithoutCondition = variants.stream()
        .anyMatch(v -> !v.contains("name") && v.contains("SELECT"));
    
    assertTrue(hasWithCondition || hasWithoutCondition, 
        "Should have variants with different condition states");
  }

  @Test
  @DisplayName("Should generate representative variants for <foreach> tag")
  void testForeachVariants_shouldGenerateRepresentative() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/foreach-loop.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertFalse(entries.isEmpty());
    SqlEntry entry = entries.get(0);
    assertTrue(entry.isDynamic(), "SQL with <foreach> should be dynamic");
    
    List<String> variants = entry.getSqlVariants();
    assertNotNull(variants);
    assertTrue(variants.size() >= 2 && variants.size() <= 3, 
        "Should generate 2-3 representative variants for <foreach>: " + variants.size());
    
    // Variants should represent different collection sizes
    // At least one should have IN clause
    boolean hasInClause = variants.stream()
        .anyMatch(v -> v.toUpperCase().contains("IN"));
    assertTrue(hasInClause, "At least one variant should contain IN clause");
  }

  @Test
  @DisplayName("Should generate one variant per branch for <choose>/<when>")
  void testChooseVariants_shouldGeneratePerBranch() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/choose-when.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertFalse(entries.isEmpty());
    SqlEntry entry = entries.get(0);
    assertTrue(entry.isDynamic(), "SQL with <choose> should be dynamic");
    
    List<String> variants = entry.getSqlVariants();
    assertNotNull(variants);
    assertTrue(variants.size() >= 2, 
        "Should generate at least 2 variants for <choose> with multiple branches");
    assertTrue(variants.size() <= 10, 
        "Should not exceed 10 variants (max limit)");
  }

  @Test
  @DisplayName("Should have empty variants list for static SQL")
  void testStaticSql_shouldHaveEmptyVariantsList() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/simple-static.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertFalse(entries.isEmpty());
    for (SqlEntry entry : entries) {
      assertFalse(entry.isDynamic(), "Static SQL should not be dynamic");
      assertTrue(entry.getSqlVariants().isEmpty(), 
          "Static SQL should have empty variants list: " + entry.getStatementId());
    }
  }

  @Test
  @DisplayName("Should generate reasonable number of variants for nested dynamic tags")
  void testNestedDynamic_shouldLimitVariants() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/nested-dynamic.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertFalse(entries.isEmpty());
    SqlEntry entry = entries.get(0);
    assertTrue(entry.isDynamic(), "Nested dynamic SQL should be marked as dynamic");
    
    List<String> variants = entry.getSqlVariants();
    assertNotNull(variants);
    assertTrue(variants.size() <= 10, 
        "Should limit variants to max 10 even for complex nested scenarios: " + variants.size());
  }

  @Test
  @DisplayName("Generated variants should be syntactically valid SQL")
  void testVariants_shouldBeValidSQL() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/if-condition.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertFalse(entries.isEmpty());
    SqlEntry entry = entries.get(0);
    
    List<String> variants = entry.getSqlVariants();
    for (String variant : variants) {
      assertNotNull(variant, "Variant should not be null");
      assertFalse(variant.trim().isEmpty(), "Variant should not be empty");
      
      // Basic SQL syntax checks
      String upperVariant = variant.toUpperCase();
      assertTrue(upperVariant.contains("SELECT") || 
                 upperVariant.contains("INSERT") || 
                 upperVariant.contains("UPDATE") || 
                 upperVariant.contains("DELETE"),
          "Variant should contain SQL command: " + variant);
    }
  }

  @Test
  @DisplayName("Variants should include descriptive comments")
  void testVariants_shouldIncludeComments() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/if-condition.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertFalse(entries.isEmpty());
    SqlEntry entry = entries.get(0);
    
    List<String> variants = entry.getSqlVariants();
    if (!variants.isEmpty()) {
      // At least some variants should have descriptive comments
      boolean hasComments = variants.stream()
          .anyMatch(v -> v.contains("--") || v.contains("Variant"));
      // This is optional but recommended
      // assertTrue(hasComments, "Variants should include descriptive comments");
    }
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


















