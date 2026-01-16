# Set Operation Rule

**Risk Level:** 🔴 CRITICAL

## Overview

The Set Operation rule detects SQL set operations (UNION, MINUS, EXCEPT, INTERSECT) that can be exploited for SQL injection attacks, particularly data exfiltration. These operations allow attackers to combine results from unauthorized tables, bypassing application-level access controls.

## What It Detects

SQL statements containing set operations:

- **UNION / UNION ALL** - Combines results from multiple SELECT statements
- **MINUS** - Returns rows from first query not in second (Oracle)
- **EXCEPT** - Returns rows from first query not in second (PostgreSQL/SQL Server)
- **INTERSECT** - Returns rows common to both queries

## Why Dangerous

### Data Exfiltration (UNION Injection)

```sql
-- ❌ CRITICAL: Attacker extracts admin passwords
SELECT username, email FROM users WHERE id = '1' 
UNION SELECT username, password FROM admin_users--
```

**Real-World Impact:**
- Attacker injects UNION clause to extract sensitive data from other tables
- Result: Complete database schema and sensitive data exposed
- Recovery: Data breach notification, credential rotation, forensic analysis
- Cost: GDPR fines, reputation damage, legal liability

### Schema Discovery

```sql
-- ❌ CRITICAL: Attacker discovers database structure
SELECT column_name, table_name FROM users WHERE id = '1'
UNION SELECT column_name, table_name FROM information_schema.columns--
```

**Real-World Impact:**
- Attacker maps entire database schema
- Identifies high-value tables (passwords, credit cards, PII)
- Enables targeted data theft

### Privilege Escalation (INTERSECT)

```sql
-- ❌ CRITICAL: Attacker identifies privileged accounts
SELECT user_id FROM active_sessions 
INTERSECT SELECT id FROM admin_users
```

**Real-World Impact:**
- Attacker correlates data across tables
- Identifies active admin sessions for session hijacking
- Enables privilege escalation attacks

### Data Leakage (MINUS/EXCEPT)

```sql
-- ❌ CRITICAL: Oracle - Attacker finds unblocked users
SELECT id FROM all_users MINUS SELECT id FROM blocked_users
```

```sql
-- ❌ CRITICAL: PostgreSQL - Attacker finds subscribed emails
SELECT email FROM users EXCEPT SELECT email FROM unsubscribed
```

## Examples

### BAD: UNION Injection (Data Exfiltration)

```sql
-- ❌ CRITICAL: Extracts passwords from admin table
SELECT name, email FROM users WHERE id = 1
UNION SELECT username, password FROM admin_users
```

**Violation Message:**
```
[CRITICAL] 检测到SQL集合操作: UNION (可能存在SQL注入风险)
MapperId: com.example.UserMapper.findById
SQL: SELECT name, email FROM users WHERE id = 1 UNION SELECT username, password FROM admin_users
Suggestion: 移除集合操作或将其添加到allowedOperations配置中
```

### BAD: UNION ALL Injection (Full Data Dump)

```sql
-- ❌ CRITICAL: Dumps all data including duplicates
SELECT * FROM products
UNION ALL SELECT * FROM secret_inventory
```

**Violation Message:**
```
[CRITICAL] 检测到SQL集合操作: UNION_ALL (可能存在SQL注入风险)
```

### BAD: MINUS Injection (Oracle-Specific)

```sql
-- ❌ CRITICAL: Oracle - Finds users not in exclusion list
SELECT id FROM users
MINUS SELECT id FROM blocked_users
```

**Violation Message:**
```
[CRITICAL] 检测到SQL集合操作: MINUS (可能存在SQL注入风险)
```

### BAD: EXCEPT Injection (PostgreSQL-Specific)

```sql
-- ❌ CRITICAL: PostgreSQL - Finds active subscribers
SELECT email FROM all_users
EXCEPT SELECT email FROM unsubscribed_users
```

**Violation Message:**
```
[CRITICAL] 检测到SQL集合操作: EXCEPT (可能存在SQL注入风险)
```

### BAD: INTERSECT Injection (Data Correlation)

```sql
-- ❌ CRITICAL: Correlates data across security boundaries
SELECT user_id FROM orders
INTERSECT SELECT id FROM premium_users
```

**Violation Message:**
```
[CRITICAL] 检测到SQL集合操作: INTERSECT (可能存在SQL注入风险)
```

### BAD: Multiple Set Operations

```sql
-- ❌ CRITICAL: Complex attack with multiple operations
SELECT a FROM t1
UNION SELECT b FROM t2
UNION SELECT c FROM t3
```

**Violation Message:**
```
[CRITICAL] 检测到SQL集合操作: UNION (可能存在SQL注入风险)
[CRITICAL] 检测到SQL集合操作: UNION (可能存在SQL注入风险)
```

### GOOD: Normal SELECT Without Set Operations

```sql
-- ✅ SAFE: Simple query without set operations
SELECT * FROM users WHERE id = ?

-- ✅ SAFE: JOIN instead of UNION
SELECT u.*, o.* FROM users u 
LEFT JOIN orders o ON u.id = o.user_id
WHERE u.status = 'active'

-- ✅ SAFE: Subquery in WHERE clause
SELECT * FROM users WHERE id IN (
    SELECT user_id FROM orders WHERE status = 'completed'
)

-- ✅ SAFE: CTE (Common Table Expression)
WITH active_users AS (
    SELECT * FROM users WHERE status = 'active'
)
SELECT * FROM active_users WHERE created_at > '2023-01-01'
```

### GOOD: Allowed Operations When Configured

```sql
-- ✅ SAFE: UNION allowed in config (allowedOperations: [UNION])
SELECT id, name FROM table_a
UNION
SELECT id, name FROM table_b
```

## Expected Messages

### UNION Violation

```
[CRITICAL] 检测到SQL集合操作: UNION (可能存在SQL注入风险)
MapperId: {namespace}.{methodId}
SQL: SELECT ... UNION SELECT ...
Suggestion: 移除集合操作或将其添加到allowedOperations配置中
Risk: Data exfiltration, schema discovery
```

### MINUS Violation (Oracle)

```
[CRITICAL] 检测到SQL集合操作: MINUS (可能存在SQL注入风险)
MapperId: {namespace}.{methodId}
SQL: SELECT ... MINUS SELECT ...
Suggestion: 移除集合操作或将其添加到allowedOperations配置中
Risk: Data leakage through set difference
```

### EXCEPT Violation (PostgreSQL/SQL Server)

```
[CRITICAL] 检测到SQL集合操作: EXCEPT (可能存在SQL注入风险)
MapperId: {namespace}.{methodId}
SQL: SELECT ... EXCEPT SELECT ...
Suggestion: 移除集合操作或将其添加到allowedOperations配置中
Risk: Data leakage through set difference
```

### INTERSECT Violation

```
[CRITICAL] 检测到SQL集合操作: INTERSECT (可能存在SQL注入风险)
MapperId: {namespace}.{methodId}
SQL: SELECT ... INTERSECT SELECT ...
Suggestion: 移除集合操作或将其添加到allowedOperations配置中
Risk: Unauthorized data correlation
```

## How to Fix

### Option 1: Remove Set Operations (Recommended)

```sql
-- Before (dangerous)
SELECT name FROM users WHERE id = ? 
UNION SELECT name FROM admins

-- After (safe) - Use separate queries
SELECT name FROM users WHERE id = ?
-- Then in application code, query admins separately if authorized
```

### Option 2: Use JOINs Instead

```sql
-- Before (dangerous)
SELECT id FROM active_users
INTERSECT SELECT id FROM premium_users

-- After (safe) - Use JOIN
SELECT a.id FROM active_users a
INNER JOIN premium_users p ON a.id = p.id
```

### Option 3: Use Subqueries

```sql
-- Before (dangerous)
SELECT email FROM users
EXCEPT SELECT email FROM unsubscribed

-- After (safe) - Use NOT IN subquery
SELECT email FROM users
WHERE email NOT IN (SELECT email FROM unsubscribed)
```

### Option 4: Allowlist Specific Operations (Use with Caution)

For legitimate reporting or analytics use cases:

```yaml
sql-guard:
  rules:
    set-operation:
      enabled: true
      allowed-operations:
        - UNION       # Allow for report aggregation
        - INTERSECT   # Allow for data validation
```

**Warning:** Only allowlist operations for specific, verified use cases. Ensure input validation is in place.

## Configuration

### Enable/Disable

```yaml
sql-guard:
  rules:
    set-operation:
      enabled: true  # Default: true
```

### Adjust Risk Level

```yaml
sql-guard:
  rules:
    set-operation:
      risk-level: CRITICAL  # Default: CRITICAL
```

**Not recommended:** Downgrading risk level reduces security protection.

### Allow Specific Operations

```yaml
sql-guard:
  rules:
    set-operation:
      allowed-operations: []  # Default: empty (block all)
```

To allow specific operations:

```yaml
sql-guard:
  rules:
    set-operation:
      allowed-operations:
        - UNION         # Standard UNION (removes duplicates)
        - UNION_ALL     # UNION keeping all rows
        - INTERSECT     # Set intersection
        # - MINUS       # Oracle-specific (not allowed)
        # - EXCEPT      # PostgreSQL-specific (not allowed)
```

### Valid Operation Names

| Operation | Description | Dialects |
|-----------|-------------|----------|
| UNION | Combines results, removes duplicates | All |
| UNION_ALL | Combines results, keeps duplicates | All |
| MINUS | Set difference | Oracle |
| MINUS_ALL | Set difference, keeps duplicates | Oracle |
| EXCEPT | Set difference | PostgreSQL, SQL Server |
| EXCEPT_ALL | Set difference, keeps duplicates | PostgreSQL |
| INTERSECT | Set intersection | All |
| INTERSECT_ALL | Set intersection, keeps duplicates | PostgreSQL |

## Edge Cases

### Case 1: Subquery with Set Operation

```sql
-- Subquery contains UNION
SELECT * FROM users WHERE id IN (
    SELECT id FROM table_a UNION SELECT id FROM table_b
)
```

**Behavior:** The outer SELECT passes, but if the subquery's UNION is detected, it will be flagged.

### Case 2: CTE with Set Operation

```sql
-- CTE contains UNION
WITH combined AS (
    SELECT * FROM t1 UNION SELECT * FROM t2
)
SELECT * FROM combined
```

**Behavior:** Set operation in CTE is detected and flagged.

### Case 3: Dynamic SQL

```xml
<!-- MyBatis dynamic SQL -->
<select id="searchUsers">
    SELECT * FROM users WHERE status = 'active'
    <if test="includeAdmins">
        UNION SELECT * FROM admin_users
    </if>
</select>
```

**Behavior:**
- **Static Scanner:** Generates variants (with/without UNION)
- **Runtime Validator:** Validates resolved SQL after condition evaluation

## Multi-Dialect Support

### MySQL

```sql
-- Supported operations
SELECT * FROM t1 UNION SELECT * FROM t2
SELECT * FROM t1 UNION ALL SELECT * FROM t2
```

### Oracle

```sql
-- Supported operations (includes MINUS)
SELECT * FROM t1 UNION SELECT * FROM t2
SELECT * FROM t1 MINUS SELECT * FROM t2
SELECT * FROM t1 INTERSECT SELECT * FROM t2
```

### PostgreSQL

```sql
-- Supported operations (includes EXCEPT)
SELECT * FROM t1 UNION SELECT * FROM t2
SELECT * FROM t1 EXCEPT SELECT * FROM t2
SELECT * FROM t1 INTERSECT SELECT * FROM t2
```

### SQL Server

```sql
-- Supported operations
SELECT * FROM t1 UNION SELECT * FROM t2
SELECT * FROM t1 EXCEPT SELECT * FROM t2
SELECT * FROM t1 INTERSECT SELECT * FROM t2
```

## Production Incidents Prevented

### Incident 1: Customer Data Breach via UNION Injection

**Company:** E-commerce platform  
**Incident:** Attacker injected UNION clause to extract customer credit card data  
**Impact:** 500,000 credit card numbers exposed  
**Recovery:** PCI-DSS breach notification, card replacement costs  
**Prevention:** SQL Guard would have blocked the UNION operation

### Incident 2: Admin Credential Theft

**Company:** SaaS platform  
**Incident:** UNION injection extracted admin password hashes  
**Impact:** Complete admin account takeover  
**Recovery:** Full password reset, security audit  
**Prevention:** SQL Guard would have detected and blocked UNION

### Incident 3: Schema Discovery Attack

**Company:** Financial services  
**Incident:** Attacker used UNION with information_schema to map database  
**Impact:** Complete database structure exposed, enabling targeted attacks  
**Recovery:** Database restructuring, enhanced monitoring  
**Prevention:** SQL Guard would have blocked schema enumeration

## Best Practices

### 1. Block All Set Operations by Default

```yaml
sql-guard:
  rules:
    set-operation:
      enabled: true
      allowed-operations: []  # Block all
```

### 2. Use Parameterized Queries

```java
// ❌ Dangerous: String concatenation
String sql = "SELECT * FROM users WHERE id = '" + userId + "'";

// ✅ Safe: Parameterized query
@Select("SELECT * FROM users WHERE id = #{id}")
User findById(@Param("id") Long id);
```

### 3. Validate Input Before SQL Construction

```java
// Validate input doesn't contain SQL keywords
if (input.toUpperCase().contains("UNION") || 
    input.toUpperCase().contains("EXCEPT")) {
    throw new SecurityException("Invalid input detected");
}
```

### 4. Use Application-Level Data Combination

```java
// ❌ Dangerous: UNION in SQL
String sql = "SELECT * FROM users UNION SELECT * FROM admins";

// ✅ Safe: Combine in application
List<User> users = userMapper.findAll();
List<Admin> admins = adminMapper.findAll();
List<Person> combined = Stream.concat(users.stream(), admins.stream())
    .collect(Collectors.toList());
```

## Testing

### Unit Test Example

```java
@Test
public void testUnionInjectionDetection() {
    // Given
    String sql = "SELECT name FROM users UNION SELECT password FROM admins";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .statementId("UserMapper.search")
        .statement(CCJSqlParserUtil.parse(sql))
        .build();
    
    // When
    ValidationResult result = validator.validate(context);
    
    // Then
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertTrue(result.getViolations().get(0).getMessage().contains("UNION"));
}
```

### Integration Test Example

```java
@Test
public void testUnionInjectionBlocked() {
    // Given: BLOCK strategy configured
    
    // When/Then: SQLException thrown
    assertThrows(SQLException.class, () -> {
        userMapper.searchWithInjection("1' UNION SELECT password FROM admins--");
    });
}
```

## Related Rules

- **[Multi-Statement Rule](multi-statement.md)** - Detects multi-statement injection (semicolon attacks)
- **[SQL Comment Rule](sql-comment.md)** - Detects comment-based injection
- **[No WHERE Clause Rule](no-where-clause.md)** - Detects missing WHERE clause

## Next Steps

- **[Multi-Statement Rule](multi-statement.md)** - Protect against stacked query injection
- **[Configuration Reference](../configuration-reference.md)** - Configure rule settings
- **[Deployment Guide](../deployment.md)** - Roll out to production safely

---

**Questions?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).
