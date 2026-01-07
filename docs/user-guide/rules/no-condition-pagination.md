# No Condition Pagination Rule

**Risk Level:** 🔴 CRITICAL

## Overview

The No Condition Pagination rule detects SELECT statements with LIMIT/pagination but without a WHERE clause, preventing unintended full table scans disguised as pagination. This pattern is particularly dangerous as it appears to limit results but still scans the entire table.

## What It Detects

SELECT statements with pagination but no filtering:

- **SELECT with LIMIT** but no WHERE clause
- **RowBounds pagination** without WHERE clause (MyBatis)
- **IPage pagination** without WHERE clause (MyBatis-Plus)

## Why Dangerous

### Performance Impact

```sql
-- ❌ CRITICAL: Scans all 10M users, returns 100
SELECT * FROM users LIMIT 100
```

**Real-World Impact:**
- Database scans entire table (10 million rows)
- Returns only 100 rows but reads millions
- Locks table during scan
- Exhausts database I/O and CPU
- Impacts other queries on same table

### Unpredictable Results

```sql
-- ❌ CRITICAL: Which 100 users? Order undefined!
SELECT * FROM users LIMIT 100
```

**Problems:**
- Results vary between executions
- Cannot reproduce issues
- Pagination becomes meaningless
- Users see different data on refresh

### Hidden Full Table Scan

Developers often assume LIMIT prevents full table scan:

```sql
-- ❌ WRONG ASSUMPTION: "Only reading 100 rows, should be fast"
SELECT * FROM users LIMIT 100  -- Actually scans entire table!
```

**Reality:**
- LIMIT only controls output, not input scan
- WHERE clause is what limits scan
- Database still reads all rows to find first 100

## Examples

### BAD: LIMIT Without WHERE

```sql
-- ❌ CRITICAL: Full table scan
SELECT * FROM users LIMIT 100
```

**Violation Message:**
```
[CRITICAL] SELECT with pagination but no WHERE clause detected
MapperId: com.example.UserMapper.selectPage
SQL: SELECT * FROM users LIMIT 100
Suggestion: Add WHERE clause to limit scan range
Risk: Full table scan, performance degradation
```

### BAD: LIMIT OFFSET Without WHERE

```sql
-- ❌ CRITICAL: Scans 10100 rows, returns 100
SELECT * FROM users LIMIT 100 OFFSET 10000
```

**Violation Message:**
```
[CRITICAL] SELECT with pagination but no WHERE clause detected
MapperId: com.example.UserMapper.selectPage
SQL: SELECT * FROM users LIMIT 100 OFFSET 10000
Suggestion: Add WHERE clause before using pagination
Risk: Deep scan of entire table
```

### BAD: MyBatis RowBounds Without WHERE

```java
// ❌ CRITICAL: Full table scan
List<User> users = userMapper.selectAll(new RowBounds(0, 100));
```

```xml
<select id="selectAll" resultType="User">
    SELECT * FROM users  <!-- No WHERE clause -->
</select>
```

**Violation Message:**
```
[CRITICAL] Pagination detected but no WHERE clause present
MapperId: com.example.UserMapper.selectAll
SQL: SELECT * FROM users
Suggestion: Add WHERE clause or enable pagination plugin
```

### BAD: MyBatis-Plus IPage Without WHERE

```java
// ❌ CRITICAL: Full table scan
Page<User> users = userMapper.selectPage(
    new Page<>(1, 100),
    new QueryWrapper<>()  // Empty wrapper = no WHERE
);
```

**Violation Message:**
```
[CRITICAL] IPage pagination with empty QueryWrapper detected
MapperId: com.example.UserMapper.selectPage
SQL: SELECT * FROM users LIMIT 100
Suggestion: Add conditions to QueryWrapper before pagination
```

### GOOD: LIMIT With WHERE Clause

```sql
-- ✅ SAFE: Scans only active users
SELECT * FROM users
WHERE status = 'active'
  AND create_time > '2024-01-01'
LIMIT 100
```

### GOOD: Cursor-Based Pagination

```sql
-- ✅ BEST PRACTICE: Cursor pagination with WHERE
SELECT * FROM users
WHERE id > ?  -- Last seen ID
  AND status = 'active'
ORDER BY id
LIMIT 100
```

### GOOD: MyBatis-Plus With Conditions

```java
// ✅ SAFE: WHERE clause present
Page<User> users = userMapper.selectPage(
    new Page<>(1, 100),
    new QueryWrapper<User>()
        .eq("status", "active")
        .gt("create_time", "2024-01-01")
);
```

## Expected Messages

### LIMIT Without WHERE

```
[CRITICAL] SELECT with pagination but no WHERE clause detected
MapperId: {namespace}.{methodId}
SQL: SELECT * FROM {table} LIMIT {pageSize}
Suggestion: Add WHERE clause to limit scan range
Risk: Full table scan, unpredictable results
```

### OFFSET Without WHERE

```
[CRITICAL] SELECT with OFFSET but no WHERE clause detected
MapperId: {namespace}.{methodId}
SQL: SELECT * FROM {table} LIMIT {pageSize} OFFSET {offset}
Suggestion: Add WHERE clause before using OFFSET
Risk: Deep scan of entire table
```

### Empty QueryWrapper

```
[CRITICAL] IPage pagination with empty QueryWrapper detected
MapperId: {namespace}.{methodId}
SQL: SELECT * FROM {table} LIMIT {pageSize}
Suggestion: Add conditions to QueryWrapper
Risk: Full table scan disguised as pagination
```

## How to Fix

### Option 1: Add WHERE Clause (Recommended)

```sql
-- Before (dangerous)
SELECT * FROM users LIMIT 100

-- After (safe)
SELECT * FROM users
WHERE status = 'active'
LIMIT 100
```

```java
// Before (dangerous)
@Select("SELECT * FROM users LIMIT #{pageSize}")
List<User> selectPage(@Param("pageSize") int pageSize);

// After (safe)
@Select("SELECT * FROM users WHERE status = #{status} LIMIT #{pageSize}")
List<User> selectPage(@Param("status") String status,
                      @Param("pageSize") int pageSize);
```

### Option 2: Use MyBatis-Plus QueryWrapper

```java
// Before (dangerous)
Page<User> users = userMapper.selectPage(
    new Page<>(1, 100),
    new QueryWrapper<>()  // Empty
);

// After (safe)
Page<User> users = userMapper.selectPage(
    new Page<>(1, 100),
    new QueryWrapper<User>()
        .eq("status", "active")
        .ge("create_time", LocalDateTime.now().minusDays(30))
);
```

### Option 3: Add Default Filter

For cases where you truly need "all records" but paginated:

```java
// Add a default filter that matches most records
QueryWrapper<User> wrapper = new QueryWrapper<User>()
    .ne("deleted", 1);  // Soft delete filter

Page<User> users = userMapper.selectPage(
    new Page<>(1, 100),
    wrapper
);
```

### Option 4: Use Cursor Pagination

```java
// Instead of OFFSET pagination
public Page<User> getUsers(Long lastSeenId, int pageSize) {
    QueryWrapper<User> wrapper = new QueryWrapper<User>()
        .gt(lastSeenId != null, "id", lastSeenId)
        .eq("status", "active")
        .orderByAsc("id")
        .last("LIMIT " + pageSize);

    return userMapper.selectPage(new Page<>(1, pageSize), wrapper);
}
```

## Configuration

### Enable/Disable

```yaml
sql-guard:
  rules:
    no-condition-pagination:
      enabled: true  # Default: true
```

### Adjust Risk Level

```yaml
sql-guard:
  rules:
    no-condition-pagination:
      risk-level: CRITICAL  # Default: CRITICAL
```

**Not recommended:** This is a CRITICAL rule protecting against performance issues.

### Whitelist Specific Mappers

For small, controlled tables:

```yaml
sql-guard:
  rules:
    no-condition-pagination:
      whitelist-mapper-ids:
        - "ConfigMapper.selectPage"  # Config table has <100 rows
        - "DictionaryMapper.selectPage"  # Dictionary is small
```

**Caution:** Only whitelist if table size is guaranteed small and performance is not critical.

## Edge Cases

### Case 1: LIMIT Without OFFSET

```sql
-- Still violates (scans entire table to find first 100)
SELECT * FROM users LIMIT 100
```

**Behavior:** Flagged as violation
**Reason:** Full table scan still occurs

### Case 2: ORDER BY Without WHERE

```sql
-- Has ORDER BY but no WHERE
SELECT * FROM users ORDER BY id LIMIT 100
```

**Behavior:** Flagged as violation
**Reason:** ORDER BY doesn't prevent full table scan

### Case 3: Dynamic WHERE That Becomes Empty

```xml
<select id="selectUsers">
    SELECT * FROM users
    <where>
        <if test="status != null">
            AND status = #{status}
        </if>
    </where>
    LIMIT #{pageSize}
</select>
```

**Behavior:**
- **Static Scanner:** Generates variant without WHERE, flagged as violation
- **Runtime Validator:** If `status=null`, resolved SQL has no WHERE, flagged as violation

**Fix:** Ensure WHERE always has at least one condition:

```xml
<select id="selectUsers">
    SELECT * FROM users
    WHERE 1=1  <!-- Ensure WHERE always present -->
    <if test="status != null">
        AND status = #{status}
    </if>
    LIMIT #{pageSize}
</select>
```

### Case 4: Subquery With LIMIT

```sql
-- Main query has no WHERE, subquery has LIMIT
SELECT * FROM (
    SELECT * FROM users LIMIT 1000
) t
```

**Behavior:** Both main query and subquery validated separately
**Detection:** Subquery flagged for no WHERE + LIMIT

## Relationship with Other Rules

### Early Return Mechanism

No Condition Pagination checker triggers early return to prevent misleading violations:

```sql
-- Example: SELECT * FROM users LIMIT 100
-- Violations checked:
-- 1. No Condition Pagination (CRITICAL) ✓ Detected, early return
-- 2. No Pagination (skipped due to early return)
-- 3. Missing ORDER BY (still checked independently)
```

### Related Rules

- **[No WHERE Clause](no-where-clause.md)** - Detects SELECT without WHERE (less severe)
- **[Deep Pagination](deep-pagination.md)** - Detects high OFFSET values
- **[Missing ORDER BY](missing-orderby.md)** - Detects pagination without stable ordering
- **[Logical Pagination](logical-pagination.md)** - Detects RowBounds without pagination plugin

## Production Incidents Prevented

### Incident 1: Product Listing Full Table Scan

**Company:** E-commerce platform
**Incident:** Product listing page executed `SELECT * FROM products LIMIT 20`
**Impact:**
- 5M product table scanned on every page load
- Database CPU spiked to 100%
- Page load time increased from 100ms to 5s
- Site slowdown during peak hours

**Root Cause:** Developer assumed LIMIT 20 was efficient
**Prevention:** SQL Guard would have blocked execution (BLOCK strategy)
**Fix:** Added `WHERE category_id = ? AND status = 'active'` before LIMIT

### Incident 2: Admin User Search

**Company:** SaaS platform
**Incident:** Admin search with empty query executed `SELECT * FROM users LIMIT 50`
**Impact:**
- 10M user table scanned
- Locked table for 30 seconds
- Blocked other user queries
- Customer complaints about slow login

**Root Cause:** Empty search query → no WHERE clause
**Prevention:** SQL Guard would have blocked execution (BLOCK strategy)
**Fix:** Required minimum search criteria before allowing query

### Incident 3: Report Generation

**Company:** Analytics platform
**Incident:** "Recent events" report executed `SELECT * FROM events LIMIT 1000`
**Impact:**
- 100M event table scanned
- Memory exhaustion from sorting
- Report generation timeout (30min)
- Failed background jobs

**Root Cause:** Missing WHERE clause for time range
**Prevention:** SQL Guard would have blocked execution (BLOCK strategy)
**Fix:** Added `WHERE event_time > NOW() - INTERVAL 7 DAY` before LIMIT

## Best Practices

### 1. Always Combine WHERE + LIMIT

```sql
-- ❌ Never do this
SELECT * FROM users LIMIT 100

-- ✅ Always do this
SELECT * FROM users
WHERE status = 'active'
  AND create_time > ?
LIMIT 100
```

### 2. Use Meaningful WHERE Conditions

```sql
-- ❌ Dummy WHERE (bypasses check but still scans all)
SELECT * FROM users
WHERE 1=1
LIMIT 100

-- ✅ Real WHERE condition
SELECT * FROM users
WHERE status = 'active'
LIMIT 100
```

### 3. Add Default Filters

```java
// ✅ Always include default filters
QueryWrapper<User> wrapper = new QueryWrapper<User>()
    .eq("deleted", 0)  // Soft delete filter
    .eq("status", "active")  // Status filter
    .ge("create_time", thirtyDaysAgo);  // Time range

Page<User> users = userMapper.selectPage(new Page<>(1, 100), wrapper);
```

### 4. Validate Input Before Query

```java
// ✅ Require at least one search criteria
public Page<User> searchUsers(UserSearchDto search, Page page) {
    if (search.isEmpty()) {
        throw new IllegalArgumentException(
            "At least one search criteria required"
        );
    }

    QueryWrapper<User> wrapper = buildSearchWrapper(search);
    return userMapper.selectPage(page, wrapper);
}
```

### 5. Use Cursor Pagination for Large Datasets

```java
// ❌ OFFSET pagination (scans and skips)
SELECT * FROM users
WHERE status = 'active'
LIMIT 100 OFFSET 10000  -- Scans 10100 rows

// ✅ Cursor pagination (scans only needed rows)
SELECT * FROM users
WHERE id > ?  -- Last seen ID
  AND status = 'active'
ORDER BY id
LIMIT 100  -- Scans only 100 rows
```

## Testing

### Unit Test Example

```java
@Test
public void testNoConditionPaginationDetection() {
    // Given
    String sql = "SELECT * FROM users LIMIT 100";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .mapperId("UserMapper.selectPage")
        .build();

    // When
    ValidationResult result = validator.validate(context);

    // Then
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertTrue(result.getViolations().get(0).getMessage()
        .contains("pagination but no WHERE clause"));
}
```

### Integration Test Example

```java
@Test
public void testPaginationWithoutWhereBlocked() {
    // Given: BLOCK strategy configured

    // When/Then: SQLException thrown
    assertThrows(SQLException.class, () -> {
        userMapper.selectPage(new RowBounds(0, 100));
    });
}
```

## Next Steps

- **[Deep Pagination Rule](deep-pagination.md)** - Detect high OFFSET values
- **[Missing ORDER BY Rule](missing-orderby.md)** - Ensure stable pagination
- **[No WHERE Clause Rule](no-where-clause.md)** - Broader protection
- **[Configuration Reference](../configuration-reference.md)** - Configure rule settings

---

**Questions?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).
