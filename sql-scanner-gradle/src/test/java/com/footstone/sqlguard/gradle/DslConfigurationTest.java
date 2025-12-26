package com.footstone.sqlguard.gradle;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DSL configuration in {@link SqlGuardExtension}.
 * 
 * @author SQL Safety Guard Team
 */
class DslConfigurationTest {

    @TempDir
    File tempDir;

    private Project project;
    private SqlGuardExtension extension;

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build();
        
        extension = new SqlGuardExtension(project);
    }

    @Test
    void testDsl_console_shouldSetOutputFormat() {
        // When
        extension.console();

        // Then
        assertEquals("console", extension.getOutputFormat(),
            "console() should set output format to 'console'");
    }

    @Test
    void testDsl_html_shouldSetOutputFormat() {
        // When
        extension.html();

        // Then
        assertEquals("html", extension.getOutputFormat(),
            "html() should set output format to 'html'");
    }

    @Test
    void testDsl_htmlWithFile_shouldSetOutputFormatAndFile() {
        // Given
        File outputFile = new File(tempDir, "custom-report.html");

        // When
        extension.html(outputFile);

        // Then
        assertEquals("html", extension.getOutputFormat(),
            "html(File) should set output format to 'html'");
        assertEquals(outputFile, extension.getOutputFile(),
            "html(File) should set output file");
    }

    @Test
    void testDsl_both_shouldSetOutputFormat() {
        // When
        extension.both();

        // Then
        assertEquals("both", extension.getOutputFormat(),
            "both() should set output format to 'both'");
    }

    @Test
    void testDsl_failOnCritical_shouldSetFlag() {
        // Given: default is false
        assertFalse(extension.isFailOnCritical(), "Default should be false");

        // When
        extension.failOnCritical();

        // Then
        assertTrue(extension.isFailOnCritical(),
            "failOnCritical() should set flag to true");
    }

    @Test
    void testDsl_configFile_shouldSetFile() {
        // Given
        File configFile = new File(tempDir, "sqlguard-config.yml");

        // When
        extension.setConfigFile(configFile);

        // Then
        assertEquals(configFile, extension.getConfigFile(),
            "setConfigFile() should set config file");
    }

    @Test
    void testDsl_projectPath_shouldSetPath() {
        // Given
        File projectPath = new File(tempDir, "custom-path");

        // When
        extension.setProjectPath(projectPath);

        // Then
        assertEquals(projectPath, extension.getProjectPath(),
            "setProjectPath() should set project path");
    }

    @Test
    void testExtensionToTask_shouldPropagateValues() {
        // Given: extension with custom values
        File customPath = new File(tempDir, "custom");
        File configFile = new File(tempDir, "config.yml");
        File outputFile = new File(tempDir, "output.html");
        
        extension.setProjectPath(customPath);
        extension.setConfigFile(configFile);
        extension.html(outputFile);
        extension.failOnCritical();

        // When: values are read
        // Then: all values should be set correctly
        assertEquals(customPath, extension.getProjectPath());
        assertEquals(configFile, extension.getConfigFile());
        assertEquals("html", extension.getOutputFormat());
        assertEquals(outputFile, extension.getOutputFile());
        assertTrue(extension.isFailOnCritical());
    }

    @Test
    void testExtensionDefaults_shouldApplyToTask() {
        // Given: extension with defaults
        // (constructor sets projectPath to project directory)

        // Then: defaults should be set
        assertEquals(project.getProjectDir(), extension.getProjectPath(),
            "Default project path should be project directory");
        assertEquals("console", extension.getOutputFormat(),
            "Default output format should be 'console'");
        assertFalse(extension.isFailOnCritical(),
            "Default failOnCritical should be false");
        assertNull(extension.getConfigFile(),
            "Default config file should be null");
        assertNull(extension.getOutputFile(),
            "Default output file should be null");
    }

    @Test
    void testDslExample_shouldWork() {
        // Given: DSL-style configuration
        extension.setProjectPath(new File(tempDir, "src/main"));
        extension.setConfigFile(new File(tempDir, "sqlguard.yml"));
        extension.html(new File(tempDir, "build/reports/sql-report.html"));
        extension.failOnCritical();

        // Then: all configuration should be applied
        assertEquals(new File(tempDir, "src/main"), extension.getProjectPath());
        assertEquals(new File(tempDir, "sqlguard.yml"), extension.getConfigFile());
        assertEquals("html", extension.getOutputFormat());
        assertEquals(new File(tempDir, "build/reports/sql-report.html"), 
            extension.getOutputFile());
        assertTrue(extension.isFailOnCritical());
    }
}















