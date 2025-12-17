# P6Spy SQL Safety Guard - Troubleshooting Guide

## Common Issues and Solutions

### Issue 1: Module Not Loaded

**Symptoms:**
- No SQL validation occurring
- No log messages from P6SpySqlSafetyModule
- SQL violations not detected

**Diagnosis:**
```bash
# Check if module is registered
grep "Initializing P6Spy SQL Safety Module" application.log
```

**Solutions:**

1. **Verify spy.properties location:**
   ```bash
   # Must be in classpath root
   src/main/resources/spy.properties
   ```

2. **Check modulelist configuration:**
   ```properties
   # spy.properties
   modulelist=com.footstone.sqlguard.interceptor.p6spy.P6SpySqlSafetyModule
   ```

3. **Verify P6Spy driver configuration:**
   ```properties
   # application.properties
   spring.datasource.driver-class-name=com.p6spy.engine.spy.P6SpyDriver
   spring.datasource.url=jdbc:p6spy:mysql://localhost:3306/mydb
   ```

4. **Check dependencies:**
   ```xml
   <dependency>
       <groupId>p6spy</groupId>
       <artifactId>p6spy</artifactId>
       <version>3.9.1</version>
   </dependency>
   <dependency>
       <groupId>com.footstone</groupId>
       <artifactId>sql-guard-jdbc</artifactId>
       <version>1.0.0-SNAPSHOT</version>
   </dependency>
   ```

---

### Issue 2: Validator Not Found

**Symptoms:**
```
ERROR c.f.s.i.p6spy.P6SpySqlSafetyModule - Failed to load SqlSafetyValidator
java.lang.IllegalStateException: Failed to load SqlSafetyValidator
```

**Solutions:**

1. **Verify sql-guard-core dependency:**
   ```xml
   <dependency>
       <groupId>com.footstone</groupId>
       <artifactId>sql-guard-core</artifactId>
       <version>1.0.0-SNAPSHOT</version>
   </dependency>
   ```

2. **Check if validator classes are available:**
   ```bash
   jar tf target/myapp.jar | grep DefaultSqlSafetyValidator
   ```

3. **Use fallback validator (automatic):**
   - Module automatically creates default validator if ServiceLoader fails
   - Check logs for: "No SqlSafetyValidator found via ServiceLoader, creating default instance"

---

### Issue 3: Double Validation

**Symptoms:**
- SQL validated twice
- Performance degradation
- Duplicate log messages

**Cause:**
- Multiple interceptors active simultaneously
- P6Spy + MyBatis interceptor both enabled

**Solutions:**

1. **Choose one interception layer:**
   ```yaml
   # Option A: Use MyBatis interceptor (preferred for MyBatis apps)
   mybatis:
     configuration:
       interceptors:
         - com.footstone.sqlguard.interceptor.mybatis.SqlSafetyInterceptor
   
   # Option B: Use P6Spy (universal fallback)
   spring:
     datasource:
       driver-class-name: com.p6spy.engine.spy.P6SpyDriver
   ```

2. **Deduplication is automatic:**
   - SqlDeduplicationFilter prevents redundant validation
   - Same SQL within 100ms TTL skips re-validation

---

### Issue 4: Performance Degradation

**Symptoms:**
- Slow query execution
- High CPU usage
- Application response time increased

**Diagnosis:**
```bash
# Check P6Spy overhead
# Expected: ~15% overhead for P6Spy
# Compare to: ~5% for native solutions
```

**Solutions:**

1. **Use native integration if available:**
   - Druid → DruidSqlSafetyFilter (~5% overhead)
   - HikariCP → HikariSqlSafetyProxyFactory (~3% overhead)
   - MyBatis → SqlSafetyInterceptor (<5% overhead)

2. **Optimize deduplication settings:**
   ```java
   // Increase cache size for high-traffic applications
   SqlDeduplicationFilter filter = new SqlDeduplicationFilter(5000, 100L);
   ```

3. **Disable validation in non-critical paths:**
   ```properties
   # Use LOG strategy in production
   -Dsqlguard.p6spy.strategy=LOG
   ```

4. **Profile application:**
   ```bash
   # Use JProfiler or YourKit to identify bottlenecks
   java -agentpath:/path/to/profiler myapp.jar
   ```

---

### Issue 5: SQLException Thrown Unexpectedly

**Symptoms:**
```
java.sql.SQLException: SQL Safety Violation (BLOCK): SQL: SELECT * FROM users | Risk: HIGH
```

**Cause:**
- BLOCK strategy enabled
- SQL violates safety rules

**Solutions:**

1. **Change strategy to WARN or LOG:**
   ```bash
   # Development
   -Dsqlguard.p6spy.strategy=BLOCK
   
   # Staging
   -Dsqlguard.p6spy.strategy=WARN
   
   # Production (initial)
   -Dsqlguard.p6spy.strategy=LOG
   ```

2. **Fix the SQL violation:**
   ```sql
   -- Before (violation)
   SELECT * FROM users;
   
   -- After (fixed)
   SELECT * FROM users WHERE status = 'active' LIMIT 1000;
   ```

3. **Review violation details in logs:**
   ```
   [BLOCK] SQL Safety Violation: SQL: SELECT * FROM users | Risk: HIGH | 
   Violations: [Missing WHERE clause (Suggestion: Add WHERE condition)]
   ```

---

### Issue 6: P6Spy Not Intercepting SQL

**Symptoms:**
- Application works normally
- No P6Spy logs
- SQL validation not occurring

**Diagnosis:**
```bash
# Check if P6Spy driver is being used
grep "P6SpyDriver" application.log
```

**Solutions:**

1. **Verify driver class name:**
   ```properties
   # Must be P6SpyDriver, not the actual database driver
   spring.datasource.driver-class-name=com.p6spy.engine.spy.P6SpyDriver
   ```

2. **Verify JDBC URL prefix:**
   ```properties
   # Must start with jdbc:p6spy:
   spring.datasource.url=jdbc:p6spy:mysql://localhost:3306/mydb
   ```

3. **Check for driver conflicts:**
   ```xml
   <!-- Remove explicit driver dependency if present -->
   <!-- P6Spy will load the real driver automatically -->
   ```

---

### Issue 7: ClassNotFoundException for P6Spy

**Symptoms:**
```
java.lang.ClassNotFoundException: com.p6spy.engine.spy.P6SpyDriver
```

**Solutions:**

1. **Add P6Spy dependency:**
   ```xml
   <dependency>
       <groupId>p6spy</groupId>
       <artifactId>p6spy</artifactId>
       <version>3.9.1</version>
   </dependency>
   ```

2. **Verify dependency scope:**
   ```xml
   <!-- Should be compile scope, not provided -->
   <dependency>
       <groupId>p6spy</groupId>
       <artifactId>p6spy</artifactId>
       <version>3.9.1</version>
       <scope>compile</scope> <!-- Not provided -->
   </dependency>
   ```

3. **Check Maven/Gradle build:**
   ```bash
   mvn clean install
   # OR
   gradle clean build
   ```

---

### Issue 8: Invalid Violation Strategy

**Symptoms:**
```
WARN c.f.s.i.p6spy.P6SpySqlSafetyModule - Invalid violation strategy 'INVALID', 
defaulting to LOG. Valid values: BLOCK, WARN, LOG
```

**Solutions:**

1. **Use valid strategy values:**
   ```bash
   # Valid values only
   -Dsqlguard.p6spy.strategy=BLOCK
   -Dsqlguard.p6spy.strategy=WARN
   -Dsqlguard.p6spy.strategy=LOG
   ```

2. **Check for typos:**
   ```bash
   # Case-insensitive, but must be exact
   -Dsqlguard.p6spy.strategy=block  # OK
   -Dsqlguard.p6spy.strategy=BLOK   # INVALID
   ```

---

### Issue 9: Logs Not Appearing

**Symptoms:**
- No SQL validation logs
- Module initialized successfully
- Validation occurring but not logged

**Solutions:**

1. **Configure logging level:**
   ```xml
   <!-- logback.xml -->
   <logger name="com.footstone.sqlguard.interceptor.p6spy" level="WARN"/>
   <logger name="p6spy" level="INFO"/>
   ```

2. **Check SLF4J binding:**
   ```bash
   # Ensure SLF4J implementation is present
   mvn dependency:tree | grep slf4j
   ```

3. **Verify appender configuration:**
   ```properties
   # spy.properties
   appender=com.p6spy.engine.spy.appender.Slf4JLogger
   ```

---

### Issue 10: Module Initialization Failure

**Symptoms:**
```
ERROR c.f.s.i.p6spy.P6SpySqlSafetyModule - Failed to initialize P6Spy SQL Safety Module
java.lang.IllegalStateException: P6Spy SQL Safety Module initialization failed
```

**Solutions:**

1. **Check full stack trace:**
   ```bash
   grep -A 20 "Failed to initialize" application.log
   ```

2. **Verify all dependencies:**
   ```bash
   mvn dependency:tree -Dincludes=com.footstone:*
   ```

3. **Check for version conflicts:**
   ```xml
   <!-- Ensure compatible versions -->
   <properties>
       <p6spy.version>3.9.1</p6spy.version>
       <sql-guard.version>1.0.0-SNAPSHOT</sql-guard.version>
   </properties>
   ```

---

## Debugging Tips

### Enable Debug Logging

```xml
<!-- logback.xml -->
<logger name="com.footstone.sqlguard" level="DEBUG"/>
<logger name="p6spy" level="DEBUG"/>
```

### Verify P6Spy Configuration

```bash
# Check if spy.properties is loaded
java -Dspy.properties=/path/to/spy.properties -jar myapp.jar
```

### Test with Simple Query

```java
@Test
void testP6SpyInterception() throws SQLException {
    Connection conn = DriverManager.getConnection(
        "jdbc:p6spy:h2:mem:test");
    
    Statement stmt = conn.createStatement();
    // This should trigger validation
    stmt.executeQuery("SELECT * FROM users");
}
```

---

## Getting Help

If issues persist:

1. **Check logs** for detailed error messages
2. **Review documentation** at [p6spy-setup.md](p6spy-setup.md)
3. **Verify configuration** against examples
4. **Test with minimal setup** to isolate the issue
5. **Report issues** with full stack traces and configuration

---

## Related Documentation

- [Setup Guide](p6spy-setup.md)
- [Performance Analysis](p6spy-performance-analysis.md)
- [SQL Safety Guard Docs](../../docs/)



