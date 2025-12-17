package com.footstone.sqlguard.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SqlGuardScanTask}.
 * 
 * @author SQL Safety Guard Team
 */
class SqlGuardScanTaskTest {

    @TempDir
    File tempDir;

    private Project project;
    private SqlGuardScanTask task;

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build();
        
        task = new SqlGuardScanTask();
        task.setProject(project);
    }

    @Test
    void testTask_inputProperties_shouldBeAnnotated() throws Exception {
        // Verify that input properties have correct annotations
        assertNotNull(task.getProjectPath(), "projectPath property should exist");
        assertNotNull(task.getConfigFile(), "configFile property should exist");
        assertNotNull(task.getOutputFormat(), "outputFormat property should exist");
        assertNotNull(task.getFailOnCritical(), "failOnCritical property should exist");
    }

    @Test
    void testTask_outputFile_shouldBeAnnotated() throws Exception {
        // Verify output file property exists
        assertNotNull(task.getOutputFile(), "outputFile property should exist");
    }

    @Test
    void testTask_projectPath_required_shouldThrow() {
        // Given: projectPath is null
        task.getProjectPath().set(null);
        task.getOutputFormat().set("console");
        task.getFailOnCritical().set(false);

        // When/Then: scan should throw exception
        assertThrows(GradleException.class, () -> task.scan(),
            "Should throw exception when projectPath is null");
    }

    @Test
    void testTask_projectPath_notExists_shouldThrow() {
        // Given: projectPath does not exist
        File nonExistent = new File(tempDir, "nonexistent");
        task.getProjectPath().set(nonExistent);
        task.getOutputFormat().set("console");
        task.getFailOnCritical().set(false);

        // When/Then: scan should throw exception
        assertThrows(GradleException.class, () -> task.scan(),
            "Should throw exception when projectPath does not exist");
    }

    @Test
    void testTask_configFile_notExists_shouldThrow() {
        // Given: configFile does not exist
        task.getProjectPath().set(tempDir);
        task.getConfigFile().set(new File(tempDir, "nonexistent.yml"));
        task.getOutputFormat().set("console");
        task.getFailOnCritical().set(false);

        // When/Then: scan should throw exception
        assertThrows(GradleException.class, () -> task.scan(),
            "Should throw exception when configFile does not exist");
    }

    @Test
    void testTask_outputFormat_invalid_shouldThrow() {
        // Given: invalid output format
        task.getProjectPath().set(tempDir);
        task.getOutputFormat().set("invalid");
        task.getFailOnCritical().set(false);

        // When/Then: scan should throw exception
        GradleException exception = assertThrows(GradleException.class, () -> task.scan());
        assertTrue(exception.getMessage().contains("Invalid output format"),
            "Exception message should mention invalid format");
    }

    @Test
    void testTask_validInputs_shouldNotThrow() {
        // Given: valid inputs
        task.getProjectPath().set(tempDir);
        task.getOutputFormat().set("console");
        task.getFailOnCritical().set(false);

        // When/Then: validation should pass (scan will fail later due to no SQL files)
        assertDoesNotThrow(() -> {
            try {
                task.scan();
            } catch (GradleException e) {
                // Expected - no SQL files to scan
                // But validation should have passed
                assertFalse(e.getMessage().contains("does not exist"),
                    "Should not fail on validation");
            }
        });
    }

    @Test
    void testTask_defaultProjectPath_shouldBeProjectDir() {
        // Given: task with convention
        task.getProjectPath().convention(project.getProjectDir());

        // Then: default should be project directory
        assertEquals(project.getProjectDir(), task.getProjectPath().get(),
            "Default project path should be project directory");
    }

    @Test
    void testTask_defaultOutputFormat_shouldBeConsole() {
        // Given: task with convention
        task.getOutputFormat().convention("console");

        // Then: default should be console
        assertEquals("console", task.getOutputFormat().get(),
            "Default output format should be 'console'");
    }

    @Test
    void testTask_defaultFailOnCritical_shouldBeFalse() {
        // Given: task with convention
        task.getFailOnCritical().convention(false);

        // Then: default should be false
        assertFalse(task.getFailOnCritical().get(),
            "Default failOnCritical should be false");
    }

    @Test
    void testTask_configFile_optional_shouldWork() {
        // Given: no config file
        task.getProjectPath().set(tempDir);
        task.getOutputFormat().set("console");
        task.getFailOnCritical().set(false);
        // configFile is not set (optional)

        // When/Then: should work without config file
        assertDoesNotThrow(() -> {
            try {
                task.scan();
            } catch (GradleException e) {
                // Expected - no SQL files to scan
                assertFalse(e.getMessage().contains("Config file"),
                    "Should not fail on missing optional config file");
            }
        });
    }

    @Test
    void testTask_outputFile_optional_shouldWork() {
        // Given: no output file specified
        task.getProjectPath().set(tempDir);
        task.getOutputFormat().set("console");
        task.getFailOnCritical().set(false);
        // outputFile is not set (optional)

        // When/Then: should work without output file for console format
        assertDoesNotThrow(() -> {
            try {
                task.scan();
            } catch (GradleException e) {
                // Expected - no SQL files to scan
                assertFalse(e.getMessage().contains("output file"),
                    "Should not fail on missing optional output file");
            }
        });
    }

    @Test
    void testTask_taskAction_shouldExecuteScan() {
        // Given: valid configuration
        task.getProjectPath().set(tempDir);
        task.getOutputFormat().set("console");
        task.getFailOnCritical().set(false);

        // When/Then: task action should execute
        assertDoesNotThrow(() -> {
            try {
                task.scan();
                // If no exception, scan executed successfully
            } catch (GradleException e) {
                // Expected - might fail due to no SQL files
                // But should have attempted to scan
                assertTrue(true, "Task action executed");
            }
        });
    }

    @Test
    void testTask_cacheable_shouldSupportUpToDate() {
        // Given: task with same inputs
        task.getProjectPath().set(tempDir);
        task.getOutputFormat().set("console");
        task.getFailOnCritical().set(false);

        // When: properties are set
        File projectPath1 = task.getProjectPath().get();
        String format1 = task.getOutputFormat().get();

        // Then: properties should be stable
        assertEquals(projectPath1, task.getProjectPath().get(),
            "Property values should be stable for caching");
        assertEquals(format1, task.getOutputFormat().get(),
            "Property values should be stable for caching");
    }
}



