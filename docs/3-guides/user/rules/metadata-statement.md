# Metadata Statement Rule

**Risk Level:** 🟠 HIGH

## Overview

The Metadata Statement rule detects SQL metadata disclosure commands (SHOW/DESCRIBE/USE) that can leak database schema information to attackers, enabling reconnaissance and targeted attacks.

## What It Detects

SQL metadata commands that reveal schema information:

- **SHOW** - MySQL SHOW commands (SHOW TABLES, SHOW DATABASES, SHOW COLUMNS, etc.)
- **DESCRIBE/DESC** - Table structure disclosure commands
- **USE** - Database switching commands

## Why Dangerous

### Schema Enumeration Attacks

```sql
-- ❌ HIGH: Reveals all tables in database
SHOW TABLES

-- ❌ HIGH: Reveals all databases on server
SHOW DATABASES

-- ❌ HIGH: Reveals table structure
DESCRIBE users
```

**Real-World Impact:**
- Attackers learn table names for SQL injection targeting
- Database names revealed for enumeration attacks
- Column names exposed for crafting precise injection payloads
- Schema information enables exploitation planning

### Information Disclosure Scenarios

**Scenario 1: SQL Injection Reconnaissance**
```sql
-- Attacker discovers user table exists
SHOW TABLES LIKE 'user%'
-- Result: users, user_sessions, user_credentials

-- Attacker learns password column exists
DESCRIBE user_credentials
-- Result: id, user_id, password_hash, salt, created_at
```

**Scenario 2: Database Enumeration**
```sql
-- Attacker discovers production databases
SHOW DATABASES
-- Result: production_db, staging_db, backup_db

-- Attacker switches to sensitive database
USE production_db
```

**Scenario 3: Privilege Discovery**
```sql
-- Attacker discovers user privileges
SHOW GRANTS FOR CURRENT_USER()
-- Result: GRANT SELECT, INSERT, UPDATE ON *.* TO 'webapp'@'localhost'
```

## Examples

### BAD: SHOW TABLES

```sql
-- ❌ HIGH: Reveals all table names
SHOW TABLES
```

**Violation Message:**
```
[HIGH] 检测到元数据语句: SHOW TABLES (可能泄露数据库架构信息)
MapperId: com.example.AdminMapper.showTables
SQL: SHOW TABLES
Suggestion: 移除SHOW命令，使用SELECT查询INFORMATION_SCHEMA获取元数据信息
```

### BAD: SHOW DATABASES

```sql
-- ❌ HIGH: Reveals all database names
SHOW DATABASES
```

**Violation Message:**
```
[HIGH] 检测到元数据语句: SHOW DATABASES (可能泄露数据库架构信息)
MapperId: com.example.AdminMapper.showDatabases
SQL: SHOW DATABASES
Suggestion: 移除SHOW命令，使用SELECT查询INFORMATION_SCHEMA获取元数据信息
```

### BAD: DESCRIBE Table

```sql
-- ❌ HIGH: Reveals table structure
DESCRIBE users
DESC orders
```

**Violation Message:**
```
[HIGH] 检测到元数据语句: DESCRIBE users (可能泄露表结构信息)
MapperId: com.example.AdminMapper.describeTable
SQL: DESCRIBE users
Suggestion: 移除DESCRIBE命令，使用SELECT查询INFORMATION_SCHEMA.COLUMNS获取列信息
```

### BAD: USE Database

```sql
-- ❌ HIGH: Database switching command
USE production_db
```

**Violation Message:**
```
[HIGH] 检测到元数据语句: USE production_db (可能用于数据库枚举攻击)
MapperId: com.example.AdminMapper.useDatabase
SQL: USE production_db
Suggestion: 移除USE命令，在连接字符串中指定数据库或使用完全限定表名
```

### BAD: SHOW with Options

```sql
-- ❌ HIGH: Filtered table enumeration
SHOW TABLES LIKE 'user%'

-- ❌ HIGH: Column enumeration
SHOW COLUMNS FROM users

-- ❌ HIGH: Index information disclosure
SHOW INDEX FROM users

-- ❌ HIGH: Table creation script
SHOW CREATE TABLE users
```

### GOOD: INFORMATION_SCHEMA Queries

```sql
-- ✅ SAFE: Proper metadata access via SELECT
SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = 'mydb'

-- ✅ SAFE: Column information via SELECT
SELECT COLUMN_NAME, DATA_TYPE 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_NAME = 'users'

-- ✅ SAFE: Schema information via SELECT
SELECT * FROM INFORMATION_SCHEMA.SCHEMATA
```

### GOOD: Normal DML Operations

```sql
-- ✅ SAFE: Normal SELECT
SELECT * FROM users WHERE id = ?

-- ✅ SAFE: Normal INSERT
INSERT INTO users (name, email) VALUES (?, ?)

-- ✅ SAFE: Normal UPDATE
UPDATE users SET name = ? WHERE id = ?

-- ✅ SAFE: Normal DELETE
DELETE FROM users WHERE id = ?
```

## Expected Messages

### SHOW Violation

```
[HIGH] 检测到元数据语句: SHOW {type} (可能泄露数据库架构信息)
MapperId: {namespace}.{methodId}
SQL: SHOW {type} ...
Suggestion: 移除SHOW命令，使用SELECT查询INFORMATION_SCHEMA获取元数据信息
Risk: Database schema information disclosure
```

### DESCRIBE Violation

```
[HIGH] 检测到元数据语句: DESCRIBE {table} (可能泄露表结构信息)
MapperId: {namespace}.{methodId}
SQL: DESCRIBE {table}
Suggestion: 移除DESCRIBE命令，使用SELECT查询INFORMATION_SCHEMA.COLUMNS获取列信息
Risk: Table structure information disclosure
```

### USE Violation

```
[HIGH] 检测到元数据语句: USE {database} (可能用于数据库枚举攻击)
MapperId: {namespace}.{methodId}
SQL: USE {database}
Suggestion: 移除USE命令，在连接字符串中指定数据库或使用完全限定表名
Risk: Database enumeration attack enablement
```

## How to Fix

### Option 1: Use INFORMATION_SCHEMA (Recommended)

```sql
-- Before (dangerous)
SHOW TABLES

-- After (safe)
SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = DATABASE()
```

```sql
-- Before (dangerous)
DESCRIBE users

-- After (safe)
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_NAME = 'users' AND TABLE_SCHEMA = DATABASE()
```

### Option 2: Use Fully Qualified Table Names

```sql
-- Before (dangerous)
USE production_db;
SELECT * FROM users;

-- After (safe)
SELECT * FROM production_db.users WHERE id = ?
```

### Option 3: Configure Database in Connection String

```java
// Before (dangerous - allows USE command)
String url = "jdbc:mysql://localhost:3306";

// After (safe - database pre-selected)
String url = "jdbc:mysql://localhost:3306/production_db";
```

### Option 4: Whitelist for Admin Tools (Last Resort)

For legitimate database administration tools:

```yaml
sql-guard:
  rules:
    metadata-statement:
      enabled: true
      allowed-statements:
        - SHOW  # Allow SHOW for admin console
```

**Caution:** Only whitelist for trusted admin tools with proper access controls.

## Configuration

### Enable/Disable

```yaml
sql-guard:
  rules:
    metadata-statement:
      enabled: true  # Default: true
```

### Adjust Risk Level

```yaml
sql-guard:
  rules:
    metadata-statement:
      risk-level: HIGH  # Default: HIGH
```

### Allow Specific Statement Types

```yaml
sql-guard:
  rules:
    metadata-statement:
      allowed-statements:
        - SHOW      # Allow SHOW commands
        - DESCRIBE  # Allow DESCRIBE commands
        # USE is not allowed
```

### Supported Statement Types

| Type | Description | Example |
|------|-------------|---------|
| SHOW | MySQL SHOW commands | SHOW TABLES, SHOW DATABASES |
| DESCRIBE | Table structure commands | DESCRIBE users, DESC orders |
| USE | Database switching | USE production_db |

## Edge Cases

### Case 1: INFORMATION_SCHEMA Queries

```sql
-- These are NOT metadata commands - they use proper SELECT syntax
SELECT * FROM INFORMATION_SCHEMA.TABLES
```

**Behavior:** Passes validation (proper metadata access method)  
**Detection:** Not flagged - INFORMATION_SCHEMA queries are safe

### Case 2: Case Variations

```sql
-- All case variations are detected
SHOW TABLES
show tables
Show Tables
```

**Behavior:** All detected and blocked (case-insensitive matching)

### Case 3: Whitespace Handling

```sql
-- Leading/trailing whitespace is handled
   SHOW TABLES
DESCRIBE   users
```

**Behavior:** Detected regardless of whitespace

### Case 4: Comments

```sql
-- Comments before metadata commands
/* admin query */ SHOW TABLES
```

**Behavior:** May pass if comment is at start (pattern matches from start)  
**Recommendation:** Ensure SQL is properly sanitized before validation

## Security Best Practices

### 1. Never Expose Metadata Commands to User Input

```java
// ❌ Dangerous: User-controlled SQL
String sql = "SHOW " + userInput;

// ✅ Safe: Parameterized INFORMATION_SCHEMA query
String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ?";
```

### 2. Use Database Permissions

```sql
-- Revoke metadata access from application user
REVOKE SHOW DATABASES ON *.* FROM 'webapp'@'localhost';
REVOKE PROCESS ON *.* FROM 'webapp'@'localhost';
```

### 3. Separate Admin and Application Connections

```yaml
# Application connection (restricted)
app-datasource:
  username: webapp
  # No metadata privileges

# Admin connection (privileged, separate)
admin-datasource:
  username: admin
  # Metadata access for admin tools only
```

### 4. Audit Metadata Access

```yaml
sql-guard:
  rules:
    metadata-statement:
      enabled: true
      violation-strategy: AUDIT  # Log but don't block for monitoring
```

## Testing

### Unit Test Example

```java
@Test
public void testShowTablesDetection() {
    // Given
    String sql = "SHOW TABLES";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .mapperId("AdminMapper.showTables")
        .build();
    
    // When
    ValidationResult result = validator.validate(context);
    
    // Then
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertTrue(result.getViolations().get(0).getMessage()
        .contains("SHOW"));
}
```

### Integration Test Example

```java
@Test
public void testMetadataCommandBlocked() {
    // Given: BLOCK strategy configured
    
    // When/Then: SQLException thrown
    assertThrows(SQLException.class, () -> {
        jdbcTemplate.execute("SHOW TABLES");
    });
}

@Test
public void testInformationSchemaAllowed() {
    // Given: Same configuration
    
    // When/Then: Query succeeds
    List<String> tables = jdbcTemplate.queryForList(
        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE()",
        String.class
    );
    
    assertNotNull(tables);
}
```

## Related Rules

- **[Into Outfile Rule](into-outfile.md)** - Detects file write operations
- **[No WHERE Clause Rule](no-where-clause.md)** - Detects missing WHERE clauses
- **[SQL Injection Prevention](sql-injection.md)** - Prevents SQL injection attacks

## Production Incidents Prevented

### Incident 1: Schema Reconnaissance

**Company:** Financial services platform  
**Incident:** Attacker used SHOW TABLES through SQL injection to enumerate tables  
**Impact:** Discovered `credit_cards`, `bank_accounts` tables  
**Follow-up:** Targeted data exfiltration attack  
**Prevention:** SQL Guard would have blocked SHOW command

### Incident 2: Database Enumeration

**Company:** Multi-tenant SaaS platform  
**Incident:** SHOW DATABASES revealed all tenant database names  
**Impact:** Tenant isolation breach, competitive intelligence leak  
**Recovery:** Database restructuring, customer notifications  
**Prevention:** SQL Guard would have blocked SHOW DATABASES

### Incident 3: Privilege Escalation Planning

**Company:** Healthcare application  
**Incident:** SHOW GRANTS revealed excessive privileges  
**Impact:** Attacker exploited UPDATE privilege for data modification  
**Recovery:** Privilege audit, access control review  
**Prevention:** SQL Guard would have blocked SHOW GRANTS

---

**Questions?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).
