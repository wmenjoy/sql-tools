package com.footstone.sqlguard.scanner.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for report output and CI/CD integration.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Console output generation</li>
 *   <li>HTML output generation</li>
 *   <li>Exit code logic for CI/CD</li>
 *   <li>Fail-on-critical behavior</li>
 *   <li>Quiet mode output</li>
 * </ul>
 */
public class ReportOutputTest {

  @Test
  public void testConsoleOutput_shouldPrintToStdout() throws Exception {
    // Use test-project from resources
    URL testProjectUrl = getClass().getClassLoader().getResource("test-project");
    assertNotNull(testProjectUrl, "test-project resource not found");
    Path testProject = Paths.get(testProjectUrl.toURI());

    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(testProject);
    cli.setOutputFormat("console");

    // Capture output
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(outputStream));

    try {
      int exitCode = cli.call();
      assertEquals(0, exitCode);

      String output = outputStream.toString();
      assertTrue(output.contains("SQL Safety Scan Report"));
      assertTrue(output.contains("Total SQL"));
    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  public void testHtmlOutput_shouldWriteFile(@TempDir Path tempDir) throws Exception {
    // Use test-project from resources
    URL testProjectUrl = getClass().getClassLoader().getResource("test-project");
    assertNotNull(testProjectUrl, "test-project resource not found");
    Path testProject = Paths.get(testProjectUrl.toURI());

    Path outputFile = tempDir.resolve("report.html");

    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(testProject);
    cli.setOutputFormat("html");
    cli.setOutputFile(outputFile);

    int exitCode = cli.call();

    assertEquals(0, exitCode);
    assertTrue(Files.exists(outputFile), "HTML report file should exist");

    String htmlContent = new String(Files.readAllBytes(outputFile));
    assertTrue(htmlContent.contains("<!DOCTYPE html"));
    assertTrue(htmlContent.contains("SQL Safety Scan Report"));
  }

  @Test
  public void testFailOnCritical_withoutCritical_shouldExitZero(@TempDir Path tempDir) throws Exception {
    // Use test-project (no violations)
    URL testProjectUrl = getClass().getClassLoader().getResource("test-project");
    assertNotNull(testProjectUrl, "test-project resource not found");
    Path testProject = Paths.get(testProjectUrl.toURI());

    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(testProject);
    cli.setOutputFormat("console");
    cli.setFailOnCritical(true);

    int exitCode = cli.call();

    // Should succeed (no CRITICAL violations)
    assertEquals(0, exitCode);
  }

  @Test
  public void testExitCodes_shouldMatchCiConvention(@TempDir Path tempDir) throws Exception {
    // Create minimal project
    Files.createDirectories(tempDir.resolve("src/main/resources"));
    Files.createDirectories(tempDir.resolve("src/main/java"));

    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(tempDir);
    cli.setOutputFormat("console");

    int exitCode = cli.call();

    // Should succeed (no violations)
    assertEquals(0, exitCode);
  }

  @Test
  public void testQuietMode_onlyErrorsShown(@TempDir Path tempDir) throws Exception {
    // Use test-project
    URL testProjectUrl = getClass().getClassLoader().getResource("test-project");
    assertNotNull(testProjectUrl, "test-project resource not found");
    Path testProject = Paths.get(testProjectUrl.toURI());

    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(testProject);
    cli.setOutputFormat("console");
    cli.setQuiet(true);

    // Capture output
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(outputStream));

    try {
      int exitCode = cli.call();
      assertEquals(0, exitCode);

      String output = outputStream.toString();
      // In quiet mode, should still show report but not progress messages
      assertTrue(output.contains("SQL Safety Scan Report"));
    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  public void testHtmlWithConsoleFormat_shouldOnlyPrintConsole(@TempDir Path tempDir) throws Exception {
    // Use test-project
    URL testProjectUrl = getClass().getClassLoader().getResource("test-project");
    assertNotNull(testProjectUrl, "test-project resource not found");
    Path testProject = Paths.get(testProjectUrl.toURI());

    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(testProject);
    cli.setOutputFormat("console"); // Console format
    cli.setOutputFile(tempDir.resolve("report.html")); // Output file provided but not used

    // Capture output
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(outputStream));

    try {
      int exitCode = cli.call();
      assertEquals(0, exitCode);

      String output = outputStream.toString();
      assertTrue(output.contains("SQL Safety Scan Report"));

      // HTML file should NOT be created (console format)
      assertFalse(Files.exists(tempDir.resolve("report.html")));
    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  public void testSuccessMessage_shouldShowSummary(@TempDir Path tempDir) throws Exception {
    // Create minimal project
    Files.createDirectories(tempDir.resolve("src/main/resources"));
    Files.createDirectories(tempDir.resolve("src/main/java"));

    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(tempDir);
    cli.setOutputFormat("console");

    // Capture output
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(outputStream));

    try {
      int exitCode = cli.call();
      assertEquals(0, exitCode);

      String output = outputStream.toString();
      assertTrue(output.contains("No violations found") || output.contains("Scan complete"));
    } finally {
      System.setOut(originalOut);
    }
  }
}




