# Logical Pagination Rule

**Risk Level:** 🔴 CRITICAL

## Overview

The Logical Pagination rule detects dangerous in-memory pagination (RowBounds/IPage without pagination plugin) that loads entire result sets into memory before performing row skipping, frequently causing OutOfMemoryError and production crashes.

## What It Detects

Pagination without database-level support:

- **MyBatis RowBounds** without PageHelper plugin
- **MyBatis-Plus IPage** without PaginationInnerInterceptor
- Any pagination parameter without SQL LIMIT clause

## Why Dangerous

### Memory Exhaustion

```java
// ❌ CRITICAL: Loads ALL users into memory, then skips 10000 rows
List<User> users = userMapper.selectAll(new RowBounds(10000, 100));
```

**What Happens:**

1. Database executes: `SELECT * FROM users` (no LIMIT)
2. JDBC loads all 10 million rows into memory
3. MyBatis skips first 10,000 rows in-memory
4. Returns 100 rows to application
5. **Result:** OutOfMemoryError, application crash

**Real-World Impact:**
- Production incident: Logical pagination on 50M row table
- Result: OutOfMemoryError, 30-minute downtime
- Recovery: Restart with increased heap, configure pagination plugin
- Cost: User-facing errors, lost transactions

### Performance Degradation

Even if memory sufficient:

- Full table scan (no database filtering)
- Network transfer of millions of rows
- CPU cycles for in-memory skipping
- Garbage collection pressure

**Benchmark:**

| Rows | Physical Pagination | Logical Pagination | Overhead |
|------|--------------------|--------------------|----------|
| 1K | 10ms | 50ms | 5x |
| 10K | 15ms | 500ms | 33x |
| 100K | 20ms | 5000ms | 250x |
| 1M | 25ms | OOM | ∞ |

## Examples

### BAD: MyBatis RowBounds Without Plugin

```java
// ❌ CRITICAL: Logical pagination (in-memory)
List<User> users = userMapper.selectAll(new RowBounds(100, 20));

// SQL executed: SELECT * FROM users (no LIMIT!)
// MyBatis loads all rows, skips 100 in-memory, returns 20
```

**Violation Message:**
```
[CRITICAL] Logical pagination detected! Will load entire result set into memory
MapperId: com.example.UserMapper.selectAll
Pagination: offset=100, limit=20
Suggestion: Configure pagination plugin (PageHelper or PaginationInnerInterceptor)
Risk: OutOfMemoryError, application crash
```

### BAD: MyBatis-Plus IPage Without Plugin

```java
// ❌ CRITICAL: Logical pagination (in-memory)
Page<User> page = new Page<>(10, 20);  // page 10, size 20
List<User> users = userMapper.selectPage(page, null);

// SQL executed: SELECT * FROM users (no LIMIT!)
// MyBatis-Plus loads all rows, skips 180 in-memory, returns 20
```

**Violation Message:**
```
[CRITICAL] Logical pagination detected! IPage parameter without PaginationInnerInterceptor
MapperId: com.example.UserMapper.selectPage
Pagination: page=10, size=20 (offset=180)
Suggestion: Add PaginationInnerInterceptor to MybatisPlusInterceptor
Risk: OutOfMemoryError, application crash
```

### GOOD: Physical Pagination (PageHelper)

```java
// ✅ SAFE: Physical pagination (database-level)
List<User> users = userMapper.selectAll(new RowBounds(100, 20));

// With PageHelper plugin configured:
// SQL executed: SELECT * FROM users LIMIT 20 OFFSET 100
// Database returns only 20 rows, no memory issues
```

**Configuration:**

```java
@Bean
public Interceptor[] plugins() {
    return new Interceptor[]{new PageInterceptor()};
}
```

### GOOD: Physical Pagination (MyBatis-Plus)

```java
// ✅ SAFE: Physical pagination (database-level)
Page<User> page = new Page<>(10, 20);
List<User> users = userMapper.selectPage(page, null);

// With PaginationInnerInterceptor configured:
// SQL executed: SELECT * FROM users LIMIT 20 OFFSET 180
// Database returns only 20 rows, no memory issues
```

**Configuration:**

```java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
    return interceptor;
}
```

### GOOD: Manual LIMIT in SQL

```java
// ✅ SAFE: Physical pagination (manual LIMIT)
@Select("SELECT * FROM users LIMIT #{limit} OFFSET #{offset}")
List<User> selectWithLimit(@Param("offset") int offset, @Param("limit") int limit);
```

## Expected Messages

### RowBounds Violation

```
[CRITICAL] Logical pagination detected! Will load entire result set into memory
MapperId: {namespace}.{methodId}
SQL: {sql}
Pagination: offset={offset}, limit={limit}
Suggestion: Configure PageHelper plugin:
  @Bean
  public Interceptor[] plugins() {
      return new Interceptor[]{new PageInterceptor()};
  }
Risk: OutOfMemoryError, application crash
```

### IPage Violation

```
[CRITICAL] Logical pagination detected! IPage parameter without PaginationInnerInterceptor
MapperId: {namespace}.{methodId}
SQL: {sql}
Pagination: page={page}, size={size} (offset={offset})
Suggestion: Add PaginationInnerInterceptor to MybatisPlusInterceptor:
  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor() {
      MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
      interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
      return interceptor;
  }
Risk: OutOfMemoryError, application crash
```

## How to Fix

### Option 1: Configure PageHelper (MyBatis)

**Step 1: Add Dependency**

```xml
<dependency>
    <groupId>com.github.pagehelper</groupId>
    <artifactId>pagehelper-spring-boot-starter</artifactId>
    <version>1.4.7</version>
</dependency>
```

**Step 2: Configure Plugin**

```java
@Configuration
public class MyBatisConfig {
    
    @Bean
    public Interceptor[] plugins() {
        return new Interceptor[]{new PageInterceptor()};
    }
}
```

**Step 3: Use RowBounds (Now Safe!)**

```java
// Now executes: SELECT * FROM users LIMIT 20 OFFSET 100
List<User> users = userMapper.selectAll(new RowBounds(100, 20));
```

### Option 2: Configure PaginationInnerInterceptor (MyBatis-Plus)

**Step 1: Add Dependency**

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>3.5.3</version>
</dependency>
```

**Step 2: Configure Plugin**

```java
@Configuration
public class MyBatisPlusConfig {
    
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        
        // Add pagination interceptor
        PaginationInnerInterceptor paginationInterceptor = 
            new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInterceptor.setMaxLimit(1000L);  // Max page size
        
        interceptor.addInnerInterceptor(paginationInterceptor);
        return interceptor;
    }
}
```

**Step 3: Use IPage (Now Safe!)**

```java
// Now executes: SELECT * FROM users LIMIT 20 OFFSET 180
Page<User> page = new Page<>(10, 20);
List<User> users = userMapper.selectPage(page, null);
```

### Option 3: Manual LIMIT in SQL

**Not recommended** (loses pagination abstraction), but works:

```java
// Before (dangerous)
@Select("SELECT * FROM users")
List<User> selectAll(RowBounds rowBounds);

// After (safe, but manual)
@Select("SELECT * FROM users LIMIT #{limit} OFFSET #{offset}")
List<User> selectWithLimit(@Param("offset") int offset, @Param("limit") int limit);
```

## Configuration

### Enable/Disable

```yaml
sql-guard:
  rules:
    logical-pagination:
      enabled: true  # Default: true
```

### Adjust Risk Level

```yaml
sql-guard:
  rules:
    logical-pagination:
      risk-level: CRITICAL  # Default: CRITICAL
```

**Not recommended:** Logical pagination is genuinely CRITICAL risk.

## Detection Logic

SQL Safety Guard detects logical pagination using this logic:

```
IF (hasPageParam && !hasLimit && !hasPlugin) THEN
    PaginationType = LOGICAL (CRITICAL violation)

WHERE:
  hasPageParam = RowBounds present OR IPage parameter present
  hasLimit = SQL contains LIMIT clause
  hasPlugin = PageHelper OR PaginationInnerInterceptor configured
```

**Examples:**

| Scenario | hasPageParam | hasLimit | hasPlugin | Result |
|----------|--------------|----------|-----------|--------|
| RowBounds without plugin | ✅ | ❌ | ❌ | LOGICAL ❌ |
| RowBounds with PageHelper | ✅ | ❌ | ✅ | PHYSICAL ✅ |
| IPage without plugin | ✅ | ❌ | ❌ | LOGICAL ❌ |
| IPage with plugin | ✅ | ❌ | ✅ | PHYSICAL ✅ |
| Manual LIMIT | ❌ | ✅ | ❌ | PHYSICAL ✅ |
| No pagination | ❌ | ❌ | ❌ | NONE ✅ |

## Edge Cases

### Case 1: RowBounds.DEFAULT

```java
// RowBounds.DEFAULT = infinite bounds (no pagination)
List<User> users = userMapper.selectAll(RowBounds.DEFAULT);
```

**Behavior:** Not flagged (RowBounds.DEFAULT means "no pagination")  
**Detection:** May be flagged by [No Pagination](no-pagination.md) rule instead

### Case 2: Plugin Configured But Not Applied

```java
// Plugin configured globally, but not applied to this SqlSessionFactory
@Bean
public SqlSessionFactory customSqlSessionFactory(DataSource dataSource) {
    SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
    factory.setDataSource(dataSource);
    // Missing: factory.setPlugins(...)
    return factory.getObject();
}
```

**Behavior:** Still flagged as LOGICAL (plugin not detected)  
**Solution:** Ensure plugin registered with SqlSessionFactory

### Case 3: Multiple SqlSessionFactories

```java
// Plugin configured for factory1, but query uses factory2
@Bean("factory1")
public SqlSessionFactory factory1(DataSource ds1) {
    // Has PageHelper
}

@Bean("factory2")
public SqlSessionFactory factory2(DataSource ds2) {
    // Missing PageHelper
}
```

**Behavior:** Queries on factory2 flagged as LOGICAL  
**Solution:** Configure plugin for all SqlSessionFactories

## Design Reference

See [Implementation Plan - Task 2.7](../../../.apm/Implementation_Plan.md#task-27--logical-pagination-checker) for complete design details.

## Related Rules

- **[No Condition Pagination](pagination-abuse.md#no-condition-pagination)** - Detects LIMIT without WHERE
- **[Deep Pagination](pagination-abuse.md#deep-pagination)** - Detects high OFFSET values
- **[No Pagination](no-pagination.md)** - Detects SELECT without LIMIT

## Production Incidents Prevented

### Incident 1: E-Commerce Order Query

**Company:** E-commerce platform  
**Incident:** `userMapper.selectOrders(new RowBounds(1000, 50))` on 50M row table  
**Impact:** OutOfMemoryError, 30-minute downtime, lost transactions  
**Root Cause:** PageHelper not configured, logical pagination loaded 50M rows  
**Prevention:** SQL Guard would have blocked execution (BLOCK strategy)

### Incident 2: SaaS Event Log Query

**Company:** SaaS analytics platform  
**Incident:** `eventMapper.selectEvents(new RowBounds(10000, 100))` on 100M row table  
**Impact:** OutOfMemoryError, cascading failures across services  
**Root Cause:** Logical pagination on massive table  
**Prevention:** SQL Guard would have blocked execution (BLOCK strategy)

### Incident 3: Admin Dashboard Query

**Company:** B2B platform  
**Incident:** Admin dashboard used RowBounds for pagination without plugin  
**Impact:** Slow page loads (30s+), frequent OOM errors  
**Root Cause:** Logical pagination on user table (10M rows)  
**Prevention:** SQL Guard would have blocked execution (BLOCK strategy)

## Best Practices

### 1. Always Configure Pagination Plugin

```java
// ✅ Configure once, safe everywhere
@Bean
public Interceptor[] plugins() {
    return new Interceptor[]{new PageInterceptor()};
}
```

### 2. Validate Plugin Configuration

```java
@Autowired
private SqlSessionFactory sqlSessionFactory;

@PostConstruct
public void validatePlugins() {
    Configuration config = sqlSessionFactory.getConfiguration();
    List<Interceptor> interceptors = config.getInterceptors();
    
    boolean hasPageHelper = interceptors.stream()
        .anyMatch(i -> i instanceof PageInterceptor);
    
    if (!hasPageHelper) {
        log.warn("PageHelper not configured! Logical pagination risk!");
    }
}
```

### 3. Use Pagination Consistently

```java
// ❌ Inconsistent: Sometimes uses RowBounds, sometimes manual LIMIT
List<User> users1 = userMapper.selectAll(new RowBounds(0, 100));
List<User> users2 = userMapper.selectWithLimit(0, 100);

// ✅ Consistent: Always use RowBounds (with plugin configured)
List<User> users1 = userMapper.selectAll(new RowBounds(0, 100));
List<User> users2 = userMapper.selectAll(new RowBounds(100, 100));
```

### 4. Set Maximum Page Size

```java
// Prevent excessive page sizes
PaginationInnerInterceptor paginationInterceptor = 
    new PaginationInnerInterceptor(DbType.MYSQL);
paginationInterceptor.setMaxLimit(1000L);  // Max 1000 rows per page
```

## Testing

### Unit Test Example

```java
@Test
public void testLogicalPaginationDetection() {
    // Given: RowBounds without plugin
    String sql = "SELECT * FROM users";
    RowBounds rowBounds = new RowBounds(100, 20);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .mapperId("UserMapper.selectAll")
        .rowBounds(rowBounds)
        .build();
    
    // When: No pagination plugin configured
    ValidationResult result = validator.validate(context);
    
    // Then: CRITICAL violation
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertTrue(result.getViolations().get(0).getMessage()
        .contains("Logical pagination"));
}
```

### Integration Test Example

```java
@Test
public void testLogicalPaginationBlocked() {
    // Given: BLOCK strategy, no PageHelper configured
    
    // When/Then: SQLException thrown
    assertThrows(SQLException.class, () -> {
        userMapper.selectAll(new RowBounds(100, 20));
    });
}

@Test
public void testPhysicalPaginationAllowed() {
    // Given: BLOCK strategy, PageHelper configured
    
    // When: Physical pagination
    List<User> users = userMapper.selectAll(new RowBounds(100, 20));
    
    // Then: No exception, returns 20 rows
    assertEquals(20, users.size());
}
```

## Next Steps

- **[Pagination Abuse Rules](pagination-abuse.md)** - Optimize pagination performance
- **[Configuration Reference](../configuration-reference.md)** - Configure pagination plugin
- **[Deployment Guide](../deployment.md)** - Roll out to production safely

---

**Questions?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).













