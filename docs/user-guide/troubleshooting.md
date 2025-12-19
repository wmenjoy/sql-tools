# Troubleshooting Guide

Common issues and solutions for SQL Safety Guard.

## Table of Contents

- [Installation Issues](#installation-issues)
- [Configuration Issues](#configuration-issues)
- [Runtime Issues](#runtime-issues)
- [Performance Issues](#performance-issues)
- [Integration Issues](#integration-issues)

## Installation Issues

### Auto-Configuration Not Loading

**Symptoms:**
- No SQL Guard logs at startup
- Validation not happening
- Beans not created

**Diagnosis:**

```bash
# Check if starter dependency is present
mvn dependency:tree | grep sql-guard-spring-boot-starter

# Check if spring.factories is on classpath
jar -tf target/your-app.jar | grep spring.factories

# Enable debug logging
```

```yaml
logging:
  level:
    com.footstone.sqlguard: DEBUG
    org.springframework.boot.autoconfigure: DEBUG
```

**Solutions:**

1. **Verify starter dependency:**
   ```xml
   <dependency>
       <groupId>com.footstone</groupId>
       <artifactId>sql-guard-spring-boot-starter</artifactId>
       <version>1.0.0-SNAPSHOT</version>
   </dependency>
   ```

2. **Check META-INF/spring.factories:**
   ```properties
   org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
   com.footstone.sqlguard.spring.autoconfigure.SqlGuardAutoConfiguration
   ```

3. **Verify not disabled:**
   ```yaml
   sql-guard:
     enabled: true  # Must be true
   ```

4. **Check for conflicting configurations:**
   ```bash
   # Search for duplicate SqlGuardAutoConfiguration
   find . -name "*.jar" -exec jar -tf {} \; | grep SqlGuardAutoConfiguration
   ```

### Version Conflicts

**Symptoms:**
- `ClassNotFoundException`
- `NoSuchMethodError`
- `NoClassDefFoundError`

**Diagnosis:**

```bash
# Check dependency tree
mvn dependency:tree

# Find conflicting versions
mvn dependency:tree | grep mybatis
mvn dependency:tree | grep jsqlparser
```

**Solutions:**

1. **Exclude transitive dependencies:**
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

2. **Use dependency management:**
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

3. **Verify compatible versions:**
   - MyBatis 3.4.6+ or 3.5.13+
   - MyBatis-Plus 3.4.0+ or 3.5.3+
   - Spring Boot 2.7.x or 3.x
   - Java 8+

### Build Plugin Not Found

**Symptoms:**
- `Plugin 'com.footstone:sql-scanner-maven' not found`
- `Plugin 'com.footstone.sqlguard' not found` (Gradle)

**Diagnosis:**

```bash
# Check if plugin is installed
ls ~/.m2/repository/com/footstone/sql-scanner-maven/

# Check Gradle plugin cache
ls ~/.gradle/caches/modules-2/files-2.1/com.footstone/sql-scanner-gradle/
```

**Solutions:**

1. **Install plugin to local repository:**
   ```bash
   cd sql-scanner-maven
   mvn clean install
   ```

2. **Verify plugin coordinates:**
   ```xml
   <plugin>
       <groupId>com.footstone</groupId>
       <artifactId>sql-scanner-maven</artifactId>
       <version>1.0.0-SNAPSHOT</version>
   </plugin>
   ```

3. **Check Maven settings.xml:**
   ```xml
   <settings>
       <localRepository>~/.m2/repository</localRepository>
   </settings>
   ```

## Configuration Issues

### YAML Parsing Errors

**Symptoms:**
- `ConfigLoadException: Failed to parse YAML`
- `YAMLException: mapping values are not allowed here`

**Diagnosis:**

```bash
# Validate YAML syntax
yamllint application.yml

# Check for common issues
cat application.yml | grep -E "^\s+[a-z-]+:[^:]"
```

**Solutions:**

1. **Fix indentation (use spaces, not tabs):**
   ```yaml
   # ❌ Wrong (tabs)
   sql-guard:
   	enabled: true
   
   # ✅ Correct (2 spaces)
   sql-guard:
     enabled: true
   ```

2. **Fix mapping syntax:**
   ```yaml
   # ❌ Wrong (missing space after colon)
   sql-guard:
     enabled:true
   
   # ✅ Correct (space after colon)
   sql-guard:
     enabled: true
   ```

3. **Quote special characters:**
   ```yaml
   # ❌ Wrong (unquoted colon)
   dummy-condition:
     patterns:
       - 1:1
   
   # ✅ Correct (quoted)
   dummy-condition:
     patterns:
       - "1:1"
   ```

### Invalid Configuration Values

**Symptoms:**
- `IllegalArgumentException: Invalid value for...`
- `ConstraintViolationException: Validation failed`

**Diagnosis:**

```yaml
logging:
  level:
    com.footstone.sqlguard.config: DEBUG
```

**Solutions:**

1. **Check valid values:**
   ```yaml
   # ❌ Wrong (invalid strategy)
   sql-guard:
     active-strategy: IGNORE  # Must be LOG, WARN, or BLOCK
   
   # ✅ Correct
   sql-guard:
     active-strategy: LOG
   ```

2. **Check numeric ranges:**
   ```yaml
   # ❌ Wrong (negative value)
   deduplication:
     cache-size: -1  # Must be > 0
   
   # ✅ Correct
   deduplication:
     cache-size: 1000
   ```

3. **Check required fields:**
   ```yaml
   # ❌ Wrong (missing required fields)
   rules:
     no-where-clause: {}  # Missing enabled
   
   # ✅ Correct
   rules:
     no-where-clause:
       enabled: true
   ```

### Configuration Not Applied

**Symptoms:**
- Changes to `application.yml` not taking effect
- Default values used instead of configured values

**Diagnosis:**

```java
@Autowired
private SqlGuardProperties properties;

@PostConstruct
public void logConfig() {
    log.info("SQL Guard config: {}", properties);
}
```

**Solutions:**

1. **Verify profile active:**
   ```bash
   # Check active profile
   java -jar app.jar --spring.profiles.active=prod
   ```

2. **Check property precedence:**
   ```
   Priority (highest to lowest):
   1. Command-line arguments (--sql-guard.enabled=false)
   2. Environment variables (SQL_GUARD_ENABLED=false)
   3. application-{profile}.yml
   4. application.yml
   5. Default values
   ```

3. **Verify property names:**
   ```yaml
   # ❌ Wrong (camelCase in YAML)
   sql-guard:
     activeStrategy: LOG
   
   # ✅ Correct (kebab-case in YAML)
   sql-guard:
     active-strategy: LOG
   ```

## Runtime Issues

### SQLException: SQL Safety Violation

**Symptoms:**
- `SQLException: SQL Safety Violation: [CRITICAL] No WHERE clause`
- Application errors with SQLState `42000`

**Diagnosis:**

```java
try {
    userMapper.deleteAll();
} catch (SQLException e) {
    log.error("SQL blocked: {}", e.getMessage());
    log.error("SQLState: {}", e.getSQLState());
    log.error("Cause: {}", e.getCause());
}
```

**Solutions:**

1. **Fix SQL (recommended):**
   ```sql
   -- ❌ Dangerous
   DELETE FROM users
   
   -- ✅ Safe
   DELETE FROM users WHERE id = ?
   ```

2. **Downgrade strategy temporarily:**
   ```yaml
   sql-guard:
     active-strategy: WARN  # Downgrade from BLOCK
   ```

3. **Whitelist specific mapper:**
   ```yaml
   rules:
     no-where-clause:
       whitelist-mapper-ids:
         - "UserMapper.deleteAll"  # Whitelist if legitimate
   ```

4. **Disable rule (not recommended):**
   ```yaml
   rules:
     no-where-clause:
       enabled: false  # Last resort
   ```

### False Positives

**Symptoms:**
- Legitimate SQL flagged as violation
- Excessive warnings in logs

**Diagnosis:**

```bash
# Analyze violation patterns
grep "SQL Safety Violation" application.log | \
  awk '{print $10}' | sort | uniq -c | sort -rn
```

**Solutions:**

1. **Review violation details:**
   ```
   WARN  SqlSafetyInterceptor - SQL Safety Warning: [HIGH] Blacklist fields only
   WARN  SqlSafetyInterceptor - MapperId: com.example.ConfigMapper.selectAll
   WARN  SqlSafetyInterceptor - SQL: SELECT * FROM config WHERE status = 'active'
   WARN  SqlSafetyInterceptor - Fields: [status]
   WARN  SqlSafetyInterceptor - Suggestion: Add high-selectivity field (id, config_key)
   ```

2. **Determine if false positive:**
   - Small table (<1000 rows)? → Whitelist
   - Unavoidable pattern? → Adjust configuration
   - Legitimate issue? → Fix SQL

3. **Whitelist small tables:**
   ```yaml
   rules:
     no-pagination:
       whitelist-tables:
         - config
         - metadata
         - system_settings
   ```

4. **Adjust blacklist:**
   ```yaml
   rules:
     blacklist-fields:
       blacklist-fields:
         - deleted
         - status
         # Remove 'type' - too many false positives
   ```

### Validation Not Triggering

**Symptoms:**
- No validation logs
- Dangerous SQL executing without warnings

**Diagnosis:**

```java
@Autowired
private DefaultSqlSafetyValidator validator;

@PostConstruct
public void testValidator() {
    SqlContext context = SqlContext.builder()
        .sql("DELETE FROM users")
        .type(SqlCommandType.DELETE)
        .mapperId("test.delete")
        .build();
    
    ValidationResult result = validator.validate(context);
    log.info("Validation result: {}", result);
}
```

**Solutions:**

1. **Verify interceptor registered:**
   ```java
   @Autowired
   private List<Interceptor> interceptors;
   
   @PostConstruct
   public void logInterceptors() {
       log.info("Registered interceptors: {}", interceptors);
   }
   ```

2. **Check interceptor order:**
   ```java
   // MyBatis-Plus: PaginationInnerInterceptor MUST be before MpSqlSafetyInnerInterceptor
   @Bean
   public MybatisPlusInterceptor mybatisPlusInterceptor() {
       MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
       interceptor.addInnerInterceptor(new PaginationInnerInterceptor());  // First
       interceptor.addInnerInterceptor(new MpSqlSafetyInnerInterceptor()); // Second
       return interceptor;
   }
   ```

3. **Verify SQL Guard enabled:**
   ```yaml
   sql-guard:
     enabled: true
   ```

## Performance Issues

### High Latency (P99 >50ms)

**Symptoms:**
- Slow query execution
- High P99 latency
- User complaints about performance

**Diagnosis:**

```java
@Scheduled(fixedRate = 60000)
public void logPerformance() {
    CacheStats stats = filter.getCacheStatistics();
    log.info("Cache hit rate: {}%", stats.getHitRate() * 100);
    log.info("Avg validation time: {}ms", getAvgValidationTime());
}
```

**Solutions:**

1. **Increase cache size:**
   ```yaml
   deduplication:
     cache-size: 10000  # Increase from 1000
   ```

2. **Increase TTL:**
   ```yaml
   deduplication:
     ttl-ms: 500  # Increase from 100
   ```

3. **Disable low-value rules:**
   ```yaml
   rules:
     missing-order-by:
       enabled: false
   ```

4. **Profile validation:**
   ```bash
   # Use async-profiler
   ./profiler.sh -d 60 -f flamegraph.html <pid>
   ```

See [Performance Guide](performance.md) for detailed tuning.

### Low Cache Hit Rate (<50%)

**Symptoms:**
- Cache hit rate <50%
- High validation overhead

**Diagnosis:**

```java
CacheStats stats = filter.getCacheStatistics();
log.info("Cache stats: hits={}, misses={}, rate={}%",
    stats.getHitCount(),
    stats.getMissCount(),
    stats.getHitRate() * 100
);
```

**Solutions:**

1. **Increase cache size:**
   ```yaml
   deduplication:
     cache-size: 5000  # 5x increase
   ```

2. **Analyze SQL diversity:**
   ```bash
   # Count unique SQL patterns
   grep "Validating SQL" application.log | \
     awk '{print $NF}' | sort | uniq | wc -l
   ```

3. **Increase TTL if appropriate:**
   ```yaml
   deduplication:
     ttl-ms: 500  # Increase if acceptable
   ```

### Memory Pressure

**Symptoms:**
- High heap usage
- Frequent GC pauses
- OutOfMemoryError

**Diagnosis:**

```bash
# Heap dump
jmap -dump:format=b,file=heap.bin <pid>

# Analyze with VisualVM or Eclipse MAT
```

**Solutions:**

1. **Decrease cache size:**
   ```yaml
   deduplication:
     cache-size: 500  # Decrease from 1000
   ```

2. **Decrease TTL:**
   ```yaml
   deduplication:
     ttl-ms: 50  # Decrease from 100
   ```

3. **Monitor thread count:**
   ```bash
   # Each thread has separate cache
   jstack <pid> | grep "Thread-" | wc -l
   ```

## Integration Issues

### MyBatis Interceptor Not Working

**Symptoms:**
- MyBatis queries not validated
- No interceptor logs

**Diagnosis:**

```java
@Autowired
private SqlSessionFactory sqlSessionFactory;

@PostConstruct
public void logInterceptors() {
    Configuration config = sqlSessionFactory.getConfiguration();
    log.info("Interceptors: {}", config.getInterceptors());
}
```

**Solutions:**

1. **Verify interceptor registered:**
   ```java
   @Bean
   public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
       SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
       factory.setDataSource(dataSource);
       
       // Add interceptor
       SqlSafetyInterceptor interceptor = new SqlSafetyInterceptor(
           validator, ViolationStrategy.BLOCK
       );
       factory.setPlugins(new Interceptor[]{interceptor});
       
       return factory.getObject();
   }
   ```

2. **Check MyBatis version:**
   ```xml
   <!-- Minimum version 3.4.6 -->
   <dependency>
       <groupId>org.mybatis</groupId>
       <artifactId>mybatis</artifactId>
       <version>3.5.13</version>
   </dependency>
   ```

### MyBatis-Plus Plugin Not Working

**Symptoms:**
- MyBatis-Plus queries not validated
- IPage pagination not detected

**Diagnosis:**

```java
@Autowired
private MybatisPlusInterceptor mybatisPlusInterceptor;

@PostConstruct
public void logInnerInterceptors() {
    log.info("Inner interceptors: {}", mybatisPlusInterceptor.getInterceptors());
}
```

**Solutions:**

1. **Verify interceptor order:**
   ```java
   @Bean
   public MybatisPlusInterceptor mybatisPlusInterceptor() {
       MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
       
       // PaginationInnerInterceptor MUST be first
       interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
       
       // MpSqlSafetyInnerInterceptor second
       interceptor.addInnerInterceptor(new MpSqlSafetyInnerInterceptor(
           validator, ViolationStrategy.BLOCK
       ));
       
       return interceptor;
   }
   ```

2. **Check MyBatis-Plus version:**
   ```xml
   <!-- Minimum version 3.4.0 -->
   <dependency>
       <groupId>com.baomidou</groupId>
       <artifactId>mybatis-plus-boot-starter</artifactId>
       <version>3.5.3</version>
   </dependency>
   ```

### JDBC Interceptor Not Working

**Symptoms:**
- JDBC queries not validated
- PreparedStatement not intercepted

**Diagnosis:**

```bash
# Check connection pool type
grep "dataSource" application.yml

# Check interceptor logs
grep "SqlSafetyFilter\|SqlSafetyProxy\|SqlSafetyListener" application.log
```

**Solutions:**

1. **Druid: Verify filter registered:**
   ```java
   @Bean
   public DataSource dataSource() {
       DruidDataSource dataSource = new DruidDataSource();
       // ... configuration ...
       
       // Add filter
       DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(
           validator, ViolationStrategy.BLOCK
       );
       dataSource.setProxyFilters(Arrays.asList(filter));
       
       return dataSource;
   }
   ```

2. **HikariCP: Verify proxy registered:**
   ```java
   @Bean
   public DataSource dataSource() {
       HikariDataSource hikari = new HikariDataSource();
       // ... configuration ...
       
       // Wrap with proxy
       return HikariSqlSafetyProxyFactory.wrap(
           hikari, validator, ViolationStrategy.BLOCK
       );
   }
   ```

3. **P6Spy: Verify spy.properties:**
   ```properties
   # src/main/resources/spy.properties
   modulelist=com.footstone.sqlguard.jdbc.p6spy.P6SpySqlSafetyModule
   ```

## How to Report Bugs

### Bug Report Checklist

1. **Search existing issues:** [GitHub Issues](https://github.com/footstone/sql-safety-guard/issues)

2. **Gather information:**
   - SQL Safety Guard version
   - Java version
   - MyBatis/MyBatis-Plus version
   - Spring Boot version (if applicable)
   - Database type and version
   - Connection pool type

3. **Create minimal reproduction:**
   ```java
   // Minimal test case
   @Test
   public void testIssue() {
       // SQL causing issue
       String sql = "DELETE FROM users WHERE id = ?";
       
       // Configuration
       SqlGuardConfig config = new SqlGuardConfig();
       // ...
       
       // Expected vs actual behavior
       // ...
   }
   ```

4. **Include logs:**
   ```yaml
   logging:
     level:
       com.footstone.sqlguard: DEBUG
   ```

5. **Submit issue:** Use GitHub issue template

### Security Issues

**Do NOT report security vulnerabilities publicly.**

Email: security@footstone.com

## Next Steps

- **[FAQ](faq.md)** - Common questions and answers
- **[Performance Guide](performance.md)** - Optimization strategies
- **[Configuration Reference](configuration-reference.md)** - Complete property documentation

---

**Still stuck?** Contact support@footstone.com







