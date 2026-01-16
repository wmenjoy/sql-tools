# SQL Comment Rule

**Risk Level:** 🔴 CRITICAL

## Overview

The SQL Comment rule detects SQL comments (`--`, `/* */`, `#`) that can be used by attackers to bypass security detection, hide malicious code, or comment out WHERE clauses. This is a critical security check that helps prevent SQL injection attacks.

## What It Detects

SQL comments in three formats:

- **Single-line comments (`--`)**: SQL standard, comments out rest of line
- **Multi-line comments (`/* */`)**: SQL standard, can hide code anywhere in query
- **MySQL hash comments (`#`)**: MySQL-specific, comments out rest of line

## Why Dangerous

### 1. Single-Line Comments (`--`)

```sql
-- ❌ CRITICAL: Comments out password check
SELECT * FROM users WHERE username = 'admin'-- AND password = 'wrong'

-- ❌ CRITICAL: Comments out WHERE clause
DELETE FROM users WHERE id = 1 -- OR 1=1
```

**Attack Pattern:**
- Attacker inputs: `admin'--`
- Original query: `SELECT * FROM users WHERE username = 'admin'--' AND password = 'x'`
- Effect: Password check is commented out, authentication bypassed

### 2. Multi-Line Comments (`/* */`)

```sql
-- ❌ CRITICAL: Hides malicious code
SELECT * FROM users /* UNION SELECT * FROM passwords */

-- ❌ CRITICAL: Comments out security conditions
SELECT * FROM users WHERE /* id = 1 AND */ admin = 1

-- ❌ CRITICAL: Obfuscates injection
SELECT * FROM users WHERE id = 1/**/UNION/**/SELECT/**/password/**/FROM/**/admins
```

**Attack Pattern:**
- Comments can appear anywhere in SQL
- Used to hide malicious UNION injections
- Used to bypass WAF (Web Application Firewall) detection
- Used to comment out WHERE clauses for mass data access

### 3. MySQL Hash Comments (`#`)

```sql
-- ❌ CRITICAL: MySQL-specific comment injection
SELECT * FROM users WHERE id = 1 # AND deleted = 0

-- ❌ CRITICAL: Authentication bypass
SELECT * FROM users WHERE username = 'admin'# AND password = 'x'
```

**Attack Pattern:**
- MySQL-specific syntax
- Same effect as `--` comments
- Often used when `--` is filtered

## Real-World Attack Examples

### Example 1: Authentication Bypass

```sql
-- Normal login query
SELECT * FROM users WHERE username = 'user' AND password = 'pass'

-- Attacker input: username = "admin'--"
SELECT * FROM users WHERE username = 'admin'--' AND password = 'wrong'
-- Result: Password check bypassed, admin access granted
```

### Example 2: Data Exfiltration

```sql
-- Normal query
SELECT name FROM users WHERE id = 1

-- Attacker input: id = "1 /* UNION SELECT password FROM admins */"
SELECT name FROM users WHERE id = 1 /* UNION SELECT password FROM admins */
-- Result: Passwords potentially exposed (if comment is not detected)
```

### Example 3: WHERE Clause Removal

```sql
-- Normal delete
DELETE FROM users WHERE id = 1

-- Attacker input: id = "1 OR 1=1 --"
DELETE FROM users WHERE id = 1 OR 1=1 -- AND status = 'inactive'
-- Result: All users deleted
```

## Examples

### BAD: Single-Line Comment

```sql
-- ❌ CRITICAL: Comment detected
SELECT * FROM users WHERE id = 1 -- this is a comment
```

**Violation Message:**
```
[CRITICAL] 检测到SQL注释: 单行注释(--) (可能存在SQL注入风险)
MapperId: com.example.UserMapper.selectById
SQL: SELECT * FROM users WHERE id = 1 -- this is a comment
Suggestion: 移除SQL中的单行注释(--)，注释可能被用于绕过安全检测或隐藏恶意代码
```

### BAD: Multi-Line Comment

```sql
-- ❌ CRITICAL: Comment detected
SELECT /* hidden */ * FROM users
```

**Violation Message:**
```
[CRITICAL] 检测到SQL注释: 多行注释(/* */) (可能存在SQL注入风险)
MapperId: com.example.UserMapper.selectAll
SQL: SELECT /* hidden */ * FROM users
Suggestion: 移除SQL中的多行注释(/* */)，注释可能被用于绕过安全检测或隐藏恶意代码
```

### BAD: MySQL Hash Comment

```sql
-- ❌ CRITICAL: MySQL comment detected
SELECT * FROM users # WHERE deleted = 0
```

**Violation Message:**
```
[CRITICAL] 检测到SQL注释: MySQL注释(#) (可能存在SQL注入风险)
MapperId: com.example.UserMapper.selectActive
SQL: SELECT * FROM users # WHERE deleted = 0
Suggestion: 移除SQL中的MySQL注释(#)，注释可能被用于绕过安全检测或隐藏恶意代码
```

### GOOD: Clean SQL Without Comments

```sql
-- ✅ SAFE: No comments
SELECT * FROM users WHERE id = ?

-- ✅ SAFE: No comments
UPDATE users SET name = ? WHERE id = ?

-- ✅ SAFE: No comments
DELETE FROM users WHERE id = ?
```

### GOOD: String Literals Containing Comment Markers

```sql
-- ✅ SAFE: Comment marker is inside string literal
SELECT * FROM users WHERE name = '--admin'

-- ✅ SAFE: Comment marker is inside string literal
SELECT * FROM users WHERE description = '/* test */'

-- ✅ SAFE: Hash inside string
SELECT * FROM users WHERE tag = '#trending'
```

### GOOD: Oracle Optimizer Hints (When Allowed)

```sql
-- ✅ SAFE: Oracle hint when allowHintComments=true
SELECT /*+ INDEX(users idx_email) */ * FROM users WHERE email = ?

-- ✅ SAFE: Multiple hints when allowed
SELECT /*+ FIRST_ROWS(10) INDEX(users idx_name) */ * FROM users WHERE name LIKE ?
```

## Configuration

### Enable/Disable

```yaml
sql-guard:
  rules:
    sql-comment:
      enabled: true  # Default: true
```

### Adjust Risk Level

```yaml
sql-guard:
  rules:
    sql-comment:
      risk-level: CRITICAL  # Default: CRITICAL
```

**Not recommended:** Downgrading risk level reduces security protection.

### Allow Oracle Optimizer Hints

For applications using Oracle optimizer hints for performance tuning:

```yaml
sql-guard:
  rules:
    sql-comment:
      allow-hint-comments: true  # Default: false
```

**When to use `allow-hint-comments: true`:**
- Your application uses Oracle database
- DBA has approved specific optimizer hints
- Hints are hardcoded in mapper files (not from user input)

**Security Note:** Even with `allow-hint-comments: true`:
- Single-line (`--`) comments are still blocked
- MySQL (`#`) comments are still blocked
- Only `/*+ ... */` format hints are allowed
- Non-hint multi-line comments (`/* */`) are still blocked

### Programmatic Configuration

```java
// Default: Block all comments including hints
SqlCommentConfig config = new SqlCommentConfig();
SqlCommentChecker checker = new SqlCommentChecker(config);

// Allow Oracle optimizer hints
SqlCommentConfig hintConfig = new SqlCommentConfig(true, true);
SqlCommentChecker hintChecker = new SqlCommentChecker(hintConfig);

// Disable checker (not recommended)
SqlCommentConfig disabledConfig = new SqlCommentConfig(false);
SqlCommentChecker disabledChecker = new SqlCommentChecker(disabledConfig);
```

## How to Fix

### Option 1: Remove Comments (Recommended)

```sql
-- Before (dangerous)
SELECT * FROM users WHERE id = 1 -- get user by id

-- After (safe)
SELECT * FROM users WHERE id = 1
```

### Option 2: Use Prepared Statements

Ensure all user input is parameterized:

```java
// ❌ Dangerous: String concatenation
String sql = "SELECT * FROM users WHERE name = '" + userInput + "'";

// ✅ Safe: Prepared statement
String sql = "SELECT * FROM users WHERE name = ?";
PreparedStatement ps = conn.prepareStatement(sql);
ps.setString(1, userInput);
```

### Option 3: Input Validation

Validate and sanitize user input before SQL construction:

```java
// Validate input doesn't contain comment markers
if (userInput.contains("--") || userInput.contains("/*") || userInput.contains("#")) {
    throw new IllegalArgumentException("Invalid input");
}
```

### Option 4: Configure Hint Allowance (For Oracle)

If you legitimately need Oracle optimizer hints:

```yaml
sql-guard:
  rules:
    sql-comment:
      allow-hint-comments: true
```

## Edge Cases

### Case 1: Comment Markers in String Literals

```sql
-- Comment marker inside string is SAFE
SELECT * FROM users WHERE name = '--admin'
```

**Behavior:** Passes - the `--` is inside a string literal, not an actual comment.

### Case 2: Oracle Hints vs Regular Comments

```sql
-- Oracle hint (/*+ ... */)
SELECT /*+ INDEX(users idx_email) */ * FROM users

-- Regular comment (/* ... */)
SELECT /* comment */ * FROM users
```

**Behavior:**
- With `allow-hint-comments: false` (default): Both fail
- With `allow-hint-comments: true`: Hint passes, regular comment fails

### Case 3: Fake Hints

```sql
-- Space before + makes it NOT a valid hint
SELECT /* + INDEX(users idx_email) */ * FROM users
```

**Behavior:** Fails - JSqlParser doesn't recognize this as a valid hint.

### Case 4: Multiple Comments

```sql
-- Multiple comments in one SQL
SELECT * FROM users -- comment1
WHERE id = 1 /* comment2 */
```

**Behavior:** Fails with 2 violations (one per comment).

## Detection Algorithm

The SqlCommentChecker uses SQL string parsing (not AST) because JSqlParser does not preserve comments:

1. **Character-by-character scan** of SQL string
2. **Track string literal boundaries** (', ", `) to avoid false positives
3. **Detect comment markers** outside string literals:
   - `--` for single-line comments
   - `/*` for multi-line comments (and `/*+` for hints)
   - `#` for MySQL comments
4. **Filter hint comments** if `allowHintComments=true`
5. **Report violations** for remaining comments

**Performance:** ~1-2ms overhead for string parsing.

## Multi-Dialect Support

| Dialect | `--` Comments | `/* */` Comments | `#` Comments | `/*+ */` Hints |
|---------|--------------|------------------|--------------|----------------|
| MySQL | ✅ Detected | ✅ Detected | ✅ Detected | N/A |
| Oracle | ✅ Detected | ✅ Detected | N/A | ✅ Configurable |
| PostgreSQL | ✅ Detected | ✅ Detected | N/A | N/A |
| SQL Server | ✅ Detected | ✅ Detected | N/A | N/A |

## Testing

### Unit Test Example

```java
@Test
public void testCommentDetection() {
    // Given
    String sql = "SELECT * FROM users -- comment";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .statementId("UserMapper.selectAll")
        .build();
    
    // When
    ValidationResult result = validator.validate(context);
    
    // Then
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertTrue(result.getViolations().get(0).getMessage()
        .contains("单行注释(--)"));
}
```

### Integration Test Example

```java
@Test
public void testCommentBlocked() {
    // Given: BLOCK strategy configured
    
    // When/Then: SQLException thrown
    assertThrows(SQLException.class, () -> {
        userMapper.selectByName("admin'--");
    });
}
```

## Related Rules

- **[Multi-Statement Rule](multi-statement.md)** - Detects SQL injection via multiple statements
- **[Set Operation Rule](set-operation.md)** - Detects UNION injection attacks
- **[No WHERE Clause Rule](no-where-clause.md)** - Detects missing WHERE clauses
- **[Dummy Condition Rule](dummy-condition.md)** - Detects ineffective WHERE clauses (1=1)

## Security Best Practices

### 1. Always Use Parameterized Queries

```java
// ❌ Never do this
String sql = "SELECT * FROM users WHERE name = '" + input + "'";

// ✅ Always do this
String sql = "SELECT * FROM users WHERE name = ?";
```

### 2. Validate Input at Application Layer

```java
// Reject input containing comment markers
if (containsSqlCommentMarkers(input)) {
    throw new SecurityException("Invalid input");
}
```

### 3. Use SQL Guard in BLOCK Mode

```yaml
sql-guard:
  violation-strategy: BLOCK  # Block dangerous SQL
```

### 4. Monitor and Alert

```yaml
sql-guard:
  violation-strategy: LOG_AND_CONTINUE
  audit:
    enabled: true
    alert-on-critical: true
```

## Next Steps

- **[Configuration Reference](../configuration-reference.md)** - Configure rule settings
- **[Deployment Guide](../deployment.md)** - Roll out to production safely
- **[FAQ](../faq.md)** - Common questions and answers

---

**Questions?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).
