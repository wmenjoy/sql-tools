# SQL Guard Maven Plugin - Usage Examples

This document provides detailed usage examples for the SQL Guard Maven Plugin.

## Table of Contents

1. [Basic Usage](#basic-usage)
2. [Report Generation](#report-generation)
3. [CI/CD Integration](#cicd-integration)
4. [Custom Configuration](#custom-configuration)
5. [Multi-Module Projects](#multi-module-projects)
6. [Advanced Scenarios](#advanced-scenarios)

## Basic Usage

### Example 1.1: First Time Setup

**Scenario**: You want to add SQL safety checking to an existing MyBatis project.

**Step 1**: Add plugin to `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.footstone</groupId>
            <artifactId>sql-scanner-maven</artifactId>
            <version>1.0.0-SNAPSHOT</version>
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
```

**Step 2**: Run initial scan:

```bash
mvn sqlguard:scan
```

**Expected Output**:

```
[INFO] Starting SQL Safety Guard scan...
[INFO] Project path: /Users/dev/myproject
[INFO] Using default configuration
[INFO] Initializing parsers...
[INFO] Creating SQL Safety Validator...
[INFO] Creating SQL Scanner...
[INFO] Executing scan...
[INFO] Scan completed. SQL statements: 42, Total violations: 8
[INFO] Generating console report...
[INFO] 
[INFO] ═══════════════════════════════════════════════════════════
[INFO]               SQL Safety Guard Report
[INFO] ═══════════════════════════════════════════════════════════
[INFO] 
[INFO] Summary:
[INFO]   Total SQL Statements: 42
[INFO]   Total Violations: 8
[INFO]   CRITICAL: 2
[INFO]   HIGH: 3
[INFO]   MEDIUM: 3
[INFO]   LOW: 0
[INFO] 
[INFO] SQL Safety Guard scan completed
```

### Example 1.2: Command Line Scan

**Scenario**: Quick scan without modifying `pom.xml`.

```bash
mvn com.footstone:sql-scanner-maven:1.0.0-SNAPSHOT:scan
```

With custom parameters:

```bash
mvn sqlguard:scan \
  -Dsqlguard.outputFormat=html \
  -Dsqlguard.outputFile=./sql-report.html \
  -Dsqlguard.failOnCritical=false
```

### Example 1.3: Skip Scan Conditionally

**Scenario**: Skip scan in development but run in CI.

```xml
<plugin>
    <groupId>com.footstone</groupId>
    <artifactId>sql-scanner-maven</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <configuration>
        <skip>${skipSqlScan}</skip>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>scan</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**Properties**:

```xml
<properties>
    <skipSqlScan>false</skipSqlScan>
</properties>
```

**Usage**:

```bash
# Run scan
mvn verify

# Skip scan
mvn verify -DskipSqlScan=true
```

## Report Generation

### Example 2.1: Console Report (Default)

**Configuration**:

```xml
<configuration>
    <outputFormat>console</outputFormat>
</configuration>
```

**Output**:

```
[INFO] Generating console report...
[INFO] 
[INFO] ═══════════════════════════════════════════════════════════
[INFO]               SQL Safety Guard Report
[INFO] ═══════════════════════════════════════════════════════════
[INFO] 
[ERROR] [CRITICAL] No WHERE clause in DELETE/UPDATE
[ERROR]   File: src/main/resources/mappers/UserMapper.xml
[ERROR]   Line: 15
[ERROR]   SQL: DELETE FROM users
[ERROR]   Suggestion: Add WHERE clause to prevent accidental data deletion
[ERROR] 
[ERROR] [HIGH] Dummy condition detected
[ERROR]   File: src/main/resources/mappers/OrderMapper.xml
[ERROR]   Line: 23
[ERROR]   SQL: SELECT * FROM orders WHERE 1=1
[ERROR]   Suggestion: Replace dummy condition with real business logic
```

### Example 2.2: HTML Report

**Configuration**:

```xml
<configuration>
    <outputFormat>html</outputFormat>
    <outputFile>${project.build.directory}/reports/sql-safety.html</outputFile>
</configuration>
```

**Generated Report Features**:
- Interactive violation filtering by severity
- Syntax-highlighted SQL code
- Clickable file paths
- Summary charts
- Export to PDF option

**Access Report**:

```bash
mvn sqlguard:scan
open target/reports/sql-safety.html
```

### Example 2.3: Both Console and HTML

**Configuration**:

```xml
<configuration>
    <outputFormat>both</outputFormat>
    <outputFile>${project.build.directory}/sql-safety-report.html</outputFile>
</configuration>
```

**Usage**:

```bash
mvn sqlguard:scan
# Console output displayed immediately
# HTML report saved to target/sql-safety-report.html
```

## CI/CD Integration

### Example 3.1: GitHub Actions

**File**: `.github/workflows/sql-safety.yml`

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
      - name: Checkout code
        uses: actions/checkout@v3
      
      - name: Set up JDK 11
        uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'
          cache: 'maven'
      
      - name: Run SQL Safety Guard
        run: |
          mvn sqlguard:scan \
            -Dsqlguard.outputFormat=both \
            -Dsqlguard.failOnCritical=true
      
      - name: Upload HTML Report
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: sql-safety-report
          path: target/sqlguard-report.html
          retention-days: 30
      
      - name: Comment PR with Results
        if: github.event_name == 'pull_request'
        uses: actions/github-script@v6
        with:
          script: |
            const fs = require('fs');
            const report = fs.readFileSync('target/sqlguard-report.html', 'utf8');
            // Parse report and post comment
```

### Example 3.2: GitLab CI

**File**: `.gitlab-ci.yml`

```yaml
stages:
  - test
  - report

sql-safety-check:
  stage: test
  image: maven:3.8-openjdk-11
  script:
    - mvn sqlguard:scan -Dsqlguard.failOnCritical=true
  artifacts:
    when: always
    paths:
      - target/sqlguard-report.html
    reports:
      junit: target/surefire-reports/TEST-*.xml
    expire_in: 1 week
  only:
    - merge_requests
    - main

pages:
  stage: report
  dependencies:
    - sql-safety-check
  script:
    - mkdir -p public
    - cp target/sqlguard-report.html public/index.html
  artifacts:
    paths:
      - public
  only:
    - main
```

### Example 3.3: Jenkins Pipeline

**File**: `Jenkinsfile`

```groovy
pipeline {
    agent any
    
    tools {
        maven 'Maven 3.8'
        jdk 'JDK 11'
    }
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        
        stage('SQL Safety Check') {
            steps {
                sh '''
                    mvn sqlguard:scan \
                        -Dsqlguard.outputFormat=both \
                        -Dsqlguard.failOnCritical=true
                '''
            }
            post {
                always {
                    publishHTML([
                        reportDir: 'target',
                        reportFiles: 'sqlguard-report.html',
                        reportName: 'SQL Safety Report',
                        keepAll: true,
                        alwaysLinkToLastBuild: true
                    ])
                }
                failure {
                    emailext(
                        subject: "SQL Safety Check Failed: ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
                        body: "Check console output at ${env.BUILD_URL}",
                        to: "${env.CHANGE_AUTHOR_EMAIL}"
                    )
                }
            }
        }
    }
}
```

### Example 3.4: Azure DevOps

**File**: `azure-pipelines.yml`

```yaml
trigger:
  - main
  - develop

pool:
  vmImage: 'ubuntu-latest'

steps:
  - task: Maven@3
    inputs:
      mavenPomFile: 'pom.xml'
      goals: 'sqlguard:scan'
      options: '-Dsqlguard.outputFormat=both -Dsqlguard.failOnCritical=true'
      publishJUnitResults: false
      javaHomeOption: 'JDKVersion'
      jdkVersionOption: '1.11'
      mavenVersionOption: 'Default'
    displayName: 'Run SQL Safety Guard'
  
  - task: PublishBuildArtifacts@1
    condition: always()
    inputs:
      pathToPublish: 'target/sqlguard-report.html'
      artifactName: 'sql-safety-report'
    displayName: 'Publish SQL Safety Report'
```

## Custom Configuration

### Example 4.1: Basic YAML Configuration

**File**: `sqlguard-config.yaml`

```yaml
rules:
  noWhereClause:
    enabled: true
    riskLevel: CRITICAL
  
  dummyCondition:
    enabled: true
    riskLevel: HIGH
  
  blacklistFields:
    enabled: true
    riskLevel: HIGH
  
  whitelistFields:
    enabled: true
    riskLevel: MEDIUM
```

**Plugin Configuration**:

```xml
<configuration>
    <configFile>${project.basedir}/sqlguard-config.yaml</configFile>
</configuration>
```

### Example 4.2: Advanced Configuration with Exclusions

**File**: `sqlguard-config.yaml`

```yaml
rules:
  noWhereClause:
    enabled: true
    riskLevel: CRITICAL
    excludedTables:
      - audit_log        # Bulk operations allowed
      - system_config    # Admin operations
      - temp_data        # Temporary data
    excludedStatements:
      - deleteExpiredSessions
      - clearCache
  
  dummyCondition:
    enabled: true
    riskLevel: HIGH
    allowedPatterns:
      - "WHERE 1=1 AND"  # Allow if followed by real conditions
  
  blacklistFields:
    enabled: true
    riskLevel: HIGH
    fields:
      - password
      - password_hash
      - credit_card
      - credit_card_number
      - ssn
      - social_security_number
      - api_key
      - secret_key
      - private_key
    excludedStatements:
      - authenticateUser  # Password check is legitimate
      - hashPassword      # Password hashing is legitimate
  
  whitelistFields:
    enabled: true
    riskLevel: MEDIUM
    allowSelectStar: false
    whitelistedFields:
      - id
      - created_at
      - updated_at
      - created_by
      - updated_by
      - version
```

### Example 4.3: Environment-Specific Configuration

**Development** (`sqlguard-dev.yaml`):

```yaml
rules:
  noWhereClause:
    enabled: true
    riskLevel: HIGH  # Warn but don't fail
  
  dummyCondition:
    enabled: true
    riskLevel: MEDIUM
  
  blacklistFields:
    enabled: false  # Disabled in dev
  
  whitelistFields:
    enabled: false  # Disabled in dev
```

**Production** (`sqlguard-prod.yaml`):

```yaml
rules:
  noWhereClause:
    enabled: true
    riskLevel: CRITICAL  # Fail build
  
  dummyCondition:
    enabled: true
    riskLevel: HIGH
  
  blacklistFields:
    enabled: true
    riskLevel: CRITICAL
  
  whitelistFields:
    enabled: true
    riskLevel: HIGH
```

**Plugin Configuration**:

```xml
<profiles>
    <profile>
        <id>dev</id>
        <activation>
            <activeByDefault>true</activeByDefault>
        </activation>
        <build>
            <plugins>
                <plugin>
                    <groupId>com.footstone</groupId>
                    <artifactId>sql-scanner-maven</artifactId>
                    <configuration>
                        <configFile>${project.basedir}/sqlguard-dev.yaml</configFile>
                        <failOnCritical>false</failOnCritical>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
    
    <profile>
        <id>prod</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>com.footstone</groupId>
                    <artifactId>sql-scanner-maven</artifactId>
                    <configuration>
                        <configFile>${project.basedir}/sqlguard-prod.yaml</configFile>
                        <failOnCritical>true</failOnCritical>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

**Usage**:

```bash
# Development (lenient)
mvn verify

# Production (strict)
mvn verify -Pprod
```

## Multi-Module Projects

### Example 5.1: Parent POM Configuration

**Project Structure**:

```
my-app/
├── pom.xml (parent)
├── user-service/
│   ├── pom.xml
│   └── src/main/resources/mappers/
├── order-service/
│   ├── pom.xml
│   └── src/main/resources/mappers/
└── payment-service/
    ├── pom.xml
    └── src/main/resources/mappers/
```

**Parent** `pom.xml`:

```xml
<build>
    <pluginManagement>
        <plugins>
            <plugin>
                <groupId>com.footstone</groupId>
                <artifactId>sql-scanner-maven</artifactId>
                <version>1.0.0-SNAPSHOT</version>
                <configuration>
                    <configFile>${session.executionRootDirectory}/sqlguard-config.yaml</configFile>
                    <failOnCritical>true</failOnCritical>
                    <outputFormat>html</outputFormat>
                    <outputFile>${project.build.directory}/sql-safety-${project.artifactId}.html</outputFile>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>scan</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </pluginManagement>
</build>
```

**Module** `pom.xml` (user-service, order-service, payment-service):

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.footstone</groupId>
            <artifactId>sql-scanner-maven</artifactId>
        </plugin>
    </plugins>
</build>
```

**Run Scan**:

```bash
# Scan all modules
mvn sqlguard:scan

# Scan specific module
mvn sqlguard:scan -pl user-service

# Scan multiple modules
mvn sqlguard:scan -pl user-service,order-service
```

### Example 5.2: Aggregated Report

**Parent** `pom.xml`:

```xml
<plugin>
    <groupId>com.footstone</groupId>
    <artifactId>sql-scanner-maven</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <executions>
        <execution>
            <id>aggregate-report</id>
            <phase>verify</phase>
            <goals>
                <goal>scan</goal>
            </goals>
            <configuration>
                <outputFormat>html</outputFormat>
                <outputFile>${project.build.directory}/sql-safety-aggregate.html</outputFile>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Advanced Scenarios

### Example 6.1: Pre-commit Hook

**File**: `.git/hooks/pre-commit`

```bash
#!/bin/bash

echo "Running SQL Safety Guard..."

mvn sqlguard:scan -Dsqlguard.failOnCritical=true -q

if [ $? -ne 0 ]; then
    echo "❌ SQL Safety Guard found CRITICAL violations!"
    echo "Please fix violations before committing."
    echo "Run 'mvn sqlguard:scan' to see details."
    exit 1
fi

echo "✅ SQL Safety Guard passed"
exit 0
```

Make executable:

```bash
chmod +x .git/hooks/pre-commit
```

### Example 6.2: Incremental Scan (Changed Files Only)

**Script**: `scan-changed-files.sh`

```bash
#!/bin/bash

# Get changed SQL files
CHANGED_FILES=$(git diff --name-only HEAD | grep -E '\.xml$|\.java$')

if [ -z "$CHANGED_FILES" ]; then
    echo "No SQL files changed"
    exit 0
fi

echo "Scanning changed files:"
echo "$CHANGED_FILES"

# Run full scan (plugin will scan all files)
mvn sqlguard:scan -Dsqlguard.failOnCritical=true
```

### Example 6.3: Custom Violation Threshold

**Configuration**:

```xml
<configuration>
    <failOnCritical>true</failOnCritical>
    <maxViolations>10</maxViolations>
    <maxCriticalViolations>0</maxCriticalViolations>
    <maxHighViolations>5</maxHighViolations>
</configuration>
```

**Note**: These parameters would need to be added to the plugin implementation.

### Example 6.4: Integration with SonarQube

**File**: `sonar-project.properties`

```properties
sonar.projectKey=my-project
sonar.projectName=My Project
sonar.sources=src/main
sonar.tests=src/test

# SQL Safety Guard Report
sonar.externalIssuesReportPaths=target/sqlguard-sonar-report.json
```

**Plugin Configuration**:

```xml
<configuration>
    <outputFormat>sonar</outputFormat>
    <outputFile>${project.build.directory}/sqlguard-sonar-report.json</outputFile>
</configuration>
```

**Note**: Sonar format would need to be added to the plugin implementation.

## Best Practices

1. **Start Lenient, Get Stricter**: Begin with `failOnCritical=false` and gradually enable stricter checks.

2. **Use Configuration Files**: Store configuration in version control for consistency across team.

3. **Generate HTML Reports in CI**: Always generate HTML reports in CI for easy review.

4. **Set Up Pre-commit Hooks**: Catch violations before they reach CI.

5. **Review Violations Regularly**: Don't let violations accumulate.

6. **Document Exclusions**: Always document why certain tables/statements are excluded.

7. **Use Environment-Specific Configs**: Different strictness for dev vs. prod.

8. **Monitor Trends**: Track violation counts over time to measure code quality improvements.

## Troubleshooting

See main [README.md](../README.md#troubleshooting) for common issues and solutions.

