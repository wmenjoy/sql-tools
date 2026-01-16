# P6Spy Audit Setup Guide

## Overview

P6Spy provides universal JDBC audit logging for environments without native interceptor support. It works as a JDBC driver proxy that intercepts all SQL executions, making it compatible with any JDBC driver, connection pool, or framework.

## When to Use P6Spy Audit

### ✅ Use P6Spy When:

- **Connection pool lacks native support**: C3P0, DBCP, Tomcat JDBC, or other pools without built-in interceptors
- **Mixed framework environments**: Applications using MyBatis + JPA + JdbcTemplate together
- **Rapid deployment needed**: No application code changes required, only configuration
- **Testing/debugging scenarios**: Comprehensive SQL capture for troubleshooting
- **Universal compatibility required**: Works with any JDBC-compliant driver and framework

### ⚠️ Use Native Solutions When:

- **Performance is critical**: P6Spy has 12-18% overhead vs 5-8% for native solutions
- **Native support available**: Druid, HikariCP have lower-overhead alternatives
- **Framework-specific features needed**: MyBatis, MyBatis-Plus offer tighter integration

## Performance Characteristics

| Solution | Overhead | Compatibility | Setup Complexity |
|----------|----------|---------------|------------------|
| **P6Spy Audit** | 12-18% | Universal JDBC | Low |
| Druid Audit | ~7% | Druid only | Medium |
| HikariCP Audit | ~8% | HikariCP only | Medium |
| MyBatis Audit | ~5% | MyBatis only | Low |

**Tradeoff**: P6Spy offers universal compatibility at the cost of higher overhead due to its driver proxy architecture.

---

## Configuration

### Step 1: Add P6Spy Dependency

```xml
<dependency>
    <groupId>p6spy</groupId>
    <artifactId>p6spy</artifactId>
    <version>3.9.1</version>
</dependency>

<!-- SQL Guard JDBC module includes P6Spy audit listener -->
<dependency>
    <groupId>com.footstone</groupId>
    <artifactId>sql-guard-jdbc</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Step 2: Configure spy.properties

Create `src/main/resources/spy.properties`:

```properties
# P6Spy Configuration for SQL Guard Audit and Safety

# Module list - register both safety validation and audit logging modules
modulelist=com.footstone.sqlguard.interceptor.p6spy.P6SpySqlSafetyModule,\
          com.footstone.sqlguard.interceptor.p6spy.P6SpySqlAuditModule

# Logging configuration
appender=com.p6spy.engine.spy.appender.Slf4JLogger
logMessageFormat=com.p6spy.engine.spy.appender.SingleLineFormat

# Optional: Specify JDBC drivers to wrap (auto-detect if not specified)
driverlist=com.mysql.cj.jdbc.Driver,org.postgresql.Driver,org.h2.Driver

# Optional: Filter configuration
# filter=false
# exclude=
# include=

# Optional: Performance tuning (exclude less important categories)
# excludecategories=info,debug,result,resultset
```

### Step 3: Update JDBC URL

Modify your datasource configuration to use P6Spy driver:

**Before:**
```properties
jdbc.url=jdbc:mysql://localhost:3306/mydb
jdbc.driver=com.mysql.cj.jdbc.Driver
```

**After:**
```properties
jdbc.url=jdbc:p6spy:mysql://localhost:3306/mydb
jdbc.driver=com.p6spy.engine.spy.P6SpyDriver
```

**Pattern**: Insert `p6spy:` after `jdbc:` in the URL, and use `P6SpyDriver` as the driver class.

---

## Spring Boot Integration

### application.yml

```yaml
spring:
  datasource:
    driver-class-name: com.p6spy.engine.spy.P6SpyDriver
    url: jdbc:p6spy:mysql://localhost:3306/mydb
    username: root
    password: password
```

### application.properties

```properties
spring.datasource.driver-class-name=com.p6spy.engine.spy.P6SpyDriver
spring.datasource.url=jdbc:p6spy:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
```

---

## Multi-Driver Examples

### MySQL

```properties
# Original
jdbc.url=jdbc:mysql://localhost:3306/mydb

# P6Spy wrapped
jdbc.url=jdbc:p6spy:mysql://localhost:3306/mydb
jdbc.driver=com.p6spy.engine.spy.P6SpyDriver
```

### PostgreSQL

```properties
# Original
jdbc.url=jdbc:postgresql://localhost:5432/mydb

# P6Spy wrapped
jdbc.url=jdbc:p6spy:postgresql://localhost:5432/mydb
jdbc.driver=com.p6spy.engine.spy.P6SpyDriver
```

### Oracle

```properties
# Original
jdbc.url=jdbc:oracle:thin:@localhost:1521:orcl

# P6Spy wrapped
jdbc.url=jdbc:p6spy:oracle:thin:@localhost:1521:orcl
jdbc.driver=com.p6spy.engine.spy.P6SpyDriver
```

### H2 (In-Memory)

```properties
# Original
jdbc.url=jdbc:h2:mem:testdb

# P6Spy wrapped
jdbc.url=jdbc:p6spy:h2:mem:testdb
jdbc.driver=com.p6spy.engine.spy.P6SpyDriver
```

---

## Audit Log Configuration

### Logback Configuration (logback.xml)

```xml
<configuration>
    <!-- SQL Guard Audit Logger -->
    <appender name="AUDIT_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/sql-audit.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/sql-audit.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
            <totalSizeCap>10GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%msg%n</pattern>
        </encoder>
    </appender>

    <!-- Async Wrapper for High-Throughput -->
    <appender name="ASYNC_AUDIT" class="ch.qos.logback.classic.AsyncAppender">
        <queueSize>10000</queueSize>
        <discardingThreshold>0</discardingThreshold>
        <appender-ref ref="AUDIT_FILE"/>
    </appender>

    <!-- SQL Guard Audit Logger -->
    <logger name="com.footstone.sqlguard.audit.AUDIT" level="INFO" additivity="false">
        <appender-ref ref="ASYNC_AUDIT"/>
    </logger>

    <!-- Root Logger -->
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

---

## Advanced Configuration

### Custom AuditLogWriter

If you need a custom audit log writer (e.g., database, message queue):

```java
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.interceptor.p6spy.P6SpySqlAuditModule;

public class CustomAuditWriter implements AuditLogWriter {
    @Override
    public void writeAuditLog(AuditEvent event) {
        // Custom implementation: write to database, Kafka, etc.
    }
}

// Configure before P6Spy initialization
public class ApplicationInitializer {
    static {
        AuditLogWriter customWriter = new CustomAuditWriter();
        P6SpySqlAuditModule.setAuditLogWriter(customWriter);
    }
}
```

### Safety Validation Strategy

Configure violation handling strategy via system property:

```properties
# System property
-Dsqlguard.p6spy.strategy=BLOCK

# Options:
# - BLOCK: Throw SQLException to prevent dangerous SQL execution
# - WARN:  Log ERROR and allow execution
# - LOG:   Log WARN and allow execution (default)
```

---

## Audit Event Format

P6Spy audit events are logged as JSON:

```json
{
  "sqlId": "a3f2b1c4d5e6f7g8h9i0j1k2l3m4n5o6",
  "sql": "SELECT * FROM users WHERE id = 1",
  "sqlType": "SELECT",
  "mapperId": "p6spy-jdbc",
  "datasource": null,
  "params": null,
  "executionTimeMs": 150,
  "rowsAffected": -1,
  "errorMessage": null,
  "timestamp": "2025-12-17T10:30:45.123Z",
  "violations": {
    "passed": false,
    "riskLevel": "MEDIUM",
    "violations": [
      {
        "riskLevel": "MEDIUM",
        "message": "SQL without WHERE clause detected",
        "suggestion": "Add WHERE clause to restrict query scope"
      }
    ]
  }
}
```

### Field Descriptions

| Field | Description | Notes |
|-------|-------------|-------|
| `sqlId` | MD5 hash of SQL statement | For deduplication |
| `sql` | SQL statement with parameter values | ⚠️ Contains actual values |
| `sqlType` | SQL command type | SELECT, UPDATE, DELETE, INSERT |
| `mapperId` | Identifier | Always "p6spy-jdbc" for P6Spy |
| `executionTimeMs` | Execution time in milliseconds | From P6Spy timing |
| `rowsAffected` | Rows affected by DML | -1 for SELECT |
| `errorMessage` | SQLException message if failed | null if successful |
| `violations` | Pre-execution validation violations | null if no violations |

---

## Limitations

### 1. Parameter Substitution

**Issue**: P6Spy's `getSqlWithValues()` returns SQL with actual parameter values substituted.

**Security Concern**: Audit logs contain sensitive data (passwords, PII, etc.).

**Mitigation**:
- Encrypt audit log files
- Implement custom `AuditLogWriter` with data masking
- Restrict log file access with filesystem permissions

### 2. Higher Overhead

**Issue**: P6Spy has 12-18% overhead vs 5-8% for native solutions.

**Reason**: Driver proxy layer, statement wrapping, callback dispatch.

**Mitigation**:
- Use native solutions (Druid, HikariCP, MyBatis) when available
- Profile application to ensure overhead is acceptable
- Consider using P6Spy only in dev/test environments

### 3. Limited Dependency Injection

**Issue**: P6Spy modules are loaded via SPI, limiting DI framework integration.

**Workaround**:
- Use `P6SpySqlAuditModule.setAuditLogWriter()` in static initializer
- Or configure via properties file and system properties

### 4. Batch Result Detail

**Issue**: P6Spy provides aggregate batch results, not per-statement detail for all drivers.

**Impact**: Audit log shows total rows affected, not individual statement results.

---

## Troubleshooting

### P6Spy Not Intercepting SQL

**Symptoms**: No audit logs generated, SQL executes normally.

**Checklist**:
1. Verify P6Spy driver: `com.p6spy.engine.spy.P6SpyDriver`
2. Verify URL format: `jdbc:p6spy:mysql://...` (note `p6spy:` prefix)
3. Check `spy.properties` exists in classpath
4. Verify module list includes `P6SpySqlAuditModule`
5. Check SPI file: `META-INF/services/com.p6spy.engine.spy.P6Factory`

### ClassNotFoundException for P6SpyDriver

**Cause**: P6Spy dependency missing.

**Solution**: Add P6Spy dependency (see Step 1).

### Audit Logs Not Written

**Symptoms**: P6Spy intercepts SQL, but no audit logs.

**Checklist**:
1. Verify Logback configuration for `com.footstone.sqlguard.audit.AUDIT` logger
2. Check log file permissions
3. Verify `AsyncAppender` is not dropping events (check `discardingThreshold`)
4. Enable P6Spy debug logging: `logMessageFormat=com.p6spy.engine.spy.appender.SingleLineFormat`

### High Performance Overhead

**Symptoms**: Application slowdown after enabling P6Spy.

**Solutions**:
1. Use `excludecategories` to reduce P6Spy logging:
   ```properties
   excludecategories=info,debug,result,resultset,batch
   ```
2. Switch to native audit solution if available (Druid, HikariCP)
3. Profile application to identify hotspots
4. Consider using P6Spy only in non-production environments

---

## Testing

### Verify P6Spy is Active

```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class P6SpyTest {
    public static void main(String[] args) throws Exception {
        // Load P6Spy driver
        Class.forName("com.p6spy.engine.spy.P6SpyDriver");
        
        // Connect via P6Spy
        Connection conn = DriverManager.getConnection(
            "jdbc:p6spy:h2:mem:testdb", "sa", "");
        
        // Execute SQL
        Statement stmt = conn.createStatement();
        stmt.executeQuery("SELECT 1");
        
        // Check logs for audit events
        System.out.println("Check logs/sql-audit.log for audit events");
    }
}
```

---

## References

- [P6Spy Official Documentation](https://p6spy.readthedocs.io/)
- [SQL Guard Architecture](../architecture/README.md)
- [Audit Event Schema](../api/audit-event.md)
- [Performance Benchmarks](../performance/benchmarks.md)

---

## Support

For issues or questions:
- Check [Troubleshooting](#troubleshooting) section
- Review P6Spy logs for errors
- Verify configuration against examples
- Consult SQL Guard documentation













