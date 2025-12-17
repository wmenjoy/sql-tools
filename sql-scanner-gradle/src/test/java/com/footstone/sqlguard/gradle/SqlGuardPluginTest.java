package com.footstone.sqlguard.gradle;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SqlGuardPlugin}.
 * 
 * @author SQL Safety Guard Team
 */
class SqlGuardPluginTest {

    private Project project;
    private SqlGuardPlugin plugin;

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build();
        plugin = new SqlGuardPlugin();
    }

    @Test
    void testPlugin_shouldRegisterExtension() {
        // When
        plugin.apply(project);

        // Then
        ExtensionContainer extensions = project.getExtensions();
        assertNotNull(extensions.findByName("sqlguard"),
            "Plugin should register 'sqlguard' extension");
        
        Object extension = extensions.findByName("sqlguard");
        assertTrue(extension instanceof SqlGuardExtension,
            "Extension should be instance of SqlGuardExtension");
    }

    @Test
    void testPlugin_shouldRegisterTask() {
        // When
        plugin.apply(project);

        // Then
        TaskContainer tasks = project.getTasks();
        Task task = tasks.findByName("sqlguardScan");
        assertNotNull(task, "Plugin should register 'sqlguardScan' task");
        assertTrue(task instanceof SqlGuardScanTask,
            "Task should be instance of SqlGuardScanTask");
    }

    @Test
    void testPlugin_taskGroup_shouldBeVerification() {
        // When
        plugin.apply(project);

        // Then
        Task task = project.getTasks().findByName("sqlguardScan");
        assertNotNull(task);
        assertEquals("verification", task.getGroup(),
            "Task group should be 'verification'");
    }

    @Test
    void testPlugin_taskDescription_shouldBeSet() {
        // When
        plugin.apply(project);

        // Then
        Task task = project.getTasks().findByName("sqlguardScan");
        assertNotNull(task);
        assertEquals("Scan SQL for safety violations", task.getDescription(),
            "Task description should be set");
    }

    @Test
    void testExtension_defaultProjectPath_shouldBeProjectDir() {
        // When
        plugin.apply(project);

        // Then
        SqlGuardExtension extension = 
            (SqlGuardExtension) project.getExtensions().findByName("sqlguard");
        assertNotNull(extension);
        assertEquals(project.getProjectDir(), extension.getProjectPath(),
            "Default project path should be project directory");
    }

    @Test
    void testExtension_defaultOutputFormat_shouldBeConsole() {
        // When
        plugin.apply(project);

        // Then
        SqlGuardExtension extension = 
            (SqlGuardExtension) project.getExtensions().findByName("sqlguard");
        assertNotNull(extension);
        assertEquals("console", extension.getOutputFormat(),
            "Default output format should be 'console'");
    }

    @Test
    void testExtension_defaultFailOnCritical_shouldBeFalse() {
        // When
        plugin.apply(project);

        // Then
        SqlGuardExtension extension = 
            (SqlGuardExtension) project.getExtensions().findByName("sqlguard");
        assertNotNull(extension);
        assertFalse(extension.isFailOnCritical(),
            "Default failOnCritical should be false");
    }

    @Test
    void testPlugin_apply_shouldNotThrow() {
        // When/Then
        assertDoesNotThrow(() -> plugin.apply(project),
            "Applying plugin should not throw exception");
    }

    @Test
    void testPlugin_multipleApply_shouldNotConflict() {
        // When
        plugin.apply(project);
        
        // Then - applying again should not throw
        assertDoesNotThrow(() -> plugin.apply(project),
            "Applying plugin multiple times should not throw exception");
    }

    @Test
    void testPlugin_taskDependsOn_shouldBeConfigurable() {
        // When
        plugin.apply(project);
        Task task = project.getTasks().findByName("sqlguardScan");
        
        // Then
        assertNotNull(task);
        assertDoesNotThrow(() -> task.dependsOn("compileJava"),
            "Task dependencies should be configurable");
    }
}
