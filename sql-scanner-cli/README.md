# SQL Scanner CLI

Command-line interface tool for static SQL safety scanning in MyBatis applications.

## Overview

SQL Scanner CLI provides a production-ready command-line tool for scanning Java projects to detect SQL safety issues. It analyzes XML mappers, Java annotation mappers, and MyBatis-Plus QueryWrapper usage to identify potential security risks and code quality issues.

## Features

- 🔍 **Multi-Source Scanning**: XML mappers, Java annotations, QueryWrapper usage
- 📊 **Dual-Format Reports**: Console (ANSI-colored) and HTML (styled web page)
- ⚙️ **Flexible Configuration**: YAML configuration or sensible defaults
- 🚀 **CI/CD Integration**: Exit codes for build pipeline integration
- 🔇 **Quiet Mode**: Minimal output for automated environments
- ✅ **Fail-Fast Validation**: Clear error messages with suggestions

## Installation

### Build from Source

```bash
# Clone the repository
git clone <repository-url>
cd sqltools

# Build the project
mvn clean install

# Executable JAR will be located at:
# sql-scanner-cli/target/sql-scanner-cli.jar
```

### Run the CLI

```bash
# Show help
java -jar sql-scanner-cli/target/sql-scanner-cli.jar --help

# Or use the simplified filename
java -jar sql-scanner-cli.jar --help
```

> **Note**: The build process uses Maven Shade Plugin to create an executable fat JAR (`sql-scanner-cli.jar`) with all dependencies included, ready to run.

## Usage

### Basic Syntax

```bash
java -jar sql-scanner-cli.jar [OPTIONS]
```

### Command-Line Options

#### Required Options

| Option | Short | Description |
|--------|-------|-------------|
| `--project-path` | `-p` | Project root directory to scan (required) |

#### Optional Options

| Option | Short | Default | Description |
|--------|-------|---------|-------------|
| `--config-file` | `-c` | - | YAML configuration file path |
| `--output-format` | `-f` | `console` | Output format: `console` or `html` |
| `--output-file` | `-o` | - | Output file path (required if format=html) |
| `--fail-on-critical` | - | `false` | Exit with code 1 if CRITICAL violations found |
| `--quiet` | `-q` | `false` | Suppress non-error output for CI/CD |
| `--help` | `-h` | - | Display help message |
| `--version` | `-v` | - | Display version information |

### Usage Examples

#### 1. Basic Scan (Console Output)

Scan a project with default configuration and console output:

```bash
java -jar sql-scanner-cli.jar --project-path=/path/to/project
```

**Output:**
```
Configuration loaded successfully
Initializing parsers...
Scanning project: /path/to/project
Scan complete: 42 SQL statements found
================================================================================
SQL Safety Scan Report
================================================================================
Total SQL: 42 | Violations: 0
================================================================================

✓ No violations found - all SQL statements are safe!

================================================================================

Scan complete: No violations found ✓
```

#### 2. Scan with Custom Configuration

Use a custom YAML configuration file:

```bash
java -jar sql-scanner-cli.jar \
  --project-path=/path/to/project \
  --config-file=config.yml
```

**Example config.yml:**
```yaml
enabled: true
activeStrategy: prod

rules:
  noWhereClause:
    enabled: true
    riskLevel: CRITICAL
  
  dummyCondition:
    enabled: true
    riskLevel: HIGH
```

#### 3. Generate HTML Report

Generate a styled HTML report:

```bash
java -jar sql-scanner-cli.jar \
  --project-path=/path/to/project \
  --output-format=html \
  --output-file=report.html
```

The HTML report includes:
- Interactive dashboard with statistics
- Sortable violation table
- Collapsible SQL preview sections
- Color-coded risk levels
- Responsive design

#### 4. CI/CD Integration

Run in CI/CD pipeline with fail-on-critical:

```bash
java -jar sql-scanner-cli.jar \
  --project-path=/path/to/project \
  --fail-on-critical \
  --quiet
```

**Exit Codes:**
- `0` - Success or non-critical warnings (HIGH/MEDIUM/LOW violations)
- `1` - CRITICAL violations found (with `--fail-on-critical`) OR errors
- `2` - Invalid command-line arguments

#### 5. Short Option Aliases

Use short option aliases for brevity:

```bash
java -jar sql-scanner-cli.jar \
  -p /path/to/project \
  -c config.yml \
  -f html \
  -o report.html \
  -q
```

### CI/CD Integration Examples

#### GitHub Actions

```yaml
name: SQL Safety Scan

on: [push, pull_request]

jobs:
  scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 8
        uses: actions/setup-java@v3
        with:
          java-version: '8'
          distribution: 'temurin'
      
      - name: Build SQL Scanner
        run: |
          cd sqltools
          mvn clean install -DskipTests
      
      - name: Run SQL Safety Scan
        run: |
          java -jar sqltools/sql-scanner-cli/target/sql-scanner-cli-1.0.0-SNAPSHOT.jar \
            --project-path=. \
            --fail-on-critical \
            --quiet
      
      - name: Generate HTML Report
        if: always()
        run: |
          java -jar sqltools/sql-scanner-cli/target/sql-scanner-cli-1.0.0-SNAPSHOT.jar \
            --project-path=. \
            --output-format=html \
            --output-file=sql-safety-report.html
      
      - name: Upload Report
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: sql-safety-report
          path: sql-safety-report.html
```

#### GitLab CI

```yaml
sql-safety-scan:
  stage: test
  image: maven:3.8-openjdk-8
  script:
    - cd sqltools
    - mvn clean install -DskipTests
    - |
      java -jar sql-scanner-cli/target/sql-scanner-cli-1.0.0-SNAPSHOT.jar \
        --project-path=. \
        --fail-on-critical \
        --output-format=html \
        --output-file=sql-safety-report.html
  artifacts:
    when: always
    paths:
      - sql-safety-report.html
    reports:
      junit: sql-safety-report.html
```

#### Jenkins Pipeline

```groovy
pipeline {
    agent any
    
    stages {
        stage('SQL Safety Scan') {
            steps {
                sh '''
                    cd sqltools
                    mvn clean install -DskipTests
                    
                    java -jar sql-scanner-cli/target/sql-scanner-cli-1.0.0-SNAPSHOT.jar \
                        --project-path=. \
                        --fail-on-critical \
                        --quiet
                '''
            }
        }
        
        stage('Generate Report') {
            when {
                always()
            }
            steps {
                sh '''
                    java -jar sqltools/sql-scanner-cli/target/sql-scanner-cli-1.0.0-SNAPSHOT.jar \
                        --project-path=. \
                        --output-format=html \
                        --output-file=sql-safety-report.html
                '''
                
                publishHTML([
                    reportDir: '.',
                    reportFiles: 'sql-safety-report.html',
                    reportName: 'SQL Safety Report'
                ])
            }
        }
    }
}
```

## Configuration

### Default Configuration

If no configuration file is provided, the CLI uses sensible defaults:

```yaml
enabled: true
activeStrategy: prod

interceptors:
  mybatis:
    enabled: true
  mybatisPlus:
    enabled: false
  jdbc:
    enabled: true
    type: auto

deduplication:
  enabled: true
  cacheSize: 1000
  ttlMs: 100

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
    fields:
      - deleted
      - del_flag
      - status
  
  paginationAbuse:
    enabled: true
    riskLevel: HIGH
  
  noPagination:
    enabled: true
    riskLevel: MEDIUM
  
  estimatedRows:
    enabled: true
    riskLevel: HIGH
```

### Custom Configuration

Create a `config.yml` file to customize scanning behavior:

```yaml
# Enable/disable the entire system
enabled: true

# Active strategy: dev, test, or prod
activeStrategy: prod

# Rule configurations
rules:
  # No WHERE clause detection
  noWhereClause:
    enabled: true
    riskLevel: CRITICAL
    exemptTables:
      - config_table
      - metadata_table
  
  # Dummy condition detection (1=1, 1<>1, etc.)
  dummyCondition:
    enabled: true
    riskLevel: HIGH
    patterns:
      - "1\\s*=\\s*1"
      - "1\\s*<>\\s*1"
  
  # Blacklist field detection
  blacklistFields:
    enabled: true
    riskLevel: HIGH
    fields:
      - deleted
      - del_flag
      - is_deleted
      - status
  
  # Pagination abuse detection
  paginationAbuse:
    enabled: true
    riskLevel: HIGH
    physicalDeepPagination:
      maxOffset: 10000
      maxPageNum: 1000
    largePageSize:
      maxPageSize: 500
  
  # Missing pagination detection
  noPagination:
    enabled: true
    riskLevel: MEDIUM
    enforceForAllQueries: false
    whitelistMapperIds:
      - "selectById"
      - "selectByPrimaryKey"
    whitelistTables:
      - config
      - metadata
```

## Scanning Process

The CLI tool performs the following steps:

1. **Input Validation**: Validates all command-line arguments (fail-fast)
2. **Configuration Loading**: Loads YAML config or uses defaults
3. **Parser Initialization**: Instantiates XML, Annotation, and QueryWrapper parsers
4. **Project Scanning**:
   - Discovers XML mapper files under `src/main/resources`
   - Discovers Java source files under `src/main/java`
   - Parses XML mappers for SQL statements
   - Parses Java annotations (@Select, @Insert, @Update, @Delete)
   - Scans for MyBatis-Plus QueryWrapper usage
5. **Report Generation**: Produces console or HTML report
6. **Exit Code Determination**: Returns appropriate exit code for CI/CD

## Report Formats

### Console Report

ANSI-colored terminal output with:
- Statistics summary
- Violations grouped by risk level (CRITICAL → HIGH → MEDIUM → LOW)
- File path and line number for each violation
- SQL snippet preview
- Violation message and suggestion

**Example:**
```
================================================================================
SQL Safety Scan Report
================================================================================
Total SQL: 42 | Violations: 3 (CRITICAL: 1, HIGH: 2)
================================================================================

[CRITICAL] 1 violation

  [UserMapper.xml:15] com.example.UserMapper.deleteAllUsers
  SQL: DELETE FROM users
  Message: DELETE statement without WHERE clause detected
  Suggestion: Add WHERE clause to prevent accidental data loss

[HIGH] 2 violations

  [UserMapper.xml:23] com.example.UserMapper.selectUsers
  SQL: SELECT * FROM users WHERE 1=1 AND name = #{name}
  Message: Dummy condition detected: 1=1
  Suggestion: Remove dummy condition, use dynamic SQL instead

  [UserService.java:45] com.example.UserService.findUsers
  SQL: QueryWrapper usage detected
  Message: QueryWrapper requires runtime validation
  Suggestion: Ensure proper input validation

================================================================================
```

### HTML Report

Styled web page with:
- Interactive dashboard with statistics cards
- Sortable violation table (click column headers)
- Collapsible SQL preview sections
- Color-coded risk level badges
- Responsive design for mobile/desktop
- Wrapper usage section

## Error Handling

### Validation Errors

Clear, actionable error messages with suggestions:

```
Validation error: Project path does not exist: /invalid/path
Please provide a valid project root directory.
```

```
Validation error: HTML format requires --output-file option
Example: --output-format=html --output-file=report.html
```

### Parse Errors

Graceful error handling with detailed messages:

```
Parse error: Failed to parse XML file: UserMapper.xml
Line 23: Unexpected closing tag </select>
```

### I/O Errors

File system error handling:

```
I/O error: Output directory is not writable: /readonly/path
Please ensure you have write permissions.
```

## Troubleshooting

### Common Issues

#### 1. "Missing required option: '--project-path'"

**Solution**: Always provide the `--project-path` option:
```bash
java -jar sql-scanner-cli.jar --project-path=/path/to/project
```

#### 2. "HTML format requires --output-file option"

**Solution**: Provide `--output-file` when using HTML format:
```bash
java -jar sql-scanner-cli.jar \
  --project-path=/path/to/project \
  --output-format=html \
  --output-file=report.html
```

#### 3. "Config file does not exist"

**Solution**: Verify the config file path is correct:
```bash
# Check if file exists
ls -la config.yml

# Use absolute path if needed
java -jar sql-scanner-cli.jar \
  --project-path=/path/to/project \
  --config-file=/absolute/path/to/config.yml
```

#### 4. No SQL statements found

**Possible causes:**
- Project structure doesn't match expected layout (`src/main/resources`, `src/main/java`)
- No XML mappers or Java annotations in the project
- Files are in non-standard locations

**Solution**: Ensure project follows standard Maven/Gradle structure:
```
project/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/
│       │       ├── UserMapper.java
│       │       └── UserService.java
│       └── resources/
│           └── mappers/
│               └── UserMapper.xml
```

## Performance

### Scanning Speed

- **Small projects** (<100 files): ~1-2 seconds
- **Medium projects** (100-1000 files): ~5-10 seconds
- **Large projects** (>1000 files): ~20-30 seconds

### Memory Usage

- **Baseline**: ~50-100 MB
- **Per 1000 SQL statements**: ~10-20 MB additional

### Optimization Tips

1. **Use quiet mode** in CI/CD to reduce logging overhead
2. **Exclude test directories** if not needed
3. **Use HTML format** for large reports (better performance than console)

## Development

### Running Tests

```bash
cd sql-scanner-cli
mvn test
```

**Test Coverage:**
- CLI argument parsing: 12 tests
- Input validation: 11 tests
- Scan orchestration: 7 tests
- Report output: 7 tests
- **Total: 37 tests**

### Building

```bash
mvn clean package
```

The executable JAR will be created at:
```
target/sql-scanner-cli-1.0.0-SNAPSHOT.jar
```

## License

Copyright (c) 2025 Footstone

## Support

For issues, questions, or contributions, please refer to the main project documentation.

