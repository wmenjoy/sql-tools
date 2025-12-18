# SQL Scanner CLI - Quick Reference

## Installation

```bash
mvn clean install
```

## Basic Usage

```bash
java -jar sql-scanner-cli.jar --project-path=<path>
```

## Command-Line Options

### Required
| Option | Short | Description |
|--------|-------|-------------|
| `--project-path` | `-p` | Project root directory |

### Optional
| Option | Short | Default | Description |
|--------|-------|---------|-------------|
| `--config-file` | `-c` | - | YAML config file |
| `--output-format` | `-f` | `console` | `console` or `html` |
| `--output-file` | `-o` | - | Output file path |
| `--fail-on-critical` | - | `false` | Exit 1 on CRITICAL |
| `--quiet` | `-q` | `false` | Suppress output |

## Common Commands

### Console Report
```bash
java -jar sql-scanner-cli.jar -p /path/to/project
```

### HTML Report
```bash
java -jar sql-scanner-cli.jar \
  -p /path/to/project \
  -f html \
  -o report.html
```

### Custom Config
```bash
java -jar sql-scanner-cli.jar \
  -p /path/to/project \
  -c config.yml
```

### CI/CD Mode
```bash
java -jar sql-scanner-cli.jar \
  -p /path/to/project \
  --fail-on-critical \
  -q
```

## Exit Codes

| Code | Meaning |
|------|---------|
| `0` | Success or non-critical warnings |
| `1` | CRITICAL violations OR errors |
| `2` | Invalid arguments |

## Risk Levels

| Level | Description | Example |
|-------|-------------|---------|
| **CRITICAL** | Severe security issues | DELETE without WHERE |
| **HIGH** | Serious issues | Dummy conditions (1=1) |
| **MEDIUM** | Moderate issues | Missing pagination |
| **LOW** | Minor issues | Informational |

## Configuration Example

**config.yml:**
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
  
  paginationAbuse:
    enabled: true
    riskLevel: HIGH
    physicalDeepPagination:
      maxOffset: 10000
      maxPageNum: 1000
```

## CI/CD Integration

### GitHub Actions
```yaml
- name: SQL Safety Scan
  run: |
    java -jar sql-scanner-cli.jar \
      -p . \
      --fail-on-critical \
      -q
```

### GitLab CI
```yaml
sql-scan:
  script:
    - java -jar sql-scanner-cli.jar -p . --fail-on-critical -q
```

### Jenkins
```groovy
sh 'java -jar sql-scanner-cli.jar -p . --fail-on-critical -q'
```

## Troubleshooting

### Missing required option
```bash
# ❌ Wrong
java -jar sql-scanner-cli.jar

# ✅ Correct
java -jar sql-scanner-cli.jar -p /path/to/project
```

### HTML format requires output file
```bash
# ❌ Wrong
java -jar sql-scanner-cli.jar -p . -f html

# ✅ Correct
java -jar sql-scanner-cli.jar -p . -f html -o report.html
```

### Config file not found
```bash
# ❌ Wrong (relative path might fail)
java -jar sql-scanner-cli.jar -p . -c config.yml

# ✅ Correct (absolute path)
java -jar sql-scanner-cli.jar -p . -c /absolute/path/config.yml
```

## Project Structure

Expected project layout:
```
project/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/
│       │       ├── UserMapper.java      # @Select, @Insert, etc.
│       │       └── UserService.java     # QueryWrapper usage
│       └── resources/
│           └── mappers/
│               └── UserMapper.xml       # XML mappers
```

## Performance

| Project Size | Scan Time |
|--------------|-----------|
| Small (<100 files) | ~1-2 sec |
| Medium (100-1000 files) | ~5-10 sec |
| Large (>1000 files) | ~20-30 sec |

## Help & Version

```bash
# Display help
java -jar sql-scanner-cli.jar --help

# Display version
java -jar sql-scanner-cli.jar --version
```

## Full Documentation

See [sql-scanner-cli/README.md](../sql-scanner-cli/README.md) for complete documentation.







