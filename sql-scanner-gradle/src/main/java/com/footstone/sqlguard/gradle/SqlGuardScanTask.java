package com.footstone.sqlguard.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

/**
 * Gradle task for scanning SQL files for safety violations.
 * 
 * <p>This task integrates with sql-scanner-core to perform static analysis
 * of SQL files in the project and generate reports.
 * 
 * <p>Usage:
 * <pre>
 * gradle sqlguardScan
 * </pre>
 * 
 * @author SQL Safety Guard Team
 * @since 1.0.0
 */
public class SqlGuardScanTask extends DefaultTask {

    private final SimpleProperty<File> projectPath = new SimpleProperty<>();
    private final SimpleProperty<File> configFile = new SimpleProperty<>();
    private final SimpleProperty<String> outputFormat = new SimpleProperty<>();
    private final SimpleProperty<File> outputFile = new SimpleProperty<>();
    private final SimpleProperty<Boolean> failOnCritical = new SimpleProperty<>();

    /**
     * Gets the project path to scan.
     * 
     * @return the project path property
     */
    @Input
    public Property<File> getProjectPath() {
        return projectPath;
    }

    /**
     * Gets the configuration file path.
     * 
     * @return the configuration file property
     */
    @Optional
    @Input
    public Property<File> getConfigFile() {
        return configFile;
    }

    /**
     * Gets the output format.
     * 
     * @return the output format property (console, html, or both)
     */
    @Input
    public Property<String> getOutputFormat() {
        return outputFormat;
    }

    /**
     * Gets the output file for HTML reports.
     * 
     * @return the output file property
     */
    @Optional
    @OutputFile
    public Property<File> getOutputFile() {
        return outputFile;
    }

    /**
     * Gets whether to fail build on critical violations.
     * 
     * @return the fail on critical property
     */
    @Input
    public Property<Boolean> getFailOnCritical() {
        return failOnCritical;
    }

    /**
     * Simple implementation of Property interface for testing.
     */
    private static class SimpleProperty<T> implements Property<T> {
        private T value;

        @Override
        public T get() {
            return value;
        }

        @Override
        public T getOrNull() {
            return value;
        }

        @Override
        public T getOrElse(T defaultValue) {
            return value != null ? value : defaultValue;
        }

        @Override
        public void set(T value) {
            this.value = value;
        }

        @Override
        public void convention(T value) {
            if (this.value == null) {
                this.value = value;
            }
        }
    }

    /**
     * Executes the SQL scan task.
     * 
     * <p>This method:
     * <ul>
     *   <li>Validates task parameters</li>
     *   <li>Loads configuration</li>
     *   <li>Executes the SQL scan</li>
     *   <li>Generates reports</li>
     *   <li>Handles violations (fails build if configured)</li>
     * </ul>
     * 
     * @throws GradleException if validation fails or critical violations found
     */
    @TaskAction
    public void scan() {
        getLogger().info("Starting SQL Safety Guard scan...");
        
        // Step 1: Validate parameters
        validateParameters();

        // Step 2: Load configuration
        com.footstone.sqlguard.config.SqlGuardConfig config = loadConfiguration();

        // Step 3: Execute scan
        com.footstone.sqlguard.scanner.model.ScanReport report = executeScan(config);

        // Step 4: Generate reports
        try {
            generateReports(report);
        } catch (java.io.IOException e) {
            throw new GradleException("Report generation failed", e);
        }

        // Step 5: Handle violations
        handleViolations(report);

        getLogger().info("SQL Safety Guard scan completed");
    }

    /**
     * Validates task parameters.
     * 
     * @throws GradleException if parameters are invalid
     */
    private void validateParameters() {
        File projectPath = getProjectPath().get();
        if (projectPath == null || !projectPath.exists()) {
            throw new GradleException(
                "Project path does not exist: " + projectPath);
        }

        if (!projectPath.isDirectory()) {
            throw new GradleException(
                "Project path is not a directory: " + projectPath);
        }

        File configFile = getConfigFile().getOrNull();
        if (configFile != null && !configFile.exists()) {
            throw new GradleException(
                "Config file does not exist: " + configFile);
        }

        String format = getOutputFormat().get();
        if (!"console".equals(format) && !"html".equals(format) && !"both".equals(format)) {
            throw new GradleException(
                "Invalid output format: " + format + 
                ". Must be one of: console, html, both");
        }
    }

    /**
     * Loads configuration from file or returns default configuration.
     * 
     * @return SqlGuardConfig instance
     * @throws GradleException if configuration loading fails
     */
    private com.footstone.sqlguard.config.SqlGuardConfig loadConfiguration() {
        File configFile = getConfigFile().getOrNull();
        if (configFile != null) {
            try {
                getLogger().info("Loading configuration from: " + configFile.getAbsolutePath());
                com.footstone.sqlguard.config.YamlConfigLoader loader =
                    new com.footstone.sqlguard.config.YamlConfigLoader();
                return loader.loadFromFile(configFile.toPath());
            } catch (Exception e) {
                throw new GradleException("Failed to load config file: " + configFile, e);
            }
        }

        getLogger().info("Using default configuration");
        return com.footstone.sqlguard.config.SqlGuardConfigDefaults.getDefault();
    }

    /**
     * Executes the SQL scan using sql-scanner-core.
     * 
     * @param config SqlGuardConfig instance
     * @return ScanReport containing scan results
     * @throws GradleException if scan execution fails
     */
    private com.footstone.sqlguard.scanner.model.ScanReport executeScan(
            com.footstone.sqlguard.config.SqlGuardConfig config) {
        try {
            getLogger().info("Initializing parsers...");

            // Create parsers
            com.footstone.sqlguard.scanner.parser.impl.XmlMapperParser xmlParser =
                new com.footstone.sqlguard.scanner.parser.impl.XmlMapperParser(null, null, null);
            com.footstone.sqlguard.scanner.parser.impl.AnnotationParser annotationParser =
                new com.footstone.sqlguard.scanner.parser.impl.AnnotationParser();
            com.footstone.sqlguard.scanner.wrapper.QueryWrapperScanner wrapperScanner =
                new com.footstone.sqlguard.scanner.wrapper.QueryWrapperScanner();

            getLogger().info("Creating SQL Safety Validator...");

            // Create validator
            com.footstone.sqlguard.validator.DefaultSqlSafetyValidator validator = 
                createValidator(config);

            getLogger().info("Creating SQL Scanner...");

            // Create scanner with validator (no semantic analysis for Gradle plugin)
            com.footstone.sqlguard.scanner.SqlScanner scanner =
                new com.footstone.sqlguard.scanner.SqlScanner(
                    xmlParser, annotationParser, wrapperScanner, validator, null);

            getLogger().info("Executing scan...");

            // Create scan context
            com.footstone.sqlguard.scanner.model.ScanContext context =
                new com.footstone.sqlguard.scanner.model.ScanContext(
                    getProjectPath().get().toPath(), config);

            // Execute scan
            com.footstone.sqlguard.scanner.model.ScanReport report = scanner.scan(context);

            // Calculate statistics
            report.calculateStatistics();

            getLogger().info(String.format(
                "Scan completed. SQL statements: %d, Total violations: %d",
                report.getEntries().size(), report.getTotalViolations()));

            return report;

        } catch (Exception e) {
            throw new GradleException("Scan execution failed", e);
        }
    }

    /**
     * Creates DefaultSqlSafetyValidator with configuration.
     * 
     * @param config SqlGuardConfig instance
     * @return configured validator
     */
    private com.footstone.sqlguard.validator.DefaultSqlSafetyValidator createValidator(
            com.footstone.sqlguard.config.SqlGuardConfig config) {
        // Create JSqlParser facade
        com.footstone.sqlguard.parser.JSqlParserFacade facade =
            new com.footstone.sqlguard.parser.JSqlParserFacade(false); // fail-fast mode

        // Create all rule checkers
        java.util.List<com.footstone.sqlguard.validator.rule.RuleChecker> checkers =
            createAllCheckers(config);

        // Create orchestrator
        com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator orchestrator =
            new com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator(checkers);

        // Create deduplication filter
        com.footstone.sqlguard.validator.SqlDeduplicationFilter filter =
            new com.footstone.sqlguard.validator.SqlDeduplicationFilter();

        // Create and return validator
        return new com.footstone.sqlguard.validator.DefaultSqlSafetyValidator(
            facade, checkers, orchestrator, filter);
    }

    /**
     * Creates all rule checkers with configuration.
     * 
     * @param config the SQL Guard configuration
     * @return list of configured rule checkers
     */
    private java.util.List<com.footstone.sqlguard.validator.rule.RuleChecker> createAllCheckers(
            com.footstone.sqlguard.config.SqlGuardConfig config) {
        java.util.List<com.footstone.sqlguard.validator.rule.RuleChecker> checkers =
            new java.util.ArrayList<>();

        // Checker 1: NoWhereClauseChecker
        com.footstone.sqlguard.validator.rule.impl.NoWhereClauseConfig noWhereConfig =
            new com.footstone.sqlguard.validator.rule.impl.NoWhereClauseConfig();
        noWhereConfig.setEnabled(true);
        checkers.add(
            new com.footstone.sqlguard.validator.rule.impl.NoWhereClauseChecker(noWhereConfig));

        // Checker 2: DummyConditionChecker
        com.footstone.sqlguard.validator.rule.impl.DummyConditionConfig dummyConfig =
            new com.footstone.sqlguard.validator.rule.impl.DummyConditionConfig();
        dummyConfig.setEnabled(true);
        checkers.add(
            new com.footstone.sqlguard.validator.rule.impl.DummyConditionChecker(dummyConfig));

        // Checker 3: BlacklistFieldChecker
        com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig blacklistConfig =
            new com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig();
        blacklistConfig.setEnabled(true);
        checkers.add(
            new com.footstone.sqlguard.validator.rule.impl.BlacklistFieldChecker(blacklistConfig));

        // Checker 4: WhitelistFieldChecker
        com.footstone.sqlguard.validator.rule.impl.WhitelistFieldsConfig whitelistConfig =
            new com.footstone.sqlguard.validator.rule.impl.WhitelistFieldsConfig();
        whitelistConfig.setEnabled(true);
        checkers.add(
            new com.footstone.sqlguard.validator.rule.impl.WhitelistFieldChecker(whitelistConfig));

        return checkers;
    }

    /**
     * Generates reports based on outputFormat configuration.
     * 
     * @param report ScanReport to generate reports from
     * @throws java.io.IOException if report generation fails
     */
    private void generateReports(com.footstone.sqlguard.scanner.model.ScanReport report) 
            throws java.io.IOException {
        String format = getOutputFormat().get();
        
        if ("console".equals(format) || "both".equals(format)) {
            generateConsoleReport(report);
        }

        if ("html".equals(format) || "both".equals(format)) {
            generateHtmlReport(report);
        }
    }

    /**
     * Generates console report and outputs to Gradle logger.
     * 
     * @param report ScanReport to generate from
     * @throws java.io.IOException if report generation fails
     */
    private void generateConsoleReport(com.footstone.sqlguard.scanner.model.ScanReport report) 
            throws java.io.IOException {
        getLogger().info("Generating console report...");

        com.footstone.sqlguard.scanner.report.ConsoleReportGenerator generator =
            new com.footstone.sqlguard.scanner.report.ConsoleReportGenerator();

        // Capture console output to string
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.PrintStream ps = new java.io.PrintStream(baos);
        java.io.PrintStream oldOut = System.out;

        try {
            System.setOut(ps);
            generator.printToConsole(report);
            System.out.flush();
            String consoleReport = baos.toString();

            // Output to Gradle log with appropriate log levels
            for (String line : consoleReport.split("\n")) {
                if (line.contains("CRITICAL") || line.contains("HIGH")) {
                    getLogger().error(line);
                } else if (line.contains("MEDIUM")) {
                    getLogger().warn(line);
                } else {
                    getLogger().info(line);
                }
            }
        } finally {
            System.setOut(oldOut);
        }
    }

    /**
     * Generates HTML report and writes to file.
     * 
     * @param report ScanReport to generate from
     * @throws java.io.IOException if report generation or file writing fails
     */
    private void generateHtmlReport(com.footstone.sqlguard.scanner.model.ScanReport report) 
            throws java.io.IOException {
        getLogger().info("Generating HTML report...");

        com.footstone.sqlguard.scanner.report.EnhancedHtmlReportGenerator generator =
            new com.footstone.sqlguard.scanner.report.EnhancedHtmlReportGenerator();

        File htmlFile = getOutputFile().getOrElse(
            new File(getProject().getBuildDir(), "reports/sqlguard/report.html")
        );

        // Ensure parent directory exists
        if (htmlFile.getParentFile() != null && !htmlFile.getParentFile().exists()) {
            htmlFile.getParentFile().mkdirs();
        }

        generator.writeToFile(report, htmlFile.toPath());
        getLogger().info("HTML report generated: " + htmlFile.getAbsolutePath());
    }

    /**
     * Handles violations and fails build if configured.
     * 
     * @param report ScanReport to check for violations
     * @throws GradleException if failOnCritical is true and critical violations exist
     */
    private void handleViolations(com.footstone.sqlguard.scanner.model.ScanReport report) {
        if (!getFailOnCritical().get()) {
            return;
        }

        if (report.hasCriticalViolations()) {
            throw new GradleException(String.format(
                "SQL Safety Guard detected CRITICAL violation(s). " +
                "Build failed. See report for details. Total violations: %d",
                report.getTotalViolations()
            ));
        }
    }
}
