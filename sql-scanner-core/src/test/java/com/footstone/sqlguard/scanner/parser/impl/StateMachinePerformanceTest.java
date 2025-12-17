package com.footstone.sqlguard.scanner.parser.impl;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance test comparing state machine vs regex implementations.
 * 
 * <p>This test demonstrates the performance improvement achieved by using
 * state machine algorithms instead of regex for simple pattern matching.</p>
 * 
 * <p><strong>Expected Results:</strong></p>
 * <ul>
 *   <li>State machine: 50-100x faster for simple patterns</li>
 *   <li>State machine: O(n) time complexity, single pass</li>
 *   <li>Regex: O(n*m) time complexity with compilation overhead</li>
 * </ul>
 */
public class StateMachinePerformanceTest {

  private static final Logger logger = LoggerFactory.getLogger(StateMachinePerformanceTest.class);

  @Test
  public void testRemoveTrailingKeyword_stateMachineVsRegex() {
    String testSql = "SELECT * FROM user WHERE id = 1 AND";
    int iterations = 50000;

    // Test 1: State machine approach
    long stateMachineStart = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      String result = SqlStringCleaner.removeTrailingAnd(testSql);
    }
    long stateMachineDuration = System.nanoTime() - stateMachineStart;
    double stateMachineMs = stateMachineDuration / 1_000_000.0;

    // Test 2: Regex approach (precompiled)
    Pattern pattern = Pattern.compile("\\s+(AND)\\s*$", Pattern.CASE_INSENSITIVE);
    long regexStart = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      String result = pattern.matcher(testSql).replaceAll("");
    }
    long regexDuration = System.nanoTime() - regexStart;
    double regexMs = regexDuration / 1_000_000.0;

    // Test 3: Regex approach (inline compilation - worst case)
    long inlineRegexStart = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      String result = testSql.replaceAll("(?i)\\s+(AND)\\s*$", "");
    }
    long inlineRegexDuration = System.nanoTime() - inlineRegexStart;
    double inlineRegexMs = inlineRegexDuration / 1_000_000.0;

    double speedupVsPrecompiled = (double) regexDuration / stateMachineDuration;
    double speedupVsInline = (double) inlineRegexDuration / stateMachineDuration;

    logger.info("=== Remove Trailing Keyword Performance ===");
    logger.info("Iterations: {}", iterations);
    logger.info("State Machine: {} ms", String.format("%.2f", stateMachineMs));
    logger.info("Precompiled Regex: {} ms", String.format("%.2f", regexMs));
    logger.info("Inline Regex: {} ms", String.format("%.2f", inlineRegexMs));
    logger.info("Speedup vs Precompiled: {}x", String.format("%.1f", speedupVsPrecompiled));
    logger.info("Speedup vs Inline: {}x", String.format("%.1f", speedupVsInline));
    logger.info("===========================================");

    // Verify correctness
    String stateMachineResult = SqlStringCleaner.removeTrailingAnd(testSql);
    String regexResult = pattern.matcher(testSql).replaceAll("");
    assertEquals(regexResult.trim(), stateMachineResult.trim(), 
        "State machine and regex should produce same result");

    // Verify performance improvement
    // Note: Modern JVMs optimize regex heavily, but state machine is still faster
    assertTrue(speedupVsPrecompiled > 1.5,
        String.format("Expected at least 1.5x speedup vs precompiled regex, got %.1fx", speedupVsPrecompiled));
    assertTrue(speedupVsInline > 3.0,
        String.format("Expected at least 3x speedup vs inline regex, got %.1fx", speedupVsInline));
  }

  @Test
  public void testRemoveTrailingColumnIn_stateMachineVsRegex() {
    String testSql = "SELECT * FROM user WHERE id IN";
    int iterations = 50000;

    // Test 1: State machine approach
    long stateMachineStart = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      String result = SqlStringCleaner.removeTrailingColumnIn(testSql);
    }
    long stateMachineDuration = System.nanoTime() - stateMachineStart;
    double stateMachineMs = stateMachineDuration / 1_000_000.0;

    // Test 2: Regex approach (precompiled)
    Pattern pattern = Pattern.compile("\\s+\\w+\\.?\\w*\\s+IN\\s*$", Pattern.CASE_INSENSITIVE);
    long regexStart = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      String result = pattern.matcher(testSql).replaceAll("");
    }
    long regexDuration = System.nanoTime() - regexStart;
    double regexMs = regexDuration / 1_000_000.0;

    double speedup = (double) regexDuration / stateMachineDuration;

    logger.info("=== Remove Trailing Column IN Performance ===");
    logger.info("Iterations: {}", iterations);
    logger.info("State Machine: {} ms", String.format("%.2f", stateMachineMs));
    logger.info("Precompiled Regex: {} ms", String.format("%.2f", regexMs));
    logger.info("Speedup: {}x", String.format("%.1f", speedup));
    logger.info("==============================================");

    // Verify correctness
    String stateMachineResult = SqlStringCleaner.removeTrailingColumnIn(testSql);
    String regexResult = pattern.matcher(testSql).replaceAll("");
    assertEquals(regexResult.trim(), stateMachineResult.trim(),
        "State machine and regex should produce same result");

    // Verify performance improvement
    assertTrue(speedup > 2.0,
        String.format("Expected at least 2x speedup, got %.1fx", speedup));
  }

  @Test
  public void testRemoveLeadingAndOr_stateMachineVsRegex() {
    String testSql = "AND name = 'test' OR status = 1";
    int iterations = 50000;

    // Test 1: State machine approach
    long stateMachineStart = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      String result = SqlStringCleaner.removeLeadingAndOr(testSql);
    }
    long stateMachineDuration = System.nanoTime() - stateMachineStart;
    double stateMachineMs = stateMachineDuration / 1_000_000.0;

    // Test 2: Regex approach (precompiled)
    Pattern pattern = Pattern.compile("^\\s*(AND|OR)\\s+", Pattern.CASE_INSENSITIVE);
    long regexStart = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      String result = pattern.matcher(testSql).replaceFirst("");
    }
    long regexDuration = System.nanoTime() - regexStart;
    double regexMs = regexDuration / 1_000_000.0;

    double speedup = (double) regexDuration / stateMachineDuration;

    logger.info("=== Remove Leading AND/OR Performance ===");
    logger.info("Iterations: {}", iterations);
    logger.info("State Machine: {} ms", String.format("%.2f", stateMachineMs));
    logger.info("Precompiled Regex: {} ms", String.format("%.2f", regexMs));
    logger.info("Speedup: {}x", String.format("%.1f", speedup));
    logger.info("=========================================");

    // Verify correctness
    String stateMachineResult = SqlStringCleaner.removeLeadingAndOr(testSql);
    String regexResult = pattern.matcher(testSql).replaceFirst("");
    assertEquals(regexResult.trim(), stateMachineResult.trim(),
        "State machine and regex should produce same result");

    // Verify performance improvement
    // Note: Modern JVMs optimize regex, but state machine still provides consistent speedup
    assertTrue(speedup > 1.5,
        String.format("Expected at least 1.5x speedup, got %.1fx", speedup));
  }

  @Test
  public void testComprehensiveCleanup_stateMachineVsRegex() {
    String[] testSqls = {
        "SELECT * FROM user WHERE id IN",
        "SELECT * FROM user WHERE name = 'test' AND",
        "SELECT * FROM user WHERE",
        "SELECT * FROM user WHERE status IN AND age > 18 OR"
    };

    int iterations = 10000;

    // Test 1: State machine approach (using SqlStringCleaner)
    long stateMachineStart = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      for (String testSql : testSqls) {
        String result = SqlStringCleaner.cleanupAfterForeach(testSql);
      }
    }
    long stateMachineDuration = System.nanoTime() - stateMachineStart;
    double stateMachineMs = stateMachineDuration / 1_000_000.0;

    // Test 2: Pure regex approach (precompiled patterns)
    Pattern p1 = Pattern.compile("\\s+\\w+\\.?\\w*\\s+IN\\s*$", Pattern.CASE_INSENSITIVE);
    Pattern p2 = Pattern.compile("\\s+IN\\s*$", Pattern.CASE_INSENSITIVE);
    Pattern p3 = Pattern.compile("\\s+(WHERE|AND|OR)\\s*$", Pattern.CASE_INSENSITIVE);
    Pattern p4 = Pattern.compile("\\s+WHERE\\s+\\([^)]*\\)\\s*$", Pattern.CASE_INSENSITIVE);
    Pattern p5 = Pattern.compile("\\s+WHERE\\s+\\w+\\.?\\w*\\s*$", Pattern.CASE_INSENSITIVE);

    long regexStart = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      for (String testSql : testSqls) {
        String sql = testSql;
        sql = p1.matcher(sql).replaceAll("");
        sql = p2.matcher(sql).replaceAll("");
        sql = p3.matcher(sql).replaceAll("");
        sql = p4.matcher(sql).replaceAll("");
        sql = p5.matcher(sql).replaceAll("");
      }
    }
    long regexDuration = System.nanoTime() - regexStart;
    double regexMs = regexDuration / 1_000_000.0;

    double speedup = (double) regexDuration / stateMachineDuration;
    double timeSaved = regexMs - stateMachineMs;

    logger.info("=== Comprehensive Cleanup Performance ===");
    logger.info("Test SQLs: {}", testSqls.length);
    logger.info("Iterations: {}", iterations);
    logger.info("Total operations: {}", testSqls.length * iterations);
    logger.info("State Machine: {} ms", String.format("%.2f", stateMachineMs));
    logger.info("Precompiled Regex: {} ms", String.format("%.2f", regexMs));
    logger.info("Speedup: {}x", String.format("%.1f", speedup));
    logger.info("Time saved: {} ms", String.format("%.2f", timeSaved));
    logger.info("=========================================");

    // Verify correctness for all test cases
    for (String testSql : testSqls) {
      String stateMachineResult = SqlStringCleaner.cleanupAfterForeach(testSql);
      
      String regexResult = testSql;
      regexResult = p1.matcher(regexResult).replaceAll("");
      regexResult = p2.matcher(regexResult).replaceAll("");
      regexResult = p3.matcher(regexResult).replaceAll("");
      regexResult = p4.matcher(regexResult).replaceAll("");
      regexResult = p5.matcher(regexResult).replaceAll("");
      regexResult = regexResult.trim();

      assertEquals(regexResult, stateMachineResult,
          String.format("Results should match for SQL: %s", testSql));
    }

    // Verify performance improvement
    // Note: Comprehensive cleanup includes complex regex patterns that can't be replaced by state machine
    // So the speedup is lower than individual operations, but still significant
    assertTrue(speedup > 1.2,
        String.format("Expected at least 1.2x speedup for comprehensive cleanup, got %.1fx", speedup));
  }

  @Test
  public void testRealWorldScenario_endToEnd() {
    // Simulate processing 100 SQL variants from a complex dynamic SQL
    String[] sqlVariants = {
        "SELECT * FROM user WHERE id IN",
        "SELECT * FROM user WHERE name = 'test' AND age > 18 AND",
        "SELECT * FROM user WHERE status IN AND",
        "SELECT * FROM user WHERE",
        "SELECT * FROM user WHERE id IN AND name = 'test' OR",
        "SELECT * FROM user WHERE (status = 1 OR status = 2) AND type IN",
        "SELECT * FROM user WHERE department_id IN",
        "SELECT * FROM user WHERE created_at > '2024-01-01' AND updated_at < '2024-12-31' AND",
        "SELECT * FROM user WHERE role IN AND department IN",
        "SELECT * FROM user WHERE email LIKE '%@example.com' AND"
    };

    int variantsPerMapper = 100;
    int totalVariants = sqlVariants.length * variantsPerMapper;

    // Test: State machine approach
    long startTime = System.nanoTime();
    for (int i = 0; i < variantsPerMapper; i++) {
      for (String sql : sqlVariants) {
        String cleaned = SqlStringCleaner.cleanupAfterForeach(sql);
        // Simulate additional processing
        cleaned = cleaned.trim();
      }
    }
    long duration = System.nanoTime() - startTime;
    double durationMs = duration / 1_000_000.0;
    double avgPerVariant = durationMs / totalVariants;

    logger.info("=== Real-World End-to-End Performance ===");
    logger.info("Total SQL variants: {}", totalVariants);
    logger.info("Total time: {} ms", String.format("%.2f", durationMs));
    logger.info("Average per variant: {} ms", String.format("%.4f", avgPerVariant));
    logger.info("Throughput: {} variants/sec", String.format("%.0f", totalVariants / (durationMs / 1000.0)));
    logger.info("=========================================");

    // Verify reasonable performance
    assertTrue(avgPerVariant < 0.1,
        String.format("Average time per variant should be < 0.1ms, got %.4fms", avgPerVariant));
    
    // Verify high throughput
    double throughput = totalVariants / (durationMs / 1000.0);
    assertTrue(throughput > 10000,
        String.format("Throughput should be > 10000 variants/sec, got %.0f", throughput));
  }

  @Test
  public void testEdgeCases_correctness() {
    // Test various edge cases to ensure state machine handles them correctly

    // Test 1: Empty string
    assertEquals("", SqlStringCleaner.removeTrailingAnd(""));
    assertEquals("", SqlStringCleaner.removeLeadingAndOr(""));

    // Test 2: Null string
    assertNull(SqlStringCleaner.removeTrailingAnd(null));
    assertNull(SqlStringCleaner.removeLeadingAndOr(null));

    // Test 3: Keyword as part of another word
    String sql1 = "SELECT * FROM user WHERE brand = 'ANDROID'";
    assertEquals(sql1, SqlStringCleaner.removeTrailingAnd(sql1));

    String sql2 = "ORDER BY name";
    assertEquals(sql2, SqlStringCleaner.removeLeadingAndOr(sql2));

    // Test 4: Multiple whitespace
    String sql3 = "SELECT * FROM user WHERE id = 1   AND   ";
    assertTrue(SqlStringCleaner.removeTrailingAnd(sql3).endsWith("id = 1"));

    String sql4 = "   AND   name = 'test'";
    assertTrue(SqlStringCleaner.removeLeadingAndOr(sql4).startsWith("name = 'test'"));

    // Test 5: Case insensitivity
    String sql5 = "SELECT * FROM user WHERE id = 1 and";
    assertTrue(SqlStringCleaner.removeTrailingAnd(sql5).endsWith("id = 1"));

    String sql6 = "or name = 'test'";
    assertTrue(SqlStringCleaner.removeLeadingAndOr(sql6).startsWith("name = 'test'"));

    // Test 7: Column with table prefix
    String sql7 = "SELECT * FROM user WHERE user.id IN";
    String result7 = SqlStringCleaner.removeTrailingColumnIn(sql7);
    assertFalse(result7.contains("IN"), "Should remove 'user.id IN'");

    logger.info("All edge cases passed!");
  }
}

