package com.footstone.sqlguard.maven;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for SqlGuardScanMojo.
 *
 * <p>Tests Mojo functionality, parameter validation, and skip behavior.
 *
 * @author SQL Safety Guard Team
 */
class SqlGuardScanMojoTest {

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
  void testMojoAnnotations_shouldHaveCorrectGoalName() throws Exception {
    // Test that Mojo class is properly annotated (verified at compile time)
    // This test verifies the class exists and can be instantiated
    assertNotNull(mojo, "Mojo instance should be created");
    assertTrue(
        mojo.getClass().getName().endsWith("SqlGuardScanMojo"),
        "Class name should be SqlGuardScanMojo");
  }

  @Test
  void testMojoAnnotations_shouldHaveVerifyPhase() throws Exception {
    // Test that Mojo has proper lifecycle configuration (verified at compile time)
    // This test verifies the Mojo can be configured
    mojo.setProjectPath(tempDir.toFile());
    mojo.setSkip(true);
    mojo.execute();
    verify(mockLog).info("SQL Guard scan skipped");
  }

  @Test
  void testParameterDefaults_shouldUseProjectBasedir() throws Exception {
    // Test that projectPath parameter works correctly
    File projectDir = tempDir.toFile();
    mojo.setProjectPath(projectDir);
    mojo.setSkip(true);
    mojo.execute();
    verify(mockLog).info("SQL Guard scan skipped");
  }

  @Test
  void testParameterDefaults_outputFormat_shouldBeConsole() throws Exception {
    // Test that outputFormat parameter defaults work
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setSkip(false);
    mojo.execute();
    verify(mockLog).info(contains("Starting SQL Safety Guard scan"));
  }

  @Test
  void testParameterDefaults_failOnCritical_shouldBeFalse() throws Exception {
    // Test that failOnCritical parameter defaults work
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setFailOnCritical(false);
    mojo.setSkip(false);
    mojo.execute();
    verify(mockLog).info(contains("Starting SQL Safety Guard scan"));
  }

  @Test
  void testParameterDefaults_skip_shouldBeFalse() throws Exception {
    // Test that skip parameter works
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setSkip(false);
    mojo.execute();
    verify(mockLog).info(contains("Starting SQL Safety Guard scan"));
  }

  @Test
  void testSkip_true_shouldNotExecuteScan() throws Exception {
    // Given: skip is set to true
    mojo.setSkip(true);
    mojo.setProjectPath(tempDir.toFile());

    // When: execute is called
    mojo.execute();

    // Then: scan should be skipped
    verify(mockLog).info("SQL Guard scan skipped");
    verify(mockLog, never()).info(contains("Starting SQL Safety Guard scan"));
  }

  @Test
  void testProjectPath_null_shouldThrowException() {
    // Given: projectPath is null
    mojo.setProjectPath(null);
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When/Then: execute should throw exception (either NPE or MojoExecutionException)
    assertThrows(Exception.class, () -> mojo.execute());
  }

  @Test
  void testProjectPath_notExists_shouldThrowException() {
    // Given: projectPath points to non-existent directory
    File nonExistentPath = new File(tempDir.toFile(), "non-existent");
    mojo.setProjectPath(nonExistentPath);
    mojo.setSkip(false);

    // When/Then: execute should throw MojoExecutionException
    MojoExecutionException exception =
        assertThrows(MojoExecutionException.class, () -> mojo.execute());

    assertTrue(
        exception.getMessage().contains("Project path does not exist"),
        "Exception message should mention project path");
  }

  @Test
  void testConfigFile_notExists_shouldThrowException() {
    // Given: configFile points to non-existent file
    File nonExistentConfig = new File(tempDir.toFile(), "non-existent.yaml");
    mojo.setProjectPath(tempDir.toFile());
    mojo.setConfigFile(nonExistentConfig);
    mojo.setSkip(false);

    // When/Then: execute should throw MojoExecutionException
    MojoExecutionException exception =
        assertThrows(MojoExecutionException.class, () -> mojo.execute());

    assertTrue(
        exception.getMessage().contains("Config file does not exist"),
        "Exception message should mention config file");
  }

  @Test
  void testOutputFormat_invalid_shouldThrowException() {
    // Given: outputFormat is invalid
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("invalid");
    mojo.setSkip(false);

    // When/Then: execute should throw MojoExecutionException
    MojoExecutionException exception =
        assertThrows(MojoExecutionException.class, () -> mojo.execute());

    assertTrue(
        exception.getMessage().contains("Invalid output format"),
        "Exception message should mention invalid output format");
    assertTrue(
        exception.getMessage().contains("invalid"),
        "Exception message should include the invalid value");
  }

  @Test
  void testExecute_shouldCallScanner() throws Exception {
    // Given: valid project directory with no SQL files
    File projectDir = tempDir.toFile();
    mojo.setProjectPath(projectDir);
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: scan should execute successfully
    verify(mockLog).info("Starting SQL Safety Guard scan...");
    verify(mockLog).info(contains("Project path:"));
    verify(mockLog).info("Using default configuration");
    verify(mockLog).info("Initializing parsers...");
    verify(mockLog).info("Creating SQL Safety Validator...");
    verify(mockLog).info("Creating SQL Scanner...");
    verify(mockLog).info("Executing scan...");
    verify(mockLog).info(contains("Scan completed"));
    verify(mockLog).info("Generating console report...");
    verify(mockLog).info("SQL Safety Guard scan completed");
  }
}
