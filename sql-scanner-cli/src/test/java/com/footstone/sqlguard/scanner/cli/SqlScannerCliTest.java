package com.footstone.sqlguard.scanner.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for SqlScannerCli argument parsing with picocli.
 *
 * <p>Tests cover all command-line options, required/optional parameters,
 * validation, help/version flags, and error handling.</p>
 */
public class SqlScannerCliTest {

  @Test
  public void testRequiredProjectPath_shouldParse(@TempDir Path tempDir) {
    SqlScannerCli cli = new SqlScannerCli();
    CommandLine cmd = new CommandLine(cli);

    String[] args = {"--project-path", tempDir.toString()};
    int exitCode = cmd.execute(args);

    assertEquals(0, exitCode);
    assertEquals(tempDir, cli.getProjectPath());
  }

  @Test
  public void testOptionalConfigFile_shouldParse(@TempDir Path tempDir) throws Exception {
    // Create a dummy config file
    Path configFile = tempDir.resolve("config.yml");
    Files.createFile(configFile);

    SqlScannerCli cli = new SqlScannerCli();
    CommandLine cmd = new CommandLine(cli);

    String[] args = {
        "--project-path", tempDir.toString(),
        "--config-file", configFile.toString()
    };
    int exitCode = cmd.execute(args);

    assertEquals(0, exitCode);
    assertEquals(configFile, cli.getConfigFile());
  }

  @Test
  public void testOutputFormatValues_shouldParseConsoleAndHtml(@TempDir Path tempDir) {
    // Test console format
    SqlScannerCli cli1 = new SqlScannerCli();
    CommandLine cmd1 = new CommandLine(cli1);

    String[] args1 = {
        "--project-path", tempDir.toString(),
        "--output-format", "console"
    };
    int exitCode1 = cmd1.execute(args1);

    assertEquals(0, exitCode1);
    assertEquals("console", cli1.getOutputFormat());

    // Test html format (requires output file)
    SqlScannerCli cli2 = new SqlScannerCli();
    CommandLine cmd2 = new CommandLine(cli2);

    String[] args2 = {
        "--project-path", tempDir.toString(),
        "--output-format", "html",
        "--output-file", tempDir.resolve("report.html").toString()
    };
    int exitCode2 = cmd2.execute(args2);

    assertEquals(0, exitCode2);
    assertEquals("html", cli2.getOutputFormat());
  }

  @Test
  public void testOutputFile_shouldParse(@TempDir Path tempDir) {
    SqlScannerCli cli = new SqlScannerCli();
    CommandLine cmd = new CommandLine(cli);

    Path outputFile = tempDir.resolve("report.html");
    String[] args = {
        "--project-path", tempDir.toString(),
        "--output-format", "html",
        "--output-file", outputFile.toString()
    };
    int exitCode = cmd.execute(args);

    assertEquals(0, exitCode);
    assertEquals(outputFile, cli.getOutputFile());
  }

  @Test
  public void testFailOnCritical_shouldParseBoolean(@TempDir Path tempDir) {
    SqlScannerCli cli = new SqlScannerCli();
    CommandLine cmd = new CommandLine(cli);

    String[] args = {
        "--project-path", tempDir.toString(),
        "--fail-on-critical"
    };
    int exitCode = cmd.execute(args);

    assertEquals(0, exitCode);
    assertTrue(cli.isFailOnCritical());
  }

  @Test
  public void testQuietFlag_shouldParse(@TempDir Path tempDir) {
    SqlScannerCli cli = new SqlScannerCli();
    CommandLine cmd = new CommandLine(cli);

    String[] args = {
        "--project-path", tempDir.toString(),
        "--quiet"
    };
    int exitCode = cmd.execute(args);

    assertEquals(0, exitCode);
    assertTrue(cli.isQuiet());
  }

  @Test
  public void testMissingRequired_shouldFailWithError() {
    StringWriter sw = new StringWriter();
    CommandLine cmd = new CommandLine(new SqlScannerCli())
        .setErr(new PrintWriter(sw));

    String[] args = {}; // Missing required --project-path
    int exitCode = cmd.execute(args);

    assertEquals(2, exitCode); // Picocli usage error code
    assertTrue(sw.toString().contains("Missing required option: '--project-path"));
  }

  @Test
  public void testHelpFlag_shouldDisplayUsage() {
    StringWriter sw = new StringWriter();
    CommandLine cmd = new CommandLine(new SqlScannerCli())
        .setOut(new PrintWriter(sw));

    String[] args = {"--help"};
    int exitCode = cmd.execute(args);

    assertEquals(0, exitCode);
    String output = sw.toString();
    assertTrue(output.contains("sql-scanner"));
    assertTrue(output.contains("Static SQL safety scanner"));
    assertTrue(output.contains("--project-path"));
  }

  @Test
  public void testVersionFlag_shouldDisplayVersion() {
    StringWriter sw = new StringWriter();
    CommandLine cmd = new CommandLine(new SqlScannerCli())
        .setOut(new PrintWriter(sw));

    String[] args = {"--version"};
    int exitCode = cmd.execute(args);

    assertEquals(0, exitCode);
    String output = sw.toString();
    assertTrue(output.contains("1.0.0"));
  }

  @Test
  public void testShortOptionAliases_shouldWork(@TempDir Path tempDir) {
    SqlScannerCli cli = new SqlScannerCli();
    CommandLine cmd = new CommandLine(cli);

    String[] args = {
        "-p", tempDir.toString(),
        "-q"
    };
    int exitCode = cmd.execute(args);

    assertEquals(0, exitCode);
    assertEquals(tempDir, cli.getProjectPath());
    assertTrue(cli.isQuiet());
  }

  @Test
  public void testDefaultValues_shouldBeApplied(@TempDir Path tempDir) {
    SqlScannerCli cli = new SqlScannerCli();
    CommandLine cmd = new CommandLine(cli);

    String[] args = {"--project-path", tempDir.toString()};
    cmd.execute(args);

    // Verify defaults
    assertEquals("console", cli.getOutputFormat());
    assertFalse(cli.isFailOnCritical());
    assertFalse(cli.isQuiet());
    assertNull(cli.getConfigFile());
    assertNull(cli.getOutputFile());
  }

  @Test
  public void testComplexCommandLine_shouldParseAllOptions(@TempDir Path tempDir) throws Exception {
    Path configFile = tempDir.resolve("config.yml");
    Files.createFile(configFile);
    Path outputFile = tempDir.resolve("report.html");

    SqlScannerCli cli = new SqlScannerCli();
    CommandLine cmd = new CommandLine(cli);

    String[] args = {
        "--project-path", tempDir.toString(),
        "--config-file", configFile.toString(),
        "--output-format", "html",
        "--output-file", outputFile.toString(),
        "--fail-on-critical",
        "--quiet"
    };
    int exitCode = cmd.execute(args);

    assertEquals(0, exitCode);
    assertEquals(tempDir, cli.getProjectPath());
    assertEquals(configFile, cli.getConfigFile());
    assertEquals("html", cli.getOutputFormat());
    assertEquals(outputFile, cli.getOutputFile());
    assertTrue(cli.isFailOnCritical());
    assertTrue(cli.isQuiet());
  }
}

