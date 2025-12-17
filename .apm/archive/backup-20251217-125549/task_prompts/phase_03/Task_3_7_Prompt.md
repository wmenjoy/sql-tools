---
task_ref: "Task 3.7 - CLI Tool Implementation"
agent_assignment: "Agent_Static_Scanner"
memory_log_path: ".apm/Memory/Phase_03_Static_Scanner/Task_3_7_CLI_Tool_Implementation.md"
execution_type: "multi-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: CLI Tool Implementation

## Task Reference
Implementation Plan: **Task 3.7 - CLI Tool Implementation** assigned to **Agent_Static_Scanner**

## Context from Dependencies

**From Task 3.1 (Scanner Core Framework):**
- `SqlScanner` orchestration class coordinates all parsers
- Constructor accepts: XmlMapperParser, AnnotationParser, QueryWrapperScanner, SqlRiskEvaluator
- `scan(ScanContext context)` method returns `ScanReport`
- `ScanContext` requires: Path projectPath, SqlGuardConfig config

**From Task 3.6 (Report Generators):**
- `ConsoleReportGenerator.printToConsole(ScanReport report)` - ANSI-colored terminal output
- `HtmlReportGenerator.writeToFile(ScanReport report, Path outputPath)` - Styled HTML output
- Both generators process ScanReport from SqlScanner

**From Phase 1 Task 1.3 (Configuration):**
- `SqlGuardConfig` loaded from YAML via YamlConfigLoader
- Default configuration available if no config file provided

**Integration Approach:**
Your CLI tool will:
1. Parse command-line arguments with picocli
2. Validate inputs (paths, formats, cross-field dependencies)
3. Load configuration (YAML file or defaults)
4. Instantiate parsers and SqlScanner
5. Execute scan and generate reports
6. Return exit codes for CI/CD integration

## Objective
Implement production-ready command-line interface tool providing user-friendly SQL scanning with picocli argument parsing, fail-fast input validation, configuration loading, scan orchestration, dual-format report output, and CI/CD integration via exit codes for robust deployment in development workflows and continuous integration pipelines.

## Detailed Instructions
Complete this task in **4 exchanges, one step per response**. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: CLI Argument Parsing with Picocli

**Test First:**
Write test class `SqlScannerCliTest` in `com.footstone.sqlguard.cli` package covering:
- `testRequiredProjectPath_shouldParse()` - Args with `--project-path=/path` parse successfully
- `testOptionalConfigFile_shouldParse()` - `--config-file=config.yml` parsed correctly
- `testOutputFormatValues_shouldParseConsoleAndHtml()` - Both `--output-format=console` and `--output-format=html` valid
- `testOutputFile_shouldParse()` - `--output-file=report.html` parsed correctly
- `testFailOnCritical_shouldParseBoolean()` - `--fail-on-critical` sets boolean flag
- `testQuietFlag_shouldParse()` - `--quiet` flag parsed
- `testMissingRequired_shouldFailWithError()` - Args without `--project-path` fails with error message
- `testHelpFlag_shouldDisplayUsage()` - `--help` or `-h` displays usage and exits
- `testVersionFlag_shouldDisplayVersion()` - `--version` displays version and exits

**Expected CLI Usage:**
```bash
# Basic usage
sql-scanner --project-path=/path/to/project

# With config file
sql-scanner --project-path=/path/to/project --config-file=config.yml

# HTML output
sql-scanner --project-path=/path/to/project --output-format=html --output-file=report.html

# CI/CD mode
sql-scanner --project-path=/path/to/project --fail-on-critical --quiet

# Help
sql-scanner --help
```

**Then Implement:**
1. **Add picocli dependency** to `sql-scanner-cli/pom.xml`:
   ```xml
   <dependency>
       <groupId>info.picocli</groupId>
       <artifactId>picocli</artifactId>
       <version>4.7.5</version>
   </dependency>
   ```

2. **Create SqlScannerCli class:**
   ```java
   package com.footstone.sqlguard.cli;

   import picocli.CommandLine;
   import picocli.CommandLine.Command;
   import picocli.CommandLine.Option;
   import java.nio.file.Path;
   import java.util.concurrent.Callable;

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

       @Override
       public Integer call() throws Exception {
           // Implementation in next steps
           return 0;
       }

       public static void main(String[] args) {
           int exitCode = new CommandLine(new SqlScannerCli()).execute(args);
           System.exit(exitCode);
       }
   }
   ```

3. **Write tests using CommandLine test harness:**
   ```java
   @Test
   public void testRequiredProjectPath_shouldParse() {
       SqlScannerCli cli = new SqlScannerCli();
       CommandLine cmd = new CommandLine(cli);

       String[] args = {"--project-path", "/tmp/test-project"};
       int exitCode = cmd.execute(args);

       assertThat(exitCode).isEqualTo(0);
       assertThat(cli.getProjectPath()).isEqualTo(Path.of("/tmp/test-project"));
   }

   @Test
   public void testMissingRequired_shouldFailWithError() {
       StringWriter sw = new StringWriter();
       CommandLine cmd = new CommandLine(new SqlScannerCli())
           .setErr(new PrintWriter(sw));

       String[] args = {}; // Missing required --project-path
       int exitCode = cmd.execute(args);

       assertThat(exitCode).isEqualTo(2); // Picocli usage error code
       assertThat(sw.toString()).contains("Missing required option: '--project-path'");
   }
   ```

**Constraints:**
- Use picocli 4.7.x for Java 8 compatibility
- `--project-path` is REQUIRED
- All other options are optional with sensible defaults
- Help and version flags provided by `mixinStandardHelpOptions = true`
- Clear error messages for missing required options

### Step 2: Input Validation with Fail-Fast

**Test First:**
Write test class `InputValidationTest` covering:
- `testProjectPathNotExists_shouldFail()` - Non-existent path fails with clear error
- `testProjectPathIsFile_shouldFail()` - File instead of directory fails
- `testConfigFileNotExists_shouldFail()` - Non-existent config file fails
- `testOutputFormatInvalid_shouldFail()` - Invalid format (e.g., "xml") fails
- `testHtmlFormatWithoutOutputFile_shouldFail()` - HTML format requires --output-file
- `testOutputFileNotWritable_shouldFail()` - Read-only parent directory fails
- `testAllValidInputs_shouldPass()` - Valid inputs pass all validation

**Validation Rules:**
1. **projectPath**: Must exist and be a directory
2. **configFile**: If provided, must exist
3. **outputFormat**: Must be "console" or "html"
4. **outputFile**: Required if outputFormat="html", parent directory must be writable
5. **Cross-field**: HTML format requires output file

**Then Implement:**
1. **Add validation in call() method:**
   ```java
   @Override
   public Integer call() throws Exception {
       try {
           validateInputs();
           // Scan execution (next step)
           return 0;
       } catch (ValidationException e) {
           System.err.println("Validation error: " + e.getMessage());
           return 1;
       } catch (Exception e) {
           System.err.println("Error: " + e.getMessage());
           if (!quiet) {
               e.printStackTrace();
           }
           return 1;
       }
   }

   private void validateInputs() throws ValidationException {
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
   ```

2. **Create ValidationException:**
   ```java
   public class ValidationException extends Exception {
       public ValidationException(String message) {
           super(message);
       }
   }
   ```

3. **Write comprehensive validation tests:**
   - Test each validation rule independently
   - Test combinations (e.g., HTML format with output file)
   - Verify error messages are clear and actionable
   - Test edge cases (symbolic links, spaces in paths, Unicode)

**Constraints:**
- Fail-fast: Validate ALL inputs before scanning
- Clear error messages with suggestions
- Exit code 1 for validation failures
- No execution if validation fails

### Step 3: Scan Orchestration and Configuration Loading

**Test First:**
Write test class `ScanOrchestrationTest` covering:
- `testScanExecution_shouldLoadDefaultConfig()` - No config file uses defaults
- `testScanExecution_shouldLoadYamlConfig()` - Config file loaded correctly
- `testScanExecution_shouldInstantiateParsers()` - All parsers created
- `testScanExecution_shouldProduceScanReport()` - Scan returns ScanReport
- `testParserException_shouldHandleGracefully()` - Parse errors logged, exit code 1
- `testProgressLogging_shouldLogSteps()` - Progress logged unless --quiet
- `testQuietMode_shouldSuppressProgress()` - --quiet suppresses logs

**Sample Test Project:**
Create `src/test/resources/test-project/` with:
- `src/main/resources/mappers/UserMapper.xml` (simple XML mapper)
- `src/main/java/com/example/UserMapper.java` (annotation mapper)
- `src/main/java/com/example/UserService.java` (QueryWrapper usage)

**Then Implement:**
1. **Add scan orchestration to call():**
   ```java
   @Override
   public Integer call() throws Exception {
       try {
           validateInputs();

           // Configure logging
           if (quiet) {
               configureQuietLogging();
           }

           // Load configuration
           SqlGuardConfig config = loadConfiguration();
           log.info("Configuration loaded: {} rules enabled", config.getEnabledRulesCount());

           // Create scan context
           ScanContext context = new ScanContext(projectPath, config);

           // Instantiate parsers
           log.info("Initializing parsers...");
           XmlMapperParser xmlParser = new XmlMapperParser();
           AnnotationParser annotationParser = new AnnotationParser();
           QueryWrapperScanner wrapperScanner = new QueryWrapperScanner();
           SqlRiskEvaluator evaluator = new SqlRiskEvaluator(config);

           // Create scanner
           SqlScanner scanner = new SqlScanner(
               xmlParser,
               annotationParser,
               wrapperScanner,
               evaluator
           );

           // Execute scan
           log.info("Scanning project: {}", projectPath);
           ScanReport report = scanner.scan(context);
           log.info("Scan complete: {} SQL statements found", report.getTotalSqlCount());

           // Generate report (next step)
           return generateReportAndGetExitCode(report);

       } catch (ValidationException e) {
           System.err.println("Validation error: " + e.getMessage());
           return 1;
       } catch (IOException e) {
           System.err.println("I/O error: " + e.getMessage());
           return 1;
       } catch (ParseException e) {
           System.err.println("Parse error: " + e.getMessage());
           return 1;
       } catch (Exception e) {
           System.err.println("Unexpected error: " + e.getMessage());
           if (!quiet) {
               e.printStackTrace();
           }
           return 1;
       }
   }

   private SqlGuardConfig loadConfiguration() throws IOException {
       if (configFile != null) {
           log.info("Loading configuration from: {}", configFile);
           return YamlConfigLoader.loadFromFile(configFile);
       } else {
           log.info("Using default configuration");
           return SqlGuardConfigDefaults.getDefault();
       }
   }

   private void configureQuietLogging() {
       // Set root logger to ERROR level
       LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
       Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
       rootLogger.setLevel(Level.ERROR);
   }
   ```

2. **Write integration test with real scan:**
   ```java
   @Test
   public void testScanExecution_shouldProduceScanReport() throws Exception {
       // Create test project structure
       Path testProject = Files.createTempDirectory("test-project");
       createTestMappers(testProject);

       SqlScannerCli cli = new SqlScannerCli();
       cli.setProjectPath(testProject);

       int exitCode = cli.call();

       assertThat(exitCode).isEqualTo(0);
       // Verify scan report generated (captured via mocking or test hooks)
   }
   ```

**Constraints:**
- Reuse existing parsers (no duplication)
- Handle all exception types gracefully
- Progress logging respects --quiet flag
- Configuration loading supports both YAML and defaults

### Step 4: Report Output and CI/CD Integration

**Test First:**
Write test class `ReportOutputTest` covering:
- `testConsoleOutput_shouldPrintToStdout()` - Console format prints to stdout
- `testHtmlOutput_shouldWriteFile()` - HTML format writes to file
- `testFailOnCritical_withCritical_shouldExitOne()` - CRITICAL violations + flag = exit 1
- `testFailOnCritical_withoutCritical_shouldExitZero()` - No CRITICAL = exit 0
- `testFailOnCritical_withHighMedium_shouldExitZero()` - Only HIGH/MEDIUM = exit 0
- `testQuietMode_onlyErrorsShown()` - --quiet shows only errors
- `testExitCodes_shouldMatchCiConvention()` - 0=success/warnings, 1=critical/errors

**CI/CD Exit Code Convention:**
- **Exit 0:** Success or non-critical warnings (HIGH/MEDIUM/LOW violations)
- **Exit 1:** CRITICAL violations (when --fail-on-critical) OR errors (parse failures, I/O errors)

**Then Implement:**
1. **Add report generation and exit code logic:**
   ```java
   private int generateReportAndGetExitCode(ScanReport report) throws IOException {
       // Generate report in specified format
       if (outputFormat.equals("console")) {
           log.info("Generating console report...");
           ConsoleReportGenerator consoleGen = new ConsoleReportGenerator();
           consoleGen.printToConsole(report);
       } else if (outputFormat.equals("html")) {
           log.info("Generating HTML report: {}", outputFile);
           HtmlReportGenerator htmlGen = new HtmlReportGenerator();
           htmlGen.writeToFile(report, outputFile);
           System.out.println("HTML report generated: " + outputFile.toAbsolutePath());
       }

       // Determine exit code
       int criticalCount = report.getViolationCountByLevel(RiskLevel.CRITICAL);

       if (failOnCritical && criticalCount > 0) {
           System.err.println(
               String.format(
                   "\n%d CRITICAL violations found - failing build",
                   criticalCount
               )
           );
           return 1;
       }

       // Success even with HIGH/MEDIUM/LOW violations
       int totalViolations = report.getTotalViolations();
       if (totalViolations > 0) {
           System.out.println(
               String.format(
                   "\nScan complete: %d violations found (use --fail-on-critical to fail build on CRITICAL)",
                   totalViolations
               )
           );
       } else {
           System.out.println("\nScan complete: No violations found ✓");
       }

       return 0;
   }
   ```

2. **Write CI/CD integration tests:**
   ```java
   @Test
   public void testFailOnCritical_withCritical_shouldExitOne() {
       ScanReport report = createReportWithCriticalViolations();

       SqlScannerCli cli = new SqlScannerCli();
       cli.setFailOnCritical(true);

       int exitCode = cli.generateReportAndGetExitCode(report);

       assertThat(exitCode).isEqualTo(1);
   }

   @Test
   public void testFailOnCritical_withOnlyHighMedium_shouldExitZero() {
       ScanReport report = createReportWithHighMediumViolations();

       SqlScannerCli cli = new SqlScannerCli();
       cli.setFailOnCritical(true); // Flag enabled but no CRITICAL

       int exitCode = cli.generateReportAndGetExitCode(report);

       assertThat(exitCode).isEqualTo(0); // Build continues
   }
   ```

3. **Test with real CI/CD workflow:**
   ```bash
   #!/bin/bash
   # Test CI/CD integration

   # Should succeed (no violations)
   ./sql-scanner --project-path=clean-project --fail-on-critical
   echo "Exit code: $?" # Should be 0

   # Should succeed (HIGH violations only)
   ./sql-scanner --project-path=high-violations --fail-on-critical
   echo "Exit code: $?" # Should be 0

   # Should fail (CRITICAL violations)
   ./sql-scanner --project-path=critical-violations --fail-on-critical
   echo "Exit code: $?" # Should be 1
   ```

**Constraints:**
- Exit 0: Success or non-critical warnings
- Exit 1: CRITICAL violations (with --fail-on-critical) OR errors
- Clear summary messages
- --quiet respects CI/CD headless mode
- Standard Unix exit code conventions

## Expected Output

**Deliverables:**
1. `SqlScannerCli` class with picocli integration (400+ lines)
2. `ValidationException` helper class
3. Main method for CLI entry point
4. Configuration loading (YAML or defaults)
5. Parser instantiation and scan orchestration
6. Dual-format report generation (console/HTML)
7. CI/CD exit code logic (0=success/warnings, 1=critical/errors)
8. 4 test classes with 30+ tests
9. Test project resources for integration testing

**Success Criteria:**
- ✅ All command-line options parsed correctly
- ✅ Input validation comprehensive with clear error messages
- ✅ Configuration loaded from YAML or defaults
- ✅ All parsers instantiated and executed
- ✅ Console and HTML reports generated correctly
- ✅ Exit codes follow CI/CD conventions (0=success, 1=critical/error)
- ✅ --quiet mode suppresses non-error output
- ✅ --fail-on-critical enables build failure on CRITICAL violations
- ✅ Error handling graceful with meaningful messages
- ✅ All tests passing (no failures, no errors)

**File Locations:**
- CLI: `sql-scanner-cli/src/main/java/com/footstone/sqlguard/cli/SqlScannerCli.java`
- Exception: `sql-scanner-cli/src/main/java/com/footstone/sqlguard/cli/ValidationException.java`
- Tests: `sql-scanner-cli/src/test/java/com/footstone/sqlguard/cli/*Test.java`
- Test resources: `sql-scanner-cli/src/test/resources/test-project/**`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_03_Static_Scanner/Task_3_7_CLI_Tool_Implementation.md`

Follow `.apm/guides/Memory_Log_Guide.md` instructions including:
- Task completion status
- All deliverables created with file paths
- Test results (total count, pass/fail)
- CLI usage examples
- CI/CD integration approach
- Exit code convention documentation
- Error handling strategy
