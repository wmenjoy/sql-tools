package com.footstone.sqlguard.scanner.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for input validation with fail-fast behavior.
 *
 * <p>Tests cover all validation rules including:</p>
 * <ul>
 *   <li>Project path existence and type validation</li>
 *   <li>Config file existence validation</li>
 *   <li>Output format validation</li>
 *   <li>Cross-field validation (HTML format requires output file)</li>
 *   <li>Output file parent directory writability</li>
 * </ul>
 */
public class InputValidationTest {

  @Test
  public void testProjectPathNotExists_shouldFail() {
    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(Paths.get("/nonexistent/path/to/project"));

    ValidationException exception = assertThrows(ValidationException.class, () -> {
      cli.validateInputs();
    });

    assertTrue(exception.getMessage().contains("Project path does not exist"));
    assertTrue(exception.getMessage().contains("/nonexistent/path/to/project"));
  }

  @Test
  public void testProjectPathIsFile_shouldFail(@TempDir Path tempDir) throws IOException {
    // Create a file instead of directory
    Path file = tempDir.resolve("not-a-directory.txt");
    Files.createFile(file);

    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(file);

    ValidationException exception = assertThrows(ValidationException.class, () -> {
      cli.validateInputs();
    });

    assertTrue(exception.getMessage().contains("Project path is not a directory"));
    assertTrue(exception.getMessage().contains("Please provide a directory, not a file"));
  }

  @Test
  public void testConfigFileNotExists_shouldFail(@TempDir Path tempDir) {
    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(tempDir);
    cli.setConfigFile(Paths.get("/nonexistent/config.yml"));

    ValidationException exception = assertThrows(ValidationException.class, () -> {
      cli.validateInputs();
    });

    assertTrue(exception.getMessage().contains("Config file does not exist"));
    assertTrue(exception.getMessage().contains("/nonexistent/config.yml"));
  }

  @Test
  public void testOutputFormatInvalid_shouldFail(@TempDir Path tempDir) {
    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(tempDir);
    cli.setOutputFormat("xml"); // Invalid format

    ValidationException exception = assertThrows(ValidationException.class, () -> {
      cli.validateInputs();
    });

    assertTrue(exception.getMessage().contains("Invalid output format: xml"));
    assertTrue(exception.getMessage().contains("Valid formats: console, html"));
  }

  @Test
  public void testHtmlFormatWithoutOutputFile_shouldFail(@TempDir Path tempDir) {
    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(tempDir);
    cli.setOutputFormat("html");
    cli.setOutputFile(null); // Missing output file

    ValidationException exception = assertThrows(ValidationException.class, () -> {
      cli.validateInputs();
    });

    assertTrue(exception.getMessage().contains("HTML format requires --output-file option"));
    assertTrue(exception.getMessage().contains("--output-format=html --output-file=report.html"));
  }

  @Test
  public void testOutputFileNotWritable_shouldFail(@TempDir Path tempDir) throws IOException {
    // Skip this test on Windows as it doesn't support POSIX permissions
    String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("win")) {
      return; // Skip on Windows
    }

    // Create a read-only directory
    Path readOnlyDir = tempDir.resolve("readonly");
    Files.createDirectory(readOnlyDir);
    
    try {
      Files.setPosixFilePermissions(readOnlyDir, PosixFilePermissions.fromString("r-xr-xr-x"));

      SqlScannerCli cli = new SqlScannerCli();
      cli.setProjectPath(tempDir);
      cli.setOutputFormat("html");
      cli.setOutputFile(readOnlyDir.resolve("report.html"));

      ValidationException exception = assertThrows(ValidationException.class, () -> {
        cli.validateInputs();
      });

      assertTrue(exception.getMessage().contains("Output directory is not writable"));
      assertTrue(exception.getMessage().contains("write permissions"));
    } finally {
      // Restore permissions for cleanup
      Files.setPosixFilePermissions(readOnlyDir, PosixFilePermissions.fromString("rwxr-xr-x"));
    }
  }

  @Test
  public void testAllValidInputs_shouldPass(@TempDir Path tempDir) throws Exception {
    // Create valid config file
    Path configFile = tempDir.resolve("config.yml");
    Files.createFile(configFile);

    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(tempDir);
    cli.setConfigFile(configFile);
    cli.setOutputFormat("console");

    // Should not throw exception
    assertDoesNotThrow(() -> {
      cli.validateInputs();
    });
  }

  @Test
  public void testValidHtmlOutput_shouldPass(@TempDir Path tempDir) throws Exception {
    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(tempDir);
    cli.setOutputFormat("html");
    cli.setOutputFile(tempDir.resolve("report.html"));

    // Should not throw exception
    assertDoesNotThrow(() -> {
      cli.validateInputs();
    });
  }

  @Test
  public void testConsoleFormatWithoutOutputFile_shouldPass(@TempDir Path tempDir) throws Exception {
    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(tempDir);
    cli.setOutputFormat("console");
    cli.setOutputFile(null); // Console doesn't require output file

    // Should not throw exception
    assertDoesNotThrow(() -> {
      cli.validateInputs();
    });
  }

  @Test
  public void testOptionalConfigFileNull_shouldPass(@TempDir Path tempDir) throws Exception {
    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(tempDir);
    cli.setConfigFile(null); // Optional config file
    cli.setOutputFormat("console"); // Set default format

    // Should not throw exception
    assertDoesNotThrow(() -> {
      cli.validateInputs();
    });
  }

  @Test
  public void testOutputFileInCurrentDirectory_shouldPass(@TempDir Path tempDir) throws Exception {
    // Test output file with null parent (current directory)
    SqlScannerCli cli = new SqlScannerCli();
    cli.setProjectPath(tempDir);
    cli.setOutputFormat("html");
    cli.setOutputFile(Paths.get("report.html")); // No parent directory

    // Should not throw exception (parent is null, validation skips)
    assertDoesNotThrow(() -> {
      cli.validateInputs();
    });
  }
}

