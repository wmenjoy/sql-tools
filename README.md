# SQL Safety Guard System

A comprehensive SQL security framework providing static analysis and runtime validation for Java applications using MyBatis, MyBatis-Plus, and JDBC.

## Project Structure

This is a Maven multi-module project with the following modules:

- **sql-scanner-core** - Core SQL scanning engine
- **sql-scanner-cli** - Command-line interface tool
- **sql-scanner-maven** - Maven plugin for build-time scanning
- **sql-scanner-gradle** - Gradle plugin for build-time scanning
- **sql-guard-core** - Runtime validation engine
- **sql-guard-mybatis** - MyBatis interceptor
- **sql-guard-mp** - MyBatis-Plus interceptor
- **sql-guard-jdbc** - JDBC layer interceptors
- **sql-guard-spring-boot-starter** - Spring Boot auto-configuration

## Build Requirements

- Maven 3.6+
- Java 8+ (baseline compatibility: Java 8)

## Building the Project

### Default Build (Java 8)
```bash
mvn clean install
```

### Multi-Version Java Compatibility

The project supports building with different Java versions using Maven profiles:

#### Java 11
```bash
mvn clean install -Pjava11
```

#### Java 17
```bash
mvn clean install -Pjava17
```

#### Java 21
```bash
mvn clean install -Pjava21
```

### CI/CD Matrix Builds

For continuous integration pipelines, use profile activation to test across multiple Java versions:

```yaml
# Example GitHub Actions matrix
strategy:
  matrix:
    java: [8, 11, 17, 21]
steps:
  - uses: actions/setup-java@v3
    with:
      java-version: ${{ matrix.java }}
  - run: mvn clean verify -Pjava${{ matrix.java }}
```

## Code Quality

The project enforces Google Java Style using Checkstyle. Style checks run automatically during the `verify` phase:

```bash
mvn verify
```

To skip Checkstyle checks (not recommended):
```bash
mvn verify -Dcheckstyle.skip=true
```

## Testing

Run all tests:
```bash
mvn test
```

Run tests for a specific module:
```bash
mvn test -pl sql-guard-core
```

## License

Copyright (c) 2025 Footstone

