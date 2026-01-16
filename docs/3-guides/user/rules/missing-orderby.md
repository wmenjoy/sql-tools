# Missing ORDER BY in Pagination Queries

## Overview

**Rule ID:** `MISSING_ORDER_BY`
**Severity:** LOW
**Category:** Data Consistency

This rule detects pagination queries that lack an ORDER BY clause, which can lead to inconsistent or unpredictable result sets across different pages.

## Problem Description

When paginating through result sets using LIMIT/OFFSET without an ORDER BY clause, the database may return rows in any order. This causes several issues:

1. **Inconsistent Pagination**: Same query may return different results on each execution
2. **Duplicate/Missing Rows**: Rows may appear on multiple pages or be skipped entirely
3. **Unpredictable Behavior**: Results depend on database internals (physical storage order, query plan)
4. **Poor User Experience**: Users see inconsistent data when navigating between pages

## Rule Configuration

```yaml
sqlguard:
  rules:
    missing-orderby:
      enabled: true
      # Minimum page size to trigger this check
      # Only applies to pagination queries with page size >= this value
      min-page-size: 10
```

### Configuration Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable/disable this rule |
| `min-page-size` | integer | `10` | Minimum page size to trigger check |

## Detection Logic

The checker identifies queries with:
- Pagination indicators (LIMIT, OFFSET, FETCH FIRST, TOP with variables)
- Absence of ORDER BY clause
- Page size meeting the configured threshold

### Supported Pagination Patterns

```sql
-- MySQL/PostgreSQL
SELECT * FROM orders LIMIT ? OFFSET ?

-- SQL Server
SELECT * FROM orders
OFFSET ? ROWS FETCH NEXT ? ROWS ONLY

-- Oracle
SELECT * FROM orders OFFSET ? ROWS FETCH FIRST ? ROWS ONLY
```

## Examples

### ❌ Bad: Pagination Without ORDER BY

```sql
-- Inconsistent results across page loads
SELECT user_id, username, email
FROM users
LIMIT 20 OFFSET 0;

-- Different SQL dialects, same problem
SELECT * FROM products
OFFSET 100 ROWS FETCH NEXT 50 ROWS ONLY;

-- MyBatis example
<select id="listUsers" resultType="User">
  SELECT * FROM users
  LIMIT #{pageSize} OFFSET #{offset}
</select>
```

**Problem:** Row order is undefined, causing inconsistent pagination results.

### ✅ Good: Pagination With Explicit ORDER BY

```sql
-- Consistent, deterministic results
SELECT user_id, username, email
FROM users
ORDER BY user_id
LIMIT 20 OFFSET 0;

-- Using composite ordering for uniqueness
SELECT * FROM products
ORDER BY category_id, product_id
OFFSET 100 ROWS FETCH NEXT 50 ROWS ONLY;

-- MyBatis example with ordering
<select id="listUsers" resultType="User">
  SELECT * FROM users
  ORDER BY user_id
  LIMIT #{pageSize} OFFSET #{offset}
</select>
```

## Real-World Scenarios

### Scenario 1: User List Pagination

**Context:** Admin panel displaying paginated user list

```java
// ❌ Problematic code
@Select("SELECT * FROM users LIMIT #{size} OFFSET #{offset}")
List<User> listUsers(@Param("size") int size, @Param("offset") int offset);
```

**Issue:** Users may appear on multiple pages or disappear when navigating.

**Fix:**
```java
// ✅ Fixed version
@Select("SELECT * FROM users ORDER BY user_id LIMIT #{size} OFFSET #{offset}")
List<User> listUsers(@Param("size") int size, @Param("offset") int offset);
```

### Scenario 2: Report Generation

**Context:** Exporting large dataset in batches

```sql
-- ❌ Problematic batch query
DECLARE @offset INT = 0;
DECLARE @batchSize INT = 1000;

WHILE (1=1)
BEGIN
    SELECT * FROM transactions
    OFFSET @offset ROWS FETCH NEXT @batchSize ROWS ONLY;

    SET @offset = @offset + @batchSize;
END
```

**Issue:** May export duplicate rows or miss rows entirely.

**Fix:**
```sql
-- ✅ Fixed version with deterministic ordering
DECLARE @offset INT = 0;
DECLARE @batchSize INT = 1000;

WHILE (1=1)
BEGIN
    SELECT * FROM transactions
    ORDER BY transaction_id  -- Ensures consistent batches
    OFFSET @offset ROWS FETCH NEXT @batchSize ROWS ONLY;

    SET @offset = @offset + @batchSize;
END
```

## Best Practices

### 1. Use Unique Columns for Ordering

```sql
-- ✅ Primary key ensures unique, stable ordering
SELECT * FROM orders
ORDER BY order_id
LIMIT 20 OFFSET 40;

-- ✅ Composite key for uniqueness
SELECT * FROM order_items
ORDER BY order_id, item_id
LIMIT 50 OFFSET 100;
```

### 2. Include Tie-Breaker Columns

When ordering by non-unique columns, add a unique column as a tie-breaker:

```sql
-- ❌ Non-unique ordering (multiple users with same created_at)
SELECT * FROM users
ORDER BY created_at
LIMIT 20;

-- ✅ Added tie-breaker for deterministic results
SELECT * FROM users
ORDER BY created_at, user_id
LIMIT 20;
```

### 3. Index Your ORDER BY Columns

```sql
-- Create index to support efficient ordered pagination
CREATE INDEX idx_users_created_at_id ON users(created_at, user_id);

-- Now this query performs well
SELECT * FROM users
ORDER BY created_at, user_id
LIMIT 20 OFFSET 100;
```

### 4. Keyset Pagination (Seek Method)

For better performance and consistency, consider keyset pagination:

```sql
-- First page
SELECT * FROM users
WHERE user_id > 0
ORDER BY user_id
LIMIT 20;

-- Next page (using last ID from previous page)
SELECT * FROM users
WHERE user_id > 1020
ORDER BY user_id
LIMIT 20;
```

## Integration Examples

### Spring Boot with MyBatis-Plus

```java
@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    // ❌ Bad: LambdaQueryWrapper without ordering
    public IPage<User> listUsers(Page<User> page) {
        return userMapper.selectPage(page,
            new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)
        );
    }

    // ✅ Good: Explicit ordering
    public IPage<User> listUsersFixed(Page<User> page) {
        return userMapper.selectPage(page,
            new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)
                .orderByAsc(User::getUserId)  // Deterministic ordering
        );
    }
}
```

### MyBatis XML with Dynamic SQL

```xml
<!-- ❌ Bad: Missing ORDER BY -->
<select id="searchUsers" resultType="User">
  SELECT * FROM users
  <where>
    <if test="keyword != null">
      AND username LIKE CONCAT('%', #{keyword}, '%')
    </if>
  </where>
  LIMIT #{pageSize} OFFSET #{offset}
</select>

<!-- ✅ Good: Always include ORDER BY -->
<select id="searchUsers" resultType="User">
  SELECT * FROM users
  <where>
    <if test="keyword != null">
      AND username LIKE CONCAT('%', #{keyword}, '%')
    </if>
  </where>
  ORDER BY user_id
  LIMIT #{pageSize} OFFSET #{offset}
</select>
```

## Configuration Examples

### Default Configuration

```yaml
sqlguard:
  rules:
    missing-orderby:
      enabled: true
      min-page-size: 10
```

### Strict Mode (Check All Pagination)

```yaml
sqlguard:
  rules:
    missing-orderby:
      enabled: true
      min-page-size: 1  # Check even small page sizes
```

### Disabled for Development

```yaml
sqlguard:
  rules:
    missing-orderby:
      enabled: false  # Temporarily disable during prototyping
```

## Performance Considerations

### Impact of ORDER BY

1. **With Index:**
   - Minimal overhead if ORDER BY columns are indexed
   - Database can use index scan for ordered results

2. **Without Index:**
   - Requires sorting operation (filesort in MySQL)
   - Performance degrades with table size

### Optimization Tips

```sql
-- ✅ Covering index for optimal performance
CREATE INDEX idx_users_covering
ON users(status, user_id)
INCLUDE (username, email);

-- This query uses index-only scan
SELECT user_id, username, email
FROM users
WHERE status = 1
ORDER BY user_id
LIMIT 20;
```

## Common Exceptions

Some scenarios where missing ORDER BY is acceptable:

1. **Temporary/Development Queries**: Quick data exploration
2. **Single-Row Results**: LIMIT 1 for existence checks
3. **Aggregate Queries**: COUNT, SUM without detail rows
4. **Small Static Tables**: Lookup tables with few rows

For these cases, you can configure `min-page-size` or disable the rule.

## Related Rules

- [no-pagination.md](no-pagination.md) - Detects queries without pagination
- [large-page-size.md](large-page-size.md) - Prevents excessive page sizes
- [deep-pagination.md](deep-pagination.md) - Warns about deep OFFSET values

## Troubleshooting

### False Positives

**Issue:** Rule triggers on queries that don't need consistent ordering

**Solution:** Adjust `min-page-size` threshold or add whitelist:

```yaml
sqlguard:
  rules:
    missing-orderby:
      min-page-size: 20  # Only check larger page sizes
```

### Database Compatibility

**Issue:** Some databases have implicit ordering

**Note:** Never rely on implicit ordering. Always use explicit ORDER BY for:
- PostgreSQL: No guaranteed order without ORDER BY
- MySQL: Physical order may change due to table modifications
- SQL Server: No guaranteed order in heap tables
- Oracle: Row order is undefined without ORDER BY

## References

- [PostgreSQL Documentation: ORDER BY](https://www.postgresql.org/docs/current/queries-order.html)
- [MySQL: LIMIT Optimization](https://dev.mysql.com/doc/refman/8.0/en/limit-optimization.html)
- [SQL Server: ORDER BY Clause](https://docs.microsoft.com/en-us/sql/t-sql/queries/select-order-by-clause-transact-sql)
- [Pagination Best Practices](https://use-the-index-luke.com/sql/partial-results/fetch-next-page)

## Changelog

- **v1.0.0** (2024-01): Initial rule implementation
- **v1.1.0** (2024-03): Added `min-page-size` configuration
- **v1.2.0** (2024-06): Enhanced dialect support for Oracle, SQL Server
