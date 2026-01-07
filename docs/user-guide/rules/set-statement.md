# SET Statement Rule

**Risk Level:** 🟡 MEDIUM

## Overview

The SET Statement rule detects SQL session variable modification statements (SET) that can enable transaction isolation bypass and SQL mode manipulation attacks. This rule differentiates between SET statements and UPDATE...SET column assignments.

## What It Detects

SET statements that modify session variables:

- **SET autocommit** - Disables/enables auto-commit
- **SET sql_mode** - Modifies SQL mode (can remove safety checks)
- **SET @variable** - User-defined variable injection
- **SET NAMES/CHARSET** - Character set manipulation
- **SET SESSION/GLOBAL** - Session/global variable modification
- **SET TRANSACTION** - Transaction isolation level changes
- **SET search_path** (PostgreSQL) - Schema search path modification

## Why Dangerous

### Transaction Manipulation

```sql
-- ❌ MEDIUM: Disables auto-commit
SET autocommit = 0

-- ❌ MEDIUM: Bypasses transaction isolation
SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED
```

**Real-World Impact:**
- Application expects auto-commit but it's disabled
- Result: Data not committed, appears lost
- Recovery: Manual transaction management required
- Cost: Data integrity issues, debugging time

### SQL Mode Manipulation

```sql
-- ❌ MEDIUM: Removes SQL safety checks
SET sql_mode = ''

-- ❌ MEDIUM: Disables strict mode
SET sql_mode = 'NO_ENGINE_SUBSTITUTION'
```

**Real-World Impact:**
- SQL safety checks disabled
- Result: Invalid data accepted (truncation, invalid dates)
- Recovery: Data cleanup required
- Cost: Data quality degradation

### Variable Injection

```sql
-- ❌ MEDIUM: User variable injection
SET @admin_id = 1
SET @bypass_check = true
```

**Real-World Impact:**
- Attacker sets variables used in subsequent queries
- Result: Authorization bypass, data manipulation
- Recovery: Security audit required
- Cost: Security breach, compliance issues

### Character Set Manipulation

```sql
-- ❌ MEDIUM: Character set change
SET NAMES latin1
SET CHARSET gbk
```

**Real-World Impact:**
- Character encoding mismatch
- Result: SQL injection via multibyte characters
- Recovery: Security patch required
- Cost: Potential data breach

## UPDATE vs SET Disambiguation

**CRITICAL**: This rule correctly differentiates between:

### SET Statement (DETECT)

```sql
-- ❌ DETECTED: This is a SET statement
SET autocommit = 0
SET sql_mode = ''
SET @user_id = 123
```

### UPDATE...SET (ALLOW)

```sql
-- ✅ ALLOWED: This is UPDATE with column assignment, NOT a SET statement
UPDATE users SET name = 'test' WHERE id = 1
UPDATE orders SET status = 'shipped', updated_at = NOW() WHERE id = 100
```

The rule checks if the SQL starts with `UPDATE` keyword. If it does, the `SET` is part of column assignment and is allowed.

## Examples

### BAD: SET autocommit

```sql
-- ❌ MEDIUM: Disables auto-commit
SET autocommit = 0
```

**Violation Message:**
```
[MEDIUM] 检测到SET语句: autocommit (可能存在会话变量修改风险)
MapperId: com.example.ConfigMapper.setAutocommit
SQL: SET autocommit = 0
Suggestion: 移除SET语句或将其配置在应用层/连接池初始化阶段，而非运行时SQL中
```

### BAD: SET sql_mode

```sql
-- ❌ MEDIUM: Removes SQL safety checks
SET sql_mode = ''
```

**Violation Message:**
```
[MEDIUM] 检测到SET语句: sql_mode (可能存在会话变量修改风险)
MapperId: com.example.ConfigMapper.setSqlMode
SQL: SET sql_mode = ''
Suggestion: 移除SET语句或将其配置在应用层/连接池初始化阶段，而非运行时SQL中
```

### BAD: SET @variable

```sql
-- ❌ MEDIUM: User variable injection
SET @admin_id = 1
```

**Violation Message:**
```
[MEDIUM] 检测到SET语句: @admin_id (可能存在会话变量修改风险)
MapperId: com.example.UserMapper.setVariable
SQL: SET @admin_id = 1
Suggestion: 移除SET语句或将其配置在应用层/连接池初始化阶段，而非运行时SQL中
```

### GOOD: UPDATE with SET (Column Assignment)

```sql
-- ✅ SAFE: UPDATE statement with column assignment
UPDATE users SET name = 'test' WHERE id = 1

-- ✅ SAFE: Multiple column assignment
UPDATE orders SET status = 'shipped', updated_at = NOW() WHERE order_id = 100
```

## When SET is Acceptable vs Problematic

### Acceptable: Framework/Connection Pool Initialization

SET statements are acceptable when used during connection pool initialization or framework setup:

```java
// ✅ ACCEPTABLE: Connection pool initialization
HikariConfig config = new HikariConfig();
config.setConnectionInitSql("SET NAMES utf8mb4");
```

```yaml
# ✅ ACCEPTABLE: Spring Boot datasource configuration
spring:
  datasource:
    hikari:
      connection-init-sql: "SET NAMES utf8mb4"
```

### Problematic: Runtime Modification

SET statements are problematic when executed at runtime by application code:

```java
// ❌ PROBLEMATIC: Runtime SET modification
@Query("SET sql_mode = ''")
void disableSqlMode();

// ❌ PROBLEMATIC: Dynamic SET statement
jdbcTemplate.execute("SET @user_id = " + userId);
```

## How to Fix

### Option 1: Move to Connection Pool Configuration (Recommended)

```java
// Before (problematic)
@Query("SET NAMES utf8mb4")
void setCharset();

// After (safe) - Configure in connection pool
HikariConfig config = new HikariConfig();
config.setConnectionInitSql("SET NAMES utf8mb4");
```

### Option 2: Use Application Layer Configuration

```java
// Before (problematic)
@Query("SET autocommit = 0")
void disableAutocommit();

// After (safe) - Use Spring transaction management
@Transactional
public void performTransaction() {
    // Auto-commit handled by Spring
}
```

### Option 3: Remove Unnecessary SET Statements

```java
// Before (problematic)
@Query("SET sql_mode = ''")
void disableSqlMode();

// After (safe) - Remove and use proper validation
// SQL mode should be configured at database level, not runtime
```

## Configuration

### Enable/Disable

```yaml
sql-guard:
  rules:
    set-statement:
      enabled: true  # Default: true
```

### Adjust Risk Level

```yaml
sql-guard:
  rules:
    set-statement:
      risk-level: MEDIUM  # Default: MEDIUM
```

**Note:** Default is WARN (not BLOCK) because SET statements may be necessary in some frameworks for initialization.

### Whitelist Specific Mappers

```yaml
sql-guard:
  rules:
    set-statement:
      whitelist-mapper-ids:
        - "ConnectionInitMapper.setCharset"  # Connection pool init
        - "SystemConfigMapper.setTimezone"   # System configuration
```

## Edge Cases

### Case 1: SET in String Literal

```sql
-- Contains SET but in string literal
SELECT * FROM users WHERE name = 'SET autocommit = 0'
```

**Behavior:** Passes (SET is in string literal, not a statement)

### Case 2: OFFSET Keyword

```sql
-- Contains 'SET' substring in OFFSET
SELECT * FROM users LIMIT 10 OFFSET 20
```

**Behavior:** Passes (OFFSET is not SET statement)

### Case 3: Case Variations

```sql
-- All case variations detected
SET autocommit = 0
set autocommit = 0
Set autocommit = 0
```

**Behavior:** All detected (case-insensitive matching)

### Case 4: Whitespace Variations

```sql
-- Extra whitespace
   SET   autocommit   =   0
```

**Behavior:** Detected (whitespace trimmed)

## Multi-Dialect Support

### MySQL

```sql
-- All detected
SET autocommit = 0
SET sql_mode = ''
SET @variable = value
SET NAMES utf8mb4
SET CHARSET utf8
SET SESSION wait_timeout = 28800
SET GLOBAL max_connections = 1000
```

### PostgreSQL

```sql
-- All detected
SET search_path TO myschema
SET statement_timeout = '5s'
SET client_encoding = 'UTF8'
SET timezone = 'UTC'
SET LOCAL timezone = 'UTC'
```

## Security Risks Explained

### 1. Transaction Isolation Bypass

```sql
SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED
```

**Risk:** Allows reading uncommitted data from other transactions (dirty reads), potentially exposing sensitive data before validation.

### 2. SQL Mode Manipulation

```sql
SET sql_mode = ''
```

**Risk:** Disables SQL safety checks including:
- `STRICT_TRANS_TABLES` - Strict mode for transactional tables
- `NO_ZERO_DATE` - Prevents zero dates
- `ERROR_FOR_DIVISION_BY_ZERO` - Prevents division by zero

### 3. Auto-commit Manipulation

```sql
SET autocommit = 0
```

**Risk:** Disables auto-commit, potentially causing:
- Data not committed as expected
- Long-running transactions holding locks
- Connection pool exhaustion

## Best Practices

### 1. Configure at Infrastructure Level

```yaml
# ✅ Configure in database server
# my.cnf
[mysqld]
sql_mode=STRICT_TRANS_TABLES,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO
```

### 2. Use Connection Pool Initialization

```java
// ✅ Configure in connection pool
HikariConfig config = new HikariConfig();
config.setConnectionInitSql("SET NAMES utf8mb4");
```

### 3. Use Framework Transaction Management

```java
// ✅ Use Spring @Transactional instead of SET autocommit
@Transactional
public void performOperation() {
    // Transaction managed by framework
}
```

### 4. Avoid Runtime SET Statements

```java
// ❌ Never do this
jdbcTemplate.execute("SET @user_id = " + userId);

// ✅ Use parameterized queries
jdbcTemplate.update("UPDATE users SET name = ? WHERE id = ?", name, id);
```

## Related Rules

- **[Multi-Statement Rule](multi-statement.md)** - Detects SQL injection via stacked queries
- **[No WHERE Clause Rule](no-where-clause.md)** - Detects dangerous DML without WHERE
- **[Into Outfile Rule](into-outfile.md)** - Detects file write operations

## Next Steps

- **[Configuration Reference](../configuration-reference.md)** - Configure rule settings
- **[Deployment Guide](../deployment.md)** - Roll out to production safely
- **[FAQ](../faq.md)** - Common questions and answers

---

**Questions?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).
