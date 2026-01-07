# Denied Table Rule

**Risk Level:** 🔴 CRITICAL

## Overview

The Denied Table rule enforces table-level access control by preventing access to sensitive tables through a configurable blacklist with wildcard pattern support. It extracts all table references from SQL statements (FROM, JOIN, subqueries, CTEs) and blocks access to any table matching the denied patterns.

## What It Detects

SQL statements accessing denied tables:

- **SELECT** from denied tables (including JOINs, subqueries, CTEs)
- **INSERT** into denied tables
- **UPDATE** on denied tables
- **DELETE** from denied tables

## Why Dangerous

### Unauthorized Data Access

```sql
-- ❌ CRITICAL: Accessing system configuration table
SELECT * FROM sys_config WHERE key = 'db.password'

-- ❌ CRITICAL: Reading admin user credentials
SELECT password_hash FROM admin_users WHERE username = 'root'
```

**Real-World Impact:**
- Attacker gains access to sensitive system configuration
- Credential theft leading to privilege escalation
- Compliance violations (GDPR, PCI-DSS, HIPAA)

### Audit Log Tampering

```sql
-- ❌ CRITICAL: Deleting audit trail
DELETE FROM audit_log WHERE user_id = 12345

-- ❌ CRITICAL: Modifying audit records
UPDATE audit_log SET action = 'READ' WHERE action = 'DELETE'
```

**Real-World Impact:**
- Evidence destruction
- Compliance audit failures
- Inability to trace security incidents

### System Table Manipulation

```sql
-- ❌ CRITICAL: Modifying system permissions
UPDATE sys_permission SET level = 'ADMIN' WHERE user_id = 12345

-- ❌ CRITICAL: Creating backdoor accounts
INSERT INTO sys_user (name, role) VALUES ('backdoor', 'superadmin')
```

**Real-World Impact:**
- Privilege escalation
- Persistent unauthorized access
- System integrity compromise

## Wildcard Pattern Semantics

The denied table configuration supports wildcard patterns using asterisk (`*`).

### Pattern Matching Rules

| Pattern | Matches | Does NOT Match |
|---------|---------|----------------|
| `sys_*` | `sys_user`, `sys_config`, `sys_role` | `system`, `sys_user_detail` |
| `admin_*` | `admin_users`, `admin_config` | `administrator`, `admin_user_roles` |
| `audit_log` | `audit_log` (exact) | `audit_log_backup`, `audit_logs` |

### Why This Matters

**CRITICAL**: The asterisk (`*`) matches one or more characters that do NOT include additional underscores. This provides word-boundary-like matching:

- `sys_*` matches tables with `sys_` prefix followed by a single word
- `sys_*` does NOT match `system` (missing underscore)
- `sys_*` does NOT match `sys_user_detail` (extra underscore creates new word)

This prevents overly broad matching that could accidentally block legitimate tables.

## Examples

### BAD: SELECT from Denied Table

```sql
-- ❌ CRITICAL: Direct access to denied table
SELECT * FROM sys_user WHERE id = 1
```

**Violation Message:**
```
[CRITICAL] Access to denied table 'sys_user' is not allowed
MapperId: com.example.UserMapper.getSystemUser
SQL: SELECT * FROM sys_user WHERE id = 1
Suggestion: Remove access to denied tables or request access permission
```

### BAD: Denied Table in JOIN

```sql
-- ❌ CRITICAL: Denied table accessed via JOIN
SELECT u.*, r.* FROM users u 
JOIN sys_role r ON u.role_id = r.id
```

**Violation Message:**
```
[CRITICAL] Access to denied table 'sys_role' is not allowed
MapperId: com.example.UserMapper.getUserWithRole
SQL: SELECT u.*, r.* FROM users u JOIN sys_role r ON u.role_id = r.id
Suggestion: Remove access to denied tables or request access permission
```

### BAD: Denied Table in Subquery

```sql
-- ❌ CRITICAL: Denied table accessed via subquery
SELECT * FROM users 
WHERE role_id IN (SELECT id FROM sys_role WHERE name = 'admin')
```

**Violation Message:**
```
[CRITICAL] Access to denied table 'sys_role' is not allowed
MapperId: com.example.UserMapper.getAdminUsers
SQL: SELECT * FROM users WHERE role_id IN (SELECT id FROM sys_role WHERE name = 'admin')
Suggestion: Remove access to denied tables or request access permission
```

### BAD: Denied Table in CTE

```sql
-- ❌ CRITICAL: Denied table accessed via CTE (WITH clause)
WITH config AS (SELECT * FROM sys_config WHERE active = 1)
SELECT * FROM config WHERE key LIKE 'db.%'
```

**Violation Message:**
```
[CRITICAL] Access to denied table 'sys_config' is not allowed
MapperId: com.example.ConfigMapper.getDbConfig
SQL: WITH config AS (SELECT * FROM sys_config WHERE active = 1) SELECT * FROM config WHERE key LIKE 'db.%'
Suggestion: Remove access to denied tables or request access permission
```

### GOOD: Accessing Allowed Tables

```sql
-- ✅ SAFE: Accessing regular application tables
SELECT * FROM users WHERE status = 'active'

-- ✅ SAFE: 'system' does NOT match 'sys_*' pattern (no underscore)
SELECT * FROM system WHERE id = 1

-- ✅ SAFE: 'sys_user_detail' does NOT match 'sys_*' pattern (extra underscore)
SELECT * FROM sys_user_detail WHERE user_id = 1

-- ✅ SAFE: JOIN with allowed tables only
SELECT u.*, o.* FROM users u 
JOIN orders o ON u.id = o.user_id
```

## Configuration

### Enable/Disable

```yaml
sql-guard:
  rules:
    denied-table:
      enabled: true  # Default: true
```

### Configure Denied Tables

```yaml
sql-guard:
  rules:
    denied-table:
      enabled: true
      denied-tables:
        - "sys_*"           # Block all sys_ prefixed tables
        - "admin_*"         # Block all admin_ prefixed tables
        - "audit_log"       # Block specific table (exact match)
        - "sensitive_data"  # Block specific table (exact match)
```

### Programmatic Configuration

```java
DeniedTableConfig config = new DeniedTableConfig();
config.setEnabled(true);

List<String> deniedTables = new ArrayList<>();
deniedTables.add("sys_*");           // Wildcard pattern
deniedTables.add("admin_*");         // Wildcard pattern
deniedTables.add("audit_log");       // Exact match
deniedTables.add("sensitive_data");  // Exact match
config.setDeniedTables(deniedTables);

DeniedTableChecker checker = new DeniedTableChecker(config);
```

## Table Extraction Coverage

The checker extracts tables from all SQL locations:

| Location | Example SQL | Detection |
|----------|-------------|-----------|
| FROM clause | `SELECT * FROM sys_user` | ✅ Detected |
| JOIN clause | `SELECT * FROM a JOIN sys_user ON ...` | ✅ Detected |
| LEFT/RIGHT JOIN | `SELECT * FROM a LEFT JOIN sys_user ON ...` | ✅ Detected |
| Subquery (WHERE) | `SELECT * FROM a WHERE id IN (SELECT id FROM sys_user)` | ✅ Detected |
| Subquery (FROM) | `SELECT * FROM (SELECT * FROM sys_user) t` | ✅ Detected |
| CTE (WITH clause) | `WITH cte AS (SELECT * FROM sys_user) SELECT * FROM cte` | ✅ Detected |
| INSERT INTO | `INSERT INTO sys_user (name) VALUES ('test')` | ✅ Detected |
| UPDATE | `UPDATE sys_user SET name = 'test' WHERE id = 1` | ✅ Detected |
| DELETE FROM | `DELETE FROM sys_user WHERE id = 1` | ✅ Detected |

## Edge Cases

### Case 1: Table Aliases

```sql
-- Table alias 'u' does NOT affect detection
-- Actual table name 'sys_user' is extracted and checked
SELECT u.* FROM sys_user AS u WHERE u.id = 1
```

**Behavior:** Correctly detects `sys_user` (not the alias `u`)

### Case 2: Schema-Qualified Tables

```sql
-- Schema prefix is stripped for pattern matching
SELECT * FROM mydb.sys_user WHERE id = 1
```

**Behavior:** Extracts `sys_user` from `mydb.sys_user` and checks against patterns

### Case 3: Quoted Identifiers

```sql
-- MySQL backticks
SELECT * FROM `sys_user` WHERE id = 1

-- Oracle/PostgreSQL double quotes
SELECT * FROM "SYS_USER" WHERE id = 1

-- SQL Server brackets
SELECT * FROM [sys_user] WHERE id = 1
```

**Behavior:** Removes delimiters before matching (case-insensitive)

### Case 4: Empty Denied List

```yaml
sql-guard:
  rules:
    denied-table:
      enabled: true
      denied-tables: []  # Empty list
```

**Behavior:** All tables are allowed when denied list is empty

### Case 5: Case-Insensitive Matching

```sql
-- All of these match 'sys_*' pattern
SELECT * FROM sys_user WHERE id = 1
SELECT * FROM SYS_USER WHERE id = 1
SELECT * FROM Sys_User WHERE id = 1
```

**Behavior:** Pattern matching is case-insensitive

## Security Note

**Defense-in-Depth**: This checker provides an application-level protection layer for table access control. Database-level permissions (GRANT/REVOKE) remain the primary access control mechanism.

Use this checker to:
- Prevent accidental access to sensitive tables in application code
- Add an extra layer of protection against SQL injection attacks
- Enforce table access policies at the application level
- Detect and block unauthorized table access attempts

## How to Fix

### Option 1: Use Allowed Tables

```sql
-- Before (denied)
SELECT * FROM sys_user WHERE id = 1

-- After (use application-level user table)
SELECT * FROM app_users WHERE id = 1
```

### Option 2: Request Access Permission

If legitimate access is needed, work with your DBA to:
1. Remove the table from the denied list
2. Or create a view with limited columns/rows
3. Or use a service account with appropriate permissions

### Option 3: Use API/Service Layer

```java
// Before (direct table access)
@Select("SELECT * FROM sys_config WHERE key = #{key}")
Config getConfig(@Param("key") String key);

// After (use configuration service)
@Autowired
private ConfigurationService configService;

public Config getConfig(String key) {
    return configService.get(key);
}
```

## Related Rules

- **[Read-Only Table](read-only-table.md)** - Allows SELECT but blocks INSERT/UPDATE/DELETE
- **[No WHERE Clause](no-where-clause.md)** - Prevents full-table operations
- **[Whitelist Field](whitelist-field.md)** - Requires specific fields in WHERE clause

## Best Practices

### 1. Start with Common Patterns

```yaml
sql-guard:
  rules:
    denied-table:
      denied-tables:
        - "sys_*"      # System tables
        - "admin_*"    # Admin tables
        - "audit_*"    # Audit tables (if not using audit_log exact match)
        - "temp_*"     # Temporary tables
```

### 2. Add Specific Tables as Needed

```yaml
sql-guard:
  rules:
    denied-table:
      denied-tables:
        - "sys_*"
        - "password_history"   # Specific sensitive table
        - "encryption_keys"    # Specific sensitive table
        - "api_secrets"        # Specific sensitive table
```

### 3. Review and Update Regularly

- Audit denied table list quarterly
- Add new sensitive tables as they are created
- Remove patterns that cause false positives

### 4. Combine with Database Permissions

```sql
-- Database level (primary control)
REVOKE SELECT, INSERT, UPDATE, DELETE ON sys_user FROM app_user;

-- Application level (defense-in-depth via SQL Guard)
-- denied-tables: ["sys_*"]
```

## Testing

### Unit Test Example

```java
@Test
public void testDeniedTableDetection() {
    // Given
    DeniedTableConfig config = new DeniedTableConfig();
    config.setDeniedTables(Arrays.asList("sys_*"));
    DeniedTableChecker checker = new DeniedTableChecker(config);
    
    String sql = "SELECT * FROM sys_user WHERE id = 1";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(CCJSqlParserUtil.parse(sql))
        .type(SqlCommandType.SELECT)
        .statementId("UserMapper.getSystemUser")
        .build();
    
    // When
    ValidationResult result = ValidationResult.pass();
    checker.check(context, result);
    
    // Then
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertTrue(result.getViolations().get(0).getMessage()
        .contains("sys_user"));
}
```

### Integration Test Example

```java
@Test
public void testDeniedTableAccessBlocked() {
    // Given: BLOCK strategy configured
    
    // When/Then: SQLException thrown
    assertThrows(SQLException.class, () -> {
        userMapper.getSystemUser(1);  // SELECT * FROM sys_user WHERE id = ?
    });
}
```

---

**Questions?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).
