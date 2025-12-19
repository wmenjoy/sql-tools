# P6Spy SQL Safety Guard - Setup Guide

## Overview

P6Spy SQL Safety Guard provides universal JDBC-level SQL validation for any database driver and connection pool. This guide walks you through the complete setup process.

## When to Use P6Spy

### ✅ Use P6Spy When:
- Using C3P0, DBCP, or Tomcat JDBC Pool (no native integration available)
- Using bare JDBC without a connection pool
- Need a framework-agnostic solution that works with any JDBC driver
- Want minimal code changes (configuration-only setup)
- Legacy applications where code modifications are difficult

### ❌ Don't Use P6Spy When:
- Using Druid connection pool → Use `DruidSqlSafetyFilter` instead (~5% overhead vs ~15%)
- Using HikariCP → Use `HikariSqlSafetyProxyFactory` instead (~3% overhead vs ~15%)
- Using MyBatis → Use `SqlSafetyInterceptor` instead (<5% overhead)
- Using MyBatis-Plus → Use `MpSqlSafetyInnerInterceptor` instead (<5% overhead)
- Performance is critical and native integration is available

## Performance Trade-offs

| Solution | Overhead | Setup Complexity | Coverage |
|----------|----------|------------------|----------|
| MyBatis Interceptor | <5% | Low | MyBatis only |
| Druid Filter | ~5% | Medium | Druid pool only |
| HikariCP Proxy | ~3% | Medium | HikariCP only |
| **P6Spy Listener** | **~15%** | **Low** | **Universal** |

**Note:** P6Spy has higher overhead but provides universal coverage. Acceptable for safety-critical environments where comprehensive validation is more important than optimal performance.

---

## Quick Start (5 Steps)

### Step 1: Add Dependencies

Add P6Spy and SQL Guard JDBC to your project:

**Maven:**
```xml
<dependencies>
    <!-- P6Spy JDBC Proxy Driver -->
    <dependency>
        <groupId>p6spy</groupId>
        <artifactId>p6spy</artifactId>
        <version>3.9.1</version>
    </dependency>
    
    <!-- SQL Guard JDBC -->
    <dependency>
        <groupId>com.footstone</groupId>
        <artifactId>sql-guard-jdbc</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

**Gradle:**
```gradle
dependencies {
    implementation 'p6spy:p6spy:3.9.1'
    implementation 'com.footstone:sql-guard-jdbc:1.0.0-SNAPSHOT'
}
```

### Step 2: Modify JDBC Configuration

Change your JDBC driver class and URL to use P6Spy:

**Before:**
```properties
# Spring Boot application.properties
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
```

**After:**
```properties
# Spring Boot application.properties
spring.datasource.driver-class-name=com.p6spy.engine.spy.P6SpyDriver
spring.datasource.url=jdbc:p6spy:mysql://localhost:3306/mydb
```

**Pattern:** Prefix your JDBC URL with `p6spy:` and use `P6SpyDriver` as the driver class.

### Step 3: Add spy.properties to Classpath

Create `spy.properties` in `src/main/resources/`:

```properties
# Module registration
modulelist=com.footstone.sqlguard.interceptor.p6spy.P6SpySqlSafetyModule

# Logging appender
appender=com.p6spy.engine.spy.appender.Slf4JLogger

# Database drivers to wrap
driverlist=com.mysql.cj.jdbc.Driver,org.postgresql.Driver,org.h2.Driver

# Log format
logMessageFormat=com.p6spy.engine.spy.appender.SingleLineFormat
dateformat=yyyy-MM-dd HH:mm:ss
```

### Step 4: Configure Violation Strategy (Optional)

Set the violation handling strategy via system property:

```bash
# Development: Block violations
-Dsqlguard.p6spy.strategy=BLOCK

# Staging: Warn about violations
-Dsqlguard.p6spy.strategy=WARN

# Production (initial): Log violations only
-Dsqlguard.p6spy.strategy=LOG
```

**Default:** `LOG` (observation mode)

### Step 5: Verify Setup

Run your application and check logs for initialization message:

```
INFO  c.f.s.i.p6spy.P6SpySqlSafetyModule - Initializing P6Spy SQL Safety Module...
INFO  c.f.s.i.p6spy.P6SpySqlSafetyModule - P6Spy SQL Safety Module initialized successfully with strategy: LOG
```

Execute a test query with a safety violation:

```sql
SELECT * FROM users;  -- Missing WHERE clause
```

Expected log output:
```
WARN  c.f.s.i.p6spy.P6SpySqlSafetyListener - [LOG] SQL Safety Violation: SQL: SELECT * FROM users | Risk: HIGH | Violations: [Missing WHERE clause (Suggestion: Add WHERE condition)]
```

---

## Detailed Configuration

### Database Driver Configuration

Add your specific database driver to `driverlist` in `spy.properties`:

```properties
# MySQL
driverlist=com.mysql.cj.jdbc.Driver

# PostgreSQL
driverlist=org.postgresql.Driver

# H2
driverlist=org.h2.Driver

# Oracle
driverlist=oracle.jdbc.OracleDriver

# SQL Server
driverlist=com.microsoft.sqlserver.jdbc.SQLServerDriver

# Multiple drivers (comma-separated)
driverlist=com.mysql.cj.jdbc.Driver,org.postgresql.Driver,org.h2.Driver
```

### JDBC URL Patterns

| Database | Original URL | P6Spy URL |
|----------|-------------|-----------|
| MySQL | `jdbc:mysql://localhost:3306/mydb` | `jdbc:p6spy:mysql://localhost:3306/mydb` |
| PostgreSQL | `jdbc:postgresql://localhost:5432/testdb` | `jdbc:p6spy:postgresql://localhost:5432/testdb` |
| H2 (in-memory) | `jdbc:h2:mem:test` | `jdbc:p6spy:h2:mem:test` |
| H2 (file) | `jdbc:h2:file:/data/mydb` | `jdbc:p6spy:h2:file:/data/mydb` |
| Oracle | `jdbc:oracle:thin:@localhost:1521:orcl` | `jdbc:p6spy:oracle:thin:@localhost:1521:orcl` |

### Violation Strategy Configuration

Three strategies available:

#### 1. BLOCK (Fail-Closed)
```bash
-Dsqlguard.p6spy.strategy=BLOCK
```
- **Behavior:** Throws `SQLException`, prevents SQL execution
- **Use Case:** Development, zero-tolerance environments
- **Log Level:** ERROR
- **Transaction:** Rolled back if active

#### 2. WARN (Fail-Open with Visibility)
```bash
-Dsqlguard.p6spy.strategy=WARN
```
- **Behavior:** Logs error, continues execution
- **Use Case:** Staging, gradual production rollout
- **Log Level:** ERROR
- **Transaction:** Continues normally

#### 3. LOG (Observation Mode)
```bash
-Dsqlguard.p6spy.strategy=LOG
```
- **Behavior:** Logs warning, continues execution
- **Use Case:** Production initial rollout, metrics collection
- **Log Level:** WARN
- **Transaction:** Continues normally

---

## Deployment Scenarios

### Scenario 1: Quick Deployment (Legacy Application)

**Goal:** Add SQL validation with minimal code changes

**Steps:**
1. Add dependencies to `pom.xml` or `build.gradle`
2. Change driver class and URL in configuration file
3. Add `spy.properties` to resources
4. Restart application

**Time Required:** ~15 minutes

**Code Changes:** None (configuration only)

### Scenario 2: Multi-Pool Environment

**Goal:** Validate SQL across different connection pools

**Setup:**
```java
// C3P0 DataSource
ComboPooledDataSource c3p0 = new ComboPooledDataSource();
c3p0.setDriverClass("com.p6spy.engine.spy.P6SpyDriver");
c3p0.setJdbcUrl("jdbc:p6spy:mysql://localhost:3306/db1");

// DBCP DataSource
BasicDataSource dbcp = new BasicDataSource();
dbcp.setDriverClassName("com.p6spy.engine.spy.P6SpyDriver");
dbcp.setUrl("jdbc:p6spy:postgresql://localhost:5432/db2");

// Tomcat JDBC Pool
org.apache.tomcat.jdbc.pool.DataSource tomcat = 
    new org.apache.tomcat.jdbc.pool.DataSource();
tomcat.setDriverClassName("com.p6spy.engine.spy.P6SpyDriver");
tomcat.setUrl("jdbc:p6spy:h2:mem:db3");
```

**Benefit:** Single P6Spy configuration validates SQL across all pools

### Scenario 3: Fallback Solution

**Goal:** Use native integration where available, P6Spy as fallback

**Architecture:**
```
Primary DataSource (Druid) → DruidSqlSafetyFilter (~5% overhead)
Fallback DataSource (C3P0) → P6Spy (~15% overhead)
Legacy DataSource (DBCP)   → P6Spy (~15% overhead)
```

**Configuration:**
- Both solutions can coexist
- Deduplication prevents double validation
- Use native integration for high-traffic paths

---

## Spring Boot Integration

### application.properties
```properties
# DataSource Configuration
spring.datasource.driver-class-name=com.p6spy.engine.spy.P6SpyDriver
spring.datasource.url=jdbc:p6spy:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password

# Connection Pool (any pool works)
spring.datasource.type=com.mchange.v2.c3p0.ComboPooledDataSource
# OR
spring.datasource.type=org.apache.commons.dbcp2.BasicDataSource
# OR
spring.datasource.type=org.apache.tomcat.jdbc.pool.DataSource
```

### application.yml
```yaml
spring:
  datasource:
    driver-class-name: com.p6spy.engine.spy.P6SpyDriver
    url: jdbc:p6spy:mysql://localhost:3306/mydb
    username: root
    password: password
    type: com.mchange.v2.c3p0.ComboPooledDataSource
```

### JVM Arguments
```bash
java -jar myapp.jar -Dsqlguard.p6spy.strategy=WARN
```

---

## Advanced Configuration

### Custom Rule Configuration

Create `safety-rules.yml` in classpath:

```yaml
rules:
  no-where-clause:
    enabled: true
    risk-level: HIGH
    
  dummy-condition:
    enabled: true
    risk-level: MEDIUM
    
  limit-offset:
    enabled: true
    max-limit: 1000
    risk-level: MEDIUM
```

### Logging Configuration

**Logback (logback.xml):**
```xml
<logger name="com.footstone.sqlguard.interceptor.p6spy" level="WARN"/>
<logger name="p6spy" level="INFO"/>
```

**Log4j2 (log4j2.xml):**
```xml
<Logger name="com.footstone.sqlguard.interceptor.p6spy" level="WARN"/>
<Logger name="p6spy" level="INFO"/>
```

### Exclude System Queries

Add to `spy.properties`:
```properties
# Exclude internal queries from validation
excludecategories=info,debug,result,resultset

# Exclude specific SQL patterns (P6Spy feature)
#filter=true
#exclude=.*INFORMATION_SCHEMA.*
```

---

## Troubleshooting

See [p6spy-troubleshooting.md](p6spy-troubleshooting.md) for common issues and solutions.

---

## Next Steps

1. ✅ Complete setup following this guide
2. ✅ Test with sample queries
3. ✅ Monitor logs for violations
4. ✅ Adjust violation strategy based on environment
5. ✅ Review and fix identified SQL safety issues
6. ✅ Gradually increase enforcement (LOG → WARN → BLOCK)

---

## Support

- **Documentation:** [SQL Safety Guard Docs](../../docs/)
- **Performance Analysis:** [p6spy-performance-analysis.md](p6spy-performance-analysis.md)
- **Troubleshooting:** [p6spy-troubleshooting.md](p6spy-troubleshooting.md)
- **GitHub Issues:** [Report an issue](https://github.com/footstone/sql-safety-guard/issues)








