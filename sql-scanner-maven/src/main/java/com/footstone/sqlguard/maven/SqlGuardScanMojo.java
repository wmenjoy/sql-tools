package com.footstone.sqlguard.maven;

import com.footstone.sqlguard.config.SqlGuardConfig;
import com.footstone.sqlguard.scanner.SqlScanner;
import com.footstone.sqlguard.scanner.model.ScanReport;
import com.footstone.sqlguard.scanner.report.ConsoleReportGenerator;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Maven plugin goal for SQL Safety Guard scanning.
 *
 * <p>Executes static analysis on SQL files in the project, generates reports, and optionally fails
 * the build on critical violations.
 *
 * @author SQL Safety Guard Team
 * @since 1.0.0
 */
@Mojo(name = "scan", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class SqlGuardScanMojo extends AbstractMojo {

  /**
   * Project path to scan for SQL files.
   *
   * <p>Defaults to the Maven project base directory.
   */
  @Parameter(property = "sqlguard.projectPath", defaultValue = "${project.basedir}", required = true)
  private File projectPath;

  /**
   * Path to YAML configuration file.
   *
   * <p>If not specified, default configuration will be used.
   */
  @Parameter(property = "sqlguard.configFile")
  private File configFile;

  /**
   * Report output format.
   *
   * <p>Valid values: console, html, both
   *
   * <p>Default: console
   */
  @Parameter(property = "sqlguard.outputFormat", defaultValue = "console")
  private String outputFormat;

  /**
   * Output file path for HTML report.
   *
   * <p>If not specified, defaults to target/sqlguard-report.html
   */
  @Parameter(property = "sqlguard.outputFile")
  private File outputFile;

  /**
   * Whether to fail the build when CRITICAL violations are detected.
   *
   * <p>Default: false
   */
  @Parameter(property = "sqlguard.failOnCritical", defaultValue = "false")
  private boolean failOnCritical;

  /**
   * Skip plugin execution.
   *
   * <p>Default: false
   */
  @Parameter(property = "sqlguard.skip", defaultValue = "false")
  private boolean skip;

  /** Configuration loaded from file or defaults. */
  private SqlGuardConfig config;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("SQL Guard scan skipped");
      return;
    }

    getLog().info("Starting SQL Safety Guard scan...");
    if (projectPath != null) {
      getLog().info("Project path: " + projectPath.getAbsolutePath());
    }

    // Step 1: Validate parameters
    validateParameters();

    // Step 2: Load configuration
    config = loadConfiguration();

    // Step 3: Execute scan
    ScanReport report = executeScan();

    // Step 4: Generate reports
    generateReports(report);

    // Step 5: Handle violations
    handleViolations(report);

    getLog().info("SQL Safety Guard scan completed");
  }

  /**
   * Validates all plugin parameters.
   *
   * @throws MojoExecutionException if validation fails
   */
  private void validateParameters() throws MojoExecutionException {
    if (projectPath == null || !projectPath.exists()) {
      throw new MojoExecutionException(
          "Project path does not exist: " + (projectPath != null ? projectPath : "null"));
    }

    if (!projectPath.isDirectory()) {
      throw new MojoExecutionException("Project path is not a directory: " + projectPath);
    }

    if (configFile != null && !configFile.exists()) {
      throw new MojoExecutionException("Config file does not exist: " + configFile);
    }

    if (!Arrays.asList("console", "html", "both").contains(outputFormat)) {
      throw new MojoExecutionException(
          "Invalid output format: "
              + outputFormat
              + ". Valid values: console, html, both");
    }
  }

  /**
   * Loads configuration from file or returns default configuration.
   *
   * @return SqlGuardConfig instance
   * @throws MojoExecutionException if configuration loading fails
   */
  private SqlGuardConfig loadConfiguration() throws MojoExecutionException {
    if (configFile != null) {
      try {
        getLog().info("Loading configuration from: " + configFile.getAbsolutePath());
        com.footstone.sqlguard.config.YamlConfigLoader loader =
            new com.footstone.sqlguard.config.YamlConfigLoader();
        return loader.loadFromFile(configFile.toPath());
      } catch (Exception e) {
        throw new MojoExecutionException("Failed to load config file: " + configFile, e);
      }
    }

    getLog().info("Using default configuration");
    return com.footstone.sqlguard.config.SqlGuardConfigDefaults.getDefault();
  }

  /**
   * Executes the SQL scan using sql-scanner-core.
   *
   * @return ScanReport containing scan results
   * @throws MojoExecutionException if scan execution fails
   */
  private ScanReport executeScan() throws MojoExecutionException {
    try {
      getLog().info("Initializing parsers...");

      // Create parsers
      com.footstone.sqlguard.scanner.parser.impl.XmlMapperParser xmlParser =
          new com.footstone.sqlguard.scanner.parser.impl.XmlMapperParser(null, null, null);
      com.footstone.sqlguard.scanner.parser.impl.AnnotationParser annotationParser =
          new com.footstone.sqlguard.scanner.parser.impl.AnnotationParser();
      com.footstone.sqlguard.scanner.wrapper.QueryWrapperScanner wrapperScanner =
          new com.footstone.sqlguard.scanner.wrapper.QueryWrapperScanner();

      getLog().info("Creating SQL Safety Validator...");

      // Create validator
      DefaultSqlSafetyValidator validator = createValidator(config);

      getLog().info("Creating SQL Scanner...");

      // Create scanner with validator (no semantic analysis for Maven plugin)
      SqlScanner scanner =
          new SqlScanner(xmlParser, annotationParser, wrapperScanner, validator, null);

      getLog().info("Executing scan...");

      // Create scan context
      com.footstone.sqlguard.scanner.model.ScanContext context =
          new com.footstone.sqlguard.scanner.model.ScanContext(projectPath.toPath(), config);

      // Execute scan
      ScanReport report = scanner.scan(context);

      // Calculate statistics
      report.calculateStatistics();

      getLog()
          .info(
              String.format(
                  "Scan completed. SQL statements: %d, Total violations: %d",
                  report.getEntries().size(), report.getTotalViolations()));

      return report;

    } catch (Exception e) {
      throw new MojoExecutionException("Scan execution failed", e);
    }
  }

  /**
   * Creates DefaultSqlSafetyValidator with configuration.
   *
   * @param config SqlGuardConfig instance
   * @return configured validator
   */
  private DefaultSqlSafetyValidator createValidator(SqlGuardConfig config) {
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
    return new DefaultSqlSafetyValidator(facade, checkers, orchestrator, filter);
  }

  /**
   * Creates all rule checkers with configuration.
   *
   * @param config the SQL Guard configuration
   * @return list of configured rule checkers
   */
  private java.util.List<com.footstone.sqlguard.validator.rule.RuleChecker> createAllCheckers(
      SqlGuardConfig config) {
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
   * @throws MojoExecutionException if report generation fails
   */
  private void generateReports(ScanReport report) throws MojoExecutionException {
    try {
      if ("console".equals(outputFormat) || "both".equals(outputFormat)) {
        generateConsoleReport(report);
      }

      if ("html".equals(outputFormat) || "both".equals(outputFormat)) {
        generateHtmlReport(report);
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Report generation failed", e);
    }
  }

  /**
   * Generates console report and outputs to Maven log.
   *
   * @param report ScanReport to generate from
   * @throws IOException if report generation fails
   */
  private void generateConsoleReport(ScanReport report) throws IOException {
    getLog().info("Generating console report...");

    ConsoleReportGenerator generator = new ConsoleReportGenerator();

    // Capture console output to string
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    java.io.PrintStream ps = new java.io.PrintStream(baos);
    java.io.PrintStream oldOut = System.out;

    try {
      System.setOut(ps);
      generator.printToConsole(report);
      System.out.flush();
      String consoleReport = baos.toString();

      // Output to Maven log with appropriate log levels
      for (String line : consoleReport.split("\n")) {
        if (line.contains("CRITICAL") || line.contains("HIGH")) {
          getLog().error(line);
        } else if (line.contains("MEDIUM")) {
          getLog().warn(line);
        } else {
          getLog().info(line);
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
   * @throws IOException if report generation or file writing fails
   */
  private void generateHtmlReport(ScanReport report) throws IOException {
    getLog().info("Generating HTML report...");

    com.footstone.sqlguard.scanner.report.EnhancedHtmlReportGenerator generator =
        new com.footstone.sqlguard.scanner.report.EnhancedHtmlReportGenerator();

    File htmlFile =
        (outputFile != null) ? outputFile : new File(projectPath, "target/sqlguard-report.html");

    // Ensure parent directory exists
    if (htmlFile.getParentFile() != null && !htmlFile.getParentFile().exists()) {
      htmlFile.getParentFile().mkdirs();
    }

    generator.writeToFile(report, htmlFile.toPath());
    getLog().info("HTML report generated: " + htmlFile.getAbsolutePath());
  }

  /**
   * Handles violations and fails build if configured.
   *
   * @param report ScanReport to check for violations
   * @throws MojoFailureException if failOnCritical is true and critical violations exist
   */
  private void handleViolations(ScanReport report) throws MojoFailureException {
    if (!failOnCritical) {
      return;
    }

    if (report.hasCriticalViolations()) {
      String message =
          String.format(
              "SQL Safety Guard detected CRITICAL violation(s). "
                  + "Build failed. See report for details. Total violations: %d",
              report.getTotalViolations());
      throw new MojoFailureException(message);
    }
  }

  // Setters for testing

  public void setProjectPath(File projectPath) {
    this.projectPath = projectPath;
  }

  public void setConfigFile(File configFile) {
    this.configFile = configFile;
  }

  public void setOutputFormat(String outputFormat) {
    this.outputFormat = outputFormat;
  }

  public void setOutputFile(File outputFile) {
    this.outputFile = outputFile;
  }

  public void setFailOnCritical(boolean failOnCritical) {
    this.failOnCritical = failOnCritical;
  }

  public void setSkip(boolean skip) {
    this.skip = skip;
  }
}








