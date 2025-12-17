package com.footstone.sqlguard.maven;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for violation handling and build failure logic in SqlGuardScanMojo.
 *
 * <p>Tests failOnCritical behavior and build failure scenarios.
 *
 * @author SQL Safety Guard Team
 */
class ViolationHandlingTest {

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
  void testFailOnCritical_false_noViolations_shouldPass() throws Exception {
    // Given: failOnCritical=false, no violations
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setFailOnCritical(false);
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: build should pass
    verify(mockLog).info("SQL Safety Guard scan completed");
  }

  @Test
  void testFailOnCritical_false_withViolations_shouldPass() throws Exception {
    // Given: failOnCritical=false, with CRITICAL violations
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
    mojo.setFailOnCritical(false);
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: build should pass despite CRITICAL violations
    verify(mockLog).info("SQL Safety Guard scan completed");
  }

  @Test
  void testFailOnCritical_true_noViolations_shouldPass() throws Exception {
    // Given: failOnCritical=true, no violations
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setFailOnCritical(true);
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: build should pass
    verify(mockLog).info("SQL Safety Guard scan completed");
  }

  @Test
  void testFailOnCritical_true_onlyCritical_shouldFail() throws Exception {
    // Given: failOnCritical=true, with CRITICAL violations
    Path srcMain = tempDir.resolve("src/main/resources/mappers");
    Files.createDirectories(srcMain);

    Path mapperFile = srcMain.resolve("CriticalMapper.xml");
    Files.write(
        mapperFile,
        ("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
            + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" "
            + "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n"
            + "<mapper namespace=\"com.example.CriticalMapper\">\n"
            + "  <delete id=\"deleteAll\">\n"
            + "    DELETE FROM users\n"
            + "  </delete>\n"
            + "</mapper>").getBytes());

    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setFailOnCritical(true);
    mojo.setSkip(false);

    // When/Then: execute should throw MojoFailureException
    MojoFailureException exception =
        assertThrows(MojoFailureException.class, () -> mojo.execute());

    assertTrue(
        exception.getMessage().contains("CRITICAL"),
        "Exception message should mention CRITICAL violations");
  }

  @Test
  void testFailOnCritical_true_criticalAndOthers_shouldFail() throws Exception {
    // Given: failOnCritical=true, with CRITICAL and other violations
    Path srcMain = tempDir.resolve("src/main/resources/mappers");
    Files.createDirectories(srcMain);

    Path mapperFile = srcMain.resolve("MixedMapper.xml");
    Files.write(
        mapperFile,
        ("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
            + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" "
            + "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n"
            + "<mapper namespace=\"com.example.MixedMapper\">\n"
            + "  <delete id=\"deleteAll\">\n"
            + "    DELETE FROM users\n"
            + "  </delete>\n"
            + "  <select id=\"selectWithDummy\" resultType=\"User\">\n"
            + "    SELECT * FROM users WHERE 1=1\n"
            + "  </select>\n"
            + "</mapper>").getBytes());

    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setFailOnCritical(true);
    mojo.setSkip(false);

    // When/Then: execute should throw MojoFailureException
    MojoFailureException exception =
        assertThrows(MojoFailureException.class, () -> mojo.execute());

    assertTrue(
        exception.getMessage().contains("CRITICAL"),
        "Exception message should mention CRITICAL violations");
  }

  @Test
  void testFailOnCritical_true_onlyHighMediumLow_shouldPass() throws Exception {
    // Given: failOnCritical=true, with only HIGH/MEDIUM/LOW violations (no CRITICAL)
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
    mojo.setFailOnCritical(true);
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: build should pass (no CRITICAL violations)
    verify(mockLog).info("SQL Safety Guard scan completed");
  }

  @Test
  void testViolationMessage_shouldIncludeCriticalCount() throws Exception {
    // Given: failOnCritical=true, with CRITICAL violations
    Path srcMain = tempDir.resolve("src/main/resources/mappers");
    Files.createDirectories(srcMain);

    Path mapperFile = srcMain.resolve("CriticalMapper.xml");
    Files.write(
        mapperFile,
        ("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
            + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" "
            + "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n"
            + "<mapper namespace=\"com.example.CriticalMapper\">\n"
            + "  <delete id=\"deleteAll\">\n"
            + "    DELETE FROM users\n"
            + "  </delete>\n"
            + "</mapper>").getBytes());

    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setFailOnCritical(true);
    mojo.setSkip(false);

    // When/Then: execute should throw MojoFailureException with violation count
    MojoFailureException exception =
        assertThrows(MojoFailureException.class, () -> mojo.execute());

    assertTrue(
        exception.getMessage().contains("violation"),
        "Exception message should mention violations");
  }

  @Test
  void testViolationMessage_shouldMentionReport() throws Exception {
    // Given: failOnCritical=true, with CRITICAL violations
    Path srcMain = tempDir.resolve("src/main/resources/mappers");
    Files.createDirectories(srcMain);

    Path mapperFile = srcMain.resolve("CriticalMapper.xml");
    Files.write(
        mapperFile,
        ("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
            + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" "
            + "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n"
            + "<mapper namespace=\"com.example.CriticalMapper\">\n"
            + "  <delete id=\"deleteAll\">\n"
            + "    DELETE FROM users\n"
            + "  </delete>\n"
            + "</mapper>").getBytes());

    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("console");
    mojo.setFailOnCritical(true);
    mojo.setSkip(false);

    // When/Then: execute should throw MojoFailureException mentioning report
    MojoFailureException exception =
        assertThrows(MojoFailureException.class, () -> mojo.execute());

    assertTrue(
        exception.getMessage().contains("report"),
        "Exception message should mention report");
  }

  @Test
  void testExecute_fullFlow_noViolations_shouldComplete() throws Exception {
    // Given: complete configuration with no violations
    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("both");
    mojo.setOutputFile(tempDir.resolve("report.html").toFile());
    mojo.setFailOnCritical(true);
    mojo.setSkip(false);

    // When: execute is called
    mojo.execute();

    // Then: full flow should complete successfully
    verify(mockLog).info("Starting SQL Safety Guard scan...");
    verify(mockLog).info("Using default configuration");
    verify(mockLog).info("Initializing parsers...");
    verify(mockLog).info("Creating SQL Safety Validator...");
    verify(mockLog).info("Creating SQL Scanner...");
    verify(mockLog).info("Executing scan...");
    verify(mockLog).info(contains("Scan completed"));
    verify(mockLog).info("Generating console report...");
    verify(mockLog).info("Generating HTML report...");
    verify(mockLog).info(contains("HTML report generated"));
    verify(mockLog).info("SQL Safety Guard scan completed");
  }

  @Test
  void testExecute_fullFlow_withCritical_shouldFail() throws Exception {
    // Given: complete configuration with CRITICAL violations
    Path srcMain = tempDir.resolve("src/main/resources/mappers");
    Files.createDirectories(srcMain);

    Path mapperFile = srcMain.resolve("CriticalMapper.xml");
    Files.write(
        mapperFile,
        ("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
            + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" "
            + "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n"
            + "<mapper namespace=\"com.example.CriticalMapper\">\n"
            + "  <delete id=\"deleteAll\">\n"
            + "    DELETE FROM users\n"
            + "  </delete>\n"
            + "</mapper>").getBytes());

    mojo.setProjectPath(tempDir.toFile());
    mojo.setOutputFormat("both");
    mojo.setOutputFile(tempDir.resolve("report.html").toFile());
    mojo.setFailOnCritical(true);
    mojo.setSkip(false);

    // When/Then: execute should throw MojoFailureException
    MojoFailureException exception =
        assertThrows(MojoFailureException.class, () -> mojo.execute());

    // Verify scan completed before failure
    verify(mockLog).info("Starting SQL Safety Guard scan...");
    verify(mockLog).info(contains("Scan completed"));
    verify(mockLog).info("Generating console report...");
    verify(mockLog).info("Generating HTML report...");

    // Verify failure message
    assertTrue(exception.getMessage().contains("CRITICAL"));
    assertTrue(exception.getMessage().contains("Build failed"));
  }
}
