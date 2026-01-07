package com.footstone.sqlguard.scanner.cli;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.footstone.sqlguard.config.ConfigLoadException;
import com.footstone.sqlguard.config.SqlGuardConfig;
import com.footstone.sqlguard.config.SqlGuardConfigDefaults;
import com.footstone.sqlguard.config.YamlConfigLoader;
import com.footstone.sqlguard.scanner.SqlScanner;
import com.footstone.sqlguard.scanner.model.ScanContext;
import com.footstone.sqlguard.scanner.model.ScanReport;
import com.footstone.sqlguard.scanner.mybatis.MyBatisSemanticAnalysisService;
import com.footstone.sqlguard.scanner.mybatis.config.MyBatisAnalysisConfig;
import com.footstone.sqlguard.scanner.parser.impl.AnnotationParser;
import com.footstone.sqlguard.scanner.parser.impl.XmlMapperParser;
import com.footstone.sqlguard.scanner.report.ConsoleReportGenerator;
import com.footstone.sqlguard.scanner.report.HtmlReportGenerator;
import com.footstone.sqlguard.scanner.report.EnhancedHtmlReportGenerator;
import com.footstone.sqlguard.scanner.wrapper.QueryWrapperScanner;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * Command-line interface for SQL Scanner.
 *
 * <p>SqlScannerCli provides a production-ready CLI tool for static SQL scanning
 * with picocli argument parsing, input validation, configuration loading,
 * scan orchestration, and dual-format report generation.</p>
 *
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * # Basic usage
 * sql-scanner --project-path=/path/to/project
 *
 * # With config file
 * sql-scanner --project-path=/path/to/project --config-file=config.yml
 *
 * # HTML output
 * sql-scanner --project-path=/path/to/project --output-format=html --output-file=report.html
 *
 * # CI/CD mode
 * sql-scanner --project-path=/path/to/project --fail-on-critical --quiet
 * }</pre>
 *
 * <p><strong>Exit Codes:</strong></p>
 * <ul>
 *   <li>0 - Success or non-critical warnings</li>
 *   <li>1 - CRITICAL violations (with --fail-on-critical) or errors</li>
 *   <li>2 - Invalid command-line arguments</li>
 * </ul>
 */
@Command(
    name = "sql-scanner",
    description = "Static SQL safety scanner for MyBatis applications",
    mixinStandardHelpOptions = true,
    version = "1.0.0"
)
public class SqlScannerCli implements Callable<Integer> {

  @Option(
      names = {"--project-path", "-p"},
      required = true,
      description = "Project root directory to scan"
  )
  private Path projectPath;

  @Option(
      names = {"--config-file", "-c"},
      description = "Configuration YAML file path (optional)"
  )
  private Path configFile;

  @Option(
      names = {"--output-format", "-f"},
      defaultValue = "console",
      description = "Output format: console or html (default: console)"
  )
  private String outputFormat;

  @Option(
      names = {"--output-file", "-o"},
      description = "Output file path for HTML format (required if format=html)"
  )
  private Path outputFile;

  @Option(
      names = {"--fail-on-critical"},
      description = "Exit with code 1 if CRITICAL violations found (default: false)"
  )
  private boolean failOnCritical;

  @Option(
      names = {"--quiet", "-q"},
      description = "Suppress non-error output for CI/CD (default: false)"
  )
  private boolean quiet;

  @Option(
      names = {"--verbose", "-v"},
      description = "Show detailed scan progress and file information (default: false)"
  )
  private boolean verbose;

  @Option(
      names = {"--recursive", "-r"},
      description = "Recursively scan Maven sub-modules (default: true)"
  )
  private boolean recursive = true;

  @Option(
      names = {"--disable-semantic-analysis"},
      description = "Disable MyBatis semantic analysis (default: enabled)"
  )
  private boolean disableSemanticAnalysis = false;

  /**
   * Main entry point for CLI execution.
   *
   * @return exit code (0=success, 1=error/critical, 2=usage error)
   * @throws Exception if unexpected error occurs
   */
  @Override
  public Integer call() throws Exception {
    try {
      // Configure quiet logging FIRST before any other operations
      if (quiet) {
        configureQuietLogging();
      }
      
      // Configure verbose logging
      if (verbose && !quiet) {
        configureVerboseLogging();
      }
      
      validateInputs();

      // Load configuration
      SqlGuardConfig config = loadConfiguration();
      if (!quiet) {
        System.out.println("Configuration loaded successfully");
      }

      // Create scan context
      ScanContext context = new ScanContext(projectPath, config);

      // Instantiate parsers
      if (!quiet) {
        System.out.println("Initializing parsers...");
      }
      // Get pagination configuration from config if available
      java.util.List<String> limitingFieldPatterns = null;
      java.util.List<String> tableWhitelist = null;
      java.util.List<String> tableBlacklist = null;
      if (config != null && config.getRules() != null && 
          config.getRules().getPaginationAbuse() != null) {
        limitingFieldPatterns = config.getRules().getPaginationAbuse().getLimitingFieldPatterns();
        tableWhitelist = config.getRules().getPaginationAbuse().getTableWhitelist();
        tableBlacklist = config.getRules().getPaginationAbuse().getTableBlacklist();
      }
      XmlMapperParser xmlParser = new XmlMapperParser(limitingFieldPatterns, tableWhitelist, tableBlacklist);
      AnnotationParser annotationParser = new AnnotationParser();
      QueryWrapperScanner wrapperScanner = new QueryWrapperScanner();

      // Create validator
      if (!quiet) {
        System.out.println("Initializing SQL safety validator...");
      }
      com.footstone.sqlguard.validator.DefaultSqlSafetyValidator validator = createValidator(config);

      // Create semantic analysis service if enabled
      MyBatisSemanticAnalysisService semanticService = null;
      if (!disableSemanticAnalysis) {
        if (!quiet) {
          System.out.println("Initializing MyBatis semantic analysis...");
        }
        MyBatisAnalysisConfig semanticConfig = MyBatisAnalysisConfig.createDefault();
        semanticService = new MyBatisSemanticAnalysisService(semanticConfig);
      } else {
        if (!quiet) {
          System.out.println("MyBatis semantic analysis disabled");
        }
      }

      // Create scanner with validator and semantic analysis
      SqlScanner scanner = new SqlScanner(xmlParser, annotationParser, wrapperScanner, validator, semanticService);

      // Find modules to scan
      List<Path> modulesToScan = findModulesToScan();
      
      // Execute scan
      ScanReport aggregatedReport = new ScanReport();
      
      for (Path modulePath : modulesToScan) {
        if (!quiet) {
          System.out.println("Scanning module: " + modulePath);
          if (verbose) {
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
          }
        }
        
        ScanContext moduleContext = new ScanContext(modulePath, config);
        ScanReport moduleReport = scanner.scan(moduleContext);
        
        // Aggregate results
        for (com.footstone.sqlguard.scanner.model.SqlEntry entry : moduleReport.getEntries()) {
          aggregatedReport.addEntry(entry);
        }
        for (com.footstone.sqlguard.scanner.model.WrapperUsage usage : moduleReport.getWrapperUsages()) {
          aggregatedReport.addWrapperUsage(usage);
        }
        
        if (!quiet && verbose) {
          System.out.println("  Module SQL statements: " + moduleReport.getEntries().size());
          System.out.println("  Module wrapper usages: " + moduleReport.getWrapperUsages().size());
        }
      }
      
      if (!quiet) {
        if (verbose) {
          System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
          System.out.println("✓ All modules scanned!");
          System.out.println("  Total modules: " + modulesToScan.size());
          System.out.println("  Total SQL statements: " + aggregatedReport.getEntries().size());
          System.out.println("  Total wrapper usages: " + aggregatedReport.getWrapperUsages().size());
        } else {
          System.out.println("Scan complete: " + aggregatedReport.getEntries().size() + " SQL statements found across " + modulesToScan.size() + " module(s)");
        }
      }
      
      ScanReport report = aggregatedReport;
      
      // Calculate statistics for the aggregated report
      report.calculateStatistics();

      // Generate report and determine exit code
      return generateReportAndGetExitCode(report);

    } catch (ValidationException e) {
      System.err.println("Validation error: " + e.getMessage());
      return 1;
    } catch (IOException e) {
      System.err.println("I/O error: " + e.getMessage());
      if (!quiet) {
        e.printStackTrace();
      }
      return 1;
    } catch (ConfigLoadException e) {
      System.err.println("Configuration error: " + e.getMessage());
      if (!quiet) {
        e.printStackTrace();
      }
      return 1;
    } catch (Exception e) {
      System.err.println("Unexpected error: " + e.getMessage());
      if (!quiet) {
        e.printStackTrace();
      }
      return 1;
    }
  }

  /**
   * Validates all command-line inputs with fail-fast behavior.
   *
   * <p>Validation rules:</p>
   * <ul>
   *   <li>projectPath must exist and be a directory</li>
   *   <li>configFile (if provided) must exist</li>
   *   <li>outputFormat must be "console" or "html"</li>
   *   <li>HTML format requires outputFile</li>
   *   <li>outputFile parent directory must be writable</li>
   * </ul>
   *
   * @throws ValidationException if any validation fails
   */
  void validateInputs() throws ValidationException {
    // Validate project path
    if (!Files.exists(projectPath)) {
      throw new ValidationException(
          "Project path does not exist: " + projectPath + "\n" +
          "Please provide a valid project root directory."
      );
    }
    if (!Files.isDirectory(projectPath)) {
      throw new ValidationException(
          "Project path is not a directory: " + projectPath + "\n" +
          "Please provide a directory, not a file."
      );
    }

    // Validate config file if provided
    if (configFile != null && !Files.exists(configFile)) {
      throw new ValidationException(
          "Config file does not exist: " + configFile + "\n" +
          "Please provide a valid YAML configuration file."
      );
    }

    // Validate output format
    if (!outputFormat.equals("console") && !outputFormat.equals("html")) {
      throw new ValidationException(
          "Invalid output format: " + outputFormat + "\n" +
          "Valid formats: console, html"
      );
    }

    // Cross-field validation: HTML format requires output file
    if (outputFormat.equals("html") && outputFile == null) {
      throw new ValidationException(
          "HTML format requires --output-file option\n" +
          "Example: --output-format=html --output-file=report.html"
      );
    }

    // Validate output file parent directory writable
    if (outputFile != null) {
      Path parentDir = outputFile.getParent();
      if (parentDir != null && !Files.isWritable(parentDir)) {
        throw new ValidationException(
            "Output directory is not writable: " + parentDir + "\n" +
            "Please ensure you have write permissions."
        );
      }
    }
  }

  /**
   * Main method for command-line execution.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new SqlScannerCli()).execute(args);
    System.exit(exitCode);
  }

  // Getters for testing

  /**
   * Gets the project path.
   *
   * @return project path
   */
  public Path getProjectPath() {
    return projectPath;
  }

  /**
   * Gets the config file path.
   *
   * @return config file path (may be null)
   */
  public Path getConfigFile() {
    return configFile;
  }

  /**
   * Gets the output format.
   *
   * @return output format (console or html)
   */
  public String getOutputFormat() {
    return outputFormat;
  }

  /**
   * Gets the output file path.
   *
   * @return output file path (may be null)
   */
  public Path getOutputFile() {
    return outputFile;
  }

  /**
   * Checks if fail-on-critical is enabled.
   *
   * @return true if enabled
   */
  public boolean isFailOnCritical() {
    return failOnCritical;
  }

  /**
   * Checks if quiet mode is enabled.
   *
   * @return true if enabled
   */
  public boolean isQuiet() {
    return quiet;
  }

  public boolean isVerbose() {
    return verbose;
  }

  public boolean isRecursive() {
    return recursive;
  }

  // Setters for testing

  /**
   * Sets the project path (for testing).
   *
   * @param projectPath project path
   */
  public void setProjectPath(Path projectPath) {
    this.projectPath = projectPath;
  }

  /**
   * Sets the config file (for testing).
   *
   * @param configFile config file path
   */
  public void setConfigFile(Path configFile) {
    this.configFile = configFile;
  }

  /**
   * Sets the output format (for testing).
   *
   * @param outputFormat output format
   */
  public void setOutputFormat(String outputFormat) {
    this.outputFormat = outputFormat;
  }

  /**
   * Sets the output file (for testing).
   *
   * @param outputFile output file path
   */
  public void setOutputFile(Path outputFile) {
    this.outputFile = outputFile;
  }

  /**
   * Sets the fail-on-critical flag (for testing).
   *
   * @param failOnCritical true to enable
   */
  public void setFailOnCritical(boolean failOnCritical) {
    this.failOnCritical = failOnCritical;
  }

  /**
   * Sets the quiet flag (for testing).
   *
   * @param quiet true to enable
   */
  public void setQuiet(boolean quiet) {
    this.quiet = quiet;
  }

  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }

  public void setRecursive(boolean recursive) {
    this.recursive = recursive;
  }

  /**
   * Loads SQL Guard configuration from file or defaults.
   *
   * @return loaded configuration
   * @throws IOException if file reading fails
   * @throws ConfigLoadException if YAML parsing fails
   */
  SqlGuardConfig loadConfiguration() throws IOException, ConfigLoadException {
    if (configFile != null) {
      if (!quiet) {
        System.out.println("Loading configuration from: " + configFile);
      }
      YamlConfigLoader loader = new YamlConfigLoader();
      return loader.loadFromFile(configFile);
    } else {
      if (!quiet) {
        System.out.println("Using default configuration");
      }
      return SqlGuardConfigDefaults.getDefault();
    }
  }

  /**
   * Finds all modules to scan based on recursive flag.
   * 
   * @return list of module paths to scan
   * @throws IOException if directory traversal fails
   */
  private List<Path> findModulesToScan() throws IOException {
    List<Path> modules = new ArrayList<>();
    
    if (recursive) {
      // Find all Maven sub-modules recursively
      if (!quiet && verbose) {
        System.out.println("Searching for Maven sub-modules recursively...");
      }
      findMavenModules(projectPath, modules);
      
      if (!quiet && verbose) {
        System.out.println("Found " + modules.size() + " Maven module(s):");
        for (Path module : modules) {
          System.out.println("  - " + module);
        }
      }
    } else {
      // Single module scan
      modules.add(projectPath);
    }
    
    return modules;
  }
  
  /**
   * Recursively finds all Maven modules (directories containing pom.xml with src directory).
   * 
   * @param dir directory to search
   * @param modules list to accumulate found modules
   * @throws IOException if directory traversal fails
   */
  private void findMavenModules(Path dir, List<Path> modules) throws IOException {
    // Check if this directory is a Maven module (has pom.xml and src directory with actual source files)
    Path pomFile = dir.resolve("pom.xml");
    Path srcDir = dir.resolve("src");
    
    if (Files.exists(pomFile) && Files.isDirectory(srcDir)) {
      // Check if src directory contains actual source code (src/main/java or src/main/resources)
      Path srcMainJava = srcDir.resolve("main/java");
      Path srcMainResources = srcDir.resolve("main/resources");
      
      if (Files.isDirectory(srcMainJava) || Files.isDirectory(srcMainResources)) {
        // This is a valid Maven module with source code
        modules.add(dir);
        if (!quiet && verbose) {
          System.out.println("  Found module: " + dir);
        }
      }
    }
    
    // Recursively search subdirectories
    if (Files.isDirectory(dir)) {
      try (Stream<Path> paths = Files.list(dir)) {
        paths.filter(Files::isDirectory)
             .filter(p -> !p.getFileName().toString().startsWith("."))  // Skip hidden directories
             .filter(p -> !p.getFileName().toString().equals("target"))  // Skip target directory
             .filter(p -> !p.getFileName().toString().equals("node_modules"))  // Skip node_modules
             .forEach(subDir -> {
               try {
                 findMavenModules(subDir, modules);
               } catch (IOException e) {
                 // Log but continue scanning other directories
                 if (!quiet) {
                   System.err.println("Warning: Failed to scan directory " + subDir + ": " + e.getMessage());
                 }
               }
             });
      }
    }
  }

  /**
   * Configures logging for quiet mode (ERROR level only).
   */
  private void configureQuietLogging() {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    
    // Set root logger to ERROR
    Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
    rootLogger.setLevel(Level.ERROR);
    
    // Set application logger to ERROR (overrides logback.xml DEBUG setting)
    Logger appLogger = loggerContext.getLogger("com.footstone.sqlguard");
    appLogger.setLevel(Level.ERROR);
  }

  /**
   * Configures logging for verbose mode (DEBUG level for better visibility).
   */
  private void configureVerboseLogging() {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    
    // Set application logger to DEBUG for detailed output
    Logger appLogger = loggerContext.getLogger("com.footstone.sqlguard");
    appLogger.setLevel(Level.DEBUG);
    
    System.out.println("Verbose mode enabled - showing detailed scan progress");
  }

  /**
   * Creates and configures the SQL safety validator with all rule checkers.
   *
   * @param config the SQL Guard configuration
   * @return configured validator instance
   */
  private com.footstone.sqlguard.validator.DefaultSqlSafetyValidator createValidator(SqlGuardConfig config) {
    // Create JSqlParser facade in lenient mode to allow raw SQL checkers to run
    // even when SQL parsing fails (e.g., MySQL-specific syntax like INTO OUTFILE)
    com.footstone.sqlguard.parser.JSqlParserFacade facade = 
        new com.footstone.sqlguard.parser.JSqlParserFacade(true); // lenient mode

    // Create all rule checkers
    java.util.List<com.footstone.sqlguard.validator.rule.RuleChecker> checkers = createAllCheckers(config);

    // Create orchestrator
    com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator orchestrator = 
        new com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator(checkers);

    // Create deduplication filter
    com.footstone.sqlguard.validator.SqlDeduplicationFilter filter = 
        new com.footstone.sqlguard.validator.SqlDeduplicationFilter();

    // Create and return validator
    return new com.footstone.sqlguard.validator.DefaultSqlSafetyValidator(
        facade, checkers, orchestrator, filter
    );
  }

  /**
   * Creates all rule checkers with configuration.
   *
   * @param config the SQL Guard configuration
   * @return list of configured rule checkers
   */
  private java.util.List<com.footstone.sqlguard.validator.rule.RuleChecker> createAllCheckers(SqlGuardConfig config) {
    java.util.List<com.footstone.sqlguard.validator.rule.RuleChecker> checkers = new ArrayList<>();

    // Checker 1: NoWhereClauseChecker
    com.footstone.sqlguard.validator.rule.impl.NoWhereClauseConfig noWhereConfig = 
        new com.footstone.sqlguard.validator.rule.impl.NoWhereClauseConfig();
    noWhereConfig.setEnabled(true);
    checkers.add(new com.footstone.sqlguard.validator.rule.impl.NoWhereClauseChecker(noWhereConfig));

    // Checker 2: DummyConditionChecker
    com.footstone.sqlguard.validator.rule.impl.DummyConditionConfig dummyConfig = 
        new com.footstone.sqlguard.validator.rule.impl.DummyConditionConfig();
    dummyConfig.setEnabled(true);
    checkers.add(new com.footstone.sqlguard.validator.rule.impl.DummyConditionChecker(dummyConfig));

    // Checker 3: BlacklistFieldChecker
    com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig blacklistConfig = 
        new com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig();
    blacklistConfig.setEnabled(true);
    checkers.add(new com.footstone.sqlguard.validator.rule.impl.BlacklistFieldChecker(blacklistConfig));

    // Checker 4: WhitelistFieldChecker
    com.footstone.sqlguard.validator.rule.impl.WhitelistFieldsConfig whitelistConfig = 
        new com.footstone.sqlguard.validator.rule.impl.WhitelistFieldsConfig();
    whitelistConfig.setEnabled(true);
    checkers.add(new com.footstone.sqlguard.validator.rule.impl.WhitelistFieldChecker(whitelistConfig));

    // ==================== SQL Injection Checkers (Phase 1) ====================
    
    // Checker 5: MultiStatementChecker - Detects multi-statement SQL injection
    com.footstone.sqlguard.validator.rule.impl.MultiStatementConfig multiStatementConfig = 
        new com.footstone.sqlguard.validator.rule.impl.MultiStatementConfig();
    checkers.add(new com.footstone.sqlguard.validator.rule.impl.MultiStatementChecker(multiStatementConfig));

    // Checker 6: SetOperationChecker - Detects UNION/MINUS/EXCEPT/INTERSECT injection
    com.footstone.sqlguard.validator.rule.impl.SetOperationConfig setOperationConfig = 
        new com.footstone.sqlguard.validator.rule.impl.SetOperationConfig();
    checkers.add(new com.footstone.sqlguard.validator.rule.impl.SetOperationChecker(setOperationConfig));

    // Checker 7: SqlCommentChecker - Detects comment-based SQL injection
    com.footstone.sqlguard.validator.rule.impl.SqlCommentConfig sqlCommentConfig = 
        new com.footstone.sqlguard.validator.rule.impl.SqlCommentConfig();
    checkers.add(new com.footstone.sqlguard.validator.rule.impl.SqlCommentChecker(sqlCommentConfig));

    // Checker 8: IntoOutfileChecker - Detects MySQL file write operations
    com.footstone.sqlguard.validator.rule.impl.IntoOutfileConfig intoOutfileConfig = 
        new com.footstone.sqlguard.validator.rule.impl.IntoOutfileConfig();
    checkers.add(new com.footstone.sqlguard.validator.rule.impl.IntoOutfileChecker(intoOutfileConfig));

    // ==================== Dangerous Operations Checkers (Phase 1) ====================
    
    // Checker 9: DdlOperationChecker - Detects DDL operations (CREATE/ALTER/DROP/TRUNCATE)
    com.footstone.sqlguard.validator.rule.impl.DdlOperationConfig ddlOperationConfig = 
        new com.footstone.sqlguard.validator.rule.impl.DdlOperationConfig();
    checkers.add(new com.footstone.sqlguard.validator.rule.impl.DdlOperationChecker(ddlOperationConfig));

    // Checker 10: DangerousFunctionChecker - Detects dangerous functions (load_file, sys_exec, sleep, etc.)
    com.footstone.sqlguard.validator.rule.impl.DangerousFunctionConfig dangerousFunctionConfig = 
        new com.footstone.sqlguard.validator.rule.impl.DangerousFunctionConfig();
    checkers.add(new com.footstone.sqlguard.validator.rule.impl.DangerousFunctionChecker(dangerousFunctionConfig));

    // Checker 11: CallStatementChecker - Detects stored procedure calls (CALL/EXECUTE/EXEC)
    com.footstone.sqlguard.validator.rule.impl.CallStatementConfig callStatementConfig = 
        new com.footstone.sqlguard.validator.rule.impl.CallStatementConfig();
    checkers.add(new com.footstone.sqlguard.validator.rule.impl.CallStatementChecker(callStatementConfig));

    // ==================== Access Control Checkers (Phase 1) ====================
    
    // Checker 12: MetadataStatementChecker - Detects metadata disclosure (SHOW/DESCRIBE/USE)
    com.footstone.sqlguard.validator.rule.impl.MetadataStatementConfig metadataConfig = 
        new com.footstone.sqlguard.validator.rule.impl.MetadataStatementConfig();
    checkers.add(new com.footstone.sqlguard.validator.rule.impl.MetadataStatementChecker(metadataConfig));

    // Checker 13: SetStatementChecker - Detects session variable modification (SET statements)
    com.footstone.sqlguard.validator.rule.impl.SetStatementConfig setStatementConfig = 
        new com.footstone.sqlguard.validator.rule.impl.SetStatementConfig();
    checkers.add(new com.footstone.sqlguard.validator.rule.impl.SetStatementChecker(setStatementConfig));

    // Checker 14: DeniedTableChecker - Enforces table-level access control blacklist
    com.footstone.sqlguard.validator.rule.impl.DeniedTableConfig deniedTableConfig = 
        new com.footstone.sqlguard.validator.rule.impl.DeniedTableConfig();
    // Configure denied tables with wildcard patterns
    deniedTableConfig.setDeniedTables(java.util.Arrays.asList("sys_*", "admin_*", "audit_log", "sensitive_data"));
    checkers.add(new com.footstone.sqlguard.validator.rule.impl.DeniedTableChecker(deniedTableConfig));

    // Checker 15: ReadOnlyTableChecker - Protects read-only tables from write operations
    com.footstone.sqlguard.validator.rule.impl.ReadOnlyTableConfig readOnlyTableConfig = 
        new com.footstone.sqlguard.validator.rule.impl.ReadOnlyTableConfig();
    // Configure read-only tables with wildcard patterns
    readOnlyTableConfig.setReadonlyTables(java.util.Arrays.asList("audit_log", "history_*", "compliance_records"));
    checkers.add(new com.footstone.sqlguard.validator.rule.impl.ReadOnlyTableChecker(readOnlyTableConfig));

    return checkers;
  }

  /**
   * Generates report in specified format and determines exit code.
   *
   * <p>Exit code logic:</p>
   * <ul>
   *   <li>Exit 0: Success or non-critical warnings (HIGH/MEDIUM/LOW violations)</li>
   *   <li>Exit 1: CRITICAL violations (when --fail-on-critical enabled)</li>
   * </ul>
   *
   * @param report the scan report
   * @return exit code (0=success, 1=critical violations)
   * @throws IOException if report generation fails
   */
  private int generateReportAndGetExitCode(ScanReport report) throws IOException {
    // Generate report in specified format
    if (outputFormat.equals("console")) {
      if (!quiet) {
        System.out.println("Generating console report...");
      }
      ConsoleReportGenerator consoleGen = new ConsoleReportGenerator();
      consoleGen.printToConsole(report);
    } else if (outputFormat.equals("html")) {
      if (!quiet) {
        System.out.println("Generating enhanced HTML report: " + outputFile);
      }
      EnhancedHtmlReportGenerator htmlGen = new EnhancedHtmlReportGenerator();
      htmlGen.writeToFile(report, outputFile);
      System.out.println("Enhanced HTML report generated: " + outputFile.toAbsolutePath());
    }

    // Determine exit code based on violations
    int totalViolations = report.getTotalViolations();
    boolean hasCritical = report.hasCriticalViolations();

    if (totalViolations > 0 && !quiet) {
      System.out.println(
          String.format(
              "\nScan complete: %d violations found (use --fail-on-critical to fail build on CRITICAL)",
              totalViolations
          )
      );
    } else if (!quiet) {
      System.out.println("\nScan complete: No violations found ✓");
    }

    // Exit with code 1 if --fail-on-critical and CRITICAL violations exist
    if (failOnCritical && hasCritical) {
      if (!quiet) {
        System.err.println("Build failed: CRITICAL violations detected");
      }
      return 1;
    }

    return 0;
  }
}

