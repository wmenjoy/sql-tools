# Dummy Condition Rule

**Risk Level:** 🟠 HIGH

## Overview

The Dummy Condition rule detects meaningless conditions in WHERE clauses that make filtering ineffective, such as `1=1`, `true`, or `'a'='a'`. These conditions appear to provide safety but actually cause full table scans and can be exploited in SQL injection attacks.

## What It Detects

SQL statements with dummy/meaningless conditions:

- **1=1** - Always true numeric comparison
- **true** - Boolean literal true
- **'a'='a'** - Always true string comparison
- **1** - Non-zero integer (true in boolean context)

## Why Dangerous

### Full Table Scan Disguised as Filtered Query

```sql
-- ❌ HIGH RISK: Looks safe but scans entire table
SELECT * FROM users WHERE 1=1 AND name = ?
```

**Problems:**
- WHERE clause present but ineffective
- Developers assume query is "safe"
- Full table scan occurs if additional conditions evaluate to  empty
- Performance degradation
- Can bypass No WHERE Clause checker

### SQL Injection Vector

```sql
-- ❌ CRITICAL: Injection attack using dummy condition
SELECT * FROM users WHERE 1=1 OR admin = true
```

**Attack Scenario:**
```java
// Vulnerable code
String sql = "SELECT * FROM users WHERE 1=1 AND username = '" + input + "'";

// Attacker input: ' OR 1=1--
// Result: SELECT * FROM users WHERE 1=1 AND username = '' OR 1=1--'
// Effect: Returns all users (authentication bypass)
```

### Dynamic SQL Antipattern

Dummy conditions are often used incorrectly in dynamic SQL:

```xml
<!-- ❌ BAD PRACTICE: Dummy condition -->
<select id="selectUsers">
    SELECT * FROM users WHERE 1=1
    <if test="name != null">
        AND name = #{name}
    </if>
    <if test="status != null">
        AND status = #{status}
    </if>
</select>
```

**Problems:**
- If all `<if>` conditions are null → `WHERE 1=1` (full table scan)
- Better alternatives exist (MyBatis `<where>` tag)
- Misleading code (appears to have WHERE protection)

## Examples

### BAD: Numeric Dummy Condition

```sql
-- ❌ HIGH RISK: 1=1 makes WHERE clause useless
SELECT * FROM users WHERE 1=1
SELECT * FROM users WHERE 1=1 AND name = ?
UPDATE users SET status = 'active' WHERE 1=1
```

**Violation Message:**
```
[HIGH] Dummy condition detected: 1=1
MapperId: com.example.UserMapper.selectUsers
SQL: SELECT * FROM users WHERE 1=1
Suggestion: Remove dummy condition, use dynamic SQL constructs
Risk: Full table scan, SQL injection vector
```

### BAD: Boolean Literal

```sql
-- ❌ HIGH RISK: Boolean true is always true
SELECT * FROM users WHERE true
SELECT * FROM users WHERE true AND email = ?
```

**Violation Message:**
```
[HIGH] Dummy condition detected: true
MapperId: com.example.UserMapper.selectByEmail
SQL: SELECT * FROM users WHERE true AND email = ?
Suggestion: Remove boolean literal, use real conditions
```

### BAD: String Comparison Dummy

```sql
-- ❌ HIGH RISK: 'a'='a' always evaluates to true
SELECT * FROM users WHERE 'a'='a'
SELECT * FROM users WHERE 'x'='x' AND id = ?
```

**Violation Message:**
```
[HIGH] Dummy condition detected: 'a'='a'
MapperId: com.example.UserMapper.selectById
SQL: SELECT * FROM users WHERE 'a'='a' AND id = ?
Suggestion: Remove dummy string comparison
```

### BAD: Integer as Boolean

```sql
-- ❌ HIGH RISK: Non-zero integer treated as true
SELECT * FROM users WHERE 1
SELECT * FROM users WHERE 1 AND role = ?
```

**Violation Message:**
```
[HIGH] Dummy condition detected: 1
MapperId: com.example.UserMapper.selectByRole
SQL: SELECT * FROM users WHERE 1 AND role = ?
Suggestion: Remove integer literal from WHERE clause
```

### GOOD: Real Conditions

```sql
-- ✅ SAFE: Real filtering conditions
SELECT * FROM users WHERE status = 'active'
SELECT * FROM users WHERE id = ? AND deleted = 0
UPDATE users SET status = 'active' WHERE id = ?
```

### GOOD: MyBatis `<where>` Tag

```xml
<!-- ✅ SAFE: Use MyBatis <where> tag instead of dummy condition -->
<select id="selectUsers" resultType="User">
    SELECT * FROM users
    <where>
        <if test="name != null">
            AND name = #{name}
        </if>
        <if test="status != null">
            AND status = #{status}
        </if>
    </where>
</select>
```

**Benefits:**
- `<where>` tag automatically removes leading AND/OR
- No dummy condition needed
- Fails safely if all conditions are null (No WHERE Clause checker catches this)

## Expected Messages

### 1=1 Dummy Condition

```
[HIGH] Dummy condition detected: 1=1
MapperId: {namespace}.{methodId}
SQL: SELECT * FROM {table} WHERE 1=1
Suggestion: Remove dummy condition, use dynamic SQL constructs like <where>
Risk: Full table scan, SQL injection vector
```

### Boolean Literal

```
[HIGH] Dummy condition detected: {true|false}
MapperId: {namespace}.{methodId}
SQL: SELECT * FROM {table} WHERE true
Suggestion: Remove boolean literal, use real filtering conditions
```

### String Comparison Dummy

```
[HIGH] Dummy condition detected: '{string}'='{string}'
MapperId: {namespace}.{methodId}
SQL: SELECT * FROM {table} WHERE 'a'='a'
Suggestion: Remove dummy string comparison
```

## How to Fix

### Option 1: Use MyBatis `<where>` Tag (Recommended)

```xml
<!-- Before (bad) -->
<select id="selectUsers">
    SELECT * FROM users WHERE 1=1
    <if test="name != null">
        AND name = #{name}
    </if>
</select>

<!-- After (good) -->
<select id="selectUsers">
    SELECT * FROM users
    <where>
        <if test="name != null">
            AND name = #{name}
        </if>
    </where>
</select>
```

### Option 2: Use MyBatis `<trim>` Tag

```xml
<!-- Alternative: trim tag -->
<select id="selectUsers">
    SELECT * FROM users
    <trim prefix="WHERE" prefixOverrides="AND|OR">
        <if test="name != null">
            AND name = #{name}
        </if>
        <if test="status != null">
            AND status = #{status}
        </if>
    </trim>
</select>
```

### Option 3: Add Real Default Condition

```xml
<!-- If you need a default filter, use a real condition -->
<select id="selectUsers">
    SELECT * FROM users
    WHERE deleted = 0  <!-- Real condition: soft delete filter -->
    <if test="name != null">
        AND name = #{name}
    </if>
</select>
```

### Option 4: Require At Least One Condition

```java
// Validate input before building query
public List<User> searchUsers(UserSearchDto search) {
    if (search.isEmpty()) {
        throw new IllegalArgumentException(
            "At least one search criteria required"
        );
    }

    return userMapper.selectUsers(search);
}
```

## Configuration

### Enable/Disable

```yaml
sql-guard:
  rules:
    dummy-condition:
      enabled: true  # Default: true
```

### Adjust Risk Level

```yaml
sql-guard:
  rules:
    dummy-condition:
      risk-level: HIGH  # Default: HIGH
```

### Whitelist Specific Mappers

For rare cases where dummy conditions are intentional:

```yaml
sql-guard:
  rules:
    dummy-condition:
      whitelist-mapper-ids:
        - "SpecialMapper.selectAll"  # Documented reason required
```

**Caution:** Whitelisting dummy conditions is rarely justified. Document thoroughly.

## Edge Cases

### Case 1: Dummy Condition with Other Conditions

```sql
-- Dummy condition + real condition
SELECT * FROM users WHERE 1=1 AND status = 'active'
```

**Behavior:** Flagged as violation
**Reason:** Dummy condition is unnecessary and misleading
**Fix:** Remove `1=1`

### Case 2: Comparison with Parameter

```sql
-- This is NOT a dummy condition (compares with parameter)
SELECT * FROM users WHERE id = #{id}
```

**Behavior:** Not flagged (real condition)
**Reason:** Comparison involves parameter, not constant

### Case 3: Dynamic SQL That Becomes Dummy

```xml
<select id="selectUsers">
    SELECT * FROM users
    <where>
        <if test="alwaysTrue">
            AND 1=1
        </if>
    </where>
</select>
```

**Behavior:**
- **Static Scanner:** Generates variant with 1=1, flagged
- **Runtime Validator:** If alwaysTrue=true, flagged

### Case 4: Nested Dummy Conditions

```sql
-- Multiple dummy conditions
SELECT * FROM users WHERE (1=1) AND (true) AND status = ?
```

**Behavior:** All dummy conditions flagged separately
**Fix:** Remove all dummy conditions

## Relationship with Other Rules

### Interaction with No WHERE Clause Rule

```sql
-- Example progression:
-- 1. No WHERE at all → Caught by No WHERE Clause (CRITICAL)
-- 2. WHERE 1=1 → Caught by Dummy Condition (HIGH)
-- 3. WHERE id = ? → Safe ✓
```

### Related Rules

- **[No WHERE Clause](no-where-clause.md)** - Detects complete absence of WHERE
- **[Blacklist Fields](blacklist-fields.md)** - Detects low-selectivity conditions
- **[No Condition Pagination](no-condition-pagination.md)** - Detects LIMIT without real WHERE

## Production Incidents Prevented

### Incident 1: Authentication Bypass

**Company:** Financial services platform
**Incident:** Login query had `WHERE 1=1 AND username = ?`
**Attack:** SQL injection via username field → `' OR 1=1--`
**Impact:** Attacker gained access to any account
**Prevention:** SQL Guard would have warned about dummy condition
**Fix:** Removed `1=1`, used parameterized queries properly

### Incident 2: Admin Panel Full Table Scan

**Company:** SaaS platform
**Incident:** Admin search with `WHERE 1=1` when no filters provided
**Impact:**
- Full table scan on 5M user table
- Page load timeout (30s)
- Database CPU spike
- Customer complaints

**Root Cause:** Developer used `1=1` instead of `<where>` tag
**Prevention:** SQL Guard would have warned (WARN strategy)
**Fix:** Replaced with `<where>` tag, required minimum search criteria

### Incident 3: Data Export Memory Exhaustion

**Company:** Analytics platform
**Incident:** Export query `SELECT * FROM events WHERE 1=1 AND date > ?`
**Impact:**
- Exported all events when date filter was null
- 100M rows loaded into memory
- OutOfMemoryError
- Export job failures

**Root Cause:** `1=1` allowed query to run with no effective filtering
**Prevention:** SQL Guard would have blocked (if configured with BLOCK strategy)
**Fix:** Removed `1=1`, made date filter mandatory

## Best Practices

### 1. Use MyBatis Dynamic SQL Tags

```xml
<!-- ❌ Don't use dummy conditions -->
<select id="selectUsers">
    SELECT * FROM users WHERE 1=1
    <if test="name != null">
        AND name = #{name}
    </if>
</select>

<!-- ✅ Use <where> tag -->
<select id="selectUsers">
    SELECT * FROM users
    <where>
        <if test="name != null">
            AND name = #{name}
        </if>
    </where>
</select>
```

### 2. Add Real Default Filters

```xml
<!-- ✅ Use real default conditions -->
<select id="selectUsers">
    SELECT * FROM users
    WHERE deleted = 0  <!-- Soft delete filter -->
      AND status != 'archived'  <!-- Exclude archived -->
    <if test="name != null">
        AND name = #{name}
    </if>
</select>
```

### 3. Validate Input Requirements

```java
// ✅ Require meaningful search criteria
public List<User> searchUsers(String name, String email) {
    if (name == null && email == null) {
        throw new IllegalArgumentException(
            "At least one search field required"
        );
    }

    return userMapper.selectUsers(name, email);
}
```

### 4. Use QueryWrapper Properly

```java
// ❌ Don't create always-true conditions
wrapper.apply("1=1");

// ✅ Use real conditions
wrapper.eq("status", "active")
       .ne("deleted", 1);
```

## Testing

### Unit Test Example

```java
@Test
public void testDummyConditionDetection() {
    // Given
    String sql = "SELECT * FROM users WHERE 1=1";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .mapperId("UserMapper.selectAll")
        .build();

    // When
    ValidationResult result = validator.validate(context);

    // Then
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertTrue(result.getViolations().get(0).getMessage()
        .contains("Dummy condition detected: 1=1"));
}
```

### Integration Test Example

```java
@Test
public void testDummyConditionWarning() {
    // Given: WARN strategy configured

    // When
    List<User> users = userMapper.selectWithDummyCondition();

    // Then: Returns data but logs warning
    assertNotNull(users);
    verify(logger).warn(contains("Dummy condition detected"));
}
```

## Next Steps

- **[No WHERE Clause Rule](no-where-clause.md)** - Broader protection against missing WHERE
- **[Blacklist Fields Rule](blacklist-fields.md)** - Detect low-selectivity conditions
- **[Configuration Reference](../configuration-reference.md)** - Configure rule settings

---

**Questions?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).
