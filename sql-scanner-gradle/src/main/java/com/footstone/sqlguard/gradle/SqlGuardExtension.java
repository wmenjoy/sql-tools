package com.footstone.sqlguard.gradle;

import org.gradle.api.Project;

import javax.inject.Inject;
import java.io.File;

/**
 * Extension for configuring SQL Guard plugin via Gradle DSL.
 * 
 * <p>This extension provides a fluent API for configuring the SQL Guard
 * scan task. Configuration can be done in build.gradle:
 * 
 * <pre>
 * sqlguard {
 *     projectPath = file('src/main')
 *     configFile = file('sqlguard-config.yml')
 *     outputFormat = 'html'
 *     outputFile = file('build/reports/sqlguard/report.html')
 *     failOnCritical()
 * }
 * </pre>
 * 
 * @author SQL Safety Guard Team
 * @since 1.0.0
 */
public class SqlGuardExtension {

    private final Project project;
    private File projectPath;
    private File configFile;
    private String outputFormat = "console";
    private File outputFile;
    private boolean failOnCritical = false;

    /**
     * Creates a new SQL Guard extension.
     * 
     * @param project the Gradle project
     */
    @Inject
    public SqlGuardExtension(Project project) {
        this.project = project;
        this.projectPath = project.getProjectDir();
    }

    /**
     * Gets the project path to scan.
     * 
     * @return the project path
     */
    public File getProjectPath() {
        return projectPath;
    }

    /**
     * Sets the project path to scan.
     * 
     * @param projectPath the project path
     */
    public void setProjectPath(File projectPath) {
        this.projectPath = projectPath;
    }

    /**
     * Gets the configuration file path.
     * 
     * @return the configuration file path, or null if not set
     */
    public File getConfigFile() {
        return configFile;
    }

    /**
     * Sets the configuration file path.
     * 
     * @param configFile the configuration file path
     */
    public void setConfigFile(File configFile) {
        this.configFile = configFile;
    }

    /**
     * Gets the output format.
     * 
     * @return the output format (console, html, or both)
     */
    public String getOutputFormat() {
        return outputFormat;
    }

    /**
     * Sets the output format.
     * 
     * @param outputFormat the output format (console, html, or both)
     */
    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    /**
     * Gets the output file for HTML reports.
     * 
     * @return the output file, or null if not set
     */
    public File getOutputFile() {
        return outputFile;
    }

    /**
     * Sets the output file for HTML reports.
     * 
     * @param outputFile the output file
     */
    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    /**
     * Checks if build should fail on critical violations.
     * 
     * @return true if build should fail on critical violations
     */
    public boolean isFailOnCritical() {
        return failOnCritical;
    }

    /**
     * Sets whether build should fail on critical violations.
     * 
     * @param failOnCritical true to fail build on critical violations
     */
    public void setFailOnCritical(boolean failOnCritical) {
        this.failOnCritical = failOnCritical;
    }

    // DSL configuration methods

    /**
     * Configures output to console only.
     */
    public void console() {
        this.outputFormat = "console";
    }

    /**
     * Configures output to HTML only (default location).
     */
    public void html() {
        this.outputFormat = "html";
    }

    /**
     * Configures output to HTML with custom file location.
     * 
     * @param outputFile the output file for HTML report
     */
    public void html(File outputFile) {
        this.outputFormat = "html";
        this.outputFile = outputFile;
    }

    /**
     * Configures output to both console and HTML.
     */
    public void both() {
        this.outputFormat = "both";
    }

    /**
     * Enables failing the build on critical violations.
     */
    public void failOnCritical() {
        this.failOnCritical = true;
    }
}








