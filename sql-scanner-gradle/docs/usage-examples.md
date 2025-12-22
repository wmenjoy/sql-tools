# SQL Guard Gradle Plugin - Usage Examples

This document provides comprehensive examples for using the SQL Guard Gradle Plugin in various scenarios.

## Table of Contents

1. [Basic Usage](#basic-usage)
2. [Configuration Examples](#configuration-examples)
3. [CI/CD Integration](#cicd-integration)
4. [Multi-Project Builds](#multi-project-builds)
5. [Advanced Scenarios](#advanced-scenarios)

---

## Basic Usage

### Example 1: Minimal Configuration

The simplest way to use SQL Guard - just apply the plugin and run:

**build.gradle:**
```groovy
plugins {
    id 'java'
    id 'com.footstone.sqlguard' version '1.0.0-SNAPSHOT'
}
```

**Run:**
```bash
gradle sqlguardScan
```

**Output:**
```
> Task :sqlguardScan
Starting SQL Safety Guard scan...
Using default configuration
Initializing parsers...
Creating SQL Safety Validator...
Creating SQL Scanner...
Executing scan...
Scan completed. SQL statements: 15, Total violations: 3
Generating console report...

================================================================================
                        SQL Safety Guard Scan Report
================================================================================

Scan Summary:
  Total SQL Statements: 15
  Total Violations: 3
```

---

### Example 2: Generate HTML Report

Generate a visual HTML report for better analysis:

**build.gradle:**
```groovy
plugins {
    id 'java'
    id 'com.footstone.sqlguard' version '1.0.0-SNAPSHOT'
}

sqlguard {
    html()  // Generate HTML report
}
```

**Run:**
```bash
gradle sqlguardScan
```

**Result:**
- HTML report generated at: `build/reports/sqlguard/report.html`
- Open in browser for interactive viewing

---

### Example 3: Both Console and HTML Output

Get immediate feedback in console and detailed HTML report:

**build.gradle:**
```groovy
sqlguard {
    both()  // Output to both console and HTML
}
```

---

## Configuration Examples

### Example 4: Custom Configuration File

Define custom SQL safety rules in a YAML file:

**build.gradle:**
```groovy
sqlguard {
    configFile = file('config/sql-safety-rules.yml')
    both()
}
```

**config/sql-safety-rules.yml:**
```yaml
rules:
  noWhereClause:
    enabled: true
    severity: CRITICAL
    excludeOperations:
      - INSERT
    
  dummyCondition:
    enabled: true
    severity: HIGH
    patterns:
      - "1=1"
      - "1 = 1"
      - "true"
    
  blacklistFields:
    enabled: true
    severity: HIGH
    fields:
      - password
      - secret_key
      - api_token
      - credit_card
    
  whitelistFields:
    enabled: false
    fields:
      - id
      - name
      - email

scanning:
  includePaths:
    - "src/main/resources/mappers/**/*.xml"
    - "src/main/java/**/mapper/**/*.java"
  excludePaths:
    - "src/test/**"
    - "**/generated/**"
```

---

### Example 5: Custom Output Location

Specify custom location for HTML report:

**build.gradle:**
```groovy
sqlguard {
    html(file('reports/sql-analysis/safety-report.html'))
}
```

Or using property syntax:

```groovy
sqlguard {
    outputFormat = 'html'
    outputFile = file("${buildDir}/custom-reports/sql-guard.html")
}
```

---

### Example 6: Scan Specific Directory

Limit scanning to specific directories:

**build.gradle:**
```groovy
sqlguard {
    projectPath = file('src/main/resources/mappers')  // Only scan mappers
    console()
}
```

Or scan multiple source sets:

```groovy
task scanMainSources(type: com.footstone.sqlguard.gradle.SqlGuardScanTask) {
    projectPath.set(file('src/main'))
    outputFormat.set('html')
    outputFile.set(file('build/reports/sqlguard/main-report.html'))
}

task scanTestSources(type: com.footstone.sqlguard.gradle.SqlGuardScanTask) {
    projectPath.set(file('src/test'))
    outputFormat.set('html')
    outputFile.set(file('build/reports/sqlguard/test-report.html'))
}
```

---

## CI/CD Integration

### Example 7: Fail Build on Critical Violations

Prevent deployment of code with critical SQL safety issues:

**build.gradle:**
```groovy
sqlguard {
    outputFormat = 'both'
    failOnCritical()  // Fail build on CRITICAL violations
}

// Integrate with build lifecycle
build.dependsOn sqlguardScan
```

**CI/CD Pipeline:**
```bash
# Build will fail if CRITICAL violations found
gradle clean build

# Exit code: 1 if violations found
# Exit code: 0 if no violations
```

---

### Example 8: Jenkins Pipeline Integration

**Jenkinsfile:**
```groovy
pipeline {
    agent any
    
    stages {
        stage('Build') {
            steps {
                sh './gradlew clean build'
            }
        }
        
        stage('SQL Safety Check') {
            steps {
                sh './gradlew sqlguardScan'
            }
            post {
                always {
                    publishHTML([
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'build/reports/sqlguard',
                        reportFiles: 'report.html',
                        reportName: 'SQL Safety Report',
                        reportTitles: 'SQL Safety Guard Analysis'
                    ])
                }
                failure {
                    emailext(
                        subject: "SQL Safety Violations Detected: ${env.JOB_NAME}",
                        body: "Critical SQL safety violations found. Check report for details.",
                        to: "dev-team@example.com"
                    )
                }
            }
        }
    }
}
```

**build.gradle:**
```groovy
sqlguard {
    html(file('build/reports/sqlguard/report.html'))
    failOnCritical()
}
```

---

### Example 9: GitLab CI Integration

**.gitlab-ci.yml:**
```yaml
stages:
  - build
  - test
  - security

build:
  stage: build
  script:
    - ./gradlew clean build
  artifacts:
    paths:
      - build/

sql_safety_check:
  stage: security
  script:
    - ./gradlew sqlguardScan
  artifacts:
    reports:
      junit: build/reports/sqlguard/report.html
    paths:
      - build/reports/sqlguard/
    when: always
  allow_failure: false  # Fail pipeline on violations
```

**build.gradle:**
```groovy
sqlguard {
    outputFormat = 'both'
    outputFile = file('build/reports/sqlguard/report.html')
    failOnCritical()
}
```

---

### Example 10: GitHub Actions Integration

**.github/workflows/sql-safety.yml:**
```yaml
name: SQL Safety Check

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  sql-safety:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 11
      uses: actions/setup-java@v3
      with:
        java-version: '11'
        distribution: 'temurin'
    
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
    
    - name: Run SQL Safety Guard
      run: ./gradlew sqlguardScan
    
    - name: Upload SQL Safety Report
      if: always()
      uses: actions/upload-artifact@v3
      with:
        name: sql-safety-report
        path: build/reports/sqlguard/report.html
    
    - name: Comment PR with Results
      if: github.event_name == 'pull_request'
      uses: actions/github-script@v6
      with:
        script: |
          const fs = require('fs');
          const report = fs.readFileSync('build/reports/sqlguard/report.html', 'utf8');
          // Parse and comment on PR
```

**build.gradle:**
```groovy
sqlguard {
    html(file('build/reports/sqlguard/report.html'))
    failOnCritical()
}
```

---

## Multi-Project Builds

### Example 11: Configure All Subprojects

Apply SQL Guard to all subprojects:

**Root build.gradle:**
```groovy
subprojects {
    apply plugin: 'com.footstone.sqlguard'
    
    sqlguard {
        outputFormat = 'html'
        outputFile = file("${project.buildDir}/reports/sqlguard/${project.name}-report.html")
        failOnCritical()
    }
    
    // Run scan during build
    build.dependsOn sqlguardScan
}
```

**Run all scans:**
```bash
gradle sqlguardScan
```

**Run specific subproject:**
```bash
gradle :user-service:sqlguardScan
gradle :order-service:sqlguardScan
```

---

### Example 12: Selective Subproject Configuration

Apply different configurations to different subprojects:

**Root build.gradle:**
```groovy
subprojects {
    apply plugin: 'com.footstone.sqlguard'
}

project(':user-service') {
    sqlguard {
        configFile = file('config/user-service-rules.yml')
        html()
        failOnCritical()
    }
}

project(':order-service') {
    sqlguard {
        configFile = file('config/order-service-rules.yml')
        both()
        failOnCritical()
    }
}

project(':reporting-service') {
    sqlguard {
        // Reporting service has read-only queries - less strict
        console()
        failOnCritical = false
    }
}
```

---

### Example 13: Aggregate Multi-Project Reports

Create a task to aggregate reports from all subprojects:

**Root build.gradle:**
```groovy
task aggregateSqlReports {
    dependsOn subprojects.collect { it.tasks.named('sqlguardScan') }
    
    doLast {
        def reportDir = file("${buildDir}/reports/sqlguard-aggregate")
        reportDir.mkdirs()
        
        subprojects.each { subproject ->
            def subReportFile = file("${subproject.buildDir}/reports/sqlguard/report.html")
            if (subReportFile.exists()) {
                copy {
                    from subReportFile
                    into reportDir
                    rename { "${subproject.name}-report.html" }
                }
            }
        }
        
        println "Aggregate reports generated in: ${reportDir}"
    }
}
```

**Run:**
```bash
gradle aggregateSqlReports
```

---

## Advanced Scenarios

### Example 14: Environment-Specific Configuration

Use different configurations for different environments:

**build.gradle:**
```groovy
def environment = project.hasProperty('env') ? project.property('env') : 'dev'

sqlguard {
    if (environment == 'prod' || environment == 'staging') {
        // Strict configuration for production
        configFile = file('config/sql-rules-strict.yml')
        outputFormat = 'both'
        failOnCritical()
    } else if (environment == 'ci') {
        // CI configuration
        html(file('build/reports/sqlguard/ci-report.html'))
        failOnCritical()
    } else {
        // Development configuration - more lenient
        console()
        failOnCritical = false
    }
}
```

**Run:**
```bash
# Development
gradle sqlguardScan

# CI
gradle sqlguardScan -Penv=ci

# Production validation
gradle sqlguardScan -Penv=prod
```

---

### Example 15: Custom Task for Different Scan Profiles

Create multiple scan tasks with different profiles:

**build.gradle:**
```groovy
// Quick scan - console only, no build failure
task quickSqlScan(type: com.footstone.sqlguard.gradle.SqlGuardScanTask) {
    group = 'verification'
    description = 'Quick SQL scan with console output'
    
    projectPath.set(project.projectDir)
    outputFormat.set('console')
    failOnCritical.set(false)
}

// Full scan - HTML report, fail on critical
task fullSqlScan(type: com.footstone.sqlguard.gradle.SqlGuardScanTask) {
    group = 'verification'
    description = 'Full SQL scan with HTML report'
    
    projectPath.set(project.projectDir)
    outputFormat.set('both')
    outputFile.set(file('build/reports/sqlguard/full-report.html'))
    failOnCritical.set(true)
}

// Strict scan - custom rules, fail on any violation
task strictSqlScan(type: com.footstone.sqlguard.gradle.SqlGuardScanTask) {
    group = 'verification'
    description = 'Strict SQL scan with custom rules'
    
    projectPath.set(project.projectDir)
    configFile.set(file('config/strict-rules.yml'))
    outputFormat.set('both')
    failOnCritical.set(true)
}
```

**Usage:**
```bash
# During development
gradle quickSqlScan

# Before committing
gradle fullSqlScan

# Before release
gradle strictSqlScan
```

---

### Example 16: Integration with Code Quality Tools

Integrate SQL Guard with other code quality tools:

**build.gradle:**
```groovy
plugins {
    id 'java'
    id 'checkstyle'
    id 'pmd'
    id 'com.footstone.sqlguard' version '1.0.0-SNAPSHOT'
}

sqlguard {
    html(file('build/reports/sqlguard/report.html'))
    failOnCritical()
}

// Create a quality check task that runs all checks
task qualityCheck {
    group = 'verification'
    description = 'Run all code quality checks'
    
    dependsOn checkstyleMain, pmdMain, sqlguardScan
}

// Make build depend on quality check
build.dependsOn qualityCheck
```

---

### Example 17: Conditional Scanning Based on Changes

Only scan SQL files that have changed:

**build.gradle:**
```groovy
task incrementalSqlScan {
    group = 'verification'
    description = 'Scan only changed SQL files'
    
    doLast {
        def changedFiles = 'git diff --name-only HEAD~1'.execute().text
        def sqlFiles = changedFiles.split('\n').findAll { 
            it.endsWith('.xml') || it.endsWith('.java') 
        }
        
        if (sqlFiles.isEmpty()) {
            println "No SQL files changed, skipping scan"
        } else {
            println "Scanning changed files: ${sqlFiles.join(', ')}"
            tasks.sqlguardScan.execute()
        }
    }
}
```

---

### Example 18: Parallel Scanning for Large Projects

Optimize scanning for large projects:

**build.gradle:**
```groovy
// Split scanning by module
def modules = ['user', 'order', 'product', 'payment']

modules.each { module ->
    task "scan${module.capitalize()}Module"(type: com.footstone.sqlguard.gradle.SqlGuardScanTask) {
        projectPath.set(file("src/main/resources/mappers/${module}"))
        outputFormat.set('html')
        outputFile.set(file("build/reports/sqlguard/${module}-report.html"))
        failOnCritical.set(true)
    }
}

// Aggregate task
task scanAllModules {
    dependsOn modules.collect { "scan${it.capitalize()}Module" }
}
```

**Run in parallel:**
```bash
gradle scanAllModules --parallel
```

---

## Best Practices

### 1. Start with Console Output
Begin with console output during development for quick feedback:
```groovy
sqlguard {
    console()
}
```

### 2. Enable HTML Reports for CI
Use HTML reports in CI for better visualization:
```groovy
sqlguard {
    html(file('build/reports/sqlguard/ci-report.html'))
}
```

### 3. Fail Fast in Production Pipelines
Enable `failOnCritical()` for production deployments:
```groovy
sqlguard {
    failOnCritical()
}
```

### 4. Use Custom Configuration Files
Maintain separate configuration files for different environments:
```
config/
├── sql-rules-dev.yml
├── sql-rules-staging.yml
└── sql-rules-prod.yml
```

### 5. Integrate with Build Lifecycle
Make SQL scanning part of your standard build process:
```groovy
build.dependsOn sqlguardScan
```

---

## Troubleshooting Examples

### Example 19: Debug Mode

Enable detailed logging for troubleshooting:

**build.gradle:**
```groovy
sqlguard {
    console()
}

// Run with debug logging
tasks.sqlguardScan.doFirst {
    logging.level = LogLevel.DEBUG
}
```

**Run:**
```bash
gradle sqlguardScan --debug
```

---

### Example 20: Dry Run

Test configuration without failing the build:

**build.gradle:**
```groovy
task sqlScanDryRun(type: com.footstone.sqlguard.gradle.SqlGuardScanTask) {
    projectPath.set(project.projectDir)
    outputFormat.set('both')
    failOnCritical.set(false)  // Never fail in dry run
}
```

---

## Summary

This document covered comprehensive usage examples for the SQL Guard Gradle Plugin, including:

- Basic usage patterns
- Configuration options
- CI/CD integration
- Multi-project builds
- Advanced scenarios
- Best practices

For more information, see the main [README.md](../README.md) or visit the [project documentation](https://github.com/footstone/sql-safety-guard).









