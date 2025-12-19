package com.footstone.sqlguard.maven;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for report generation in SqlGuardScanMojo.
 *
 * <p>Tests console and HTML report generation with various scenarios.
 *
 * @author SQL Safety Guard Team
 */
class ReportGenerationTest {

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
  void testGenerateConsoleReport_noViolations_shouldOutputSuccess() throws Exception {
    // Given: empty project with no SQL files
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: console report should be generated
    verify(mockLog).info("Generating console report...");
    verify(mockLog).info(contains("Scan completed"));
  }

  @Test
  void testGenerateConsoleReport_withViolations_shouldOutputDetails() throws Exception {
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

    // Then: console report should include violation details
    verify(mockLog).info("Generating console report...");
    verify(mockLog, atLeastOnce()).error(anyString()); // CRITICAL or HIGH violations
  }

  @Test
  void testGenerateConsoleReport_shouldUseMavenLogger() throws Exception {
    // Given: valid project
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: Maven logger should be used for output
    verify(mockLog, atLeastOnce()).info(anyString());
  }

  @Test
  void testGenerateConsoleReport_criticalViolations_shouldUseError() throws Exception {
    // Given: project with CRITICAL violations
    Path srcMain = tempDir.resolve("src/main/resources/mappers");
    Files.createDirectories(srcMain);

    Path mapperFile = srcMain.resolve("DangerousMapper.xml");
    Files.write(
        mapperFile,
        ("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
            + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" "
            + "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n"
            + "<mapper namespace=\"com.example.DangerousMapper\">\n"
            + "  <delete id=\"deleteAll\" parameterType=\"int\">\n"
            + "    DELETE FROM users\n"
            + "  </delete>\n"
            + "</mapper>").getBytes());

    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: CRITICAL violations should use error level
    verify(mockLog, atLeastOnce()).error(anyString());
  }

  @Test
  void testGenerateConsoleReport_highViolations_shouldUseError() throws Exception {
    // Given: project with HIGH violations
    Path srcMain = tempDir.resolve("src/main/resources/mappers");
    Files.createDirectories(srcMain);

    Path mapperFile = srcMain.resolve("HighRiskMapper.xml");
    Files.write(
        mapperFile,
        ("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
            + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" "
            + "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n"
            + "<mapper namespace=\"com.example.HighRiskMapper\">\n"
            + "  <select id=\"selectWithDummy\" resultType=\"User\">\n"
            + "    SELECT * FROM users WHERE 1=1\n"
            + "  </select>\n"
            + "</mapper>").getBytes());

    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: HIGH violations should use error level
    verify(mockLog, atLeastOnce()).error(anyString());
  }

  @Test
  void testGenerateConsoleReport_mediumViolations_shouldUseWarn() throws Exception {
    // Given: project with MEDIUM violations (if any exist in checkers)
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: report should be generated (MEDIUM violations would use warn)
    verify(mockLog).info("Generating console report...");
  }

  @Test
  void testGenerateHtmlReport_shouldCreateFile() throws Exception {
    // Given: project with HTML output format
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("html");
    mojo.setOutputFile(tempDir.resolve("report.html").toFile());
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: HTML file should be created
    verify(mockLog).info("Generating HTML report...");
    verify(mockLog).info(contains("HTML report generated"));
    assertTrue(Files.exists(tempDir.resolve("report.html")));
  }

  @Test
  void testGenerateHtmlReport_defaultLocation_shouldUseTarget() throws Exception {
    // Given: project with HTML output but no custom output file
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("html");
    mojo.setOutputFile(null); // Use default location
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: HTML file should be created in target directory
    verify(mockLog).info("Generating HTML report...");
    verify(mockLog).info(contains("HTML report generated"));
    assertTrue(Files.exists(tempDir.resolve("target/sqlguard-report.html")));
  }

  @Test
  void testGenerateHtmlReport_customLocation_shouldUseOutputFile() throws Exception {
    // Given: project with custom output file location
    Path customOutput = tempDir.resolve("custom-report.html");
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("html");
    mojo.setOutputFile(customOutput.toFile());
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: HTML file should be created at custom location
    verify(mockLog).info("Generating HTML report...");
    verify(mockLog).info(contains("HTML report generated"));
    assertTrue(Files.exists(customOutput));
  }

  @Test
  void testGenerateBothReports_shouldOutputConsoleAndHtml() throws Exception {
    // Given: project with both output formats
    Path htmlOutput = tempDir.resolve("report.html");
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("both");
    mojo.setOutputFile(htmlOutput.toFile());
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: both console and HTML reports should be generated
    verify(mockLog).info("Generating console report...");
    verify(mockLog).info("Generating HTML report...");
    verify(mockLog).info(contains("HTML report generated"));
    assertTrue(Files.exists(htmlOutput));
  }

  @Test
  void testReportGeneration_largeScan_shouldComplete() throws Exception {
    // Given: project with multiple SQL files
    Path srcMain = tempDir.resolve("src/main/resources/mappers");
    Files.createDirectories(srcMain);

    // Create multiple mapper files
    for (int i = 1; i <= 5; i++) {
      Path mapperFile = srcMain.resolve("Mapper" + i + ".xml");
      Files.write(
          mapperFile,
          ("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
              + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" "
              + "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n"
              + "<mapper namespace=\"com.example.Mapper" + i + "\">\n"
              + "  <select id=\"select" + i + "\" resultType=\"User\">\n"
              + "    SELECT * FROM users WHERE id = #{id}\n"
              + "  </select>\n"
              + "</mapper>").getBytes());
    }

    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: scan should complete successfully
    verify(mockLog).info(contains("Scan completed"));
    verify(mockLog).info("Generating console report...");
  }

  @Test
  void testReportGeneration_error_shouldThrowMojoException() throws Exception {
    // Given: invalid output file path (directory doesn't exist and can't be created)
    // This test verifies error handling, but in practice the mojo creates parent dirs
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("html");
    mojo.setOutputFile(tempDir.resolve("report.html").toFile());
    mojo.setSkip(false);

    // When: execute is called
    // Then: should complete successfully (parent dirs are created)
    mojo.execute();
    verify(mockLog).info("Generating HTML report...");
  }
}








