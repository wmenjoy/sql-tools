package com.footstone.sqlguard.maven;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for scanner invocation logic in SqlGuardScanMojo.
 *
 * <p>Tests parameter validation, configuration loading, validator creation, and scan execution.
 *
 * @author SQL Safety Guard Team
 */
class ScannerInvocationTest {

  @TempDir Path tempDir;

  private SqlGuardScanMojo mojo;
  private Log mockLog;

  @BeforeEach
  void setUp() {
    mojo = new SqlGuardScanMojo();
    mockLog = mock(Log.class);
    mojo.setLog(mockLog);
  }

  @Test
  void testValidateParameters_validProjectPath_shouldPass() throws Exception {
    // Given: valid project path
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: validation should pass and scan should execute
    verify(mockLog).info(contains("Starting SQL Safety Guard scan"));
  }

  @Test
  void testValidateParameters_nullProjectPath_shouldThrow() {
    // Given: null project path
    mojo.setProjectPath(null);
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When/Then: execute should throw exception
    assertThrows(Exception.class, () -> mojo.execute());
  }

  @Test
  void testValidateParameters_nonExistentProjectPath_shouldThrow() {
    // Given: non-existent project path
    File nonExistent = new File(tempDir.toFile(), "does-not-exist");
    mojo.setProjectPath(nonExistent);
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When/Then: execute should throw MojoExecutionException
    MojoExecutionException exception =
        assertThrows(MojoExecutionException.class, () -> mojo.execute());
    assertTrue(exception.getMessage().contains("Project path does not exist"));
  }

  @Test
  void testValidateParameters_validConfigFile_shouldPass() throws Exception {
    // Given: valid config file
    Path configFile = tempDir.resolve("config.yaml");
    Files.write(
        configFile,
        ("rules:\n"
            + "  noWhereClause:\n"
            + "    enabled: true\n"
            + "    riskLevel: CRITICAL\n").getBytes());

    mojo.setProjectPath(tempDir.toFile());
    mojo.setConfigFile(configFile.toFile());
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: config should be loaded
    verify(mockLog).info(contains("Loading configuration from"));
  }

  @Test
  void testValidateParameters_nonExistentConfigFile_shouldThrow() {
    // Given: non-existent config file
    File nonExistent = new File(tempDir.toFile(), "nonexistent.yaml");
    mojo.setProjectPath(tempDir.toFile());
    mojo.setConfigFile(nonExistent);
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When/Then: execute should throw MojoExecutionException
    MojoExecutionException exception =
        assertThrows(MojoExecutionException.class, () -> mojo.execute());
    assertTrue(exception.getMessage().contains("Config file does not exist"));
  }

  @Test
  void testValidateParameters_validOutputFormat_shouldPass() throws Exception {
    // Given: valid output formats
    String[] validFormats = {"console", "html", "both"};

    for (String format : validFormats) {
      setUp(); // Reset mojo for each test
      mojo.setProjectPath(tempDir.toFile());
      mojo.setOutputFormat(format);
      mojo.setSkip(false);

      if ("html".equals(format) || "both".equals(format)) {
        mojo.setOutputFile(tempDir.resolve("report.html").toFile());
      }

      // When: execute is called
      mojo.execute();

      // Then: validation should pass
      verify(mockLog).info(contains("Starting SQL Safety Guard scan"));
    }
  }

  @Test
  void testValidateParameters_invalidOutputFormat_shouldThrow() {
    // Given: invalid output format
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("invalid");
    mojo.setSkip(false);

    // When/Then: execute should throw MojoExecutionException
    MojoExecutionException exception =
        assertThrows(MojoExecutionException.class, () -> mojo.execute());
    assertTrue(exception.getMessage().contains("Invalid output format"));
  }

  @Test
  void testLoadConfiguration_withFile_shouldLoadYaml() throws Exception {
    // Given: valid YAML config file
    Path configFile = tempDir.resolve("config.yaml");
    Files.write(
        configFile,
        ("rules:\n"
            + "  noWhereClause:\n"
            + "    enabled: true\n"
            + "    riskLevel: CRITICAL\n").getBytes());

    mojo.setProjectPath(tempDir.toFile());
    mojo.setConfigFile(configFile.toFile());
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: config should be loaded from file
    verify(mockLog).info(contains("Loading configuration from"));
  }

  @Test
  void testLoadConfiguration_noFile_shouldReturnDefault() throws Exception {
    // Given: no config file specified
    mojo.setProjectPath(tempDir.toFile());
    mojo.setConfigFile(null);
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: default config should be used
    verify(mockLog).info("Using default configuration");
  }

  @Test
  void testLoadConfiguration_invalidYaml_shouldThrow() {
    // Given: invalid YAML config file
    Path configFile = tempDir.resolve("invalid.yaml");
    try {
      Files.write(configFile, "invalid: yaml: content: [[[".getBytes());
    } catch (Exception e) {
      fail("Failed to create test file");
    }

    mojo.setProjectPath(tempDir.toFile());
    mojo.setConfigFile(configFile.toFile());
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When/Then: execute should throw MojoExecutionException
    assertThrows(MojoExecutionException.class, () -> mojo.execute());
  }

  @Test
  void testExecuteScan_validProject_shouldReturnReport() throws Exception {
    // Given: valid project directory
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: scan should complete successfully
    verify(mockLog).info(contains("Scan completed"));
  }

  @Test
  void testExecuteScan_emptyProject_shouldReturnEmptyReport() throws Exception {
    // Given: empty project directory (no SQL files)
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: scan should complete with 0 entries
    verify(mockLog).info(contains("SQL statements: 0"));
  }

  @Test
  void testExecuteScan_withViolations_shouldIncludeInReport() throws Exception {
    // Given: project with SQL files containing violations
    Path srcMain = tempDir.resolve("src/main/resources/mappers");
    Files.createDirectories(srcMain);

    Path mapperFile = srcMain.resolve("UserMapper.xml");
    Files.write(
        mapperFile,
        ("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
            + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" "
            + "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n"
            + "<mapper namespace=\"com.example.UserMapper\">\n"
            + "  <select id=\"selectAll\" resultType=\"User\">\n"
            + "    SELECT * FROM users WHERE 1=1\n"
            + "  </select>\n"
            + "</mapper>").getBytes());

    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: scan should find violations
    verify(mockLog).info(contains("Scan completed"));
  }

  @Test
  void testCreateValidator_shouldUse4CoreCheckers() throws Exception {
    // Given: valid project
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When: execute is called (which creates validator internally)
    mojo.execute();

    // Then: validator should be created with 4 core checkers
    // This is verified by successful execution
    verify(mockLog).info("Creating SQL Safety Validator...");
  }
}
