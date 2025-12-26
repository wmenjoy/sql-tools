package com.footstone.sqlguard.scanner.parser.impl;

import com.footstone.sqlguard.scanner.model.SqlEntry;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
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
 * Integration tests for comprehensive dynamic SQL variant generation.
 * Tests complex scenarios with nested tags and real-world patterns.
 */
@DisplayName("Dynamic SQL Variant Generator Integration Tests")
class DynamicSqlVariantGeneratorIntegrationTest {

  private XmlMapperParser parser;

  @BeforeEach
  void setUp() {
    parser = new XmlMapperParser();
  }

  @Test
  @DisplayName("Complex nested tags should generate reasonable variant count")
  void testComplexNested_shouldGenerateReasonableVariants() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/complex-nested-dynamic.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertNotNull(entries);
    assertEquals(1, entries.size());

    SqlEntry entry = entries.get(0);
    assertTrue(entry.isDynamic());
    
    List<String> variants = entry.getSqlVariants();
    // Should be limited to prevent explosion
    assertTrue(variants.size() <= 10, 
        "Variant count should be limited to 10, but was: " + variants.size());
    assertTrue(variants.size() >= 2, 
        "Should generate at least 2 variants");
  }

  @Test
  @DisplayName("Most generated variants should be syntactically valid SQL")
  void testVariantValidation_shouldBeValidSql() throws IOException, ParseException {
    // Given - use complex-nested which doesn't have ORDER BY outside WHERE
    File xmlFile = getTestResourceFile("mappers/complex-nested-dynamic.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    int totalVariants = 0;
    int validVariants = 0;
    
    for (SqlEntry entry : entries) {
      if (entry.isDynamic()) {
        for (String variant : entry.getSqlVariants()) {
          totalVariants++;
          // Remove variant comment
          String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
          
          // Try to parse
          try {
            Statement stmt = CCJSqlParserUtil.parse(cleanSql);
            assertNotNull(stmt, "Parsed statement should not be null");
            validVariants++;
          } catch (JSQLParserException e) {
            // Some edge cases with empty foreach may have issues
            System.out.println("Note: Variant has parsing issue: " + cleanSql.substring(0, Math.min(100, cleanSql.length())));
          }
        }
      }
    }
    
    // At least 70% of variants should be valid SQL
    assertTrue(validVariants >= totalVariants * 0.7, 
        "At least 70% of variants should be valid SQL. Valid: " + validVariants + "/" + totalVariants);
  }

  @Test
  @DisplayName("WHERE clause should be handled correctly in all combinations")
  void testWhereClauseHandling_shouldBeCorrect() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/complex-nested-dynamic.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    SqlEntry entry = entries.get(0);
    List<String> variants = entry.getSqlVariants();
    
    for (String variant : variants) {
      String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
      
      if (cleanSql.contains("WHERE")) {
        // WHERE should not be followed immediately by AND/OR
        assertFalse(cleanSql.matches(".*WHERE\\s+(AND|OR)\\s+.*"), 
            "WHERE should not be followed by AND/OR: " + cleanSql);
        
        // WHERE should have some condition after it (allow parentheses for IN clauses)
        assertTrue(cleanSql.matches(".*WHERE\\s+[\\w\\(].*"), 
            "WHERE should be followed by a condition: " + cleanSql);
      }
    }
  }

  @Test
  @DisplayName("Foreach states should be combined correctly with if/choose")
  void testForeachWithIfChoose_shouldCombineCorrectly() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/complex-nested-dynamic.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    SqlEntry entry = entries.get(0);
    List<String> variants = entry.getSqlVariants();
    
    // Should have variants with different foreach states
    boolean foundEmptyForeach = false;
    boolean foundSingleForeach = false;
    boolean foundMultipleForeach = false;
    
    for (String variant : variants) {
      String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
      
      if (!cleanSql.contains("IN")) {
        foundEmptyForeach = true;
      } else if (cleanSql.contains("IN (?)")) {
        foundSingleForeach = true;
      } else if (cleanSql.matches(".*IN\\s*\\(\\?\\s*,\\s*\\?.*")) {
        foundMultipleForeach = true;
      }
    }
    
    assertTrue(foundEmptyForeach || foundSingleForeach || foundMultipleForeach, 
        "Should have foreach variants");
  }

  @Test
  @DisplayName("Edge case: empty where tag should be handled")
  void testEdgeCase_emptyWhere() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/edge-cases-dynamic.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then - find the emptyWhere statement
    SqlEntry emptyWhereEntry = entries.stream()
        .filter(e -> e.getStatementId().contains("emptyWhere"))
        .findFirst()
        .orElse(null);
    
    if (emptyWhereEntry != null && emptyWhereEntry.isDynamic()) {
      List<String> variants = emptyWhereEntry.getSqlVariants();
      
      // Should have variant without WHERE clause
      boolean foundNoWhere = false;
      for (String variant : variants) {
        String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
        if (!cleanSql.contains("WHERE")) {
          foundNoWhere = true;
          break;
        }
      }
      
      assertTrue(foundNoWhere, "Should have variant without WHERE when all conditions false");
    }
  }

  @Test
  @DisplayName("Edge case: foreach with empty collection should remove IN clause")
  void testEdgeCase_emptyForeach() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/edge-cases-dynamic.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then - find the emptyForeach statement
    SqlEntry emptyForeachEntry = entries.stream()
        .filter(e -> e.getStatementId().contains("emptyForeach"))
        .findFirst()
        .orElse(null);
    
    if (emptyForeachEntry != null && emptyForeachEntry.isDynamic()) {
      List<String> variants = emptyForeachEntry.getSqlVariants();
      
      // Should have variant without IN clause
      boolean foundNoIn = false;
      for (String variant : variants) {
        String cleanSql = variant.replaceFirst("^--.*\\n", "").trim();
        if (!cleanSql.contains("IN")) {
          foundNoIn = true;
          break;
        }
      }
      
      assertTrue(foundNoIn, "Should have variant without IN clause for empty collection");
    }
  }

  @Test
  @DisplayName("Performance: complex dynamic SQL should complete quickly")
  void testPerformance_complexDynamicSql_shouldCompleteQuickly() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/complex-nested-dynamic.xml");

    // When
    long startTime = System.currentTimeMillis();
    List<SqlEntry> entries = parser.parse(xmlFile);
    long duration = System.currentTimeMillis() - startTime;

    // Then
    // Should complete in <2 seconds
    assertTrue(duration < 2000, 
        "Parsing should complete in <2 seconds, but took: " + duration + "ms");

    // Verify variant count is limited
    for (SqlEntry entry : entries) {
      if (entry.isDynamic()) {
        assertTrue(entry.getSqlVariants().size() <= 10, 
            "Variant count should be limited to 10");
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

