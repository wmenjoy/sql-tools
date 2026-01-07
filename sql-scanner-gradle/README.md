# SQL Guard Gradle Plugin

Gradle plugin for SQL Safety Guard providing CI/CD integration via `gradle sqlguardScan` task, leveraging sql-scanner-core static analysis engine with DSL-based configuration.

## Features

- ✅ Static analysis of SQL files in Gradle projects
- ✅ Idiomatic Gradle DSL configuration
- ✅ Console and HTML report generation
- ✅ Fail-fast build integration on critical violations
- ✅ CI/CD pipeline integration
- ✅ Compatible with Gradle 7.0+
- ✅ **15 Security Checkers** - Complete security analysis coverage matching CLI module

## Quick Start

### 1. Apply Plugin

Add the plugin to your `build.gradle`:

```groovy
plugins {
    id 'com.footstone.sqlguard' version '1.0.0-SNAPSHOT'
}
```

Or using legacy plugin application:

```groovy
buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    dependencies {
        classpath 'com.footstone:sql-scanner-gradle:1.0.0-SNAPSHOT'
    }
}

apply plugin: 'com.footstone.sqlguard'
```

### 2. Configure (Optional)

Configure the plugin using the `sqlguard` extension:

```groovy
sqlguard {
    projectPath = file('src/main')
    configFile = file('sqlguard-config.yml')
    outputFormat = 'html'
    outputFile = file('build/reports/sqlguard/custom-report.html')
    failOnCritical()
}
```

### 3. Run Scan

Execute the scan task:

```bash
gradle sqlguardScan
```

Or integrate with your build lifecycle:

```bash
gradle build  # if configured to run during build
```

## Configuration DSL

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `projectPath` | File | `project.projectDir` | Project root path to scan for SQL files |
| `configFile` | File | `null` | Path to YAML configuration file (optional) |
| `outputFormat` | String | `"console"` | Report output format: `console`, `html`, or `both` |
| `outputFile` | File | `build/reports/sqlguard/report.html` | HTML report output path (when using HTML format) |
| `failOnCritical` | Boolean | `false` | Whether to fail build when CRITICAL violations are detected |

### DSL Methods

The plugin provides fluent DSL methods for common configurations:

```groovy
sqlguard {
    // Output format configuration
    console()                              // Output to console only
    html()                                 // Output to HTML only (default location)
    html(file('my-report.html'))          // Output to HTML (custom location)
    both()                                 // Output to both console and HTML
    
    // Build failure configuration
    failOnCritical()                       // Fail build on CRITICAL violations
    
    // Path configuration
    projectPath = file('src/main')         // Custom project path
    configFile = file('sqlguard.yml')      // Custom config file
}
```

## Usage Examples

### Example 1: Basic Scan with Console Output

Simplest configuration - scan project and output to console:

```groovy
plugins {
    id 'com.footstone.sqlguard' version '1.0.0-SNAPSHOT'
}

// No configuration needed - uses defaults
```

Run:
```bash
gradle sqlguardScan
```

### Example 2: HTML Report Generation

Generate HTML report for better visualization:

```groovy
plugins {
    id 'com.footstone.sqlguard' version '1.0.0-SNAPSHOT'
}

sqlguard {
    html()  // Generate HTML report in default location
}
```

Or with custom output location:

```groovy
sqlguard {
    html(file('build/reports/sql-safety-report.html'))
}
```

### Example 3: Custom Configuration File

Use a custom YAML configuration file to define rules:

```groovy
sqlguard {
    configFile = file('config/sqlguard-rules.yml')
    both()  // Output to both console and HTML
}
```

Create `config/sqlguard-rules.yml`:

```yaml
rules:
  noWhereClause:
    enabled: true
    severity: CRITICAL
  dummyCondition:
    enabled: true
    severity: HIGH
  blacklistFields:
    enabled: true
    fields:
      - password
      - secret_key
```

### Example 4: Fail on Critical Violations (CI/CD)

Configure build to fail when critical SQL safety violations are detected:

```groovy
sqlguard {
    outputFormat = 'both'
    failOnCritical()  // Fail build on CRITICAL violations
}
```

This is ideal for CI/CD pipelines to prevent dangerous SQL from being deployed:

```bash
# In CI/CD pipeline
gradle clean build sqlguardScan

# Build will fail if CRITICAL violations are found
# Exit code: 1 (failure)
```

### Example 5: Integration with Build Lifecycle

Automatically run SQL Guard scan during the build:

```groovy
plugins {
    id 'java'
    id 'com.footstone.sqlguard' version '1.0.0-SNAPSHOT'
}

sqlguard {
    html()
    failOnCritical()
}

// Run sqlguardScan before the build task
build.dependsOn sqlguardScan

// Or run after compilation
sqlguardScan.dependsOn compileJava
build.dependsOn sqlguardScan
```

### Example 6: Multi-Project Build

Configure SQL Guard for multi-project builds:

**Root `build.gradle`:**
```groovy
subprojects {
    apply plugin: 'com.footstone.sqlguard'
    
    sqlguard {
        outputFormat = 'html'
        outputFile = file("${project.buildDir}/reports/sqlguard/${project.name}-report.html")
        failOnCritical()
    }
}
```

Run scan on all subprojects:
```bash
gradle sqlguardScan
```

Or scan specific subproject:
```bash
gradle :subproject-name:sqlguardScan
```

### Example 7: Custom Project Path

Scan a specific directory instead of the entire project:

```groovy
sqlguard {
    projectPath = file('src/main/resources')  // Only scan resources directory
    console()
}
```

### Example 8: Development vs CI Configuration

Use different configurations for development and CI:

```groovy
sqlguard {
    if (project.hasProperty('ci')) {
        // CI configuration
        outputFormat = 'both'
        outputFile = file('build/reports/sqlguard/ci-report.html')
        failOnCritical()
    } else {
        // Development configuration
        console()
        failOnCritical = false
    }
}
```

Run in CI:
```bash
gradle sqlguardScan -Pci
```

Run in development:
```bash
gradle sqlguardScan
```

## Report Formats

### Console Report

Text-based report output to Gradle logger with color-coded severity levels:

```
================================================================================
                        SQL Safety Guard Scan Report
================================================================================

Scan Summary:
  Total SQL Statements: 15
  Total Violations: 3
  
Violations by Severity:
  CRITICAL: 1
  HIGH: 1
  MEDIUM: 1
  LOW: 0

================================================================================
                              Violation Details
================================================================================

[CRITICAL] No WHERE Clause in DELETE Statement
  File: src/main/resources/mappers/UserMapper.xml
  Statement ID: deleteAll
  SQL: DELETE FROM users
  Recommendation: Add WHERE clause to prevent accidental deletion of all records
```

### HTML Report

Rich HTML report with:
- Interactive tables with sorting and filtering
- Syntax-highlighted SQL code
- Severity-based color coding
- Summary statistics and charts
- Exportable format

## Task Configuration

### Task Properties

The `sqlguardScan` task is registered in the `verification` group and can be configured:

```groovy
tasks.named('sqlguardScan') {
    // Override extension properties if needed
    projectPath.set(file('custom/path'))
    outputFormat.set('html')
    failOnCritical.set(true)
}
```

### Task Dependencies

Configure task dependencies:

```groovy
// Run after compilation
sqlguardScan.dependsOn compileJava

// Make build depend on scan
build.dependsOn sqlguardScan

// Run before tests
test.dependsOn sqlguardScan
```

## Troubleshooting

### Issue: Plugin not found

**Problem:** `Plugin with id 'com.footstone.sqlguard' not found`

**Solution:** Ensure the plugin is installed in your local Maven repository:
```bash
cd sql-scanner-gradle
mvn clean install
```

### Issue: No SQL files found

**Problem:** Scan completes but reports 0 SQL statements

**Solution:** 
- Verify `projectPath` points to correct directory
- Check that SQL files are in expected locations:
  - MyBatis XML: `src/main/resources/**/*.xml`
  - Annotations: `src/main/java/**/*.java`
- Review scanner logs for directory warnings

### Issue: Configuration file not loaded

**Problem:** Custom configuration file is not being applied

**Solution:**
- Verify file path is correct and file exists
- Check YAML syntax is valid
- Review Gradle logs for configuration loading messages

### Issue: Build fails unexpectedly

**Problem:** Build fails even without critical violations

**Solution:**
- Check `failOnCritical` setting
- Review scan report for actual violation severity levels
- Verify Gradle task configuration

### Issue: HTML report not generated

**Problem:** HTML report file is not created

**Solution:**
- Ensure `outputFormat` is set to `'html'` or `'both'`
- Check that output directory is writable
- Verify `outputFile` path is valid

## Security Checkers (15 Total)

The Gradle plugin includes all 15 security checkers to match the CLI module:

### Basic Security Checkers (1-4)

| Checker | Severity | Description |
|---------|----------|-------------|
| NoWhereClauseChecker | CRITICAL | Detects DELETE/UPDATE without WHERE clause |
| DummyConditionChecker | HIGH | Detects dummy conditions (1=1, 'a'='a') |
| BlacklistFieldChecker | HIGH | Detects queries on sensitive fields (password, etc.) |
| WhitelistFieldChecker | MEDIUM | Detects SELECT * queries |

### SQL Injection Checkers (5-8)

| Checker | Severity | Description |
|---------|----------|-------------|
| MultiStatementChecker | CRITICAL | Detects multi-statement SQL injection |
| SetOperationChecker | CRITICAL | Detects UNION/MINUS/EXCEPT/INTERSECT injection |
| SqlCommentChecker | CRITICAL | Detects comment-based SQL injection |
| IntoOutfileChecker | CRITICAL | Detects MySQL file write operations (INTO OUTFILE) |

### Dangerous Operation Checkers (9-11)

| Checker | Severity | Description |
|---------|----------|-------------|
| DdlOperationChecker | CRITICAL | Detects DDL operations (CREATE/ALTER/DROP/TRUNCATE) |
| DangerousFunctionChecker | CRITICAL | Detects dangerous functions (load_file, sys_exec, sleep) |
| CallStatementChecker | HIGH | Detects stored procedure calls (CALL/EXECUTE/EXEC) |

### Access Control Checkers (12-15)

| Checker | Severity | Description |
|---------|----------|-------------|
| MetadataStatementChecker | HIGH | Detects metadata disclosure (SHOW/DESCRIBE/USE) |
| SetStatementChecker | HIGH | Detects session variable modification (SET statements) |
| DeniedTableChecker | CRITICAL | Enforces table-level access blacklist (sys_*, admin_*) |
| ReadOnlyTableChecker | HIGH | Protects read-only tables from write operations |

## Advanced Configuration

### Custom Validators

You can extend SQL Guard with custom validators by providing a custom configuration file:

```yaml
rules:
  custom:
    enabled: true
    className: com.example.CustomSqlValidator
    severity: HIGH
    config:
      maxTableJoins: 5
      allowedFunctions:
        - COUNT
        - SUM
        - AVG
```

### Performance Tuning

For large projects, consider:

1. **Limit scan scope:**
   ```groovy
   sqlguard {
       projectPath = file('src/main/resources/mappers')  // Only scan mappers
   }
   ```

2. **Parallel execution:**
   ```bash
   gradle sqlguardScan --parallel
   ```

3. **Incremental builds:**
   The task supports Gradle's up-to-date checking based on input/output files.

## Integration with Other Tools

### SonarQube Integration

Export HTML report for SonarQube analysis:

```groovy
sqlguard {
    html(file('build/reports/sqlguard/sonar-report.html'))
}
```

### Jenkins Integration

Add to Jenkins pipeline:

```groovy
stage('SQL Safety Check') {
    steps {
        sh './gradlew sqlguardScan -PfailOnCritical'
        publishHTML([
            reportDir: 'build/reports/sqlguard',
            reportFiles: 'report.html',
            reportName: 'SQL Safety Report'
        ])
    }
}
```

### GitLab CI Integration

Add to `.gitlab-ci.yml`:

```yaml
sql_safety_check:
  stage: test
  script:
    - ./gradlew sqlguardScan -PfailOnCritical
  artifacts:
    reports:
      junit: build/reports/sqlguard/report.html
    when: always
```

## Requirements

- **Gradle:** 7.0 or higher
- **Java:** 8 or higher
- **Dependencies:** sql-scanner-core (automatically included)

## License

Copyright © 2024 Footstone. All rights reserved.

## Support

For issues, questions, or contributions:
- GitHub Issues: [sql-safety-guard/issues](https://github.com/footstone/sql-safety-guard/issues)
- Documentation: [sql-safety-guard/docs](https://github.com/footstone/sql-safety-guard/docs)
- Email: support@footstone.com

## Changelog

### Version 1.0.0-SNAPSHOT
- Initial release
- Basic SQL scanning functionality
- Console and HTML report generation
- DSL-based configuration
- CI/CD integration support
- Gradle 7.0+ compatibility
















