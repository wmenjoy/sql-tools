# Deep Pagination Rule

**Risk Level:** 🟡 MEDIUM

## Overview

The Deep Pagination rule detects LIMIT queries with excessive OFFSET values that cause severe performance degradation. Deep pagination forces the database to scan and discard large numbers of rows before returning results, with query time increasing linearly as users navigate to higher page numbers.

## What It Detects

SELECT statements with high OFFSET values in pagination:

- **LIMIT n OFFSET m** - Standard OFFSET syntax (m > threshold)
- **LIMIT m,n** - MySQL comma syntax (m > threshold)
- **Default Threshold** - OFFSET > 10,000 rows

**Detection Scope:**
- Only checks PHYSICAL pagination (LIMIT-based queries)
- Skips LOGICAL pagination (RowBounds, IPage without pagination plugin)
- Integrates with NoConditionPaginationChecker (early-return mechanism)

## Why Dangerous

### Linear Performance Degradation

```sql
-- ❌ MEDIUM RISK: Scans 100,020 rows, returns 20 rows
SELECT * FROM users
WHERE status = 'active'
ORDER BY id
LIMIT 20 OFFSET 100000
```

**Performance Impact:**
- Database scans 100,020 rows (offset + limit)
- Discards first 100,000 rows
- Returns only 20 rows
- Query time = O(offset) - increases linearly with offset

**Real-World Numbers:**
| Offset | Rows Scanned | Query Time | User Experience |
|--------|--------------|------------|-----------------|
| 0 | 20 | 10ms | ✅ Fast |
| 1,000 | 1,020 | 50ms | ✅ Acceptable |
| 10,000 | 10,020 | 500ms | ⚠️ Slow |
| 100,000 | 100,020 | 5,000ms (5s) | ❌ Timeout |
| 1,000,000 | 1,000,020 | 50,000ms (50s) | ❌ Failure |

### Resource Exhaustion

```sql
-- ❌ CRITICAL: User navigates to page 5000 (pageSize=20)
-- OFFSET = 5000 * 20 = 100,000
SELECT * FROM orders
WHERE status = 'completed'
ORDER BY create_time DESC
LIMIT 20 OFFSET 100000
```

**Problems:**
- Database CPU spikes
- Buffer pool thrashing
- Disk I/O saturation
- Connection pool depletion
- Impacts other queries on same database

### Nobody Uses High Page Numbers (But They Break Your Site)

```sql
-- ❌ Real scenario: Search engine crawler or malicious bot
-- Crawls pages 1-10,000 to index all products
SELECT * FROM products
WHERE category_id = 5
ORDER BY price
LIMIT 100 OFFSET ?  -- Bot iterates offset: 0, 100, 200, ... 999900
```

**Impact:**
- Bot generates 10,000 queries
- Last query (offset=999,900) scans 1M rows
- Database crashes under load
- Site becomes unavailable
- Real users cannot access site

## Examples

### BAD: High OFFSET Value (Standard Syntax)

```sql
-- ❌ MEDIUM RISK: offset=50,000 exceeds threshold
SELECT * FROM users
WHERE status = 'active'
ORDER BY id
LIMIT 20 OFFSET 50000
```

**Violation Message:**
```
[MEDIUM] 深分页offset=50000,需扫描并跳过50000行数据,性能较差
MapperId: com.example.UserMapper.selectPage
SQL: SELECT * FROM users WHERE status = 'active' ORDER BY id LIMIT 20 OFFSET 50000
Suggestion: 建议使用游标分页(WHERE id > lastId)避免深度offset
Risk: Linear performance degradation, database overload
```

### BAD: High OFFSET Value (MySQL Comma Syntax)

```sql
-- ❌ MEDIUM RISK: offset=100,000 (first parameter in MySQL LIMIT m,n)
SELECT * FROM orders
WHERE status = 'completed'
ORDER BY create_time DESC
LIMIT 100000, 20
```

**Violation Message:**
```
[MEDIUM] 深分页offset=100000,需扫描并跳过100000行数据,性能较差
MapperId: com.example.OrderMapper.selectPage
SQL: SELECT * FROM orders WHERE status = 'completed' ORDER BY create_time DESC LIMIT 100000, 20
Suggestion: 建议使用游标分页(WHERE id > lastId)避免深度offset
```

### BAD: Pagination with Parameter (Runtime Detection)

```java
// ❌ MEDIUM RISK: High offset passed at runtime
@Select("SELECT * FROM users WHERE status = 'active' ORDER BY id LIMIT #{pageSize} OFFSET #{offset}")
List<User> selectPage(@Param("pageSize") int pageSize,
                      @Param("offset") int offset);

// Called with offset=50000
List<User> users = userMapper.selectPage(20, 50000);
```

**Behavior:**
- **Static Scanner:** Cannot detect (offset is parameter `?`)
- **Runtime Validator:** Detects when offset=50000 is passed

### GOOD: Low OFFSET Value

```sql
-- ✅ SAFE: offset=100 is below threshold
SELECT * FROM users
WHERE status = 'active'
ORDER BY id
LIMIT 20 OFFSET 100
```

### GOOD: Cursor-Based Pagination (Recommended)

```sql
-- ✅ BEST PRACTICE: No OFFSET, always scans only 20 rows
SELECT * FROM users
WHERE id > ?         -- Last seen ID from previous page
  AND status = 'active'
ORDER BY id
LIMIT 20
```

**Benefits:**
- Constant performance: always scans ~20 rows
- No offset overhead
- Works for any "page number"
- Database index-friendly

### GOOD: Limit Maximum Page Number

```java
// ✅ SAFE: Enforce maximum page number
public Page<User> getUsers(int pageNumber, int pageSize) {
    int maxPage = 100;  // Allow only pages 1-100
    if (pageNumber > maxPage) {
        throw new IllegalArgumentException(
            "Page number cannot exceed " + maxPage
        );
    }

    int offset = (pageNumber - 1) * pageSize;
    return userMapper.selectPage(pageSize, offset);
}
```

## Expected Messages

### Standard OFFSET Syntax

```
[MEDIUM] 深分页offset={offset},需扫描并跳过{offset}行数据,性能较差
MapperId: {namespace}.{methodId}
SQL: SELECT * FROM {table} ... LIMIT {limit} OFFSET {offset}
Suggestion: 建议使用游标分页(WHERE id > lastId)避免深度offset
Risk: Linear performance degradation with increasing offset
```

### MySQL Comma Syntax

```
[MEDIUM] 深分页offset={offset},需扫描并跳过{offset}行数据,性能较差
MapperId: {namespace}.{methodId}
SQL: SELECT * FROM {table} ... LIMIT {offset}, {limit}
Suggestion: 建议使用游标分页(WHERE id > lastId)避免深度offset
```

## How to Fix

### Option 1: Cursor-Based Pagination (Recommended)

```sql
-- Before (offset-based, degrades with page number)
SELECT * FROM users
WHERE status = 'active'
ORDER BY id
LIMIT 20 OFFSET ?  -- Offset = (pageNumber - 1) * 20

-- After (cursor-based, constant performance)
SELECT * FROM users
WHERE id > ?       -- Last ID from previous page
  AND status = 'active'
ORDER BY id
LIMIT 20
```

**Java Implementation:**
```java
// Before (offset-based)
public Page<User> getUsersOffset(int pageNumber, int pageSize) {
    int offset = (pageNumber - 1) * pageSize;
    return userMapper.selectPageOffset(pageSize, offset);
}

// After (cursor-based)
public Page<User> getUsersCursor(Long lastId, int pageSize) {
    return userMapper.selectPageCursor(lastId, pageSize);
}
```

**MyBatis Mapper:**
```xml
<!-- Cursor-based pagination -->
<select id="selectPageCursor" resultType="User">
    SELECT * FROM users
    WHERE id > #{lastId}
      AND status = 'active'
    ORDER BY id
    LIMIT #{pageSize}
</select>
```

### Option 2: Limit Maximum Page Number

```java
// Enforce maximum page number in service layer
public Page<User> getUsers(int pageNumber, int pageSize) {
    int maxPage = 500;  // Allow pages 1-500 (offset up to 10,000)

    if (pageNumber > maxPage) {
        throw new IllegalArgumentException(
            String.format("Page number cannot exceed %d", maxPage)
        );
    }

    int offset = (pageNumber - 1) * pageSize;
    return userMapper.selectPage(pageSize, offset);
}
```

### Option 3: Increase Threshold for Specific Use Cases

```yaml
# Allow higher offset for specific scenarios
sql-guard:
  rules:
    deep-pagination:
      enabled: true
      max-offset: 50000  # Increase from default 10,000
      whitelist-mapper-ids:
        - "AdminReportMapper.exportAll"  # Allow deep pagination for reports
```

**Caution:** Only increase threshold if:
1. Table size is guaranteed small (<100K rows)
2. Query frequency is low (background jobs, not user-facing)
3. Performance degradation is acceptable

### Option 4: Use Elasticsearch for Search-Heavy Scenarios

```java
// For search/filter-heavy scenarios, use Elasticsearch
public Page<Product> searchProducts(SearchDto search, int page, int size) {
    // Use Elasticsearch for deep pagination
    SearchResponse response = elasticsearchClient.search(
        SearchRequest.of(s -> s
            .index("products")
            .query(q -> buildQuery(search))
            .from(page * size)
            .size(size)
        )
    );

    // ES handles deep pagination efficiently using search_after
    return mapToPage(response);
}
```

## Configuration

### Enable/Disable

```yaml
sql-guard:
  rules:
    deep-pagination:
      enabled: true  # Default: true
```

### Adjust Threshold

```yaml
sql-guard:
  rules:
    deep-pagination:
      enabled: true
      max-offset: 10000  # Default: 10,000 rows
```

**Threshold Selection Guidelines:**
| Table Size | Max Offset | Rationale |
|------------|------------|-----------|
| <10K rows | 5,000 | Smaller tables, lower threshold |
| 10K-100K rows | 10,000 | Default, balanced |
| 100K-1M rows | 5,000 | Large tables, stricter limit |
| >1M rows | 1,000 | Very large tables, cursor-based required |

### Whitelist Specific Mappers

```yaml
sql-guard:
  rules:
    deep-pagination:
      enabled: true
      whitelist-mapper-ids:
        - "ReportMapper.exportFullData"  # Batch export job
        - "AdminMapper.debugQuery"       # Admin debugging tool
```

**Caution:** Only whitelist if:
- Not user-facing (background jobs, admin tools)
- Low query frequency
- Performance degradation is acceptable

## Edge Cases

### Case 1: Parameter Placeholder for OFFSET

```sql
-- Static scanner cannot detect (offset is parameter)
SELECT * FROM users ORDER BY id LIMIT 20 OFFSET ?
```

**Behavior:**
- **Static Scanner:** Skipped (cannot evaluate parameter)
- **Runtime Validator:** Detects when actual offset value exceeds threshold

### Case 2: Early-Return from NoConditionPaginationChecker

```sql
-- No WHERE clause (CRITICAL violation already)
SELECT * FROM users LIMIT 20 OFFSET 50000
```

**Behavior:**
- NoConditionPaginationChecker triggers (CRITICAL)
- Sets early-return flag in ValidationResult
- DeepPaginationChecker skips (no point checking offset when full table scan already occurs)

**Rationale:** Avoid duplicate misleading violations

### Case 3: OFFSET=0 or Low Values

```sql
-- OFFSET within threshold
SELECT * FROM users ORDER BY id LIMIT 20 OFFSET 100
```

**Behavior:** Not flagged (offset=100 < threshold=10,000)

### Case 4: UNION or Subquery with LIMIT

```sql
-- Main query has no OFFSET, subquery has high OFFSET
SELECT * FROM (
    SELECT * FROM users LIMIT 20 OFFSET 50000
) t
```

**Behavior:**
- Main query: No violation (no LIMIT)
- Subquery: Violation detected (offset=50,000)

## Relationship with Other Rules

### Early-Return Mechanism with NoConditionPaginationChecker

```sql
-- Example: SELECT * FROM users LIMIT 20 OFFSET 50000

-- Violations checked:
-- 1. NoConditionPaginationChecker (CRITICAL) ✓ Detected, sets earlyReturn flag
-- 2. DeepPaginationChecker (MEDIUM) ✗ Skipped due to earlyReturn flag
-- 3. MissingOrderByChecker (LOW) ✓ Still checked (independent)
```

**Rationale:** If a query has no WHERE clause (full table scan), checking for deep offset is pointless and misleading.

### Related Rules

- **[No Condition Pagination](no-condition-pagination.md)** - Detects LIMIT without WHERE (CRITICAL, sets early-return)
- **[Large Page Size](large-page-size.md)** - Detects excessive LIMIT values
- **[Missing ORDER BY](missing-orderby.md)** - Detects pagination without stable ordering
- **[No Pagination](no-pagination.md)** - Detects SELECT without LIMIT

## Production Incidents Prevented

### Incident 1: Search Result Pagination Failure

**Company:** E-commerce platform
**Incident:** Product search pagination failed for pages >100 (offset >2000)
**Impact:**
- Users clicking page 101+ got 30s timeout
- Database CPU spiked to 100%
- Site slowdown during peak hours
- Lost sales (estimated $50K/hour)

**Root Cause:** OFFSET-based pagination on 5M product table
**Query:** `SELECT * FROM products WHERE category_id = 5 ORDER BY price LIMIT 20 OFFSET ?`
**Offset values:** Page 1 = 0, Page 100 = 1980, Page 500 = 9980, Page 5000 = 99980

**Prevention:** SQL Guard would have warned at offset >10,000

**Fix:**
```sql
-- Before (offset-based)
LIMIT 20 OFFSET 99980  -- Scans 100,000 rows

-- After (cursor-based)
WHERE id > #{lastId} LIMIT 20  -- Scans ~20 rows
```

### Incident 2: Bot Crawler Database Overload

**Company:** News aggregation site
**Incident:** Search engine bot crawled all pages, causing database crash
**Impact:**
- Bot requested pages 1-10,000 for each category
- Database scanned 500M+ rows total
- Database crashed (OOM)
- Site down for 2 hours
- Customers complained, traffic lost to competitors

**Root Cause:** No pagination depth limit
**Bot Behavior:**
```
GET /articles?category=tech&page=1    → offset=0
GET /articles?category=tech&page=2    → offset=100
...
GET /articles?category=tech&page=10000 → offset=999900 (999,900!)
```

**Prevention:** SQL Guard would have blocked high offsets

**Fix:**
```java
// Before (no limit)
public Page<Article> getArticles(int page, int pageSize) {
    int offset = (page - 1) * pageSize;
    return articleMapper.selectPage(pageSize, offset);
}

// After (page number limit)
public Page<Article> getArticles(int page, int pageSize) {
    int maxPage = 100;  // Allow only pages 1-100
    if (page > maxPage) {
        throw new IllegalArgumentException("Maximum page number is " + maxPage);
    }
    int offset = (page - 1) * pageSize;
    return articleMapper.selectPage(pageSize, offset);
}
```

### Incident 3: Report Export Timeout

**Company:** Analytics platform
**Incident:** Monthly report export timed out (30min limit)
**Impact:**
- Report failed to generate
- Customer complained to support
- Manual intervention required
- Engineering time wasted debugging

**Root Cause:** Report paginated through 1M rows using OFFSET
**Query:** `SELECT * FROM events WHERE event_type = 'purchase' ORDER BY event_time LIMIT 1000 OFFSET ?`
**Offset values:** 0, 1000, 2000, ..., 999000 (1000 queries!)

**Prevention:** SQL Guard would have warned about high offsets

**Fix:**
```sql
-- Before (offset-based, 1000 slow queries)
LIMIT 1000 OFFSET ?  -- Last query: OFFSET 999000

-- After (cursor-based, 1000 fast queries)
WHERE event_id > #{lastEventId} LIMIT 1000
```

## Best Practices

### 1. Use Cursor-Based Pagination by Default

```sql
-- ❌ Don't use offset-based
SELECT * FROM users ORDER BY id LIMIT 20 OFFSET ?

-- ✅ Use cursor-based
SELECT * FROM users WHERE id > ? ORDER BY id LIMIT 20
```

### 2. Enforce Maximum Page Number

```java
// ✅ Service layer enforces max page
public Page<T> getPage(int pageNumber, int pageSize) {
    int maxPage = 500;  // ~10K max offset at pageSize=20
    if (pageNumber > maxPage) {
        throw new IllegalArgumentException(
            "Page number cannot exceed " + maxPage
        );
    }
    return repository.selectPage(pageNumber, pageSize);
}
```

### 3. Use "Load More" Pattern Instead of Page Numbers

```javascript
// ✅ Frontend: Load More button (cursor-based)
function loadMore() {
    const lastId = items[items.length - 1].id;
    fetch(`/api/users?lastId=${lastId}&limit=20`)
        .then(response => response.json())
        .then(data => items.push(...data));
}
```

**Benefits:**
- No page number = no deep offset
- Natural UX for infinite scroll
- Constant query performance

### 4. Show Limited Page Navigation

```html
<!-- ❌ Don't show all pages (enables deep pagination) -->
<nav>
    <a href="?page=1">1</a>
    <a href="?page=2">2</a>
    ...
    <a href="?page=10000">10000</a>
</nav>

<!-- ✅ Show limited pages + jump to page -->
<nav>
    <a href="?page=1">1</a>
    <a href="?page=2">2</a>
    <a href="?page=3">3</a>
    ...
    <input type="number" max="100" /> <!-- Max page = 100 -->
    <button>Jump to Page</button>
</nav>
```

### 5. Use Elasticsearch for Search-Heavy Scenarios

```java
// ✅ Use Elasticsearch with search_after for deep pagination
SearchResponse response = esClient.search(s -> s
    .index("products")
    .query(buildQuery(search))
    .searchAfter(lastScore, lastId)  // Cursor-like pagination
    .size(20)
);
```

## Testing

### Unit Test Example

```java
@Test
public void testDeepPaginationDetection() {
    // Given
    String sql = "SELECT * FROM users WHERE status = 'active' ORDER BY id LIMIT 20 OFFSET 50000";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .mapperId("UserMapper.selectPage")
        .build();

    // When
    ValidationResult result = validator.validate(context);

    // Then
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertTrue(result.getViolations().get(0).getMessage()
        .contains("深分页offset=50000"));
}

@Test
public void testLowOffsetAllowed() {
    // Given: Low offset within threshold
    String sql = "SELECT * FROM users ORDER BY id LIMIT 20 OFFSET 100";
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
public void testDeepPaginationWarning() {
    // Given: WARN strategy configured

    // When
    List<User> users = userMapper.selectPage(20, 50000);

    // Then: Returns data but logs warning
    assertNotNull(users);
    verify(logger).warn(contains("深分页offset=50000"));
}
```

## Next Steps

- **[No Condition Pagination Rule](no-condition-pagination.md)** - Detects LIMIT without WHERE
- **[Large Page Size Rule](large-page-size.md)** - Detects excessive LIMIT values
- **[Missing ORDER BY Rule](missing-orderby.md)** - Ensures stable pagination
- **[Configuration Reference](../configuration-reference.md)** - Configure rule settings

---

**Questions?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).
