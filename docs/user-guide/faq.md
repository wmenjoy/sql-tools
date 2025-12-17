# Frequently Asked Questions (FAQ)

Common questions and answers about SQL Safety Guard.

## General Questions

### What is SQL Safety Guard?

SQL Safety Guard is a production-ready SQL security framework for Java applications that prevents dangerous SQL operations through dual-layer protection: static code scanning during development and runtime validation at execution time.

### Why do I need SQL Safety Guard?

SQL Safety Guard prevents common production incidents:

- **Data Loss** - Blocks `DELETE FROM users` without WHERE clause
- **Memory Exhaustion** - Prevents logical pagination loading millions of rows
- **Performance Degradation** - Detects deep pagination and missing indexes
- **Security Risks** - Identifies queries enabling data leakage

### What's the performance impact?

**Typical overhead: <5%**

- **ORM Layer** (MyBatis/MyBatis-Plus): <5%
- **Druid Connection Pool**: ~7%
- **HikariCP Connection Pool**: ~3%
- **P6Spy Universal Interceptor**: ~15%

Performance is optimized through:
- Parse-once optimization (SQL parsed once, shared across checkers)
- Deduplication caching (ThreadLocal LRU cache, 100ms TTL)
- Early-return mechanisms (skip unnecessary checks)

See [Performance Guide](performance.md) for tuning recommendations.

### Does it support my database?

**Yes!** SQL Safety Guard works with any database supported by JSqlParser:

- ✅ MySQL 5.7+, 8.0+
- ✅ PostgreSQL 10+, 11+, 12+, 13+, 14+, 15+
- ✅ Oracle 11g+, 12c+, 19c+
- ✅ SQL Server 2012+, 2016+, 2019+
- ✅ H2 (for testing)

SQL validation is database-agnostic - it analyzes SQL syntax, not database-specific behavior.

### Does it work with Spring Boot?

**Yes!** SQL Safety Guard provides zero-configuration Spring Boot integration:

```xml
<dependency>
    <groupId>com.footstone</groupId>
    <artifactId>sql-guard-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

No code changes required - just add the dependency and configure via `application.yml`.

See [Installation Guide](installation.md#spring-boot-integration) for details.

## Configuration Questions

### Can I disable specific rules?

**Yes!** Each rule can be individually enabled/disabled:

```yaml
sql-guard:
  rules:
    missing-order-by:
      enabled: false  # Disable this rule
    
    no-pagination:
      enabled: false  # Disable this rule
```

You can also adjust risk levels:

```yaml
sql-guard:
  rules:
    no-where-clause:
      enabled: true
      risk-level: HIGH  # Downgrade from CRITICAL
```

### How do I whitelist legacy SQL?

**Option 1: Whitelist Specific Mappers**

```yaml
sql-guard:
  rules:
    no-pagination:
      whitelist-mapper-ids:
        - "ConfigMapper.selectAll"  # Config table is small
        - "MetadataMapper.selectAll"  # Metadata table is small
```

**Option 2: Whitelist Specific Tables**

```yaml
sql-guard:
  rules:
    no-pagination:
      whitelist-tables:
        - config
        - metadata
        - system_settings
```

**Option 3: Disable Rule for Specific Environments**

```yaml
# application-dev.yml
sql-guard:
  rules:
    no-pagination:
      enabled: false  # Disable in development only
```

### What if scanner fails to parse SQL?

**Issue:** JSqlParser cannot parse database-specific SQL extensions.

**Solution 1: Enable Lenient Mode**

```yaml
sql-guard:
  parser:
    lenient-mode: true  # Log warning, skip validation
```

**Solution 2: Upgrade JSqlParser**

```xml
<dependency>
    <groupId>com.github.jsqlparser</groupId>
    <artifactId>jsqlparser</artifactId>
    <version>4.9.0</version>  <!-- Use latest version -->
</dependency>
```

**Solution 3: Report Issue**

If SQL is standard syntax but fails to parse, report to [JSqlParser GitHub](https://github.com/JSQLParser/JSqlParser/issues).

### How do I handle false positives?

**Step 1: Verify False Positive**

Review violation details to confirm it's truly a false positive:

```
WARN  SqlSafetyInterceptor - SQL Safety Warning: [HIGH] Blacklist fields only
WARN  SqlSafetyInterceptor - MapperId: com.example.UserMapper.selectByStatus
WARN  SqlSafetyInterceptor - SQL: SELECT * FROM users WHERE status = ?
WARN  SqlSafetyInterceptor - Fields: [status]
```

**Step 2: Choose Remediation**

| Scenario | Solution |
|----------|----------|
| Legitimate issue | Fix SQL (add high-selectivity field) |
| Small table (<1000 rows) | Whitelist mapper or table |
| Unavoidable pattern | Adjust rule configuration |
| Rule too strict | Downgrade risk level or disable |

**Step 3: Document Decision**

```yaml
sql-guard:
  rules:
    blacklist-fields:
      whitelist-mapper-ids:
        - "UserMapper.selectByStatus"  # Small table, acceptable
```

### Can I use without Spring Boot?

**Yes!** Instantiate `DefaultSqlSafetyValidator` directly:

```java
// Create parser
JSqlParserFacade facade = new JSqlParserFacade(false);

// Create checkers
List<RuleChecker> checkers = Arrays.asList(
    new NoWhereClauseChecker(new NoWhereClauseConfig()),
    new DummyConditionChecker(new DummyConditionConfig()),
    new BlacklistFieldChecker(new BlacklistFieldsConfig())
);

// Create orchestrator
RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);

// Create deduplication filter
SqlDeduplicationFilter filter = new SqlDeduplicationFilter(1000, 100);

// Create validator
DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(
    facade, checkers, orchestrator, filter
);

// Validate SQL
SqlContext context = SqlContext.builder()
    .sql("DELETE FROM users WHERE id = ?")
    .type(SqlCommandType.DELETE)
    .mapperId("UserMapper.deleteById")
    .build();

ValidationResult result = validator.validate(context);
if (!result.isPassed()) {
    // Handle violations
}
```

### Does it support prepared statements?

**Yes!** Runtime interceptors work with prepared statements:

- **MyBatis:** Intercepts after parameter binding, validates resolved SQL
- **MyBatis-Plus:** Intercepts QueryWrapper-generated SQL with parameters
- **JDBC:** Intercepts `PreparedStatement.execute()` with parameter-substituted SQL

**Static scanner limitations:**
- Cannot validate dynamic QueryWrapper logic
- Cannot validate runtime-generated SQL
- Use runtime validation for complete coverage

## Deployment Questions

### What's the recommended deployment strategy?

**Three-phase gradual rollout:**

1. **Phase 1: LOG (1-2 weeks)** - Observe violations, tune configuration
2. **Phase 2: WARN (1-2 weeks)** - Validate warnings don't disrupt UX
3. **Phase 3: BLOCK (gradual)** - Enforce safety with canary deployment

See [Deployment Guide](deployment.md) for complete strategy.

### Can I deploy directly to BLOCK mode?

**Not recommended!** Direct BLOCK deployment risks:

- Unexpected SQLException breaking user workflows
- False positives blocking legitimate operations
- Insufficient configuration tuning

**Exception:** If you've thoroughly tested in staging with BLOCK mode for 2+ weeks, direct production deployment may be acceptable.

### How do I rollback if issues occur?

**Immediate Rollback (Emergency):**

```bash
# Disable SQL Guard entirely
export SQL_GUARD_ENABLED=false
kubectl rollout restart deployment/app
```

**Graceful Rollback:**

```bash
# Downgrade strategy
export SQL_GUARD_STRATEGY=WARN
kubectl rollout restart deployment/app
```

See [Deployment Guide - Rollback Plan](deployment.md#rollback-plan) for details.

### What metrics should I monitor?

**Key Metrics:**

1. **Violation Metrics:**
   - Violations by rule type
   - Violations by risk level
   - Top violating mappers

2. **Performance Metrics:**
   - Validation latency (P50, P95, P99)
   - Cache hit rate (target >50%)
   - Deduplication effectiveness

3. **Error Metrics:**
   - SQLException count
   - Error rate by endpoint
   - User-reported issues

See [Deployment Guide - Monitoring](deployment.md#monitoring-and-metrics) for dashboard examples.

## Runtime Validation Questions

### Which interceptor should I use?

**Decision Matrix:**

| Framework | Interceptor | When to Use |
|-----------|------------|-------------|
| MyBatis | MyBatis Interceptor | Using MyBatis XML/annotations |
| MyBatis-Plus | MyBatis-Plus InnerInterceptor | Using MyBatis-Plus BaseMapper |
| Druid | Druid Filter | Using Druid connection pool |
| HikariCP | HikariCP Proxy | Using HikariCP connection pool |
| Other | P6Spy Listener | Universal fallback for any JDBC |

**Can I use multiple?**

Yes! Deduplication filter prevents redundant validation:

```yaml
sql-guard:
  interceptors:
    mybatis: true
    jdbc: true  # Both enabled, deduplication prevents double validation
```

### What happens when SQL is blocked?

**BLOCK Strategy Behavior:**

1. Validator detects violation
2. `SQLException` thrown with SQLState `42000`
3. Transaction rolled back (if in transaction)
4. Error logged with violation details
5. User sees error (application should handle gracefully)

**Example SQLException:**

```
java.sql.SQLException: SQL Safety Violation: [CRITICAL] No WHERE clause detected
  MapperId: com.example.UserMapper.deleteAll
  SQL: DELETE FROM users
  Suggestion: Add WHERE clause to prevent accidental data loss
```

**Application Error Handling:**

```java
try {
    userMapper.deleteAll();
} catch (SQLException e) {
    if (e.getSQLState().equals("42000")) {
        // SQL Safety Guard blocked execution
        log.error("Dangerous SQL blocked: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body("Operation blocked for safety reasons");
    }
    throw e;
}
```

### Does it work with transactions?

**Yes!** SQL Safety Guard validates SQL before execution:

- If violation detected (BLOCK strategy), SQLException thrown
- Transaction automatically rolled back
- No data changes committed

**Example:**

```java
@Transactional
public void dangerousOperation() {
    userMapper.updateStatus(1);  // ✅ Safe, has WHERE
    userMapper.deleteAll();      // ❌ Blocked, transaction rolled back
    // updateStatus changes NOT committed
}
```

### Can I customize violation messages?

**Not directly**, but you can:

1. **Wrap exceptions with custom messages:**

```java
try {
    userMapper.deleteAll();
} catch (SQLException e) {
    if (e.getSQLState().equals("42000")) {
        throw new BusinessException("Operation not allowed: " + 
            extractUserFriendlyMessage(e));
    }
}
```

2. **Configure logging format:**

```xml
<!-- logback.xml -->
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
        <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
</appender>
```

## Static Analysis Questions

### What does the static scanner detect?

**Static Scanner Capabilities:**

- ✅ XML mapper SQL (MyBatis)
- ✅ Java annotation SQL (@Select, @Insert, @Update, @Delete)
- ✅ QueryWrapper usage detection (MyBatis-Plus)
- ✅ Dynamic SQL variant generation (<if>, <where>, <foreach>)

**Limitations:**

- ❌ Runtime-generated SQL (use runtime validation)
- ❌ QueryWrapper logic validation (marks usage for review)
- ❌ Stored procedures
- ❌ Native JDBC SQL strings

### How do I integrate scanner into CI/CD?

**Maven Plugin:**

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

**Gradle Plugin:**

```groovy
plugins {
    id 'com.footstone.sqlguard' version '1.0.0-SNAPSHOT'
}

sqlguard {
    failOnCritical()
}
```

**CLI Tool:**

```bash
java -jar sql-scanner-cli.jar \
  --project-path=. \
  --fail-on-critical \
  --quiet
```

See [Maven Plugin README](../../sql-scanner-maven/README.md) and [Gradle Plugin README](../../sql-scanner-gradle/README.md) for details.

### Can I generate HTML reports?

**Yes!** All scanner tools support HTML report generation:

**CLI:**

```bash
java -jar sql-scanner-cli.jar \
  --project-path=. \
  --output-format=html \
  --output-file=report.html
```

**Maven:**

```xml
<configuration>
    <outputFormat>html</outputFormat>
    <outputFile>target/sqlguard-report.html</outputFile>
</configuration>
```

**Gradle:**

```groovy
sqlguard {
    html(file('build/reports/sqlguard/report.html'))
}
```

HTML reports include:
- Interactive dashboard with statistics
- Sortable violation table
- Collapsible SQL preview sections
- Color-coded risk levels

## Troubleshooting Questions

### Why isn't auto-configuration loading?

**Common Causes:**

1. **Missing Dependency:**
   ```bash
   mvn dependency:tree | grep sql-guard-spring-boot-starter
   ```

2. **Classpath Issue:**
   ```bash
   jar -tf target/your-app.jar | grep spring.factories
   ```

3. **Configuration Disabled:**
   ```yaml
   sql-guard:
     enabled: true  # Ensure not set to false
   ```

**Solution:**

Enable debug logging:

```yaml
logging:
  level:
    com.footstone.sqlguard: DEBUG
    org.springframework.boot.autoconfigure: DEBUG
```

See [Troubleshooting Guide](troubleshooting.md#auto-configuration-not-loading) for details.

### Why is performance degraded?

**Common Causes:**

1. **Low Cache Hit Rate:** Increase cache size
2. **High Deduplication TTL:** Reduce TTL
3. **Too Many Checkers:** Disable low-value rules
4. **Parse Failures:** Enable lenient mode

**Solution:**

Check cache statistics:

```java
@Autowired
private SqlDeduplicationFilter filter;

@Scheduled(fixedRate = 60000)
public void logCacheStats() {
    CacheStats stats = filter.getCacheStatistics();
    log.info("Cache hit rate: {}%", stats.getHitRate() * 100);
}
```

See [Performance Guide](performance.md) for tuning recommendations.

### How do I report bugs?

**Bug Report Checklist:**

1. **Search existing issues:** [GitHub Issues](https://github.com/footstone/sql-safety-guard/issues)
2. **Gather information:**
   - SQL Safety Guard version
   - Java version
   - MyBatis/MyBatis-Plus version
   - Spring Boot version (if applicable)
   - Database type and version
3. **Create minimal reproduction:**
   - Sample SQL causing issue
   - Configuration YAML
   - Error logs
4. **Submit issue:** Use GitHub issue template

**Security Issues:**

Report security vulnerabilities privately to: security@footstone.com

## Next Steps

- **[Installation Guide](installation.md)** - Get started with SQL Safety Guard
- **[Configuration Reference](configuration-reference.md)** - Complete property documentation
- **[Deployment Guide](deployment.md)** - Phased rollout strategy
- **[Troubleshooting Guide](troubleshooting.md)** - Resolve common issues

---

**Still have questions?** Contact support@footstone.com
