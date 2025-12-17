package com.footstone.sqlguard.scanner.model;

import com.footstone.sqlguard.config.SqlGuardConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScanContext data model.
 * Tests construction, validation, and immutability.
 */
@DisplayName("ScanContext Model Tests")
class ScanContextTest {

  @TempDir
  Path tempDir;

  @Test
  @DisplayName("Should construct with valid projectPath and config")
  void testConstructionWithValidParameters() {
    // Given
    SqlGuardConfig config = new SqlGuardConfig();
    config.setEnabled(true);

    // When
    ScanContext context = new ScanContext(tempDir, config);

    // Then
    assertNotNull(context);
    assertEquals(tempDir, context.getProjectPath());
    assertEquals(config, context.getConfig());
  }

  @Test
  @DisplayName("Should throw exception when projectPath is null")
  void testNullProjectPath() {
    // Given
    SqlGuardConfig config = new SqlGuardConfig();
    config.setEnabled(true);

    // When & Then
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new ScanContext(null, config));

    assertTrue(exception.getMessage().contains("projectPath"));
  }

  @Test
  @DisplayName("Should throw exception when config is null")
  void testNullConfig() {
    // When & Then
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new ScanContext(tempDir, null));

    assertTrue(exception.getMessage().contains("config"));
  }

  @Test
  @DisplayName("Should be immutable - projectPath cannot be changed")
  void testImmutabilityProjectPath() {
    // Given
    SqlGuardConfig config = new SqlGuardConfig();
    config.setEnabled(true);
    ScanContext context = new ScanContext(tempDir, config);

    // When
    Path originalPath = context.getProjectPath();

    // Then - getter should return same instance
    assertSame(originalPath, context.getProjectPath());
  }

  @Test
  @DisplayName("Should be immutable - config cannot be changed")
  void testImmutabilityConfig() {
    // Given
    SqlGuardConfig config = new SqlGuardConfig();
    config.setEnabled(true);
    ScanContext context = new ScanContext(tempDir, config);

    // When
    SqlGuardConfig originalConfig = context.getConfig();

    // Then - getter should return same instance
    assertSame(originalConfig, context.getConfig());
  }

  @Test
  @DisplayName("Should verify fields are final through reflection")
  void testFieldsAreFinal() throws NoSuchFieldException {
    // When
    java.lang.reflect.Field projectPathField = ScanContext.class.getDeclaredField("projectPath");
    java.lang.reflect.Field configField = ScanContext.class.getDeclaredField("config");

    // Then
    assertTrue(java.lang.reflect.Modifier.isFinal(projectPathField.getModifiers()),
        "projectPath field should be final");
    assertTrue(java.lang.reflect.Modifier.isFinal(configField.getModifiers()),
        "config field should be final");
  }

  @Test
  @DisplayName("Should accept absolute path")
  void testAbsolutePath() {
    // Given
    SqlGuardConfig config = new SqlGuardConfig();
    config.setEnabled(true);
    Path absolutePath = tempDir.toAbsolutePath();

    // When
    ScanContext context = new ScanContext(absolutePath, config);

    // Then
    assertEquals(absolutePath, context.getProjectPath());
    assertTrue(context.getProjectPath().isAbsolute());
  }

  @Test
  @DisplayName("Should accept relative path")
  void testRelativePath() {
    // Given
    SqlGuardConfig config = new SqlGuardConfig();
    config.setEnabled(true);
    Path relativePath = java.nio.file.Paths.get("src/main/java");

    // When
    ScanContext context = new ScanContext(relativePath, config);

    // Then
    assertEquals(relativePath, context.getProjectPath());
  }

  @Test
  @DisplayName("Should work with different config settings")
  void testDifferentConfigSettings() {
    // Given
    SqlGuardConfig config1 = new SqlGuardConfig();
    config1.setEnabled(true);

    SqlGuardConfig config2 = new SqlGuardConfig();
    config2.setEnabled(false);

    // When
    ScanContext context1 = new ScanContext(tempDir, config1);
    ScanContext context2 = new ScanContext(tempDir, config2);

    // Then
    assertNotSame(context1.getConfig(), context2.getConfig());
    assertTrue(context1.getConfig().isEnabled());
    assertFalse(context2.getConfig().isEnabled());
  }
}

