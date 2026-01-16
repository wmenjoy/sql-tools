# Blacklist Fields Rule

**Risk Level:** 🟠 HIGH

## Overview

The Blacklist Fields rule detects WHERE clauses that use only low-selectivity blacklisted fields, preventing near-full table scans disguised as filtered queries. These queries appear to have meaningful WHERE conditions but still match most rows in the table, causing severe performance issues.

## What It Detects

SELECT statements with WHERE clauses using only blacklisted fields:

- **Low-Selectivity Fields** - Fields that match most rows (deleted, status, enabled, type)
- **State Flags** - Boolean or enum fields with few distinct values
- **Audit Fields** - Timestamp or user fields when used alone (create_*, update_*)

**Default Blacklist:**
- `deleted` - Soft delete flag (typically 90%+ rows are deleted=0)
- `del_flag` - Alternative soft delete flag
- `status` - Generic status field (active/inactive, usually skewed distribution)

## Why Dangerous

### Near-Full Table Scan Disguised as Filtered Query

```sql
-- ❌ HIGH RISK: Matches 95% of rows (near-full scan)
SELECT * FROM users WHERE deleted = 0
```

**Problems:**
- WHERE clause present but ineffective
- Appears safe to developers ("has WHERE, must be OK")
- Database scans 95%+ of table rows
- No index can efficiently support this query
- Performance degrades linearly with table size

### Index Inefficiency

```sql
-- ❌ HIGH RISK: Index on 'status' is nearly useless
SELECT * FROM orders WHERE status = 'active'
```

**Reality:**
- Index exists on `status` column
- But status has only 2-3 distinct values
- Database scans most of the index anyway
- Index overhead (space + maintenance) without benefit
- Often slower than full table scan

### Misleading Performance in Development

```sql
-- ❌ Works "fine" in dev (100 rows), catastrophic in prod (10M rows)
SELECT * FROM products WHERE enabled = 1
```

**Timeline:**
1. **Development:** 100 products, enabled=1 returns 90 rows → 50ms
2. **Staging:** 10K products, enabled=1 returns 9K rows → 500ms
3. **Production:** 10M products, enabled=1 returns 9.5M rows → 30s timeout

## Examples

### BAD: Single Blacklist Field

```sql
-- ❌ HIGH RISK: Soft delete filter only
SELECT * FROM users WHERE deleted = 0

-- ❌ HIGH RISK: Status filter only
SELECT * FROM orders WHERE status = 'active'

-- ❌ HIGH RISK: Enabled flag only
SELECT * FROM products WHERE enabled = 1
```

**Violation Message:**
```
[HIGH] WHERE条件只包含黑名单字段[deleted],条件过于宽泛
MapperId: com.example.UserMapper.selectActive
SQL: SELECT * FROM users WHERE deleted = 0
Suggestion: Add high-selectivity fields (id, unique key, date range)
Risk: Near-full table scan, performance degradation
```

### BAD: Multiple Blacklist Fields

```sql
-- ❌ HIGH RISK: All fields are blacklisted
SELECT * FROM products
WHERE enabled = 1 AND status = 'active'
```

**Violation Message:**
```
[HIGH] WHERE条件只包含黑名单字段[enabled, status],条件过于宽泛
MapperId: com.example.ProductMapper.selectByStatus
SQL: SELECT * FROM products WHERE enabled = 1 AND status = 'active'
Suggestion: Add non-blacklisted field (category_id, price range, id)
```

### BAD: Wildcard Pattern Match

```sql
-- ❌ HIGH RISK: Matches wildcard pattern 'create_*'
SELECT * FROM users WHERE create_time > '2024-01-01'
```

**Violation Message (if create_* is in blacklist):**
```
[HIGH] WHERE条件只包含黑名单字段[create_time],条件过于宽泛
MapperId: com.example.UserMapper.selectRecent
SQL: SELECT * FROM users WHERE create_time > '2024-01-01'
Suggestion: Add specific filter (user_id, email, status)
```

### GOOD: Mixed Fields (Blacklist + High-Selectivity)

```sql
-- ✅ SAFE: ID (high-selectivity) + deleted (blacklist)
SELECT * FROM users WHERE id = ? AND deleted = 0

-- ✅ SAFE: user_id (high-selectivity) + status (blacklist)
SELECT * FROM orders WHERE user_id = ? AND status = 'active'

-- ✅ SAFE: category_id + enabled
SELECT * FROM products
WHERE category_id = ?
  AND enabled = 1
  AND price BETWEEN 100 AND 500
```

### GOOD: High-Selectivity Field Only

```sql
-- ✅ SAFE: No blacklisted fields
SELECT * FROM users WHERE email = ?
SELECT * FROM orders WHERE order_number = ?
SELECT * FROM products WHERE sku = ?
```

## Expected Messages

### Single Blacklist Field

```
[HIGH] WHERE条件只包含黑名单字段[{field}],条件过于宽泛
MapperId: {namespace}.{methodId}
SQL: SELECT * FROM {table} WHERE {field} = {value}
Suggestion: Add high-selectivity fields to WHERE clause
Risk: Near-full table scan, poor index utilization
```

### Multiple Blacklist Fields

```
[HIGH] WHERE条件只包含黑名单字段[{field1}, {field2}],条件过于宽泛
MapperId: {namespace}.{methodId}
SQL: SELECT * FROM {table} WHERE {field1} = ? AND {field2} = ?
Suggestion: Add at least one non-blacklisted field
```

## How to Fix

### Option 1: Add High-Selectivity Field (Recommended)

```sql
-- Before (dangerous)
SELECT * FROM users WHERE deleted = 0

-- After (safe)
SELECT * FROM users WHERE id = ? AND deleted = 0
SELECT * FROM users WHERE email = ? AND deleted = 0
```

```java
// Before (dangerous)
@Select("SELECT * FROM users WHERE status = #{status}")
List<User> selectByStatus(@Param("status") String status);

// After (safe)
@Select("SELECT * FROM users WHERE user_id = #{userId} AND status = #{status}")
List<User> selectByUserIdAndStatus(@Param("userId") Long userId,
                                    @Param("status") String status);
```

### Option 2: Add Date Range Filter

```sql
-- Before (dangerous)
SELECT * FROM orders WHERE status = 'active'

-- After (safe)
SELECT * FROM orders
WHERE status = 'active'
  AND create_time >= NOW() - INTERVAL 30 DAY
```

```xml
<!-- Before (dangerous) -->
<select id="selectActive" resultType="Order">
    SELECT * FROM orders WHERE status = 'active'
</select>

<!-- After (safe) -->
<select id="selectActive" resultType="Order">
    SELECT * FROM orders
    WHERE status = 'active'
      AND create_time >= NOW() - INTERVAL 30 DAY
</select>
```

### Option 3: Use MyBatis-Plus with Additional Conditions

```java
// Before (dangerous)
QueryWrapper<User> wrapper = new QueryWrapper<User>()
    .eq("deleted", 0);
List<User> users = userMapper.selectList(wrapper);

// After (safe)
QueryWrapper<User> wrapper = new QueryWrapper<User>()
    .eq("deleted", 0)
    .like("username", searchTerm)  // Add search condition
    .ge("create_time", startDate);  // Add date range
List<User> users = userMapper.selectList(wrapper);
```

### Option 4: Require Search Criteria

```java
// Before (dangerous)
public List<User> searchUsers(String status) {
    QueryWrapper<User> wrapper = new QueryWrapper<User>()
        .eq("status", status);
    return userMapper.selectList(wrapper);
}

// After (safe)
public List<User> searchUsers(UserSearchDto search) {
    // Validate: require at least one non-blacklisted field
    if (search.getEmail() == null &&
        search.getUsername() == null &&
        search.getUserId() == null) {
        throw new IllegalArgumentException(
            "At least one search criteria required (email, username, or userId)"
        );
    }

    QueryWrapper<User> wrapper = new QueryWrapper<User>()
        .eq(search.getUserId() != null, "id", search.getUserId())
        .like(search.getUsername() != null, "username", search.getUsername())
        .eq(search.getEmail() != null, "email", search.getEmail())
        .eq("status", search.getStatus());  // Blacklist field OK with others

    return userMapper.selectList(wrapper);
}
```

## Configuration

### Enable/Disable

```yaml
sql-guard:
  rules:
    blacklist-fields:
      enabled: true  # Default: true
```

### Customize Blacklist

```yaml
sql-guard:
  rules:
    blacklist-fields:
      enabled: true
      fields:
        - deleted
        - del_flag
        - status
        - enabled
        - is_active
        - type
        - create_*    # Wildcard: matches create_time, create_by, etc.
        - update_*    # Wildcard: matches update_time, update_by, etc.
        - last_*      # Wildcard: matches last_login, last_seen, etc.
```

### Adjust Risk Level

```yaml
sql-guard:
  rules:
    blacklist-fields:
      risk-level: HIGH  # Default: HIGH
```

### Whitelist Specific Mappers

For controlled, small tables:

```yaml
sql-guard:
  rules:
    blacklist-fields:
      whitelist-mapper-ids:
        - "ConfigMapper.selectActive"  # Config table has <100 rows
        - "DictionaryMapper.selectByStatus"  # Dictionary is small
```

**Caution:** Only whitelist if:
1. Table size is guaranteed small (<1000 rows)
2. Query frequency is low
3. Performance impact is negligible

## Edge Cases

### Case 1: Wildcard Pattern Matching

```sql
-- If blacklist includes "create_*"

-- VIOLATES: Matches prefix pattern
SELECT * FROM users WHERE create_time > ?
SELECT * FROM users WHERE create_by = ?

-- SAFE: Doesn't match pattern
SELECT * FROM users WHERE created_at > ?  -- Different prefix
SELECT * FROM users WHERE id = ? AND create_time > ?  -- Mixed
```

**Behavior:**
- Wildcard `create_*` matches any field starting with `create_`
- Case-insensitive matching
- Underscore `_` is part of the prefix, not a SQL wildcard

### Case 2: Mixed Blacklist and Non-Blacklist Fields

```sql
-- Has both blacklisted and non-blacklisted fields
SELECT * FROM users
WHERE id = ?           -- Non-blacklisted (high-selectivity)
  AND deleted = 0      -- Blacklisted (low-selectivity)
```

**Behavior:** Not flagged as violation
**Reason:** At least one non-blacklisted field present

### Case 3: No WHERE Clause

```sql
-- No WHERE clause at all
SELECT * FROM users
```

**Behavior:** Not checked by BlacklistFieldChecker
**Reason:** Handled by NoWhereClauseChecker (CRITICAL)

### Case 4: Dynamic SQL with Optional Blacklist Fields

```xml
<select id="selectUsers">
    SELECT * FROM users
    <where>
        <if test="userId != null">
            AND id = #{userId}
        </if>
        <if test="status != null">
            AND status = #{status}
        </if>
        AND deleted = 0
    </where>
</select>
```

**Behavior:**
- **Static Scanner:** Generates variant with only `deleted = 0` → flagged as violation
- **Runtime Validator:** If `userId=null`, SQL becomes `WHERE deleted = 0` → flagged as violation

**Fix:** Ensure at least one non-blacklisted field is always present:

```xml
<select id="selectUsers">
    SELECT * FROM users
    WHERE id > 0  <!-- Always present non-blacklisted condition -->
    <if test="status != null">
        AND status = #{status}
    </if>
    AND deleted = 0
</select>
```

## Relationship with Other Rules

### Interaction with No WHERE Clause Rule

```sql
-- Severity progression:
-- 1. No WHERE at all → Caught by No WHERE Clause (CRITICAL)
-- 2. WHERE with blacklist-only → Caught by Blacklist Fields (HIGH)
-- 3. WHERE with mixed fields → Safe ✓
```

### Related Rules

- **[No WHERE Clause](no-where-clause.md)** - Detects complete absence of WHERE
- **[Whitelist Fields](whitelist-fields.md)** - Enforces high-selectivity field requirements
- **[Dummy Condition](dummy-condition.md)** - Detects meaningless conditions like 1=1
- **[No Pagination](no-pagination.md)** - Detects SELECT without LIMIT

## Production Incidents Prevented

### Incident 1: User Listing Full Table Scan

**Company:** SaaS platform (10M users)
**Incident:** Admin user list page executed `SELECT * FROM users WHERE deleted = 0`
**Impact:**
- 10M row table scan on every admin page load
- Page load time: 100ms → 15s
- Database CPU: 30% → 95%
- Locked table caused login failures for customers

**Root Cause:** Developer assumed `deleted = 0` was sufficient filtering
**Query Statistics:**
- `deleted = 0`: 9.8M rows (98% of table)
- `deleted = 1`: 200K rows (2% of table)

**Prevention:** SQL Guard would have warned (WARN strategy) or blocked (BLOCK strategy)

**Fix:**
```sql
-- Before (dangerous)
SELECT * FROM users WHERE deleted = 0

-- After (safe)
SELECT * FROM users
WHERE deleted = 0
  AND create_time > NOW() - INTERVAL 30 DAY
LIMIT 100
```

### Incident 2: Order Search Performance Collapse

**Company:** E-commerce platform (50M orders)
**Incident:** Order search with `WHERE status = 'active'` caused timeout
**Impact:**
- Search timeout increased from 200ms to 30s
- 500 errors during peak hours
- Customer complaints about slow search
- Database connection pool exhaustion

**Root Cause:** Status field has only 3 distinct values (active/completed/cancelled)
**Distribution:**
- `status = 'active'`: 5M orders (10%)
- `status = 'completed'`: 44M orders (88%)
- `status = 'cancelled'`: 1M orders (2%)

**Prevention:** SQL Guard would have warned about blacklist-only condition

**Fix:**
```sql
-- Before (dangerous)
SELECT * FROM orders WHERE status = 'active'

-- After (safe)
SELECT * FROM orders
WHERE user_id = ?          -- High-selectivity field
  AND status = 'active'    -- Blacklist field OK with others
  AND create_time > ?      -- Date range for additional filtering
```

### Incident 3: Product Catalog Slow Query

**Company:** Retail platform (5M products)
**Incident:** Product listing `SELECT * FROM products WHERE enabled = 1`
**Impact:**
- Category page load: 100ms → 5s
- Database read IOPS: 1000 → 50000
- Monthly database cost increased 3x
- Poor customer experience during holiday season

**Root Cause:** Enabled flag distribution:
- `enabled = 1`: 4.8M products (96%)
- `enabled = 0`: 200K products (4%)

**Index Analysis:**
- Index on `enabled` column exists but useless
- Database optimizer often ignores index (full scan faster)
- Index maintenance overhead without benefit

**Prevention:** SQL Guard would have blocked execution (BLOCK strategy)

**Fix:**
```sql
-- Before (dangerous)
SELECT * FROM products WHERE enabled = 1

-- After (safe)
SELECT * FROM products
WHERE category_id = ?      -- High-selectivity field
  AND enabled = 1          -- Blacklist field OK with others
  AND price BETWEEN ? AND ?
ORDER BY sales_rank
LIMIT 20
```

## Best Practices

### 1. Always Combine Blacklist Fields with High-Selectivity Fields

```sql
-- ❌ Never use blacklist fields alone
SELECT * FROM users WHERE deleted = 0

-- ✅ Always combine with high-selectivity fields
SELECT * FROM users WHERE id = ? AND deleted = 0
SELECT * FROM users WHERE email = ? AND deleted = 0
SELECT * FROM users WHERE username LIKE ? AND deleted = 0
```

### 2. Use Date Ranges for Time-Based Queries

```sql
-- ❌ Status filter alone
SELECT * FROM orders WHERE status = 'active'

-- ✅ Status + date range
SELECT * FROM orders
WHERE status = 'active'
  AND create_time >= NOW() - INTERVAL 7 DAY
```

### 3. Require Search Criteria in Service Layer

```java
// ✅ Validate before querying
public List<User> searchUsers(UserSearchDto search) {
    // Require at least one high-selectivity field
    if (!search.hasHighSelectivityField()) {
        throw new IllegalArgumentException(
            "Search requires at least one of: email, username, userId"
        );
    }

    QueryWrapper<User> wrapper = new QueryWrapper<User>()
        .like(search.getEmail() != null, "email", search.getEmail())
        .like(search.getUsername() != null, "username", search.getUsername())
        .eq(search.getUserId() != null, "id", search.getUserId())
        .eq("deleted", 0);  // Blacklist field OK with others

    return userMapper.selectList(wrapper);
}
```

### 4. Design Blacklist Based on Cardinality

**High-Cardinality Fields (Good for WHERE):**
- `id`, `uuid`, `email`, `username` - Unique or near-unique
- `order_number`, `sku`, `barcode` - Business unique keys
- Specific date ranges with `create_time BETWEEN`

**Low-Cardinality Fields (Add to Blacklist):**
- `deleted`, `del_flag`, `is_deleted` - Binary flag
- `status`, `state`, `type` - Enum with few values
- `enabled`, `is_active`, `visible` - Boolean fields
- `gender`, `country`, `language` - Limited distinct values

**Rule of Thumb:**
- Cardinality < 10 distinct values → Add to blacklist
- Cardinality > 1000 distinct values → Safe for WHERE
- Cardinality 10-1000 → Evaluate based on distribution

### 5. Use Composite Indexes Correctly

```sql
-- ❌ Index on blacklist field alone
CREATE INDEX idx_status ON orders(status);

-- ✅ Composite index with high-selectivity field first
CREATE INDEX idx_user_status ON orders(user_id, status);
CREATE INDEX idx_time_status ON orders(create_time, status);
```

**Index Design Principle:**
- High-selectivity columns first
- Low-selectivity (blacklist) columns last
- This allows index to narrow down rows quickly

## Testing

### Unit Test Example

```java
@Test
public void testBlacklistFieldsDetection() {
    // Given
    String sql = "SELECT * FROM users WHERE deleted = 0";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .mapperId("UserMapper.selectActive")
        .build();

    // When
    ValidationResult result = validator.validate(context);

    // Then
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertTrue(result.getViolations().get(0).getMessage()
        .contains("WHERE条件只包含黑名单字段"));
    assertTrue(result.getViolations().get(0).getMessage()
        .contains("deleted"));
}

@Test
public void testMixedFieldsAllowed() {
    // Given: Blacklist field + high-selectivity field
    String sql = "SELECT * FROM users WHERE id = 1 AND deleted = 0";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .mapperId("UserMapper.selectById")
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
public void testBlacklistFieldsWarning() {
    // Given: WARN strategy configured

    // When
    List<User> users = userMapper.selectByStatus("active");

    // Then: Returns data but logs warning
    assertNotNull(users);
    verify(logger).warn(contains("WHERE条件只包含黑名单字段"));
}

@Test
public void testBlacklistFieldsBlocking() {
    // Given: BLOCK strategy configured

    // When/Then: SQLException thrown
    assertThrows(SQLException.class, () -> {
        userMapper.selectByStatus("active");
    });
}
```

## Next Steps

- **[Whitelist Fields Rule](whitelist-fields.md)** - Enforces high-selectivity field requirements
- **[No WHERE Clause Rule](no-where-clause.md)** - Broader protection against missing WHERE
- **[Dummy Condition Rule](dummy-condition.md)** - Detects meaningless conditions
- **[Configuration Reference](../configuration-reference.md)** - Configure rule settings

---

**Questions?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).
