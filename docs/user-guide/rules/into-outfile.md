# INTO OUTFILE/DUMPFILE Rule

**Risk Level:** 🔴 CRITICAL

## Overview

The INTO OUTFILE/DUMPFILE rule detects MySQL file write operations (`SELECT INTO OUTFILE` and `SELECT INTO DUMPFILE`) that enable arbitrary file writes and data exfiltration attacks. This rule correctly differentiates between MySQL file operations (blocked) and Oracle SELECT INTO variable syntax (allowed).

## What It Detects

MySQL file write operations:

- **SELECT INTO OUTFILE** - Writes query results to a text file
- **SELECT INTO DUMPFILE** - Writes query results to a binary file

## Why Dangerous

### Arbitrary File Writes

```sql
-- ❌ CRITICAL: Writes arbitrary file to system
SELECT * INTO OUTFILE '/etc/cron.d/malicious' FROM dual
```

**Attack Vector:**
- Attacker can write files to any location MySQL has write access
- Can overwrite system configuration files
- Can create scheduled tasks (cron jobs) for persistence

### Web Shell Creation

```sql
-- ❌ CRITICAL: Creates PHP web shell for remote code execution
SELECT '<?php system($_GET["cmd"]); ?>' INTO OUTFILE '/var/www/html/shell.php'
```

**Attack Vector:**
- Attacker writes executable code to web directory
- Gains remote code execution on the server
- Complete system compromise possible

### Data Exfiltration

```sql
-- ❌ CRITICAL: Exports sensitive data to file
SELECT username, password, credit_card INTO OUTFILE '/tmp/stolen_data.txt' FROM users
```

**Attack Vector:**
- Attacker exports sensitive database contents
- Data written to attacker-accessible location
- Bypasses application-level access controls

### Binary File Creation

```sql
-- ❌ CRITICAL: Creates binary executable
SELECT UNHEX('4D5A...') INTO DUMPFILE '/tmp/malware.exe'
```

**Attack Vector:**
- Attacker creates executable files on the server
- Can be combined with other vulnerabilities for code execution
- Enables malware deployment

## Examples

### BAD: INTO OUTFILE with Unix Path

```sql
-- ❌ CRITICAL: File write to Unix path
SELECT * INTO OUTFILE '/tmp/data.txt' FROM users
```

**Violation Message:**
```
[CRITICAL] 检测到MySQL文件写入操作: INTO OUTFILE '/tmp/data.txt' (可能存在任意文件写入攻击风险)
MapperId: com.example.UserMapper.exportUsers
Suggestion: 移除INTO OUTFILE子句，使用应用层导出功能代替数据库文件写入
```

### BAD: INTO OUTFILE with Windows Path

```sql
-- ❌ CRITICAL: File write to Windows path
SELECT * INTO OUTFILE 'C:\\data\\export.csv' FROM users
```

### BAD: INTO DUMPFILE for Binary Data

```sql
-- ❌ CRITICAL: Binary file write
SELECT LOAD_FILE('/etc/passwd') INTO DUMPFILE '/tmp/passwd_copy'
```

### BAD: Web Shell Creation

```sql
-- ❌ CRITICAL: Web shell injection
SELECT '<?php system($_GET["cmd"]); ?>' INTO OUTFILE '/var/www/html/shell.php' FROM dual
```

### BAD: Data Exfiltration from information_schema

```sql
-- ❌ CRITICAL: Schema exfiltration
SELECT table_name, column_name INTO OUTFILE '/tmp/schema.txt' FROM information_schema.columns
```

### BAD: Path Traversal Attack

```sql
-- ❌ CRITICAL: Path traversal to write outside intended directory
SELECT * INTO OUTFILE '/tmp/../../../etc/cron.d/malicious' FROM users
```

### GOOD: Normal SELECT Statement

```sql
-- ✅ SAFE: Normal query without file operations
SELECT * FROM users WHERE id = ?
```

### GOOD: Oracle SELECT INTO Variable (Allowed)

```sql
-- ✅ SAFE: Oracle PL/SQL variable assignment (NOT a file operation)
SELECT id INTO v_id FROM users WHERE name = 'test'

-- ✅ SAFE: Oracle multiple variable assignment
SELECT id, name INTO v_id, v_name FROM users WHERE id = 1

-- ✅ SAFE: Oracle BULK COLLECT
SELECT salary INTO v_salary FROM employees WHERE employee_id = 100
```

**Important:** The checker correctly differentiates between:
- MySQL `INTO OUTFILE`/`INTO DUMPFILE` → **BLOCKED** (file operation)
- Oracle `INTO variable` → **ALLOWED** (variable assignment)

## Expected Messages

### INTO OUTFILE Violation

```
[CRITICAL] 检测到MySQL文件写入操作: INTO OUTFILE '/path/to/file' (可能存在任意文件写入攻击风险)
MapperId: {namespace}.{methodId}
SQL: SELECT ... INTO OUTFILE '/path/to/file' ...
Suggestion: 移除INTO OUTFILE子句，使用应用层导出功能代替数据库文件写入
Risk: Arbitrary file write, web shell creation, data exfiltration
```

### INTO DUMPFILE Violation

```
[CRITICAL] 检测到MySQL文件写入操作: INTO DUMPFILE '/path/to/file' (可能存在二进制文件写入攻击风险)
MapperId: {namespace}.{methodId}
SQL: SELECT ... INTO DUMPFILE '/path/to/file' ...
Suggestion: 移除INTO DUMPFILE子句，使用应用层导出功能代替数据库文件写入
Risk: Binary file creation, malware deployment
```

## How to Fix

### Option 1: Use Application-Layer Export (Recommended)

```java
// Before (dangerous - database-level file write)
@Select("SELECT * INTO OUTFILE '/tmp/export.csv' FROM users")
void exportUsers();

// After (safe - application-layer export)
@Select("SELECT * FROM users WHERE status = #{status}")
List<User> getUsers(@Param("status") String status);

// Then export in application code:
List<User> users = userMapper.getUsers("active");
csvExporter.export(users, "/tmp/export.csv");
```

### Option 2: Use Database Export Tools

Instead of SQL-based file writes, use proper database export tools:

```bash
# MySQL: Use mysqldump for data export
mysqldump -u user -p database table > export.sql

# MySQL: Use SELECT with output redirection
mysql -u user -p -e "SELECT * FROM users" database > export.txt
```

### Option 3: Use Secure File Transfer

For data exports, implement secure file transfer in the application layer:

```java
// Application-controlled export with security checks
public void exportData(String query, String outputPath) {
    // Validate output path is within allowed directory
    if (!isAllowedPath(outputPath)) {
        throw new SecurityException("Invalid export path");
    }
    
    // Execute query and write results
    List<Map<String, Object>> results = jdbcTemplate.queryForList(query);
    fileExporter.writeSecurely(results, outputPath);
}
```

## Configuration

### Enable/Disable

```yaml
sql-guard:
  rules:
    into-outfile:
      enabled: true  # Default: true
```

### Adjust Risk Level

```yaml
sql-guard:
  rules:
    into-outfile:
      risk-level: CRITICAL  # Default: CRITICAL
```

**Warning:** Downgrading the risk level is strongly discouraged as INTO OUTFILE/DUMPFILE are high-severity security risks.

## MySQL-Specific Considerations

### MySQL Secure File Privileges

MySQL has built-in protections:

1. **secure_file_priv** - Limits file operations to specific directory
2. **FILE privilege** - Required for INTO OUTFILE/DUMPFILE

However, these can be misconfigured:

```sql
-- Check current setting
SHOW VARIABLES LIKE 'secure_file_priv';

-- Empty value means NO restriction (dangerous!)
-- NULL means file operations disabled (safe)
```

### Why SQL Guard Still Matters

Even with MySQL's built-in protections:

1. **Defense in depth** - Multiple layers of security
2. **Configuration drift** - Settings may change over time
3. **Development environments** - Often have relaxed security
4. **Early detection** - Catch issues before deployment

## Multi-Dialect Support

| Database | Syntax | Behavior |
|----------|--------|----------|
| MySQL | `INTO OUTFILE '/path'` | **BLOCKED** |
| MySQL | `INTO DUMPFILE '/path'` | **BLOCKED** |
| Oracle | `INTO variable` | **ALLOWED** (variable assignment) |
| Oracle | `INTO v1, v2` | **ALLOWED** (multiple variables) |
| PostgreSQL | `COPY ... TO '/path'` | Not detected by this rule* |

*Note: PostgreSQL uses different syntax (`COPY TO`) for file operations.

## Production Incidents Prevented

### Incident 1: Web Shell Deployment

**Company:** E-commerce platform  
**Incident:** SQL injection led to `SELECT ... INTO OUTFILE '/var/www/shell.php'`  
**Impact:** Complete server compromise, customer data breach  
**Prevention:** SQL Guard would have blocked the INTO OUTFILE operation

### Incident 2: Data Exfiltration

**Company:** Financial services  
**Incident:** Insider threat exported customer data via `INTO OUTFILE`  
**Impact:** 100K customer records exported to external storage  
**Prevention:** SQL Guard would have detected and blocked the file write

### Incident 3: Privilege Escalation

**Company:** Healthcare provider  
**Incident:** Attacker wrote cron job via `INTO OUTFILE '/etc/cron.d/backdoor'`  
**Impact:** Persistent backdoor access to database server  
**Prevention:** SQL Guard would have blocked the system file write

## Best Practices

### 1. Never Use INTO OUTFILE in Application Code

```sql
-- ❌ Never do this in application SQL
SELECT * INTO OUTFILE '/tmp/export.csv' FROM users

-- ✅ Always use application-layer exports
SELECT * FROM users WHERE status = 'active'
```

### 2. Implement Application-Layer Export Controls

```java
// ✅ Safe: Application controls export with validation
public void exportUsers(String format, Path outputDir) {
    // Validate export is allowed
    if (!exportPolicy.isAllowed(currentUser, "users")) {
        throw new SecurityException("Export not authorized");
    }
    
    // Validate output directory
    if (!outputDir.startsWith(allowedExportDir)) {
        throw new SecurityException("Invalid export directory");
    }
    
    // Perform export with audit logging
    List<User> users = userRepository.findAll();
    auditLog.record("EXPORT", "users", currentUser);
    exporter.export(users, format, outputDir);
}
```

### 3. Configure MySQL Securely

```ini
# my.cnf - Secure MySQL configuration
[mysqld]
# Disable file operations entirely
secure_file_priv = NULL

# Or restrict to specific directory
# secure_file_priv = /var/lib/mysql-files/
```

### 4. Audit SQL Statements

```yaml
sql-guard:
  audit:
    enabled: true
    log-violations: true
    alert-on-critical: true
```

## Related Rules

- **[Set Operation Rule](set-operation.md)** - Detects UNION-based SQL injection
- **[No WHERE Clause](no-where-clause.md)** - Prevents mass data operations
- **[Multi-Statement](multi-statement.md)** - Blocks statement stacking attacks

## Next Steps

- **[Configuration Reference](../configuration-reference.md)** - Configure rule settings
- **[Deployment Guide](../deployment.md)** - Roll out to production safely
- **[Security Best Practices](../security-best-practices.md)** - Comprehensive security guide

---

**Questions?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).
