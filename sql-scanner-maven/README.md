# SQL Guard Maven Plugin

Maven plugin for SQL Safety Guard providing CI/CD integration via `mvn sqlguard:scan` goal. Performs static analysis on SQL files in MyBatis projects, generates reports, and optionally fails builds on critical violations.

## Features

- 🔍 **Static SQL Analysis** - Scans MyBatis XML mappers and Java annotations
- 📊 **Dual Report Formats** - Console and HTML reports
- 🚨 **Build Quality Gates** - Fail builds on CRITICAL violations
- ⚙️ **Configurable Rules** - YAML-based configuration
- 🎯 **Maven Lifecycle Integration** - Runs automatically in verify phase
- 🔧 **4 Core Checkers** - NoWhereClause, DummyCondition, BlacklistField, WhitelistField

## Quick Start

### 1. Add Plugin to pom.xml

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

### 2. Run Scan

```bash
mvn sqlguard:scan
```

Or as part of the build lifecycle:

```bash
mvn verify
```

## Configuration

### Parameters

| Parameter | Property | Default | Description |
|-----------|----------|---------|-------------|
| projectPath | sqlguard.projectPath | ${project.basedir} | Project root path to scan |
| configFile | sqlguard.configFile | null | YAML config file path |
| outputFormat | sqlguard.outputFormat | console | Report format: console, html, both |
| outputFile | sqlguard.outputFile | target/sqlguard-report.html | HTML report output path |
| failOnCritical | sqlguard.failOnCritical | false | Fail build on CRITICAL violations |
| skip | sqlguard.skip | false | Skip plugin execution |

### Configuration Examples

#### Basic Configuration

```xml
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
```

#### HTML Report Output

```xml
<plugin>
    <groupId>com.footstone</groupId>
    <artifactId>sql-scanner-maven</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>scan</goal>
            </goals>
            <configuration>
                <outputFormat>html</outputFormat>
                <outputFile>${project.build.directory}/sql-safety-report.html</outputFile>
            </configuration>
        </execution>
    </executions>
</plugin>
```

#### Fail on Critical Violations

```xml
<plugin>
    <groupId>com.footstone</groupId>
    <artifactId>sql-scanner-maven</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>scan</goal>
            </goals>
            <configuration>
                <failOnCritical>true</failOnCritical>
                <outputFormat>both</outputFormat>
            </configuration>
        </execution>
    </executions>
</plugin>
```

#### Custom Configuration File

```xml
<plugin>
    <groupId>com.footstone</groupId>
    <artifactId>sql-scanner-maven</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>scan</goal>
            </goals>
            <configuration>
                <configFile>${project.basedir}/sqlguard-config.yaml</configFile>
            </configuration>
        </execution>
    </executions>
</plugin>
```

#### Skip Execution

```xml
<plugin>
    <groupId>com.footstone</groupId>
    <artifactId>sql-scanner-maven</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>scan</goal>
            </goals>
            <configuration>
                <skip>${skipSqlGuard}</skip>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Then skip via command line:

```bash
mvn verify -DskipSqlGuard=true
```

## Usage Examples

### Example 1: Basic Scan

Scan project with default settings:

```bash
mvn sqlguard:scan
```

Output:
```
[INFO] Starting SQL Safety Guard scan...
[INFO] Project path: /path/to/project
[INFO] Using default configuration
[INFO] Initializing parsers...
[INFO] Creating SQL Safety Validator...
[INFO] Creating SQL Scanner...
[INFO] Executing scan...
[INFO] Scan completed. SQL statements: 15, Total violations: 3
[INFO] Generating console report...
[INFO] SQL Safety Guard scan completed
```

### Example 2: Generate HTML Report

```bash
mvn sqlguard:scan -Dsqlguard.outputFormat=html -Dsqlguard.outputFile=target/report.html
```

This generates an interactive HTML report at `target/report.html` with:
- Summary statistics
- Violation details by severity
- SQL code snippets
- File locations

### Example 3: CI/CD Integration with Build Failure

```xml
<plugin>
    <groupId>com.footstone</groupId>
    <artifactId>sql-scanner-maven</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <executions>
        <execution>
            <phase>verify</phase>
            <goals>
                <goal>scan</goal>
            </goals>
            <configuration>
                <failOnCritical>true</failOnCritical>
                <outputFormat>both</outputFormat>
            </configuration>
        </execution>
    </executions>
</plugin>
```

In CI/CD pipeline:

```bash
mvn clean verify
```

If CRITICAL violations are found, the build fails:

```
[ERROR] SQL Safety Guard detected CRITICAL violation(s). 
        Build failed. See report for details. Total violations: 5
```

### Example 4: Custom Configuration

Create `sqlguard-config.yaml`:

```yaml
rules:
  noWhereClause:
    enabled: true
    riskLevel: CRITICAL
    excludedTables:
      - audit_log
      - system_config
  
  dummyCondition:
    enabled: true
    riskLevel: HIGH
  
  blacklistFields:
    enabled: true
    riskLevel: HIGH
    fields:
      - password
      - credit_card
      - ssn
  
  whitelistFields:
    enabled: true
    riskLevel: MEDIUM
    fields:
      - id
      - created_at
      - updated_at
```

Configure plugin:

```xml
<configuration>
    <configFile>${project.basedir}/sqlguard-config.yaml</configFile>
    <failOnCritical>true</failOnCritical>
</configuration>
```

### Example 5: Multi-Module Project

For multi-module Maven projects, configure in parent POM:

```xml
<build>
    <pluginManagement>
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
                        <configuration>
                            <failOnCritical>true</failOnCritical>
                            <outputFormat>html</outputFormat>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </pluginManagement>
</build>
```

Then in each module that needs scanning:

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

Run scan on all modules:

```bash
mvn sqlguard:scan
```

## Violation Severity Levels

| Level | Description | Default Action |
|-------|-------------|----------------|
| CRITICAL | Dangerous SQL that could cause data loss or security issues | Fail build (if failOnCritical=true) |
| HIGH | Risky SQL patterns that should be reviewed | Log error |
| MEDIUM | SQL patterns that may cause issues | Log warning |
| LOW | Minor SQL quality issues | Log info |

## Core Checkers

### 1. NoWhereClauseChecker (CRITICAL)

Detects DELETE/UPDATE statements without WHERE clause:

```xml
<!-- ❌ CRITICAL: Missing WHERE clause -->
<delete id="deleteAll">
    DELETE FROM users
</delete>

<!-- ✅ OK: Has WHERE clause -->
<delete id="deleteById">
    DELETE FROM users WHERE id = #{id}
</delete>
```

### 2. DummyConditionChecker (HIGH)

Detects dummy conditions like `1=1` or `'a'='a'`:

```xml
<!-- ❌ HIGH: Dummy condition -->
<select id="selectAll">
    SELECT * FROM users WHERE 1=1
</select>

<!-- ✅ OK: Real condition -->
<select id="selectById">
    SELECT * FROM users WHERE id = #{id}
</select>
```

### 3. BlacklistFieldChecker (HIGH)

Detects queries on sensitive fields:

```xml
<!-- ❌ HIGH: Selecting sensitive field -->
<select id="getPassword">
    SELECT password FROM users WHERE id = #{id}
</select>

<!-- ✅ OK: Selecting safe fields -->
<select id="getUser">
    SELECT id, name, email FROM users WHERE id = #{id}
</select>
```

### 4. WhitelistFieldChecker (MEDIUM)

Detects SELECT * queries:

```xml
<!-- ❌ MEDIUM: SELECT * -->
<select id="selectAll">
    SELECT * FROM users WHERE id = #{id}
</select>

<!-- ✅ OK: Explicit field list -->
<select id="selectUser">
    SELECT id, name, email FROM users WHERE id = #{id}
</select>
```

## Troubleshooting

### Plugin Not Found

If Maven can't find the plugin:

```bash
mvn clean install -pl sql-scanner-maven
```

### No SQL Files Found

Ensure your project structure follows Maven conventions:
```
src/
  main/
    resources/
      mappers/          # MyBatis XML mappers here
    java/
      com/example/      # Java files with @Select, @Update, etc.
```

### Build Fails Unexpectedly

Check the HTML report for detailed violation information:

```bash
mvn sqlguard:scan -Dsqlguard.outputFormat=html
open target/sqlguard-report.html
```

### Custom Configuration Not Loaded

Verify the config file path:

```bash
mvn sqlguard:scan -Dsqlguard.configFile=./sqlguard-config.yaml -X
```

The `-X` flag enables debug logging.

## Requirements

- Maven 3.6.0 or higher
- Java 8 or higher
- MyBatis project structure

## Integration with CI/CD

### GitHub Actions

```yaml
name: SQL Safety Check

on: [push, pull_request]

jobs:
  sql-safety:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'
      - name: Run SQL Safety Guard
        run: mvn sqlguard:scan -Dsqlguard.failOnCritical=true
      - name: Upload Report
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: sql-safety-report
          path: target/sqlguard-report.html
```

### GitLab CI

```yaml
sql-safety-check:
  stage: test
  script:
    - mvn sqlguard:scan -Dsqlguard.failOnCritical=true
  artifacts:
    when: always
    paths:
      - target/sqlguard-report.html
    expire_in: 1 week
```

### Jenkins

```groovy
stage('SQL Safety Check') {
    steps {
        sh 'mvn sqlguard:scan -Dsqlguard.failOnCritical=true'
    }
    post {
        always {
            publishHTML([
                reportDir: 'target',
                reportFiles: 'sqlguard-report.html',
                reportName: 'SQL Safety Report'
            ])
        }
    }
}
```

## License

Copyright © 2025 Footstone Technology. All rights reserved.

## Support

For issues and questions:
- GitHub Issues: https://github.com/footstone/sql-safety-guard/issues
- Documentation: https://github.com/footstone/sql-safety-guard/wiki

