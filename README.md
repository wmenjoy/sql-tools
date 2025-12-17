# SQL Safety Guard System

[![Maven Central](https://img.shields.io/badge/maven--central-1.0.0--SNAPSHOT-blue)](https://search.maven.org/)
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/footstone/sql-safety-guard)
[![Coverage](https://img.shields.io/badge/coverage-95%25-brightgreen)](https://github.com/footstone/sql-safety-guard)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![Java](https://img.shields.io/badge/java-8%2B-orange)](https://www.oracle.com/java/)

**Production-ready SQL security framework preventing catastrophic database incidents through dual-layer protection: static analysis + runtime validation.**

## 🎯 Overview

SQL Safety Guard is a comprehensive security framework for Java applications that prevents dangerous SQL operations before they reach production. By combining **static code scanning** during development with **runtime interception** at execution time, it provides defense-in-depth protection against common SQL safety issues that cause data loss, performance degradation, and production outages.

### Key Features

- 🔍 **Static Analysis** - Scan SQL in XML mappers, Java annotations, and QueryWrapper usage at build time
- 🛡️ **Runtime Validation** - Intercept and validate SQL at execution time across all persistence layers
- 📊 **Multiple Report Formats** - Console (ANSI-colored) and HTML (styled, sortable) reports
- 🚀 **CI/CD Integration** - Maven/Gradle plugins and CLI tool with exit codes for build pipelines
- ⚙️ **Flexible Configuration** - YAML-based configuration with sensible defaults
- 🔌 **Framework Support** - MyBatis, MyBatis-Plus, JDBC (Druid/HikariCP/P6Spy), Spring Boot
- 📈 **Performance Optimized** - <5% overhead through parse-once optimization and deduplication caching
- 🎚️ **Phased Deployment** - LOG→WARN→BLOCK strategy for risk-mitigated production rollout

### Real-World Impact

SQL Safety Guard prevents production incidents like:

- ✅ **Data Loss** - Blocks `DELETE FROM users` without WHERE clause
- ✅ **Performance Degradation** - Detects deep pagination (`LIMIT 10000, 100`) causing slow queries
- ✅ **Memory Exhaustion** - Prevents logical pagination loading millions of rows into memory
- ✅ **Security Risks** - Identifies blacklist-only conditions (`WHERE deleted=0`) enabling data leakage

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   SQL Safety Guard System                    │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────┐      ┌─────────────────────────┐  │
│  │   Static Scanner    │      │   Runtime Validator     │  │
│  │  (Build Time)       │      │   (Execution Time)      │  │
│  ├─────────────────────┤      ├─────────────────────────┤  │
│  │ • XML Mappers       │      │ • MyBatis Interceptor   │  │
│  │ • Java Annotations  │      │ • MyBatis-Plus Plugin   │  │
│  │ • QueryWrapper      │      │ • JDBC Layer (Druid/    │  │
│  │   Detection         │      │   HikariCP/P6Spy)       │  │
│  └─────────────────────┘      └─────────────────────────┘  │
│           │                              │                   │
│           └──────────────┬───────────────┘                   │
│                          │                                   │
│                ┌─────────▼──────────┐                        │
│                │  Validation Engine │                        │
│                │  • 10 Rule Checkers│                        │
│                │  • Risk Levels     │                        │
│                │  • Deduplication   │                        │
│                └────────────────────┘                        │
└─────────────────────────────────────────────────────────────┘
```

## ⚡ Quick Start

### Spring Boot Integration (5 Minutes)

**1. Add Dependency**

```xml
<dependency>
    <groupId>com.footstone</groupId>
    <artifactId>sql-guard-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**2. Configure (Optional - Works with Zero Configuration!)**

```yaml
# application.yml
sql-guard:
  enabled: true
  active-strategy: WARN  # LOG, WARN, or BLOCK
  rules:
    no-where-clause:
      enabled: true
      risk-level: CRITICAL
    dummy-condition:
      enabled: true
      risk-level: HIGH
```

**3. Done!** 🎉

SQL Safety Guard automatically validates all SQL at runtime. No code changes required!

### Static Analysis with CLI

```bash
# Build the project
mvn clean install

# Scan your project
java -jar sql-scanner-cli/target/sql-scanner-cli.jar \
  --project-path=/path/to/your/project

# Generate HTML report
java -jar sql-scanner-cli/target/sql-scanner-cli.jar \
  --project-path=/path/to/your/project \
  --output-format=html \
  --output-file=report.html
```

### Maven Plugin Integration

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
            </configuration>
        </execution>
    </executions>
</plugin>
```

## 📚 Documentation

### User Guides
- **[Installation Guide](docs/user-guide/installation.md)** - Maven/Gradle integration and version compatibility
- **[Configuration Reference](docs/user-guide/configuration-reference.md)** - Complete YAML property documentation
- **[Rule Documentation](docs/user-guide/rules/README.md)** - All 10 validation rules with examples
- **[Deployment Guide](docs/user-guide/deployment.md)** - Phased rollout strategy (LOG→WARN→BLOCK)
- **[Performance Guide](docs/user-guide/performance.md)** - Benchmarks, tuning, and optimization
- **[FAQ](docs/user-guide/faq.md)** - Common questions and answers
- **[Troubleshooting](docs/user-guide/troubleshooting.md)** - Common issues and solutions

### Quick References
- **[CLI Quick Reference](docs/CLI-Quick-Reference.md)** - Command-line tool cheat sheet
- **[Maven Plugin README](sql-scanner-maven/README.md)** - Maven plugin usage
- **[Gradle Plugin README](sql-scanner-gradle/README.md)** - Gradle plugin usage

## 🛡️ Validation Rules

SQL Safety Guard includes 10 specialized rule checkers:

| Rule | Risk Level | Description |
|------|-----------|-------------|
| **No WHERE Clause** | CRITICAL | Detects DELETE/UPDATE without WHERE |
| **Dummy Condition** | HIGH | Detects meaningless conditions (1=1) |
| **Blacklist Fields** | HIGH | Detects WHERE using only low-cardinality fields |
| **Whitelist Fields** | HIGH | Enforces mandatory high-selectivity fields |
| **Logical Pagination** | CRITICAL | Detects in-memory pagination (OOM risk) |
| **No Condition Pagination** | CRITICAL | Detects LIMIT without WHERE |
| **Deep Pagination** | MEDIUM | Detects high OFFSET values |
| **Large Page Size** | MEDIUM | Detects excessive LIMIT values |
| **Missing ORDER BY** | LOW | Detects pagination without ORDER BY |
| **No Pagination** | VARIABLE | Detects SELECT without LIMIT |

See **[Rule Documentation](docs/user-guide/rules/README.md)** for detailed descriptions and examples.

## 🚀 CI/CD Integration

### GitHub Actions

```yaml
- name: SQL Safety Scan
  run: |
    java -jar sql-scanner-cli.jar \
      --project-path=. \
      --fail-on-critical \
      --quiet
```

### GitLab CI

```yaml
sql-scan:
  script:
    - mvn sqlguard:scan -Dsqlguard.failOnCritical=true
```

### Jenkins

```groovy
sh 'gradle sqlguardScan --fail-on-critical'
```

## 📊 Example Report

**Console Output:**

```
================================================================================
SQL Safety Scan Report
================================================================================
Total SQL: 42 | Violations: 3 (CRITICAL: 1, HIGH: 2)
================================================================================

[CRITICAL] No WHERE Clause

  [UserMapper.xml:15] com.example.UserMapper.deleteAllUsers
  SQL: DELETE FROM users
  Message: DELETE statement without WHERE clause detected
  Suggestion: Add WHERE clause to prevent accidental data loss

[HIGH] Dummy Condition

  [UserMapper.xml:23] com.example.UserMapper.selectUsers
  SQL: SELECT * FROM users WHERE 1=1 AND name = #{name}
  Message: Dummy condition detected: 1=1
  Suggestion: Remove dummy condition, use dynamic SQL instead
```

**HTML Report:** Interactive dashboard with sortable tables, syntax highlighting, and risk-level filtering.

## 🎯 Phased Deployment Strategy

SQL Safety Guard supports gradual production rollout to minimize risk:

### Phase 1: Observation Mode (1-2 weeks)
```yaml
sql-guard:
  active-strategy: LOG  # Log violations, don't block
```
- Monitor violation frequency
- Identify false positives
- Tune rule configurations

### Phase 2: Warning Mode (1-2 weeks)
```yaml
sql-guard:
  active-strategy: WARN  # Warn but allow execution
```
- Validate warnings don't disrupt UX
- Refine rules based on Phase 1 data
- Prepare for enforcement

### Phase 3: Blocking Mode
```yaml
sql-guard:
  active-strategy: BLOCK  # Block dangerous SQL
```
- Gradual rollout with canary/percentage-based deployment
- Monitor error rates
- Rollback plan ready

See **[Deployment Guide](docs/user-guide/deployment.md)** for complete strategy.

## 🔧 Project Structure

This is a Maven multi-module project:

### Static Analysis (Scanner)
- **sql-scanner-core** - Core SQL scanning engine
- **sql-scanner-cli** - Command-line interface tool
- **sql-scanner-maven** - Maven plugin
- **sql-scanner-gradle** - Gradle plugin

### Runtime Validation (Guard)
- **sql-guard-core** - Validation engine with 10 rule checkers
- **sql-guard-mybatis** - MyBatis interceptor
- **sql-guard-mp** - MyBatis-Plus interceptor
- **sql-guard-jdbc** - JDBC layer interceptors (Druid/HikariCP/P6Spy)
- **sql-guard-spring-boot-starter** - Spring Boot auto-configuration

## 🛠️ Building from Source

### Requirements
- Maven 3.6+
- Java 8+ (baseline compatibility: Java 8)

### Build

```bash
# Default build (Java 8)
mvn clean install

# Multi-version builds
mvn clean install -Pjava11
mvn clean install -Pjava17
mvn clean install -Pjava21
```

### Testing

```bash
# Run all tests
mvn test

# Run tests for specific module
mvn test -pl sql-guard-core
```

### Code Quality

```bash
# Run Checkstyle (Google Java Style)
mvn checkstyle:check

# Full verification
mvn verify
```

## 📈 Performance

- **Runtime Overhead:** <5% for ORM layers, ~7% for Druid, ~3% for HikariCP
- **Parse-Once Optimization:** SQL parsed once, shared across all checkers
- **Deduplication:** ThreadLocal LRU cache prevents redundant validation
- **Benchmark:** 1000 queries validated in <1 second

See **[Performance Guide](docs/user-guide/performance.md)** for tuning recommendations.

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

## 📄 License

Copyright © 2025 Footstone Technology. All rights reserved.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

## 🆘 Support

- **Documentation:** [docs/](docs/)
- **Issues:** [GitHub Issues](https://github.com/footstone/sql-safety-guard/issues)
- **Email:** support@footstone.com

## 🌟 Acknowledgments

Built with:
- [JSqlParser](https://github.com/JSQLParser/JSqlParser) - SQL parsing
- [MyBatis](https://mybatis.org/) - Persistence framework
- [MyBatis-Plus](https://baomidou.com/) - MyBatis enhancement
- [Spring Boot](https://spring.io/projects/spring-boot) - Application framework

---

**Prevent SQL disasters before they happen. Deploy SQL Safety Guard today!** 🛡️
