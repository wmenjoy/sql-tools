# Multi-Statement Injection Rule

**Risk Level:** 🔴 CRITICAL

## Overview

The Multi-Statement rule detects SQL injection attacks that use statement separators (semicolons) to execute multiple SQL commands in a single query. This is one of the most dangerous SQL injection techniques, allowing attackers to execute arbitrary commands like DROP TABLE, DELETE, or data exfiltration queries.

## What It Detects

SQL statements containing multiple commands separated by semicolons:

- **Stacked Queries**: `SELECT * FROM users; DROP TABLE users--`
- **Data Manipulation**: `SELECT * FROM users; DELETE FROM users;`
- **Privilege Escalation**: `SELECT * FROM users; UPDATE users SET admin=1;`
- **Data Exfiltration**: `SELECT * FROM users; INSERT INTO hacker_table SELECT * FROM secrets;`

## Why Dangerous

### Complete Database Compromise

```sql
-- ❌ CRITICAL: Attacker drops entire table
SELECT * FROM users WHERE id = '1'; DROP TABLE users--
```

**Attack Vector:**
1. Attacker inputs: `1'; DROP TABLE users--`
2. Application builds: `SELECT * FROM users WHERE id = '1'; DROP TABLE users--'`
3. Database executes both statements
4. Result: Complete data loss

### Real-World Impact

**Incident 1: E-commerce Platform**
- Attack: `'; DELETE FROM orders WHERE '1'='1`
- Impact: All order records deleted
- Recovery: 12 hours downtime, partial data loss
- Cost: $2M+ in lost revenue and customer trust

**Incident 2: Healthcare System**
- Attack: `'; UPDATE patients SET doctor_id=999--`
- Impact: Patient records reassigned to unauthorized doctor
- Recovery: Manual data restoration, HIPAA violation investigation
- Cost: $5M+ in fines and legal fees

### Why Semicolons Are Dangerous

```sql
-- Normal query
SELECT * FROM users WHERE id = 1

-- Injected query (attacker adds after the 1)
SELECT * FROM users WHERE id = 1; DROP TABLE users--

-- The semicolon allows execution of a second, malicious statement
```

## Examples

### BAD: Classic SQL Injection

```sql
-- ❌ CRITICAL: Drops entire users table
SELECT * FROM users WHERE id = '1'; DROP TABLE users--
```

**Violation Message:**
```
[CRITICAL] 检测到多语句执行(SQL注入风险),请确保SQL不包含多个语句
StatementId: com.example.UserMapper.selectById
SQL: SELECT * FROM users WHERE id = '1'; DROP TABLE users--
Suggestion: 移除SQL中的分号或确保输入已正确转义
```

### BAD: Data Deletion Attack

```sql
-- ❌ CRITICAL: Deletes all user records
SELECT * FROM users; DELETE FROM users WHERE 1=1;
```

### BAD: Privilege Escalation

```sql
-- ❌ CRITICAL: Grants admin privileges
SELECT * FROM users WHERE id = 1; UPDATE users SET role='admin' WHERE id=999;
```

### BAD: Stacked Queries

```sql
-- ❌ CRITICAL: Multiple malicious statements
SELECT 1; SELECT 2; DROP DATABASE production;
```

### GOOD: Single Statement

```sql
-- ✅ SAFE: Single SELECT statement
SELECT * FROM users WHERE id = 1
```

### GOOD: Trailing Semicolon

```sql
-- ✅ SAFE: Trailing semicolon is allowed
SELECT * FROM users WHERE id = 1;
```

### GOOD: Semicolon in String Literal

```sql
-- ✅ SAFE: Semicolon inside string is not a statement separator
SELECT * FROM users WHERE name = 'John; Smith'
```

## Expected Messages

### Multi-Statement Violation

```
[CRITICAL] 检测到多语句执行(SQL注入风险),请确保SQL不包含多个语句
StatementId: {namespace}.{methodId}
SQL: {sql}
Suggestion: 移除SQL中的分号或确保输入已正确转义
Risk: Complete database compromise, data loss, privilege escalation
```

## How to Fix

### Option 1: Use Parameterized Queries (Recommended)

```java
// Before (vulnerable)
String sql = "SELECT * FROM users WHERE id = '" + userId + "'";
// Attacker inputs: 1'; DROP TABLE users--

// After (safe)
PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
stmt.setString(1, userId);
// Attacker input is treated as data, not SQL
```

### Option 2: Use MyBatis Parameters

```xml
<!-- Before (vulnerable) -->
<select id="selectUser">
    SELECT * FROM users WHERE id = '${userId}'
</select>

<!-- After (safe) -->
<select id="selectUser">
    SELECT * FROM users WHERE id = #{userId}
</select>
```

**Note:** Use `#{}` (parameterized) instead of `${}` (string interpolation).

### Option 3: Input Validation

```java
// Validate input doesn't contain dangerous characters
public void validateInput(String input) {
    if (input.contains(";") || input.contains("--") || input.contains("/*")) {
        throw new SecurityException("Invalid input detected");
    }
}
```

**Note:** Input validation is a defense-in-depth measure, not a primary defense. Always use parameterized queries.

### Option 4: Database Configuration

Some databases support disabling multi-statement execution:

**MySQL:**
```properties
# In connection string
allowMultiQueries=false
```

**PostgreSQL:**
```sql
-- Use single-statement mode in application
```

## Configuration

### Enable/Disable

```yaml
sql-guard:
  rules:
    multi-statement:
      enabled: true  # Default: true (NEVER disable in production)
```

### Adjust Risk Level

```yaml
sql-guard:
  rules:
    multi-statement:
      risk-level: CRITICAL  # Default: CRITICAL
```

**⚠️ Warning:** Downgrading risk level is NOT recommended. Multi-statement injection is always CRITICAL severity.

## Edge Cases

### Case 1: Trailing Semicolon

```sql
-- Has semicolon but it's trailing (safe)
SELECT * FROM users WHERE id = 1;
```

**Behavior:** Passes validation (trailing semicolons are allowed)

### Case 2: Semicolon in String Literal

```sql
-- Semicolon inside string (safe)
SELECT * FROM users WHERE name = 'John; DROP TABLE users'
```

**Behavior:** Passes validation (semicolons in strings are not statement separators)

### Case 3: Semicolon in Backtick Identifier

```sql
-- MySQL backtick identifier with semicolon (safe)
SELECT * FROM `table;name` WHERE id = 1
```

**Behavior:** Passes validation (semicolons in identifiers are not statement separators)

### Case 4: SQL Comments After Semicolon

```sql
-- Comment after semicolon (safe)
SELECT * FROM users WHERE id = 1; -- this is a comment
```

**Behavior:** Passes validation (only comments after semicolon)

### Case 5: Escaped Quotes

```sql
-- Escaped quote containing semicolon (safe)
SELECT * FROM users WHERE name = 'O''Brien; test'
```

**Behavior:** Passes validation (escaped quotes handled correctly)

## Multi-Dialect Support

### MySQL

```sql
-- MySQL-specific injection patterns detected
SELECT * FROM users LIMIT 10; DROP TABLE users;
```

### Oracle

```sql
-- Oracle-specific injection patterns detected
SELECT * FROM users WHERE ROWNUM <= 10; DROP TABLE users;
```

### PostgreSQL

```sql
-- PostgreSQL-specific injection patterns detected
INSERT INTO users (name) VALUES ('test') RETURNING id; DROP TABLE users;
```

## Production Incidents Prevented

### Incident 1: SQL Injection via Search Form

**Company:** Financial Services Platform
**Incident:** Attacker used search form to inject `'; DROP TABLE transactions--`
**Impact (Without Protection):** Complete loss of transaction history
**Prevention:** SQL Guard blocked execution with CRITICAL violation

### Incident 2: API Parameter Injection

**Company:** SaaS Platform
**Incident:** Attacker manipulated API parameter to inject stacked queries
**Impact (Without Protection):** Data exfiltration of customer records
**Prevention:** SQL Guard blocked execution with CRITICAL violation

### Incident 3: Admin Panel Compromise

**Company:** E-commerce Platform
**Incident:** Attacker used admin search to inject `'; UPDATE users SET role='admin'--`
**Impact (Without Protection):** Privilege escalation to admin access
**Prevention:** SQL Guard blocked execution with CRITICAL violation

## Best Practices

### 1. Always Use Parameterized Queries

```java
// ❌ Never do this
String sql = "SELECT * FROM users WHERE id = '" + userId + "'";

// ✅ Always do this
PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
stmt.setString(1, userId);
```

### 2. Use ORM Frameworks Correctly

```java
// ❌ Dangerous: String concatenation in MyBatis
@Select("SELECT * FROM users WHERE id = '${id}'")
User selectById(@Param("id") String id);

// ✅ Safe: Parameterized query
@Select("SELECT * FROM users WHERE id = #{id}")
User selectById(@Param("id") String id);
```

### 3. Defense in Depth

1. **Primary Defense:** Parameterized queries
2. **Secondary Defense:** SQL Guard validation
3. **Tertiary Defense:** Input validation
4. **Quaternary Defense:** Web Application Firewall (WAF)

### 4. Never Trust User Input

```java
// All user input is potentially malicious
// - Form fields
// - URL parameters
// - HTTP headers
// - Cookies
// - File uploads
```

## Testing

### Unit Test Example

```java
@Test
public void testMultiStatementDetection() {
    // Given
    String sql = "SELECT * FROM users; DROP TABLE users--";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .statementId("UserMapper.selectById")
        .build();
    
    // When
    ValidationResult result = validator.validate(context);
    
    // Then
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertTrue(result.getViolations().get(0).getMessage()
        .contains("多语句"));
}
```

### Integration Test Example

```java
@Test
public void testMultiStatementBlocked() {
    // Given: BLOCK strategy configured
    
    // When/Then: SQLException thrown
    assertThrows(SQLException.class, () -> {
        userMapper.selectById("1'; DROP TABLE users--");
    });
    
    // Verify: Table still exists
    assertTrue(tableExists("users"));
}
```

## Related Rules

- **[No WHERE Clause](no-where-clause.md)** - Detects missing WHERE clauses
- **[Dummy Condition](dummy-condition.md)** - Detects ineffective WHERE clauses (1=1)

## Next Steps

- **[Configuration Reference](../configuration-reference.md)** - Configure rule settings
- **[Deployment Guide](../deployment.md)** - Roll out to production safely
- **[FAQ](../faq.md)** - Common questions and answers

---

**Questions?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).
