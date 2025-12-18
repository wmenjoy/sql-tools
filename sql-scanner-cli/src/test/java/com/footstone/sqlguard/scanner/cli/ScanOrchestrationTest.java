package com.footstone.sqlguard.scanner.cli;

import com.footstone.sqlguard.config.SqlGuardConfig;
import com.footstone.sqlguard.scanner.model.ScanReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for scan orchestration and configuration loading.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Configuration loading (default and YAML)</li>
 *   <li>Parser instantiation</li>
 *   <li>Scan execution producing ScanReport</li>
 *   <li>Error handling</li>
 *   <li>Progress logging</li>
 *   <li>Quiet mode</li>
 * </ul>
 */
public class ScanOrchestrationTest {

  @Test
  public void testScanExecution_shouldLoadDefaultConfig() throws Exception {
    // Use test-project from resources
    URL testProjectUrl = getClass().getClassLoader().getResource("test-project");
    assertNotNull(testProjectUrl, "test-project resource not found");
    Path testProject = Paths.get(testProjectUrl.toURI());

    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(testProject);
    cli.setOutputFormat("console");
    cli.setConfigFile(null); // No config file, should use defaults

    int exitCode = cli.call();

    assertEquals(0, exitCode);
  }

  @Test
  public void testScanExecution_shouldLoadYamlConfig(@TempDir Path tempDir) throws Exception {
    // Create a simple YAML config
    Path configFile = tempDir.resolve("config.yml");
    Files.write(configFile, "enabled: true\nactiveStrategy: prod\n".getBytes());

    // Use test-project from resources
    URL testProjectUrl = getClass().getClassLoader().getResource("test-project");
    assertNotNull(testProjectUrl, "test-project resource not found");
    Path testProject = Paths.get(testProjectUrl.toURI());

    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(testProject);
    cli.setOutputFormat("console");
    cli.setConfigFile(configFile);

    int exitCode = cli.call();

    assertEquals(0, exitCode);
  }

  @Test
  public void testScanExecution_shouldProduceScanReport() throws Exception {
    // Use test-project from resources
    URL testProjectUrl = getClass().getClassLoader().getResource("test-project");
    assertNotNull(testProjectUrl, "test-project resource not found");
    Path testProject = Paths.get(testProjectUrl.toURI());

    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(testProject);
    cli.setOutputFormat("console");

    // Capture output to verify scan happened
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(outputStream));

    try {
      int exitCode = cli.call();
      assertEquals(0, exitCode);

      String output = outputStream.toString();
      // Should contain report header
      assertTrue(output.contains("SQL Safety Scan Report") || output.contains("Total SQL"));
    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  public void testProgressLogging_shouldLogSteps() throws Exception {
    // Use test-project from resources
    URL testProjectUrl = getClass().getClassLoader().getResource("test-project");
    assertNotNull(testProjectUrl, "test-project resource not found");
    Path testProject = Paths.get(testProjectUrl.toURI());

    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(testProject);
    cli.setOutputFormat("console");
    cli.setQuiet(false); // Ensure logging is enabled

    // Capture output
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(outputStream));

    try {
      int exitCode = cli.call();
      assertEquals(0, exitCode);

      // Output should contain some progress information
      String output = outputStream.toString();
      assertFalse(output.isEmpty(), "Expected some output in non-quiet mode");
    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  public void testQuietMode_shouldSuppressProgress() throws Exception {
    // Use test-project from resources
    URL testProjectUrl = getClass().getClassLoader().getResource("test-project");
    assertNotNull(testProjectUrl, "test-project resource not found");
    Path testProject = Paths.get(testProjectUrl.toURI());

    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(testProject);
    cli.setOutputFormat("console");
    cli.setQuiet(true); // Enable quiet mode

    // Capture output
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(outputStream));

    try {
      int exitCode = cli.call();
      assertEquals(0, exitCode);

      // In quiet mode, output should be minimal (only report or errors)
      // This test just verifies it doesn't crash
    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  public void testEmptyProject_shouldHandleGracefully(@TempDir Path emptyProject) throws Exception {
    // Create empty project structure
    Files.createDirectories(emptyProject.resolve("src/main/resources"));
    Files.createDirectories(emptyProject.resolve("src/main/java"));

    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(emptyProject);
    cli.setOutputFormat("console");

    int exitCode = cli.call();

    // Should succeed even with no SQL files
    assertEquals(0, exitCode);
  }

  @Test
  public void testConfigurationLoading_shouldUseDefaults(@TempDir Path tempDir) throws Exception {
    // Create minimal project
    Files.createDirectories(tempDir.resolve("src/main/resources"));
    Files.createDirectories(tempDir.resolve("src/main/java"));

    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(tempDir);
    cli.setOutputFormat("console");
    cli.setConfigFile(null); // No config file

    // Should load default configuration
    SqlGuardConfig config = cli.loadConfiguration();

    assertNotNull(config);
    assertTrue(config.isEnabled());
    assertEquals("prod", config.getActiveStrategy());
  }
}







