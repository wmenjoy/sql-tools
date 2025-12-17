# No WHERE Clause Rule

**Risk Level:** 🔴 CRITICAL

## Overview

The No WHERE Clause rule detects SQL statements (SELECT/UPDATE/DELETE) that completely lack a WHERE clause, preventing catastrophic full-table operations that could delete entire datasets, update all records, or return millions of rows causing memory exhaustion.

## What It Detects

SQL statements missing WHERE clause:

- **DELETE** without WHERE - Deletes all table rows
- **UPDATE** without WHERE - Updates all table rows
- **SELECT** without WHERE - Returns all table rows

## Why Dangerous

### Data Loss (DELETE/UPDATE)

```sql
-- ❌ CRITICAL: Deletes ALL users (irreversible)
DELETE FROM users

-- ❌ CRITICAL: Updates ALL orders (data corruption)
UPDATE orders SET status = 'cancelled'
```

**Real-World Impact:**
- Production incident: Developer accidentally runs `DELETE FROM users` in production console
- Result: 10 million user records deleted
- Recovery: 8 hours downtime, partial data loss from last backup
- Cost: $500K+ in lost revenue and customer trust

### Memory Exhaustion (SELECT)

```sql
-- ❌ CRITICAL: Loads 10 million users into memory
SELECT * FROM users
```

**Real-World Impact:**
- Application loads entire user table into memory
- Result: OutOfMemoryError, application crash
- Recovery: Restart required, service disruption
- Cost: User-facing errors, degraded experience

### Performance Degradation

Full table scans without WHERE clause:

- Scan millions of rows
- Lock entire table
- Exhaust database resources
- Impact other queries

## Examples

### BAD: DELETE Without WHERE

```sql
-- ❌ CRITICAL: Deletes all users
DELETE FROM users
```

**Violation Message:**
```
[CRITICAL] No WHERE clause detected in DELETE statement
MapperId: com.example.UserMapper.deleteAll
SQL: DELETE FROM users
Suggestion: Add WHERE clause to prevent accidental data loss
```

### BAD: UPDATE Without WHERE

```sql
-- ❌ CRITICAL: Updates all orders
UPDATE orders SET status = 'shipped'
```

**Violation Message:**
```
[CRITICAL] No WHERE clause detected in UPDATE statement
MapperId: com.example.OrderMapper.updateStatus
SQL: UPDATE orders SET status = 'shipped'
Suggestion: Add WHERE clause to limit update scope
```

### BAD: SELECT Without WHERE

```sql
-- ❌ CRITICAL: Returns all users (memory exhaustion)
SELECT * FROM users
```

**Violation Message:**
```
[CRITICAL] No WHERE clause detected in SELECT statement
MapperId: com.example.UserMapper.selectAll
SQL: SELECT * FROM users
Suggestion: Add WHERE clause or LIMIT to prevent memory issues
```

### GOOD: With WHERE Clause

```sql
-- ✅ SAFE: Deletes specific user
DELETE FROM users WHERE id = ?

-- ✅ SAFE: Updates specific order
UPDATE orders SET status = 'shipped' WHERE id = ?

-- ✅ SAFE: Returns specific users
SELECT * FROM users WHERE status = 'active' AND create_time > ?
```

## Expected Messages

### DELETE Violation

```
[CRITICAL] No WHERE clause detected in DELETE statement
MapperId: {namespace}.{methodId}
SQL: DELETE FROM {table}
Suggestion: Add WHERE clause to prevent accidental data loss
Risk: Irreversible data deletion
```

### UPDATE Violation

```
[CRITICAL] No WHERE clause detected in UPDATE statement
MapperId: {namespace}.{methodId}
SQL: UPDATE {table} SET ...
Suggestion: Add WHERE clause to limit update scope
Risk: Mass data corruption
```

### SELECT Violation

```
[CRITICAL] No WHERE clause detected in SELECT statement
MapperId: {namespace}.{methodId}
SQL: SELECT * FROM {table}
Suggestion: Add WHERE clause or LIMIT to prevent memory issues
Risk: OutOfMemoryError, application crash
```

## How to Fix

### Option 1: Add WHERE Clause (Recommended)

```sql
-- Before (dangerous)
DELETE FROM users

-- After (safe)
DELETE FROM users WHERE id = ?
```

```java
// Before (dangerous)
@Delete("DELETE FROM users")
void deleteAll();

// After (safe)
@Delete("DELETE FROM users WHERE id = #{id}")
void deleteById(@Param("id") Long id);
```

### Option 2: Add LIMIT (SELECT Only)

```sql
-- Before (dangerous)
SELECT * FROM users

-- After (safer, but still risky)
SELECT * FROM users LIMIT 1000
```

**Note:** LIMIT without WHERE still scans entire table. Use WHERE + LIMIT for best performance.

### Option 3: Use Pagination

```java
// Before (dangerous)
List<User> users = userMapper.selectAll();

// After (safe)
Page<User> users = userMapper.selectPage(
    new Page<>(1, 100),
    new QueryWrapper<User>().eq("status", "active")
);
```

### Option 4: Whitelist Small Tables (Last Resort)

For genuinely small tables (<1000 rows):

```yaml
sql-guard:
  rules:
    no-where-clause:
      whitelist-mapper-ids:
        - "ConfigMapper.selectAll"  # Config table has <100 rows
        - "MetadataMapper.selectAll"  # Metadata table has <50 rows
```

**Caution:** Only whitelist if table size is guaranteed to remain small.

## Configuration

### Enable/Disable

```yaml
sql-guard:
  rules:
    no-where-clause:
      enabled: true  # Default: true
```

### Adjust Risk Level

```yaml
sql-guard:
  rules:
    no-where-clause:
      risk-level: CRITICAL  # Default: CRITICAL
```

**Not recommended:** Downgrading risk level reduces safety. Only consider for development environments.

### Whitelist Specific Mappers

```yaml
sql-guard:
  rules:
    no-where-clause:
      whitelist-mapper-ids:
        - "ConfigMapper.selectAll"
        - "MetadataMapper.selectAll"
```

### Whitelist Specific Tables

```yaml
sql-guard:
  rules:
    no-where-clause:
      whitelist-tables:
        - config
        - metadata
        - system_settings
```

## Edge Cases

### Case 1: Dummy Condition

```sql
-- Has WHERE clause, but ineffective
SELECT * FROM users WHERE 1=1
```

**Behavior:** Passes No WHERE Clause checker (WHERE present)  
**Detection:** Flagged by [Dummy Condition](dummy-condition.md) checker

### Case 2: INSERT Statement

```sql
-- INSERT doesn't have WHERE clause
INSERT INTO users (name, email) VALUES (?, ?)
```

**Behavior:** Skipped (INSERT statements don't have WHERE)  
**Detection:** Not applicable

### Case 3: Dynamic SQL

```xml
<!-- MyBatis dynamic SQL -->
<select id="selectUsers">
    SELECT * FROM users
    <where>
        <if test="status != null">
            AND status = #{status}
        </if>
    </where>
</select>
```

**Behavior:**
- **Static Scanner:** Generates variants (with/without WHERE)
- **Runtime Validator:** Validates resolved SQL after `<if>` evaluation

**Recommendation:** Use dynamic SQL properly to ensure WHERE always present:

```xml
<select id="selectUsers">
    SELECT * FROM users
    <where>
        AND 1=1  <!-- Ensure WHERE always present -->
        <if test="status != null">
            AND status = #{status}
        </if>
    </where>
</select>
```

### Case 4: Subqueries

```sql
-- Main query has WHERE, subquery doesn't
SELECT * FROM users WHERE id IN (
    SELECT user_id FROM orders  -- No WHERE in subquery
)
```

**Behavior:** Subquery flagged as separate violation  
**Detection:** Both main query and subquery validated independently

## Design Reference

See [Implementation Plan - Task 2.2](../../../.apm/Implementation_Plan.md#task-22--nowhereclausechecker-implementation) for complete design details.

## Related Rules

- **[Dummy Condition](dummy-condition.md)** - Detects ineffective WHERE clauses (1=1)
- **[No Condition Pagination](pagination-abuse.md#no-condition-pagination)** - Detects LIMIT without WHERE
- **[No Pagination](no-pagination.md)** - Detects SELECT without LIMIT

## Production Incidents Prevented

### Incident 1: Accidental User Deletion

**Company:** E-commerce platform  
**Incident:** Developer ran `DELETE FROM users` in production console  
**Impact:** 10M user records deleted  
**Recovery:** 8 hours downtime, partial data loss  
**Prevention:** SQL Guard would have blocked execution (BLOCK strategy)

### Incident 2: Mass Order Cancellation

**Company:** Logistics company  
**Incident:** Bug in code executed `UPDATE orders SET status='cancelled'`  
**Impact:** All active orders cancelled  
**Recovery:** Manual data restoration, customer complaints  
**Prevention:** SQL Guard would have blocked execution (BLOCK strategy)

### Incident 3: Memory Exhaustion

**Company:** SaaS platform  
**Incident:** `SELECT * FROM events` loaded 50M rows into memory  
**Impact:** OutOfMemoryError, application crash, 30min downtime  
**Recovery:** Restart with increased heap, fix query  
**Prevention:** SQL Guard would have blocked execution (BLOCK strategy)

## Best Practices

### 1. Always Use WHERE

```sql
-- ❌ Never do this
DELETE FROM users

-- ✅ Always do this
DELETE FROM users WHERE id = ?
```

### 2. Use Dynamic SQL Carefully

```xml
<!-- ❌ Dangerous: WHERE might be empty -->
<select id="selectUsers">
    SELECT * FROM users
    <where>
        <if test="status != null">
            AND status = #{status}
        </if>
    </where>
</select>

<!-- ✅ Safe: WHERE always present -->
<select id="selectUsers">
    SELECT * FROM users
    WHERE 1=1
    <if test="status != null">
        AND status = #{status}
    </if>
</select>
```

### 3. Validate Input Parameters

```java
// ❌ Dangerous: Null status might bypass WHERE
@Select("SELECT * FROM users WHERE status = #{status}")
List<User> selectByStatus(@Param("status") String status);

// ✅ Safe: Validate input
@Select("SELECT * FROM users WHERE status = #{status}")
List<User> selectByStatus(@Param("status") @NotNull String status);
```

### 4. Use Pagination for Large Results

```java
// ❌ Dangerous: Returns all users
List<User> users = userMapper.selectAll();

// ✅ Safe: Use pagination
Page<User> users = userMapper.selectPage(new Page<>(1, 100));
```

## Testing

### Unit Test Example

```java
@Test
public void testNoWhereClauseDetection() {
    // Given
    String sql = "DELETE FROM users";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.DELETE)
        .mapperId("UserMapper.deleteAll")
        .build();
    
    // When
    ValidationResult result = validator.validate(context);
    
    // Then
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertTrue(result.getViolations().get(0).getMessage()
        .contains("No WHERE clause"));
}
```

### Integration Test Example

```java
@Test
public void testDeleteWithoutWhereBlocked() {
    // Given: BLOCK strategy configured
    
    // When/Then: SQLException thrown
    assertThrows(SQLException.class, () -> {
        userMapper.deleteAll();  // DELETE FROM users
    });
    
    // Verify: No data deleted
    assertEquals(initialUserCount, userMapper.count());
}
```

## Next Steps

- **[Dummy Condition Rule](dummy-condition.md)** - Detect ineffective WHERE clauses
- **[Configuration Reference](../configuration-reference.md)** - Configure rule settings
- **[Deployment Guide](../deployment.md)** - Roll out to production safely

---

**Questions?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).


