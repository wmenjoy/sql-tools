---
agent: Agent_Build_Tools
task_ref: Task 5.2
depends_on:
  - Task 3.7 (CLI Tool Implementation)
  - Task 3.8 (Validator Integration)
ad_hoc_delegation: false
---

# Task 5.2 - Gradle Plugin Implementation

## Objective
Implement Gradle plugin for SQL Safety Guard providing CI/CD integration via `gradle sqlguardScan` task, leveraging sql-scanner-core static analysis engine with DSL-based configuration (sqlguard extension), generating Console/HTML reports, and enabling build failure on critical violations for automated quality gates in Gradle-based projects.

## Context

**Gradle Plugin Architecture**: Plugin<Project> interface provides plugin registration framework, Extension objects for DSL configuration, DefaultTask for task implementation, and @TaskAction for execution hooks.

**Static Scanner Integration**: Reuses Phase 3's sql-scanner-core and DefaultSqlSafetyValidator infrastructure without modification, wrapping CLI functionality in Gradle-native interface.

**CI/CD Integration**: Gradle build lifecycle integration enables fail-fast SQL validation at `check` or `build` task, preventing dangerous SQL from reaching production.

## Dependencies

### Input from Phase 3:
- sql-scanner-core (complete static scanner, 273 tests passing) ✅
- sql-scanner-cli (CLI tool with SqlScannerCli, 37 tests passing) ✅
- DefaultSqlSafetyValidator (4 core checkers) ✅
- ReportProcessor + ConsoleReportGenerator + HtmlReportGenerator ✅

### Independence:
- Task 5.2 is independent of Task 5.1 (Maven Plugin)
- Can execute in parallel with Task 5.1

## Implementation Steps

### Step 1: Gradle Plugin Infrastructure TDD
**Goal**: Create SqlGuardPlugin with Gradle plugin infrastructure

**Tasks**:
1. Add Gradle Plugin dependencies to `sql-scanner-gradle/build.gradle`:
   ```groovy
   plugins {
       id 'java-gradle-plugin'
       id 'maven-publish'
   }

   dependencies {
       implementation 'com.footstone:sql-scanner-core:${project.version}'

       testImplementation 'org.spockframework:spock-core:2.3-groovy-3.0'
       testImplementation gradleTestKit()
   }

   gradlePlugin {
       plugins {
           sqlGuardPlugin {
               id = 'com.footstone.sqlguard'
               implementationClass = 'com.footstone.sqlguard.gradle.SqlGuardPlugin'
               displayName = 'SQL Guard Plugin'
               description = 'Gradle plugin for SQL safety validation'
           }
       }
   }
   ```

2. Create `SqlGuardPlugin` class:
   ```java
   public class SqlGuardPlugin implements Plugin<Project> {
       @Override
       public void apply(Project project) {
           // Create extension for DSL configuration
           SqlGuardExtension extension = project.getExtensions()
               .create("sqlguard", SqlGuardExtension.class);

           // Register task
           project.getTasks().register("sqlguardScan",
               SqlGuardScanTask.class, task -> {
                   task.setGroup("verification");
                   task.setDescription("Scan SQL for safety violations");
                   task.getProjectPath().convention(
                       project.getProjectDir());
                   task.getOutputFormat().convention("console");
                   task.getFailOnCritical().convention(false);
               });
       }
   }
   ```

3. Create `SqlGuardExtension` class for configuration:
   ```java
   public abstract class SqlGuardExtension {
       private File projectPath;
       private File configFile;
       private String outputFormat = "console"; // console, html, both
       private File outputFile;
       private boolean failOnCritical = false;

       // Getters and setters
   }
   ```

**Test Requirements**:
- `SqlGuardPluginTest.java` (10 tests):
  - testPlugin_shouldRegisterExtension()
  - testPlugin_shouldRegisterTask()
  - testPlugin_taskGroup_shouldBeVerification()
  - testPlugin_taskDescription_shouldBeSet()
  - testExtension_defaultProjectPath_shouldBeProjectDir()
  - testExtension_defaultOutputFormat_shouldBeConsole()
  - testExtension_defaultFailOnCritical_shouldBeFalse()
  - testPlugin_apply_shouldNotThrow()
  - testPlugin_multipleApply_shouldNotConflict()
  - testPlugin_taskDependsOn_shouldBeConfigurable()

**Files to Create**:
- `sql-scanner-gradle/src/main/java/com/footstone/sqlguard/gradle/SqlGuardPlugin.java`
- `sql-scanner-gradle/src/main/java/com/footstone/sqlguard/gradle/SqlGuardExtension.java`
- `sql-scanner-gradle/src/test/java/com/footstone/sqlguard/gradle/SqlGuardPluginTest.java`

---

### Step 2: SqlGuardScanTask Implementation
**Goal**: Create Gradle Task implementation with @TaskAction

**Tasks**:
1. Create `SqlGuardScanTask` class:
   ```java
   public abstract class SqlGuardScanTask extends DefaultTask {
       @Input
       public abstract Property<File> getProjectPath();

       @Optional
       @Input
       public abstract Property<File> getConfigFile();

       @Input
       public abstract Property<String> getOutputFormat();

       @Optional
       @OutputFile
       public abstract Property<File> getOutputFile();

       @Input
       public abstract Property<Boolean> getFailOnCritical();

       @TaskAction
       public void scan() throws IOException {
           // Validate parameters
           validateParameters();

           // Load configuration
           SqlGuardConfig config = loadConfiguration();

           // Execute scan
           ScanReport report = executeScan(config);

           // Generate reports
           generateReports(report);

           // Handle violations
           handleViolations(report);
       }

       private void validateParameters() {
           File projectPath = getProjectPath().get();
           if (projectPath == null || !projectPath.exists()) {
               throw new GradleException(
                   "Project path does not exist: " + projectPath);
           }

           File configFile = getConfigFile().getOrNull();
           if (configFile != null && !configFile.exists()) {
               throw new GradleException(
                   "Config file does not exist: " + configFile);
           }

           String format = getOutputFormat().get();
           if (!Arrays.asList("console", "html", "both").contains(format)) {
               throw new GradleException(
                   "Invalid output format: " + format);
           }
       }
   }
   ```

**Test Requirements**:
- `SqlGuardScanTaskTest.java` (14 tests):
  - testTask_inputProperties_shouldBeAnnotated()
  - testTask_outputFile_shouldBeAnnotated()
  - testTask_projectPath_required_shouldThrow()
  - testTask_projectPath_notExists_shouldThrow()
  - testTask_configFile_notExists_shouldThrow()
  - testTask_outputFormat_invalid_shouldThrow()
  - testTask_validInputs_shouldNotThrow()
  - testTask_defaultProjectPath_shouldBeProjectDir()
  - testTask_defaultOutputFormat_shouldBeConsole()
  - testTask_defaultFailOnCritical_shouldBeFalse()
  - testTask_configFile_optional_shouldWork()
  - testTask_outputFile_optional_shouldWork()
  - testTask_taskAction_shouldExecuteScan()
  - testTask_cacheable_shouldSupportUpToDate()

**Files to Create**:
- `sql-scanner-gradle/src/main/java/com/footstone/sqlguard/gradle/SqlGuardScanTask.java`
- `sql-scanner-gradle/src/test/java/com/footstone/sqlguard/gradle/SqlGuardScanTaskTest.java`

---

### Step 3: Scanner Invocation and Report Generation
**Goal**: Integrate sql-scanner-core and generate reports

**Tasks**:
1. Implement scanner invocation in SqlGuardScanTask:
   ```java
   private SqlGuardConfig loadConfiguration() {
       File configFile = getConfigFile().getOrNull();
       if (configFile != null) {
           try {
               return SqlGuardConfig.loadFromYaml(
                   Files.newInputStream(configFile.toPath()));
           } catch (IOException e) {
               throw new GradleException("Failed to load config file", e);
           }
       }
       return SqlGuardConfig.getDefault();
   }

   private ScanReport executeScan(SqlGuardConfig config) {
       try {
           // Create validator
           DefaultSqlSafetyValidator validator = createValidator(config);

           // Create scanner
           SqlScanner scanner = new SqlScanner(
               getProjectPath().get().toPath(),
               validator
           );

           // Execute scan
           return scanner.scan();

       } catch (Exception e) {
           throw new GradleException("Scan execution failed", e);
       }
   }

   private void generateReports(ScanReport report) throws IOException {
       String format = getOutputFormat().get();

       if ("console".equals(format) || "both".equals(format)) {
           generateConsoleReport(report);
       }

       if ("html".equals(format) || "both".equals(format)) {
           generateHtmlReport(report);
       }
   }

   private void generateConsoleReport(ScanReport report) throws IOException {
       ConsoleReportGenerator generator = new ConsoleReportGenerator();
       String consoleReport = generator.generate(report);

       // Output to Gradle logger
       for (String line : consoleReport.split("\n")) {
           if (line.contains("[CRITICAL]") || line.contains("[HIGH]")) {
               getLogger().error(line);
           } else if (line.contains("[MEDIUM]")) {
               getLogger().warn(line);
           } else {
               getLogger().info(line);
           }
       }
   }

   private void generateHtmlReport(ScanReport report) throws IOException {
       HtmlReportGenerator generator = new HtmlReportGenerator();
       String htmlReport = generator.generate(report);

       File htmlFile = getOutputFile().getOrElse(
           new File(getProject().getBuildDir(), "reports/sqlguard/report.html")
       );

       htmlFile.getParentFile().mkdirs();
       Files.write(htmlFile.toPath(), htmlReport.getBytes());
       getLogger().info("HTML report generated: {}", htmlFile.getAbsolutePath());
   }

   private void handleViolations(ScanReport report) {
       if (!getFailOnCritical().get()) {
           return;
       }

       if (report.hasCriticalViolations()) {
           throw new GradleException(String.format(
               "SQL Safety Guard detected %d CRITICAL violation(s). " +
               "Build failed. See report for details.",
               report.getViolationCount(RiskLevel.CRITICAL)
           ));
       }
   }
   ```

**Test Requirements**:
- `ScannerInvocationTest.java` (12 tests):
  - testLoadConfiguration_withFile_shouldLoadYaml()
  - testLoadConfiguration_noFile_shouldReturnDefault()
  - testLoadConfiguration_invalidYaml_shouldThrow()
  - testExecuteScan_validProject_shouldReturnReport()
  - testExecuteScan_emptyProject_shouldReturnEmptyReport()
  - testExecuteScan_withViolations_shouldIncludeInReport()
  - testGenerateConsoleReport_shouldOutputToLogger()
  - testGenerateHtmlReport_shouldCreateFile()
  - testGenerateHtmlReport_defaultLocation_shouldUseBuildDir()
  - testGenerateBothReports_shouldOutputConsoleAndHtml()
  - testHandleViolations_failOnCritical_shouldThrow()
  - testHandleViolations_noFailOnCritical_shouldNotThrow()

**Files to Create**:
- `sql-scanner-gradle/src/test/java/com/footstone/sqlguard/gradle/ScannerInvocationTest.java`

---

### Step 4: Gradle Functional Testing
**Goal**: Test plugin with real Gradle project using GradleRunner

**Tasks**:
1. Create functional test structure:
   ```
   src/functionalTest/
   ├── groovy/
   │   └── com/footstone/sqlguard/gradle/
   │       ├── SimpleScanFunctionalTest.groovy
   │       ├── WithViolationsFunctionalTest.groovy
   │       └── FailOnCriticalFunctionalTest.groovy
   └── resources/
       ├── simple-scan/
       │   ├── build.gradle
       │   └── src/main/resources/mappers/UserMapper.xml
       ├── with-violations/
       │   ├── build.gradle
       │   └── src/main/resources/mappers/DangerousMapper.xml
       └── fail-on-critical/
           ├── build.gradle
           └── src/main/resources/mappers/CriticalMapper.xml
   ```

2. Configure functional test source set in build.gradle:
   ```groovy
   sourceSets {
       functionalTest {
           groovy.srcDir file('src/functionalTest/groovy')
           resources.srcDir file('src/functionalTest/resources')
           compileClasspath += sourceSets.main.output + configurations.testRuntimeClasspath
           runtimeClasspath += output + compileClasspath
       }
   }

   tasks.register('functionalTest', Test) {
       testClassesDirs = sourceSets.functionalTest.output.classesDirs
       classpath = sourceSets.functionalTest.runtimeClasspath
       useJUnitPlatform()
   }
   ```

3. Create functional tests using GradleRunner:
   ```groovy
   class SimpleScanFunctionalTest extends Specification {
       @TempDir
       File testProjectDir

       def "simple scan generates HTML report"() {
           given:
           // Copy test project
           def buildFile = new File(testProjectDir, 'build.gradle')
           buildFile.text = """
               plugins {
                   id 'com.footstone.sqlguard'
               }

               sqlguard {
                   outputFormat = 'html'
               }
           """

           // Copy test mappers
           def mapper = new File(testProjectDir,
               'src/main/resources/mappers/UserMapper.xml')
           mapper.parentFile.mkdirs()
           mapper.text = '...' // Simple safe SQL

           when:
           def result = GradleRunner.create()
               .withProjectDir(testProjectDir)
               .withArguments('sqlguardScan')
               .withPluginClasspath()
               .build()

           then:
           result.task(":sqlguardScan").outcome == TaskOutcome.SUCCESS
           new File(testProjectDir,
               'build/reports/sqlguard/report.html').exists()
       }
   }
   ```

**Test Requirements**:
- 3 functional tests (GradleRunner):
  - `SimpleScanFunctionalTest.groovy`: Basic scan, no violations, HTML report generated
  - `WithViolationsFunctionalTest.groovy`: Scan with HIGH/MEDIUM violations, build passes (failOnCritical=false)
  - `FailOnCriticalFunctionalTest.groovy`: Scan with CRITICAL violations, build fails (failOnCritical=true)

**Files to Create**:
- `sql-scanner-gradle/src/functionalTest/groovy/com/footstone/sqlguard/gradle/SimpleScanFunctionalTest.groovy`
- `sql-scanner-gradle/src/functionalTest/groovy/com/footstone/sqlguard/gradle/WithViolationsFunctionalTest.groovy`
- `sql-scanner-gradle/src/functionalTest/groovy/com/footstone/sqlguard/gradle/FailOnCriticalFunctionalTest.groovy`
- Test resources (build.gradle + mapper files)

---

### Step 5: DSL Configuration Enhancement
**Goal**: Enhance plugin with idiomatic Gradle DSL configuration

**Tasks**:
1. Enhance SqlGuardExtension with nested configuration:
   ```java
   public abstract class SqlGuardExtension {
       private final Project project;
       private File projectPath;
       private File configFile;
       private String outputFormat = "console";
       private File outputFile;
       private boolean failOnCritical = false;

       @Inject
       public SqlGuardExtension(Project project) {
           this.project = project;
           this.projectPath = project.getProjectDir();
       }

       // DSL configuration methods
       public void console() {
           this.outputFormat = "console";
       }

       public void html() {
           this.outputFormat = "html";
       }

       public void html(File outputFile) {
           this.outputFormat = "html";
           this.outputFile = outputFile;
       }

       public void both() {
           this.outputFormat = "both";
       }

       public void failOnCritical() {
           this.failOnCritical = true;
       }

       // Getters and setters
   }
   ```

2. Update SqlGuardPlugin to apply extension to task:
   ```java
   @Override
   public void apply(Project project) {
       SqlGuardExtension extension = project.getExtensions()
           .create("sqlguard", SqlGuardExtension.class, project);

       project.getTasks().register("sqlguardScan",
           SqlGuardScanTask.class, task -> {
               task.setGroup("verification");
               task.setDescription("Scan SQL for safety violations");

               // Connect extension to task properties
               task.getProjectPath().convention(
                   project.provider(() -> extension.getProjectPath()));
               task.getConfigFile().convention(
                   project.provider(() -> extension.getConfigFile()));
               task.getOutputFormat().convention(
                   project.provider(() -> extension.getOutputFormat()));
               task.getOutputFile().convention(
                   project.provider(() -> extension.getOutputFile()));
               task.getFailOnCritical().convention(
                   project.provider(() -> extension.isFailOnCritical()));
           });
   }
   ```

**Test Requirements**:
- `DslConfigurationTest.java` (10 tests):
  - testDsl_console_shouldSetOutputFormat()
  - testDsl_html_shouldSetOutputFormat()
  - testDsl_htmlWithFile_shouldSetOutputFormatAndFile()
  - testDsl_both_shouldSetOutputFormat()
  - testDsl_failOnCritical_shouldSetFlag()
  - testDsl_configFile_shouldSetFile()
  - testDsl_projectPath_shouldSetPath()
  - testExtensionToTask_shouldPropagateValues()
  - testExtensionDefaults_shouldApplyToTask()
  - testDslExample_shouldWork()

**Files to Create**:
- `sql-scanner-gradle/src/test/java/com/footstone/sqlguard/gradle/DslConfigurationTest.java`

---

### Step 6: Documentation and Usage Examples
**Goal**: Create comprehensive plugin documentation and usage examples

**Tasks**:
1. Create `sql-scanner-gradle/README.md`:
   ```markdown
   # SQL Guard Gradle Plugin

   ## Quick Start

   ### 1. Apply Plugin
   \```groovy
   plugins {
       id 'com.footstone.sqlguard' version '${sqlguard.version}'
   }
   \```

   ### 2. Configure (Optional)
   \```groovy
   sqlguard {
       configFile = file('sqlguard-config.yml')
       outputFormat = 'html'
       failOnCritical()
   }
   \```

   ### 3. Run Scan
   \```bash
   gradle sqlguardScan
   \```

   ## Configuration DSL

   | Property | Type | Default | Description |
   |----------|------|---------|-------------|
   | projectPath | File | project.projectDir | Project root path to scan |
   | configFile | File | null | YAML config file path |
   | outputFormat | String | "console" | Report format |
   | outputFile | File | build/reports/sqlguard/report.html | HTML report output path |
   | failOnCritical | Boolean | false | Fail build on CRITICAL violations |

   ## DSL Methods

   \```groovy
   sqlguard {
       console()              // Output to console only
       html()                 // Output to HTML only (default location)
       html(file('my-report.html'))  // Output to HTML (custom location)
       both()                 // Output to console and HTML
       failOnCritical()       // Fail build on CRITICAL violations
   }
   \```

   ## Usage Examples

   ### Example 1: Basic Scan
   ### Example 2: Custom Config File
   ### Example 3: Fail on Critical
   ### Example 4: CI/CD Integration
   ### Example 5: Multi-Project Build

   ## Troubleshooting
   \```

2. Create usage examples documentation

**Test Requirements**:
- Documentation tests (3 tests):
  - testReadme_shouldExist()
  - testReadme_shouldContainQuickStart()
  - testReadme_shouldContainDslMethods()

**Files to Create**:
- `sql-scanner-gradle/README.md` (500+ lines)
- `sql-scanner-gradle/docs/usage-examples.md` (300+ lines)

---

## Expected Outcomes

### Functional Outcomes:
1. ✅ Gradle plugin executes via `gradle sqlguardScan`
2. ✅ Integrates sql-scanner-core without modification
3. ✅ Idiomatic Gradle DSL configuration
4. ✅ Generates Console and HTML reports
5. ✅ Fails build on critical violations when enabled
6. ✅ CI/CD integration via Gradle lifecycle

### Test Outcomes:
- Total new tests: **49 tests** (46 unit + 3 functional)
- Unit tests: 46 tests across 5 test classes
- Functional tests: 3 tests via GradleRunner
- 100% pass rate required

### Architecture Outcomes:
- ✅ Zero modification to sql-scanner-core
- ✅ Reuses DefaultSqlSafetyValidator infrastructure
- ✅ Gradle-native DSL configuration
- ✅ Proper Gradle logging integration

## Validation Criteria

### Must Pass Before Completion:
1. All 49 tests passing (100% pass rate)
2. Plugin executes via `gradle sqlguardScan`
3. Console report outputs to Gradle logger
4. HTML report generated to build/reports/
5. failOnCritical=true fails build correctly
6. DSL configuration works correctly
7. Invalid parameters throw GradleException
8. GradleRunner functional tests pass
9. README.md complete with examples
10. Compatible with Gradle 7.0+

### Code Quality:
1. Google Java Style compliance
2. Comprehensive Javadoc
3. SLF4J logging via Gradle logger
4. Complete documentation

## Success Metrics

- ✅ 49 tests passing (100%)
- ✅ Gradle plugin functional
- ✅ CI/CD integration verified
- ✅ Documentation complete (800+ lines)
- ✅ Zero modifications to scanner core

## Timeline Estimate
- Step 1: 1.5 hours (Plugin infrastructure + 10 tests)
- Step 2: 2 hours (Task implementation + 14 tests)
- Step 3: 2 hours (Scanner invocation + 12 tests)
- Step 4: 2.5 hours (Functional tests + 3 scenarios)
- Step 5: 1.5 hours (DSL configuration + 10 tests)
- Step 6: 1.5 hours (Documentation + 3 tests)

**Total**: ~11 hours

## Definition of Done

- [ ] All 49 tests passing
- [ ] Plugin task `sqlguardScan` executes successfully
- [ ] Console report displays in Gradle logger
- [ ] HTML report generated to build/reports/
- [ ] failOnCritical=true fails build
- [ ] DSL configuration works
- [ ] Functional tests pass (GradleRunner)
- [ ] README.md complete
- [ ] Usage examples documented
- [ ] Memory Log created
- [ ] Compatible with Gradle 7.0+

---

**End of Task Assignment**

Gradle Plugin provides CI/CD integration for automated SQL safety validation in Gradle build lifecycle, enabling fail-fast quality gates with idiomatic Gradle DSL without modifying existing sql-scanner-core infrastructure.
