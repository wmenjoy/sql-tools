# Installation Guide

This guide covers installation and setup for all SQL Safety Guard components across different build tools and frameworks.

## Table of Contents

- [Maven Integration](#maven-integration)
- [Gradle Integration](#gradle-integration)
- [Spring Boot Integration](#spring-boot-integration)
- [Version Compatibility](#version-compatibility)
- [Build Tool Plugins](#build-tool-plugins)

## Maven Integration

### Parent POM Dependency Management

For multi-module projects, configure dependency management in your parent POM:

```xml
<dependencyManagement>
    <dependencies>
        <!-- SQL Safety Guard BOM -->
        <dependency>
            <groupId>com.footstone</groupId>
            <artifactId>sql-safety-guard-parent</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Spring Boot Starter (Recommended)

Add the Spring Boot starter for zero-configuration integration:

```xml
<dependency>
    <groupId>com.footstone</groupId>
    <artifactId>sql-guard-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

This single dependency provides:
- ✅ Automatic validator bean creation
- ✅ Runtime SQL validation
- ✅ Configuration via `application.yml`
- ✅ Support for MyBatis, MyBatis-Plus, and JDBC

### Core Validation Engine

For non-Spring Boot applications, add the core validation engine:

```xml
<dependency>
    <groupId>com.footstone</groupId>
    <artifactId>sql-guard-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Framework-Specific Interceptors

#### MyBatis Interceptor

```xml
<dependency>
    <groupId>com.footstone</groupId>
    <artifactId>sql-guard-mybatis</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

#### MyBatis-Plus Interceptor

```xml
<dependency>
    <groupId>com.footstone</groupId>
    <artifactId>sql-guard-mp</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

#### JDBC Layer Interceptors

```xml
<dependency>
    <groupId>com.footstone</groupId>
    <artifactId>sql-guard-jdbc</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Static Scanner (Build-Time Analysis)

```xml
<dependency>
    <groupId>com.footstone</groupId>
    <artifactId>sql-scanner-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

## Gradle Integration

### Kotlin DSL

```kotlin
dependencies {
    // Spring Boot Starter (recommended)
    implementation("com.footstone:sql-guard-spring-boot-starter:1.0.0-SNAPSHOT")
    
    // Or individual components
    implementation("com.footstone:sql-guard-core:1.0.0-SNAPSHOT")
    implementation("com.footstone:sql-guard-mybatis:1.0.0-SNAPSHOT")
    implementation("com.footstone:sql-guard-mp:1.0.0-SNAPSHOT")
    implementation("com.footstone:sql-guard-jdbc:1.0.0-SNAPSHOT")
    
    // Static scanner (build-time)
    testImplementation("com.footstone:sql-scanner-core:1.0.0-SNAPSHOT")
}
```

### Groovy DSL

```groovy
dependencies {
    // Spring Boot Starter (recommended)
    implementation 'com.footstone:sql-guard-spring-boot-starter:1.0.0-SNAPSHOT'
    
    // Or individual components
    implementation 'com.footstone:sql-guard-core:1.0.0-SNAPSHOT'
    implementation 'com.footstone:sql-guard-mybatis:1.0.0-SNAPSHOT'
    implementation 'com.footstone:sql-guard-mp:1.0.0-SNAPSHOT'
    implementation 'com.footstone:sql-guard-jdbc:1.0.0-SNAPSHOT'
    
    // Static scanner (build-time)
    testImplementation 'com.footstone:sql-scanner-core:1.0.0-SNAPSHOT'
}
```

## Spring Boot Integration

### 1. Add Starter Dependency

See [Maven Integration](#spring-boot-starter-recommended) or [Gradle Integration](#kotlin-dsl) above.

### 2. Configure (Optional)

Create `application.yml` in `src/main/resources`:

```yaml
sql-guard:
  enabled: true
  active-strategy: LOG  # LOG, WARN, or BLOCK
  
  rules:
    no-where-clause:
      enabled: true
      risk-level: CRITICAL
    
    dummy-condition:
      enabled: true
      risk-level: HIGH
    
    blacklist-fields:
      enabled: true
      risk-level: HIGH
      blacklist-fields:
        - deleted
        - del_flag
        - status
```

### 3. Done!

SQL Safety Guard automatically:
- ✅ Creates validator beans
- ✅ Registers interceptors
- ✅ Validates SQL at runtime

**No code changes required!**

### Custom Bean Configuration (Advanced)

Override default beans if needed:

```java
@Configuration
public class CustomSqlGuardConfig {
    
    @Bean
    public DefaultSqlSafetyValidator customValidator(
            JSqlParserFacade facade,
            List<RuleChecker> checkers,
            RuleCheckerOrchestrator orchestrator,
            SqlDeduplicationFilter filter) {
        // Custom validator configuration
        return new DefaultSqlSafetyValidator(facade, checkers, orchestrator, filter);
    }
}
```

## Version Compatibility

### Java Versions

| Java Version | Support Status | Notes |
|--------------|---------------|-------|
| Java 8 | ✅ Fully Supported | Baseline compatibility |
| Java 11 | ✅ Fully Supported | Tested with profile `-Pjava11` |
| Java 17 | ✅ Fully Supported | Tested with profile `-Pjava17` |
| Java 21 | ✅ Fully Supported | Tested with profile `-Pjava21` |

### MyBatis Versions

| MyBatis Version | Support Status | Notes |
|-----------------|---------------|-------|
| 3.4.6+ | ✅ Fully Supported | Minimum version |
| 3.5.13+ | ✅ Fully Supported | Recommended |

### MyBatis-Plus Versions

| MyBatis-Plus Version | Support Status | Notes |
|---------------------|---------------|-------|
| 3.4.0+ | ✅ Fully Supported | Minimum version |
| 3.5.3+ | ✅ Fully Supported | Recommended |

### Spring Boot Versions

| Spring Boot Version | Support Status | Notes |
|--------------------|---------------|-------|
| 2.7.x | ✅ Fully Supported | Tested with 2.7.18 |
| 3.0.x | ✅ Fully Supported | Tested with 3.0.x |
| 3.1.x | ✅ Fully Supported | Tested with 3.1.x |

### Connection Pool Compatibility

| Connection Pool | Support Status | Integration Method |
|----------------|---------------|-------------------|
| Druid | ✅ Fully Supported | DruidSqlSafetyFilter |
| HikariCP | ✅ Fully Supported | HikariSqlSafetyProxyFactory |
| C3P0 | ✅ Supported via P6Spy | P6SpySqlSafetyListener |
| DBCP | ✅ Supported via P6Spy | P6SpySqlSafetyListener |
| Tomcat JDBC | ✅ Supported via P6Spy | P6SpySqlSafetyListener |

### Database Compatibility

SQL Safety Guard works with any database supported by JSqlParser:

- ✅ MySQL 5.7+, 8.0+
- ✅ PostgreSQL 10+, 11+, 12+, 13+, 14+, 15+
- ✅ Oracle 11g+, 12c+, 19c+
- ✅ SQL Server 2012+, 2016+, 2019+
- ✅ H2 (for testing)

## Build Tool Plugins

### Maven Plugin

Add to your `pom.xml`:

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
                    </goal>
                    <configuration>
                        <failOnCritical>true</failOnCritical>
                        <outputFormat>both</outputFormat>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

Run scan:

```bash
mvn sqlguard:scan
```

See [Maven Plugin README](../../sql-scanner-maven/README.md) for complete documentation.

### Gradle Plugin

Add to your `build.gradle`:

```groovy
plugins {
    id 'com.footstone.sqlguard' version '1.0.0-SNAPSHOT'
}

sqlguard {
    html()
    failOnCritical()
}
```

Run scan:

```bash
gradle sqlguardScan
```

See [Gradle Plugin README](../../sql-scanner-gradle/README.md) for complete documentation.

### CLI Tool

Download the standalone JAR:

```bash
# Build from source
mvn clean install

# Run scanner
java -jar sql-scanner-cli/target/sql-scanner-cli.jar \
  --project-path=/path/to/project \
  --output-format=html \
  --output-file=report.html
```

See [CLI Quick Reference](../CLI-Quick-Reference.md) for complete documentation.

## Verification

### Verify Installation (Spring Boot)

1. Start your application
2. Check logs for SQL Guard initialization:

```
INFO  SqlGuardAutoConfiguration - SQL Safety Guard initialized
INFO  SqlGuardAutoConfiguration - Active strategy: LOG
INFO  SqlGuardAutoConfiguration - Enabled checkers: 10
```

3. Execute a test query and verify validation:

```java
@Autowired
private UserMapper userMapper;

@Test
public void testSqlGuardValidation() {
    // This should trigger validation
    List<User> users = userMapper.selectAll();
}
```

### Verify Installation (Non-Spring Boot)

```java
// Create validator manually
JSqlParserFacade facade = new JSqlParserFacade(false);
List<RuleChecker> checkers = Arrays.asList(
    new NoWhereClauseChecker(new NoWhereClauseConfig()),
    new DummyConditionChecker(new DummyConditionConfig())
);
RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
SqlDeduplicationFilter filter = new SqlDeduplicationFilter(1000, 100);
DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(
    facade, checkers, orchestrator, filter
);

// Test validation
SqlContext context = SqlContext.builder()
    .sql("DELETE FROM users")
    .type(SqlCommandType.DELETE)
    .mapperId("test.deleteAll")
    .build();

ValidationResult result = validator.validate(context);
System.out.println("Passed: " + result.isPassed());
System.out.println("Violations: " + result.getViolations().size());
```

## Troubleshooting

### Issue: Auto-configuration not loading

**Symptoms:** No SQL Guard logs, validation not happening

**Solutions:**

1. Verify starter dependency is present:
   ```bash
   mvn dependency:tree | grep sql-guard-spring-boot-starter
   ```

2. Check `META-INF/spring.factories` is on classpath:
   ```bash
   jar -tf target/your-app.jar | grep spring.factories
   ```

3. Enable debug logging:
   ```yaml
   logging:
     level:
       com.footstone.sqlguard: DEBUG
   ```

### Issue: Version conflicts

**Symptoms:** `ClassNotFoundException`, `NoSuchMethodError`

**Solutions:**

1. Check for conflicting versions:
   ```bash
   mvn dependency:tree
   ```

2. Exclude transitive dependencies if needed:
   ```xml
   <dependency>
       <groupId>com.footstone</groupId>
       <artifactId>sql-guard-spring-boot-starter</artifactId>
       <version>1.0.0-SNAPSHOT</version>
       <exclusions>
           <exclusion>
               <groupId>org.mybatis</groupId>
               <artifactId>mybatis</artifactId>
           </exclusion>
       </exclusions>
   </dependency>
   ```

3. Use dependency management to enforce versions:
   ```xml
   <dependencyManagement>
       <dependencies>
           <dependency>
               <groupId>org.mybatis</groupId>
               <artifactId>mybatis</artifactId>
               <version>3.5.13</version>
           </dependency>
       </dependencies>
   </dependencyManagement>
   ```

### Issue: Build plugin not found

**Symptoms:** `Plugin 'com.footstone:sql-scanner-maven' not found`

**Solutions:**

1. Install plugin to local repository:
   ```bash
   cd sql-scanner-maven
   mvn clean install
   ```

2. Verify plugin is installed:
   ```bash
   ls ~/.m2/repository/com/footstone/sql-scanner-maven/
   ```

## Next Steps

- **[Configuration Reference](configuration-reference.md)** - Complete YAML property documentation
- **[Rule Documentation](rules/README.md)** - Learn about all 10 validation rules
- **[Deployment Guide](deployment.md)** - Phased rollout strategy for production

---

**Need help?** See [Troubleshooting Guide](troubleshooting.md) or [FAQ](faq.md).
