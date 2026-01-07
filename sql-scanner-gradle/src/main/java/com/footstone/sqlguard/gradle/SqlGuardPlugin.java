package com.footstone.sqlguard.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Gradle plugin for SQL Safety Guard.
 * 
 * <p>This plugin registers the {@link SqlGuardScanTask} task and creates
 * the {@link SqlGuardExtension} extension for DSL-based configuration.
 * 
 * <p>Usage:
 * <pre>
 * plugins {
 *     id 'com.footstone.sqlguard'
 * }
 * 
 * sqlguard {
 *     outputFormat = 'html'
 *     failOnCritical()
 * }
 * </pre>
 * 
 * @author SQL Safety Guard Team
 * @since 1.0.0
 */
public class SqlGuardPlugin implements Plugin<Project> {

    /**
     * Applies the SQL Guard plugin to the given project.
     * 
     * <p>This method:
     * <ul>
     *   <li>Creates the 'sqlguard' extension for DSL configuration</li>
     *   <li>Registers the 'sqlguardScan' task with default conventions</li>
     * </ul>
     * 
     * @param project the project to apply the plugin to
     */
    @Override
    public void apply(Project project) {
        // Create extension for DSL configuration
        SqlGuardExtension extension = project.getExtensions()
            .create("sqlguard", SqlGuardExtension.class, project);

        // Register task with default conventions
        project.getTasks().register("sqlguardScan",
            SqlGuardScanTask.class, task -> {
                task.setGroup("verification");
                task.setDescription("Scan SQL for safety violations");
                
                // Set default conventions
                task.getProjectPath().convention(project.getProjectDir());
                task.getOutputFormat().convention("console");
                task.getFailOnCritical().convention(false);
            });
    }
}
















