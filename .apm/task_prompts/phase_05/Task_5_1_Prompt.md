---
agent: Agent_Build_Tools
task_ref: Task 5.1
depends_on:
  - Task 3.7 (CLI Tool Implementation)
  - Task 3.8 (Validator Integration)
ad_hoc_delegation: false
---

# Task 5.1 - Maven Plugin Implementation

## Objective
Implement Maven plugin for SQL Safety Guard providing CI/CD integration via `mvn sqlguard:scan` goal, leveraging sql-scanner-core static analysis engine with configurable parameters (project path, config file, output format, fail-on-critical), generating Console/HTML reports, and enabling build failure on critical violations for automated quality gates in Maven-based projects.

## Context

**Maven Plugin Architecture**: Mojo (Maven Plain Old Java Object) pattern provides goal execution framework with @Parameter annotations for configuration injection, @Component for dependency injection, and AbstractMojo lifecycle hooks.

**Static Scanner Integration**: Reuses Phase 3's sql-scanner-core and DefaultSqlSafetyValidator infrastructure without modification, wrapping CLI functionality in Maven-native interface.

**CI/CD Integration**: Maven build lifecycle integration enables fail-fast SQL validation at `verify` or `test` phase, preventing dangerous SQL from reaching production.

## Dependencies

### Input from Phase 3:
- sql-scanner-core (complete static scanner, 273 tests passing) ✅
- sql-scanner-cli (CLI tool with SqlScannerCli, 37 tests passing) ✅
- DefaultSqlSafetyValidator (4 core checkers) ✅
- ReportProcessor + ConsoleReportGenerator + HtmlReportGenerator ✅

### Independence:
- Task 5.1 is independent of Task 5.2 (Gradle Plugin)
- Can execute in parallel with Task 5.2

## Implementation Steps

### Step 1: Maven Plugin Mojo TDD
**Goal**: Create SqlGuardScanMojo with Maven plugin infrastructure

**Tasks**:
1. Add Maven Plugin dependencies to `sql-scanner-maven/pom.xml`:
   ```xml
   <dependency>
       <groupId>org.apache.maven</groupId>
       <artifactId>maven-plugin-api</artifactId>
       <version>3.9.5</version>
       <scope>provided</scope>
   </dependency>
   <dependency>
       <groupId>org.apache.maven.plugin-tools</groupId>
       <artifactId>maven-plugin-annotations</artifactId>
       <version>3.10.2</version>
       <scope>provided</scope>
   </dependency>
   <dependency>
       <groupId>com.footstone</groupId>
       <artifactId>sql-scanner-core</artifactId>
       <version>${project.version}</version>
   </dependency>
   ```

2. Configure maven-plugin-plugin for metadata generation:
   ```xml
   <plugin>
       <groupId>org.apache.maven.plugins</groupId>
       <artifactId>maven-plugin-plugin</artifactId>
       <version>3.10.2</version>
       <configuration>
           <goalPrefix>sqlguard</goalPrefix>
       </configuration>
   </plugin>
   ```

3. Create `SqlGuardScanMojo` class:
   ```java
   @Mojo(name = "scan", defaultPhase = LifecyclePhase.VERIFY)
   public class SqlGuardScanMojo extends AbstractMojo {
       @Parameter(property = "sqlguard.projectPath",
                  defaultValue = "${project.basedir}")
       private File projectPath;

       @Parameter(property = "sqlguard.configFile")
       private File configFile;

       @Parameter(property = "sqlguard.outputFormat",
                  defaultValue = "console")
       private String outputFormat; // console, html, both

       @Parameter(property = "sqlguard.outputFile")
       private File outputFile;

       @Parameter(property = "sqlguard.failOnCritical",
                  defaultValue = "false")
       private boolean failOnCritical;

       @Parameter(property = "sqlguard.skip",
                  defaultValue = "false")
       private boolean skip;

       @Override
       public void execute() throws MojoExecutionException, MojoFailureException {
           if (skip) {
               getLog().info("SQL Guard scan skipped");
               return;
           }

           // Validate parameters
           // Execute scan
           // Generate reports
           // Handle violations
       }
   }
   ```

**Test Requirements**:
- `SqlGuardScanMojoTest.java` (12 tests):
  - testMojoAnnotations_shouldHaveCorrectGoalName()
  - testMojoAnnotations_shouldHaveVerifyPhase()
  - testParameterDefaults_shouldUseProjectBasedir()
  - testParameterDefaults_outputFormat_shouldBeConsole()
  - testParameterDefaults_failOnCritical_shouldBeFalse()
  - testParameterDefaults_skip_shouldBeFalse()
  - testSkip_true_shouldNotExecuteScan()
  - testProjectPath_null_shouldThrowException()
  - testProjectPath_notExists_shouldThrowException()
  - testConfigFile_notExists_shouldThrowException()
  - testOutputFormat_invalid_shouldThrowException()
  - testExecute_shouldCallScanner()

**Files to Create**:
- `sql-scanner-maven/src/main/java/com/footstone/sqlguard/maven/SqlGuardScanMojo.java`
- `sql-scanner-maven/src/test/java/com/footstone/sqlguard/maven/SqlGuardScanMojoTest.java`

---

### Step 2: Scanner Invocation Implementation
**Goal**: Integrate sql-scanner-core into Mojo execute() method

**Tasks**:
1. Implement parameter validation:
   ```java
   private void validateParameters() throws MojoExecutionException {
       if (projectPath == null || !projectPath.exists()) {
           throw new MojoExecutionException(
               "Project path does not exist: " + projectPath);
       }

       if (configFile != null && !configFile.exists()) {
           throw new MojoExecutionException(
               "Config file does not exist: " + configFile);
       }

       if (!Arrays.asList("console", "html", "both")
               .contains(outputFormat)) {
           throw new MojoExecutionException(
               "Invalid output format: " + outputFormat);
       }
   }
   ```

2. Load configuration (reuse sql-scanner-cli approach):
   ```java
   private SqlGuardConfig loadConfiguration() throws MojoExecutionException {
       if (configFile != null) {
           try {
               return SqlGuardConfig.loadFromYaml(
                   Files.newInputStream(configFile.toPath()));
           } catch (IOException e) {
               throw new MojoExecutionException(
                   "Failed to load config file", e);
           }
       }
       // Return default configuration
       return SqlGuardConfig.getDefault();
   }
   ```

3. Create and execute scanner:
   ```java
   private ScanReport executeScan() throws MojoExecutionException {
       try {
           // Create validator
           DefaultSqlSafetyValidator validator = createValidator(config);

           // Create scanner
           SqlScanner scanner = new SqlScanner(
               projectPath.toPath(),
               validator
           );

           // Execute scan
           ScanReport report = scanner.scan();
           return report;

       } catch (Exception e) {
           throw new MojoExecutionException("Scan execution failed", e);
       }
   }
   ```

**Test Requirements**:
- `ScannerInvocationTest.java` (14 tests):
  - testValidateParameters_validProjectPath_shouldPass()
  - testValidateParameters_nullProjectPath_shouldThrow()
  - testValidateParameters_nonExistentProjectPath_shouldThrow()
  - testValidateParameters_validConfigFile_shouldPass()
  - testValidateParameters_nonExistentConfigFile_shouldThrow()
  - testValidateParameters_validOutputFormat_shouldPass()
  - testValidateParameters_invalidOutputFormat_shouldThrow()
  - testLoadConfiguration_withFile_shouldLoadYaml()
  - testLoadConfiguration_noFile_shouldReturnDefault()
  - testLoadConfiguration_invalidYaml_shouldThrow()
  - testExecuteScan_validProject_shouldReturnReport()
  - testExecuteScan_emptyProject_shouldReturnEmptyReport()
  - testExecuteScan_withViolations_shouldIncludeInReport()
  - testCreateValidator_shouldUse4CoreCheckers()

**Files to Create**:
- `sql-scanner-maven/src/test/java/com/footstone/sqlguard/maven/ScannerInvocationTest.java`

---

### Step 3: Report Generation Implementation
**Goal**: Generate Console and/or HTML reports based on outputFormat

**Tasks**:
1. Implement report generation:
   ```java
   private void generateReports(ScanReport report)
           throws MojoExecutionException {
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

   private void generateConsoleReport(ScanReport report) throws IOException {
       ConsoleReportGenerator generator = new ConsoleReportGenerator();
       String consoleReport = generator.generate(report);

       // Output to Maven log (getLog().info())
       for (String line : consoleReport.split("\n")) {
           if (line.contains("[CRITICAL]") || line.contains("[HIGH]")) {
               getLog().error(line);
           } else if (line.contains("[MEDIUM]")) {
               getLog().warn(line);
           } else {
               getLog().info(line);
           }
       }
   }

   private void generateHtmlReport(ScanReport report) throws IOException {
       HtmlReportGenerator generator = new HtmlReportGenerator();
       String htmlReport = generator.generate(report);

       File htmlFile = (outputFile != null)
           ? outputFile
           : new File(projectPath, "target/sqlguard-report.html");

       Files.write(htmlFile.toPath(), htmlReport.getBytes());
       getLog().info("HTML report generated: " + htmlFile.getAbsolutePath());
   }
   ```

**Test Requirements**:
- `ReportGenerationTest.java` (12 tests):
  - testGenerateConsoleReport_noViolations_shouldOutputSuccess()
  - testGenerateConsoleReport_withViolations_shouldOutputDetails()
  - testGenerateConsoleReport_shouldUseMavenLogger()
  - testGenerateConsoleReport_criticalViolations_shouldUseError()
  - testGenerateConsoleReport_highViolations_shouldUseError()
  - testGenerateConsoleReport_mediumViolations_shouldUseWarn()
  - testGenerateHtmlReport_shouldCreateFile()
  - testGenerateHtmlReport_defaultLocation_shouldUseTarget()
  - testGenerateHtmlReport_customLocation_shouldUseOutputFile()
  - testGenerateBothReports_shouldOutputConsoleAndHtml()
  - testReportGeneration_largeScan_shouldComplete()
  - testReportGeneration_error_shouldThrowMojoException()

**Files to Create**:
- `sql-scanner-maven/src/test/java/com/footstone/sqlguard/maven/ReportGenerationTest.java`

---

### Step 4: Violation Handling and Build Failure
**Goal**: Handle critical violations and fail build when failOnCritical=true

**Tasks**:
1. Implement violation handling:
   ```java
   private void handleViolations(ScanReport report)
           throws MojoFailureException {
       if (!failOnCritical) {
           return;
       }

       if (report.hasCriticalViolations()) {
           String message = String.format(
               "SQL Safety Guard detected %d CRITICAL violation(s). " +
               "Build failed. See report for details.",
               report.getViolationCount(RiskLevel.CRITICAL)
           );
           throw new MojoFailureException(message);
       }
   }
   ```

2. Update execute() method to integrate all steps:
   ```java
   @Override
   public void execute() throws MojoExecutionException, MojoFailureException {
       if (skip) {
           getLog().info("SQL Guard scan skipped");
           return;
       }

       getLog().info("Starting SQL Safety Guard scan...");
       getLog().info("Project path: " + projectPath.getAbsolutePath());

       // Step 1: Validate parameters
       validateParameters();

       // Step 2: Load configuration
       SqlGuardConfig config = loadConfiguration();

       // Step 3: Execute scan
       ScanReport report = executeScan();

       // Step 4: Generate reports
       generateReports(report);

       // Step 5: Handle violations
       handleViolations(report);

       getLog().info("SQL Safety Guard scan completed");
   }
   ```

**Test Requirements**:
- `ViolationHandlingTest.java` (10 tests):
  - testFailOnCritical_false_noViolations_shouldPass()
  - testFailOnCritical_false_withViolations_shouldPass()
  - testFailOnCritical_true_noViolations_shouldPass()
  - testFailOnCritical_true_onlyCritical_shouldFail()
  - testFailOnCritical_true_criticalAndOthers_shouldFail()
  - testFailOnCritical_true_onlyHighMediumLow_shouldPass()
  - testViolationMessage_shouldIncludeCriticalCount()
  - testViolationMessage_shouldMentionReport()
  - testExecute_fullFlow_noViolations_shouldComplete()
  - testExecute_fullFlow_withCritical_shouldFail()

**Files to Create**:
- `sql-scanner-maven/src/test/java/com/footstone/sqlguard/maven/ViolationHandlingTest.java`

---

### Step 5: Maven Integration Testing
**Goal**: Test plugin with real Maven project and maven-invoker-plugin

**Tasks**:
1. Create test project structure:
   ```
   src/it/
   ├── simple-scan/
   │   ├── pom.xml (configures sqlguard plugin)
   │   ├── src/main/resources/mappers/UserMapper.xml
   │   └── verify.groovy (assertions)
   ├── with-violations/
   │   ├── pom.xml
   │   ├── src/main/resources/mappers/DangerousMapper.xml
   │   └── verify.groovy
   └── fail-on-critical/
       ├── pom.xml (failOnCritical=true)
       ├── src/main/resources/mappers/CriticalViolationMapper.xml
       └── verify.groovy
   ```

2. Configure maven-invoker-plugin in pom.xml:
   ```xml
   <plugin>
       <groupId>org.apache.maven.plugins</groupId>
       <artifactId>maven-invoker-plugin</artifactId>
       <version>3.6.0</version>
       <configuration>
           <projectsDirectory>src/it</projectsDirectory>
           <cloneProjectsTo>${project.build.directory}/it</cloneProjectsTo>
           <pomIncludes>
               <pomInclude>*/pom.xml</pomInclude>
           </pomIncludes>
           <postBuildHookScript>verify</postBuildHookScript>
       </configuration>
       <executions>
           <execution>
               <goals>
                   <goal>install</goal>
                   <goal>integration-test</goal>
                   <goal>verify</goal>
               </goals>
           </execution>
       </executions>
   </plugin>
   ```

3. Create verify.groovy scripts for assertions:
   ```groovy
   // simple-scan/verify.groovy
   File report = new File(basedir, "target/sqlguard-report.html")
   assert report.exists() : "HTML report not generated"
   assert report.text.contains("SQL Safety Guard Report") : "Invalid report"
   ```

**Test Requirements**:
- 3 integration tests (maven-invoker-plugin):
  - `simple-scan`: Basic scan, no violations, HTML report generated
  - `with-violations`: Scan with HIGH/MEDIUM violations, build passes (failOnCritical=false)
  - `fail-on-critical`: Scan with CRITICAL violations, build fails (failOnCritical=true)

**Files to Create**:
- `sql-scanner-maven/src/it/simple-scan/pom.xml`
- `sql-scanner-maven/src/it/simple-scan/src/main/resources/mappers/UserMapper.xml`
- `sql-scanner-maven/src/it/simple-scan/verify.groovy`
- `sql-scanner-maven/src/it/with-violations/pom.xml`
- `sql-scanner-maven/src/it/with-violations/src/main/resources/mappers/DangerousMapper.xml`
- `sql-scanner-maven/src/it/with-violations/verify.groovy`
- `sql-scanner-maven/src/it/fail-on-critical/pom.xml`
- `sql-scanner-maven/src/it/fail-on-critical/src/main/resources/mappers/CriticalViolationMapper.xml`
- `sql-scanner-maven/src/it/fail-on-critical/verify.groovy`

---

### Step 6: Documentation and Usage Examples
**Goal**: Create comprehensive plugin documentation and usage examples

**Tasks**:
1. Create `sql-scanner-maven/README.md`:
   ```markdown
   # SQL Guard Maven Plugin

   ## Quick Start

   ### 1. Add Plugin to pom.xml
   \```xml
   <build>
       <plugins>
           <plugin>
               <groupId>com.footstone</groupId>
               <artifactId>sql-scanner-maven</artifactId>
               <version>${sqlguard.version}</version>
               <executions>
                   <execution>
                       <goals>
                           <goal>scan</goal>
                       </goals>
                   </execution>
               </executions>
           </plugin>
       </plugins>
   </build>
   \```

   ### 2. Run Scan
   \```bash
   mvn sqlguard:scan
   \```

   ## Configuration

   | Parameter | Property | Default | Description |
   |-----------|----------|---------|-------------|
   | projectPath | sqlguard.projectPath | ${project.basedir} | Project root path to scan |
   | configFile | sqlguard.configFile | null | YAML config file path |
   | outputFormat | sqlguard.outputFormat | console | Report format: console, html, both |
   | outputFile | sqlguard.outputFile | target/sqlguard-report.html | HTML report output path |
   | failOnCritical | sqlguard.failOnCritical | false | Fail build on CRITICAL violations |
   | skip | sqlguard.skip | false | Skip plugin execution |

   ## Usage Examples

   ### Example 1: Basic Scan
   ### Example 2: Custom Config File
   ### Example 3: Fail on Critical
   ### Example 4: CI/CD Integration
   ### Example 5: Multi-Module Project

   ## Troubleshooting
   \```

2. Create usage examples documentation

**Test Requirements**:
- Documentation tests (3 tests):
  - testReadme_shouldExist()
  - testReadme_shouldContainQuickStart()
  - testReadme_shouldContainAllParameters()

**Files to Create**:
- `sql-scanner-maven/README.md` (500+ lines)
- `sql-scanner-maven/docs/usage-examples.md` (300+ lines)

---

## Expected Outcomes

### Functional Outcomes:
1. ✅ Maven plugin executes via `mvn sqlguard:scan`
2. ✅ Integrates sql-scanner-core without modification
3. ✅ Supports all configuration parameters
4. ✅ Generates Console and HTML reports
5. ✅ Fails build on critical violations when enabled
6. ✅ CI/CD integration via Maven lifecycle

### Test Outcomes:
- Total new tests: **51 tests** (48 unit + 3 integration)
- Unit tests: 48 tests across 4 test classes
- Integration tests: 3 tests via maven-invoker-plugin
- 100% pass rate required

### Architecture Outcomes:
- ✅ Zero modification to sql-scanner-core
- ✅ Reuses DefaultSqlSafetyValidator infrastructure
- ✅ Maven-native configuration via @Parameter
- ✅ Proper Maven logging integration

## Validation Criteria

### Must Pass Before Completion:
1. All 51 tests passing (100% pass rate)
2. Plugin executes via `mvn sqlguard:scan`
3. Console report outputs to Maven log
4. HTML report generated to target/
5. failOnCritical=true fails build correctly
6. skip=true skips execution correctly
7. Invalid parameters throw MojoExecutionException
8. maven-invoker integration tests pass
9. README.md complete with examples
10. Compatible with Maven 3.6+

### Code Quality:
1. Google Java Style compliance
2. Comprehensive Javadoc
3. SLF4J logging via Maven getLog()
4. Complete documentation

## Success Metrics

- ✅ 51 tests passing (100%)
- ✅ Maven plugin functional
- ✅ CI/CD integration verified
- ✅ Documentation complete (800+ lines)
- ✅ Zero modifications to scanner core

## Timeline Estimate
- Step 1: 1.5 hours (Mojo + 12 tests)
- Step 2: 2 hours (Scanner invocation + 14 tests)
- Step 3: 1.5 hours (Report generation + 12 tests)
- Step 4: 1.5 hours (Violation handling + 10 tests)
- Step 5: 2.5 hours (Integration tests + 3 scenarios)
- Step 6: 1.5 hours (Documentation + 3 tests)

**Total**: ~10.5 hours

## Definition of Done

- [ ] All 51 tests passing
- [ ] Plugin goal `scan` executes successfully
- [ ] Console report displays in Maven log
- [ ] HTML report generated to target/
- [ ] failOnCritical=true fails build
- [ ] Configuration parameters work
- [ ] Integration tests pass (maven-invoker)
- [ ] README.md complete
- [ ] Usage examples documented
- [ ] Memory Log created
- [ ] Compatible with Maven 3.6+

---

**End of Task Assignment**

Maven Plugin provides CI/CD integration for automated SQL safety validation in Maven build lifecycle, enabling fail-fast quality gates without modifying existing sql-scanner-core infrastructure.
