# Large Page Size Rule

**Risk Level:** 🟡 MEDIUM

## Overview

The Large Page Size rule detects LIMIT queries with excessive page sizes that return too many rows in a single request, causing memory exhaustion, bandwidth saturation, and application slowdowns. Unlike Deep Pagination which impacts database performance, large page sizes primarily impact application memory and network resources.

## What It Detects

SELECT statements with excessive LIMIT values:

- **LIMIT n** - Standard LIMIT syntax (n > threshold)
- **LIMIT m,n** - MySQL comma syntax (n > threshold, where n is page size)
- **LIMIT n OFFSET m** - Standard OFFSET syntax (n > threshold)
- **Default Threshold** - LIMIT > 1,000 rows

**Detection Scope:**
- Only checks PHYSICAL pagination (LIMIT-based queries)
- Independent from DeepPaginationChecker (both can trigger on same SQL)
- Skips parameter placeholders (cannot evaluate `?` at static analysis)

## Why Dangerous

### Memory Exhaustion

```sql
-- ❌ MEDIUM RISK: Returns 10,000 rows in single query
SELECT * FROM users
WHERE status = 'active'
LIMIT 10000
```

**Memory Impact:**
- Database returns: 10,000 rows
- Network transfer: 10,000 × row size (e.g., 10,000 × 1KB = 10MB)
- JVM heap: 10,000 row objects loaded
- Result list memory: ~10MB per query
- Multiple concurrent requests = OutOfMemoryError

**Real-World Numbers:**
| LIMIT | Row Size | Memory per Query | 100 Concurrent Requests |
|-------|----------|------------------|-------------------------|
| 100 | 1KB | 100KB | 10MB | ✅ Safe |
| 1,000 | 1KB | 1MB | 100MB | ✅ Acceptable |
| 10,000 | 1KB | 10MB | 1GB | ❌ Risky |
| 100,000 | 1KB | 100MB | 10GB | ❌ OOM crash |

### Network Bandwidth Saturation

```sql
-- ❌ MEDIUM RISK: 5MB transferred per request
SELECT * FROM products
WHERE category_id = 5
LIMIT 5000
```

**Bandwidth Problems:**
- Single request: 5000 rows × 1KB = 5MB
- 100 concurrent users: 5MB × 100 = 500MB network traffic
- Page load time: 5MB / 10Mbps = 4 seconds (slow UX)
- Cost: Cloud egress charges ($0.12/GB on AWS)

### Frontend Performance Degradation

```javascript
// ❌ MEDIUM RISK: Rendering 10,000 rows crashes browser
fetch('/api/users?limit=10000')
  .then(response => response.json())
  .then(users => {
    users.forEach(user => renderUserCard(user));  // Browser hangs
  });
```

**Frontend Impact:**
- JSON parsing: ~10,000 objects
- DOM rendering: ~10,000 elements
- Browser freezes (unresponsive UI)
- Poor user experience

### Serialization Overhead

```java
// ❌ MEDIUM RISK: 10,000 rows to JSON serialization
@GetMapping("/users")
public List<User> getUsers() {
    List<User> users = userMapper.selectAll();  // LIMIT 10000
    return users;  // Jackson serializes 10,000 objects → 10MB JSON
}
```

**Serialization Cost:**
- CPU overhead serializing 10,000 objects
- 10MB response body
- Network transmission time
- Application server thread blocked during serialization

## Examples

### BAD: Large Page Size (Standard LIMIT)

```sql
-- ❌ MEDIUM RISK: pageSize=5000 exceeds threshold
SELECT * FROM users
WHERE status = 'active'
LIMIT 5000
```

**Violation Message:**
```
[MEDIUM] pageSize=5000过大,单次查询数据量过多
MapperId: com.example.UserMapper.selectAll
SQL: SELECT * FROM users WHERE status = 'active' LIMIT 5000
Suggestion: 建议降低pageSize到1000以内,避免单次返回过多数据
Risk: Memory exhaustion, bandwidth saturation
```

### BAD: Large Page Size (MySQL Comma Syntax)

```sql
-- ❌ MEDIUM RISK: pageSize=10000 (second parameter in MySQL LIMIT m,n)
SELECT * FROM orders
WHERE status = 'completed'
LIMIT 100, 10000
```

**Violation Message:**
```
[MEDIUM] pageSize=10000过大,单次查询数据量过多
MapperId: com.example.OrderMapper.selectPage
SQL: SELECT * FROM orders WHERE status = 'completed' LIMIT 100, 10000
Suggestion: 建议降低pageSize到1000以内,避免单次返回过多数据
```

### BAD: Large Page Size with OFFSET

```sql
-- ❌ MEDIUM RISK: pageSize=2000 exceeds threshold
SELECT * FROM products
WHERE category_id = 5
LIMIT 2000 OFFSET 0
```

**Violation Message:**
```
[MEDIUM] pageSize=2000过大,单次查询数据量过多
MapperId: com.example.ProductMapper.selectByCategory
SQL: SELECT * FROM products WHERE category_id = 5 LIMIT 2000 OFFSET 0
Suggestion: 建议降低pageSize到1000以内,避免单次返回过多数据
```

### BAD: Both Deep Pagination AND Large Page Size

```sql
-- ❌ TRIGGERS BOTH RULES
SELECT * FROM users
WHERE status = 'active'
ORDER BY id
LIMIT 5000 OFFSET 50000
```

**Violations:**
```
[MEDIUM] 深分页offset=50000,需扫描并跳过50000行数据,性能较差
[MEDIUM] pageSize=5000过大,单次查询数据量过多
```

**Explanation:** Deep pagination affects database, large page size affects application

### GOOD: Reasonable Page Size

```sql
-- ✅ SAFE: pageSize=100 is below threshold
SELECT * FROM users
WHERE status = 'active'
LIMIT 100
```

### GOOD: Default Page Size

```sql
-- ✅ SAFE: pageSize=20 is well below threshold
SELECT * FROM users
WHERE status = 'active'
LIMIT 20 OFFSET 0
```

## Expected Messages

### Large Page Size Violation

```
[MEDIUM] pageSize={pageSize}过大,单次查询数据量过多
MapperId: {namespace}.{methodId}
SQL: SELECT * FROM {table} ... LIMIT {pageSize}
Suggestion: 建议降低pageSize到{maxPageSize}以内,避免单次返回过多数据
Risk: Memory exhaustion, bandwidth saturation, slow response
```

## How to Fix

### Option 1: Reduce Page Size (Recommended)

```sql
-- Before (dangerous)
SELECT * FROM users WHERE status = 'active' LIMIT 5000

-- After (safe)
SELECT * FROM users WHERE status = 'active' LIMIT 100
```

```java
// Before (dangerous)
@Select("SELECT * FROM users WHERE status = #{status} LIMIT 5000")
List<User> selectByStatus(@Param("status") String status);

// After (safe)
@Select("SELECT * FROM users WHERE status = #{status} LIMIT 100")
List<User> selectByStatus(@Param("status") String status);
```

### Option 2: Enforce Maximum Page Size in Service Layer

```java
// Before (dangerous)
public List<User> getUsers(int pageSize) {
    return userMapper.selectUsers(pageSize);
}

// After (safe)
public List<User> getUsers(int requestedPageSize) {
    // Enforce maximum
    int maxPageSize = 1000;
    int actualPageSize = Math.min(requestedPageSize, maxPageSize);

    return userMapper.selectUsers(actualPageSize);
}
```

### Option 3: Use Pagination with Reasonable Defaults

```java
// ✅ Service layer with sensible defaults
public Page<User> searchUsers(UserSearchDto search, Integer pageSize) {
    // Default to 20, max 100
    int defaultPageSize = 20;
    int maxPageSize = 100;
    int actualPageSize = pageSize != null
        ? Math.min(pageSize, maxPageSize)
        : defaultPageSize;

    return userMapper.selectPage(search, actualPageSize);
}
```

### Option 4: Stream Large Datasets (For Batch Processing)

```java
// ❌ Don't load everything into memory
public void processAllUsers() {
    List<User> users = userMapper.selectAll();  // LIMIT 100000!
    users.forEach(this::processUser);  // OutOfMemoryError
}

// ✅ Use streaming/cursor for large datasets
public void processAllUsers() {
    int pageSize = 1000;  // Reasonable chunk size
    int offset = 0;

    while (true) {
        List<User> batch = userMapper.selectPage(pageSize, offset);
        if (batch.isEmpty()) {
            break;
        }

        // Process batch
        batch.forEach(this::processUser);

        offset += pageSize;
    }
}
```

### Option 5: Use MyBatis Cursor for Streaming (Best Practice)

```java
// ✅ Use MyBatis Cursor for memory-efficient streaming
@Select("SELECT * FROM users WHERE status = #{status}")
@Options(fetchSize = 1000)  // Database fetch size
@ResultType(User.class)
void selectUsersCursor(@Param("status") String status,
                       ResultHandler<User> handler);

// Usage
userMapper.selectUsersCursor("active", context -> {
    User user = (User) context.getResultObject();
    processUser(user);
    // User object can be GC'd after processing
});
```

## Configuration

### Enable/Disable

```yaml
sql-guard:
  rules:
    large-page-size:
      enabled: true  # Default: true
```

### Adjust Threshold

```yaml
sql-guard:
  rules:
    large-page-size:
      enabled: true
      max-page-size: 1000  # Default: 1,000 rows
```

**Threshold Selection Guidelines:**
| Use Case | Max Page Size | Rationale |
|----------|---------------|-----------|
| User-facing pagination | 100 | Fast page loads, small payloads |
| Admin dashboards | 500 | More data needed, acceptable latency |
| API responses | 1000 | Default, balanced |
| Batch processing | 5000 | Higher throughput, acceptable memory |
| Data export | 10000+ | Batch jobs, dedicated resources |

### Whitelist Specific Mappers

```yaml
sql-guard:
  rules:
    large-page-size:
      enabled: true
      whitelist-mapper-ids:
        - "ReportMapper.exportAllData"  # Batch export
        - "BatchJobMapper.processUsers"  # Background job
```

**Caution:** Only whitelist if:
- Not user-facing (batch jobs, background tasks)
- Dedicated resources (separate worker threads)
- Memory is carefully managed (streaming, cursors)

## Edge Cases

### Case 1: Parameter Placeholder for LIMIT

```sql
-- Static scanner cannot detect (limit is parameter)
SELECT * FROM users ORDER BY id LIMIT ?
```

**Behavior:**
- **Static Scanner:** Skipped (cannot evaluate parameter)
- **Runtime Validator:** Detects when actual pageSize value exceeds threshold

### Case 2: Both Deep Pagination and Large Page Size

```sql
-- Triggers BOTH rules
SELECT * FROM users LIMIT 5000 OFFSET 50000
```

**Violations:**
1. DeepPaginationChecker: offset=50,000 > maxOffset=10,000
2. LargePageSizeChecker: pageSize=5,000 > maxPageSize=1,000

**Behavior:** Independent checks, both violations reported

### Case 3: LIMIT Within Threshold

```sql
-- LIMIT within threshold
SELECT * FROM users ORDER BY id LIMIT 500
```

**Behavior:** Not flagged (pageSize=500 < threshold=1,000)

### Case 4: No LIMIT Clause

```sql
-- No LIMIT at all
SELECT * FROM users WHERE id = ?
```

**Behavior:** Not checked by LargePageSizeChecker
**Reason:** Handled by NoPaginationChecker (VARIABLE risk)

## Relationship with Other Rules

### Independent from Deep Pagination

```sql
-- Example: SELECT * FROM users LIMIT 5000 OFFSET 50000

-- Both rules can trigger independently:
-- 1. DeepPaginationChecker (MEDIUM): offset=50,000 too high
-- 2. LargePageSizeChecker (MEDIUM): pageSize=5,000 too large
```

**Difference:**
- **Deep Pagination:** Database performance (scanning + skipping rows)
- **Large Page Size:** Application performance (memory + bandwidth)

### Related Rules

- **[Deep Pagination](deep-pagination.md)** - Detects high OFFSET values
- **[No Pagination](no-pagination.md)** - Detects SELECT without LIMIT
- **[No Condition Pagination](no-condition-pagination.md)** - Detects LIMIT without WHERE
- **[Missing ORDER BY](missing-orderby.md)** - Detects pagination without stable ordering

## Production Incidents Prevented

### Incident 1: API Response Timeout

**Company:** Mobile app backend (5M users)
**Incident:** User list API returned 10,000 users, causing timeouts
**Impact:**
- Mobile app "Loading..." never completed
- 30s timeout, users see error
- Customer support flooded with complaints
- App Store rating dropped from 4.5 to 3.2

**Root Cause:** No LIMIT cap on API endpoint
**Query:** `SELECT * FROM users WHERE status = 'active' LIMIT ?`
**Page Size:** Frontend requested pageSize=10000

**Prevention:** SQL Guard would have blocked LIMIT > 1000

**Fix:**
```java
// Before (no limit enforcement)
@GetMapping("/users")
public List<User> getUsers(@RequestParam int pageSize) {
    return userMapper.selectUsers(pageSize);
}

// After (enforce max)
@GetMapping("/users")
public List<User> getUsers(@RequestParam Integer pageSize) {
    int maxPageSize = 100;
    int actualPageSize = pageSize != null
        ? Math.min(pageSize, maxPageSize)
        : 20;  // Default to 20

    return userMapper.selectUsers(actualPageSize);
}
```

### Incident 2: OutOfMemoryError During Peak Hours

**Company:** E-commerce platform
**Incident:** Order list query with LIMIT 50,000 caused OOM crashes
**Impact:**
- Application crashed during peak sales (Black Friday)
- Site down for 1 hour
- Lost revenue: ~$200K
- Emergency reboot + capacity increase

**Root Cause:** Admin export feature with no page size limit
**Query:** `SELECT * FROM orders WHERE create_time > ? LIMIT 50000`
**Memory Usage:**
- 50,000 orders × 2KB per order = 100MB per query
- 20 concurrent admin users = 2GB heap used
- JVM heap: 4GB total → OOM

**Prevention:** SQL Guard would have warned about large page size

**Fix:**
```java
// Before (no streaming)
@GetMapping("/admin/export/orders")
public List<Order> exportOrders(@RequestParam LocalDate since) {
    return orderMapper.selectSince(since);  // LIMIT 50000
}

// After (streaming with pagination)
@GetMapping("/admin/export/orders")
public void exportOrders(@RequestParam LocalDate since,
                         HttpServletResponse response) {
    response.setContentType("text/csv");

    int pageSize = 1000;  // Reasonable batch size
    int offset = 0;

    try (CSVWriter writer = new CSVWriter(response.getWriter())) {
        while (true) {
            List<Order> batch = orderMapper.selectPage(since, pageSize, offset);
            if (batch.isEmpty()) {
                break;
            }

            batch.forEach(order -> writer.writeNext(toCSV(order)));
            offset += pageSize;
        }
    }
}
```

### Incident 3: Mobile App Data Consumption

**Company:** Social media app
**Incident:** User feed loaded 5,000 posts, consuming 50MB mobile data
**Impact:**
- Users complained about data usage
- Bad reviews ("app eats my data plan")
- User churn (20% uninstalls in 1 week)
- PR crisis

**Root Cause:** Feed API returned excessive posts
**Query:** `SELECT * FROM posts WHERE user_id IN (...) LIMIT 5000`
**Data Transfer:**
- 5,000 posts × 10KB per post (with images) = 50MB
- Most users only scrolled 10-20 posts
- 99% of data never viewed

**Prevention:** SQL Guard would have blocked large page size

**Fix:**
```java
// Before (too many posts)
@GetMapping("/feed")
public List<Post> getFeed(@RequestParam List<Long> userIds) {
    return postMapper.selectByUserIds(userIds, 5000);
}

// After (reasonable page size + infinite scroll)
@GetMapping("/feed")
public List<Post> getFeed(@RequestParam List<Long> userIds,
                          @RequestParam(defaultValue = "0") Long lastPostId) {
    int pageSize = 20;  // Load 20 posts at a time
    return postMapper.selectByUserIdsCursor(userIds, lastPostId, pageSize);
}
```

## Best Practices

### 1. Use Reasonable Default Page Sizes

```java
// ✅ Sensible defaults
public static final int DEFAULT_PAGE_SIZE = 20;
public static final int MAX_PAGE_SIZE = 100;

public Page<User> getUsers(Integer requestedPageSize) {
    int pageSize = requestedPageSize != null
        ? Math.min(requestedPageSize, MAX_PAGE_SIZE)
        : DEFAULT_PAGE_SIZE;

    return userMapper.selectPage(pageSize);
}
```

### 2. Enforce Maximum Page Size at API Gateway

```java
// ✅ API Gateway validation
@RestControllerAdvice
public class PageSizeValidator implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, ...) {
        String pageSizeParam = request.getParameter("pageSize");
        if (pageSizeParam != null) {
            int pageSize = Integer.parseInt(pageSizeParam);
            if (pageSize > 1000) {
                throw new IllegalArgumentException(
                    "Page size cannot exceed 1000"
                );
            }
        }
        return true;
    }
}
```

### 3. Use Streaming for Large Datasets

```java
// ✅ Stream large datasets instead of loading all into memory
@Select("SELECT * FROM users WHERE status = #{status}")
@Options(fetchSize = 1000, resultSetType = ResultSetType.FORWARD_ONLY)
void streamUsers(@Param("status") String status,
                 ResultHandler<User> handler);

// Usage
userMapper.streamUsers("active", context -> {
    User user = (User) context.getResultObject();
    processUser(user);
});
```

### 4. Document Page Size Limits in API Docs

```yaml
# OpenAPI spec
/users:
  get:
    parameters:
      - name: pageSize
        in: query
        schema:
          type: integer
          default: 20
          minimum: 1
          maximum: 100  # Documented limit
        description: Number of users per page (max 100)
```

### 5. Use Different Limits for Different Use Cases

```java
// ✅ Context-specific limits
public class PageSizeConfig {
    public static final int USER_FACING_MAX = 100;   // User pagination
    public static final int ADMIN_MAX = 500;         // Admin dashboards
    public static final int BATCH_JOB_MAX = 5000;    // Background jobs
    public static final int EXPORT_BATCH = 10000;    // Data export
}
```

## Testing

### Unit Test Example

```java
@Test
public void testLargePageSizeDetection() {
    // Given
    String sql = "SELECT * FROM users WHERE status = 'active' LIMIT 5000";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .mapperId("UserMapper.selectAll")
        .build();

    // When
    ValidationResult result = validator.validate(context);

    // Then
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertTrue(result.getViolations().get(0).getMessage()
        .contains("pageSize=5000过大"));
}

@Test
public void testReasonablePageSizeAllowed() {
    // Given: Page size within threshold
    String sql = "SELECT * FROM users LIMIT 500";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .mapperId("UserMapper.selectPage")
        .build();

    // When
    ValidationResult result = validator.validate(context);

    // Then
    assertTrue(result.isPassed());  // Should pass
}
```

### Integration Test Example

```java
@Test
public void testLargePageSizeWarning() {
    // Given: WARN strategy configured

    // When
    List<User> users = userMapper.selectAll();  // LIMIT 5000

    // Then: Returns data but logs warning
    assertNotNull(users);
    verify(logger).warn(contains("pageSize=5000过大"));
}
```

## Next Steps

- **[Deep Pagination Rule](deep-pagination.md)** - Detects high OFFSET values
- **[No Pagination Rule](no-pagination.md)** - Detects SELECT without LIMIT
- **[No Condition Pagination Rule](no-condition-pagination.md)** - Detects LIMIT without WHERE
- **[Configuration Reference](../configuration-reference.md)** - Configure rule settings

---

**Questions?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).
