package com.footstone.sqlguard.scanner.parser;

import com.footstone.sqlguard.scanner.model.WrapperUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WrapperScanner interface using mock implementations.
 * Verifies contract behavior including directory traversal and error handling.
 */
@DisplayName("WrapperScanner Interface Tests")
class WrapperScannerInterfaceTest {

  @TempDir
  Path tempDir;

  @Test
  @DisplayName("Should scan project root and return list of WrapperUsage")
  void testScanReturnsListOfWrapperUsage() throws IOException {
    // Given
    WrapperScanner scanner = new MockSuccessfulScanner();
    File projectRoot = tempDir.toFile();

    // When
    List<WrapperUsage> usages = scanner.scan(projectRoot);

    // Then
    assertNotNull(usages);
    assertEquals(3, usages.size());
    assertEquals("QueryWrapper", usages.get(0).getWrapperType());
  }

  @Test
  @DisplayName("Should handle directory traversal with nested directories")
  void testScanHandlesNestedDirectories() throws IOException {
    // Given
    WrapperScanner scanner = new MockNestedDirectoryScanner();
    
    // Create nested directory structure
    Path srcDir = tempDir.resolve("src");
    Path mainDir = srcDir.resolve("main");
    Path javaDir = mainDir.resolve("java");
    Files.createDirectories(javaDir);

    // When
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then - should find usages in nested directories
    assertNotNull(usages);
    assertTrue(usages.size() > 0);
    assertTrue(usages.stream().anyMatch(u -> u.getFilePath().contains("nested")));
  }

  @Test
  @DisplayName("Should handle parse errors gracefully and continue processing")
  void testScanHandlesParseErrorsGracefully() throws IOException {
    // Given
    WrapperScanner scanner = new MockErrorHandlingScanner();
    File projectRoot = tempDir.toFile();

    // When
    List<WrapperUsage> usages = scanner.scan(projectRoot);

    // Then - should return partial results despite errors
    assertNotNull(usages);
    assertEquals(2, usages.size()); // Only successful parses
  }

  @Test
  @DisplayName("Should return empty list when no wrappers found")
  void testScanReturnsEmptyListWhenNoWrappers() throws IOException {
    // Given
    WrapperScanner scanner = new MockEmptyScanner();
    File projectRoot = tempDir.toFile();

    // When
    List<WrapperUsage> usages = scanner.scan(projectRoot);

    // Then
    assertNotNull(usages);
    assertTrue(usages.isEmpty());
  }

  @Test
  @DisplayName("Should throw IOException for invalid project root")
  void testScanThrowsIOExceptionForInvalidRoot() {
    // Given
    WrapperScanner scanner = new MockSuccessfulScanner();
    File invalidRoot = new File("/nonexistent/project/root");

    // When & Then
    IOException exception = assertThrows(IOException.class, () ->
        scanner.scan(invalidRoot));

    assertTrue(exception.getMessage().contains("not found") ||
               exception.getMessage().contains("does not exist"));
  }

  @Test
  @DisplayName("Should detect different wrapper types")
  void testScanDetectsDifferentWrapperTypes() throws IOException {
    // Given
    WrapperScanner scanner = new MockMultipleWrapperTypesScanner();
    File projectRoot = tempDir.toFile();

    // When
    List<WrapperUsage> usages = scanner.scan(projectRoot);

    // Then
    assertNotNull(usages);
    assertEquals(4, usages.size());
    
    // Verify different wrapper types detected
    assertTrue(usages.stream().anyMatch(u -> u.getWrapperType().equals("QueryWrapper")));
    assertTrue(usages.stream().anyMatch(u -> u.getWrapperType().equals("LambdaQueryWrapper")));
    assertTrue(usages.stream().anyMatch(u -> u.getWrapperType().equals("UpdateWrapper")));
    assertTrue(usages.stream().anyMatch(u -> u.getWrapperType().equals("LambdaUpdateWrapper")));
  }

  // Mock Implementations

  /**
   * Mock scanner that successfully scans and returns predefined wrapper usages.
   */
  private static class MockSuccessfulScanner implements WrapperScanner {
    @Override
    public List<WrapperUsage> scan(File projectRoot) throws IOException {
      if (!projectRoot.exists()) {
        throw new IOException("Project root not found: " + projectRoot.getAbsolutePath());
      }

      List<WrapperUsage> usages = new ArrayList<>();
      usages.add(new WrapperUsage(
          projectRoot.getAbsolutePath() + "/UserService.java",
          "getUserList",
          15,
          "QueryWrapper",
          true
      ));
      usages.add(new WrapperUsage(
          projectRoot.getAbsolutePath() + "/UserService.java",
          "updateUser",
          30,
          "UpdateWrapper",
          true
      ));
      usages.add(new WrapperUsage(
          projectRoot.getAbsolutePath() + "/OrderService.java",
          "getOrders",
          20,
          "LambdaQueryWrapper",
          true
      ));
      return usages;
    }
  }

  /**
   * Mock scanner that handles nested directory structures.
   */
  private static class MockNestedDirectoryScanner implements WrapperScanner {
    @Override
    public List<WrapperUsage> scan(File projectRoot) throws IOException {
      if (!projectRoot.exists()) {
        throw new IOException("Project root not found: " + projectRoot.getAbsolutePath());
      }

      List<WrapperUsage> usages = new ArrayList<>();
      usages.add(new WrapperUsage(
          projectRoot.getAbsolutePath() + "/src/main/java/nested/Service.java",
          "queryData",
          25,
          "QueryWrapper",
          true
      ));
      return usages;
    }
  }

  /**
   * Mock scanner that encounters errors but continues processing.
   */
  private static class MockErrorHandlingScanner implements WrapperScanner {
    @Override
    public List<WrapperUsage> scan(File projectRoot) throws IOException {
      if (!projectRoot.exists()) {
        throw new IOException("Project root not found: " + projectRoot.getAbsolutePath());
      }

      List<WrapperUsage> usages = new ArrayList<>();
      
      // Simulate successful parse of first file
      usages.add(new WrapperUsage(
          projectRoot.getAbsolutePath() + "/GoodService.java",
          "method1",
          10,
          "QueryWrapper",
          true
      ));
      
      // Simulate error in second file (logged but not thrown)
      // In real implementation, error would be logged and processing continues
      
      // Simulate successful parse of third file
      usages.add(new WrapperUsage(
          projectRoot.getAbsolutePath() + "/AnotherService.java",
          "method2",
          20,
          "UpdateWrapper",
          true
      ));
      
      return usages;
    }
  }

  /**
   * Mock scanner that returns empty list (no wrappers found).
   */
  private static class MockEmptyScanner implements WrapperScanner {
    @Override
    public List<WrapperUsage> scan(File projectRoot) throws IOException {
      if (!projectRoot.exists()) {
        throw new IOException("Project root not found: " + projectRoot.getAbsolutePath());
      }
      return new ArrayList<>();
    }
  }

  /**
   * Mock scanner that detects multiple wrapper types.
   */
  private static class MockMultipleWrapperTypesScanner implements WrapperScanner {
    @Override
    public List<WrapperUsage> scan(File projectRoot) throws IOException {
      if (!projectRoot.exists()) {
        throw new IOException("Project root not found: " + projectRoot.getAbsolutePath());
      }

      List<WrapperUsage> usages = new ArrayList<>();
      usages.add(new WrapperUsage(
          projectRoot.getAbsolutePath() + "/Service.java",
          "method1",
          10,
          "QueryWrapper",
          true
      ));
      usages.add(new WrapperUsage(
          projectRoot.getAbsolutePath() + "/Service.java",
          "method2",
          20,
          "LambdaQueryWrapper",
          true
      ));
      usages.add(new WrapperUsage(
          projectRoot.getAbsolutePath() + "/Service.java",
          "method3",
          30,
          "UpdateWrapper",
          true
      ));
      usages.add(new WrapperUsage(
          projectRoot.getAbsolutePath() + "/Service.java",
          "method4",
          40,
          "LambdaUpdateWrapper",
          true
      ));
      return usages;
    }
  }
}








