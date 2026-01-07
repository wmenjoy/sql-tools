# DDL Operation Rule

**Risk Level:** 🔴 CRITICAL

## Overview

The DDL Operation rule detects and blocks DDL (Data Definition Language) operations executed at the application layer. This enforces the production best practice that schema changes should occur via controlled migration scripts (Flyway, Liquibase), not through runtime application code.

## What It Detects

DDL statements executed through application code:

- **CREATE** - CREATE TABLE, CREATE INDEX, CREATE VIEW
- **ALTER** - ALTER TABLE, ALTER INDEX
- **DROP** - DROP TABLE, DROP INDEX, DROP VIEW
- **TRUNCATE** - TRUNCATE TABLE

## Why Application-Layer DDL Is Prohibited

### 1. Schema Changes Should Be Version Controlled

```sql
-- ❌ CRITICAL: Schema change via application code (no version control)
CREATE TABLE new_feature (id INT PRIMARY KEY, name VARCHAR(100))
```

**Problems:**
- No audit trail of schema changes
- Cannot rollback to previous schema version
- Schema drift between environments (dev/staging/prod)
- No code review for schema changes

**Proper Approach:**
```sql
-- ✅ SAFE: Schema change via Flyway migration script
-- File: V2024_01_15__create_new_feature_table.sql
CREATE TABLE new_feature (
    id INT PRIMARY KEY,
    name VARCHAR(100)
);
```

### 2. Runtime DDL Indicates Poor Deployment Practices

```sql
-- ❌ CRITICAL: Dynamic table creation at runtime
CREATE TABLE user_123_data (id INT, value TEXT)
```

**Problems:**
- Schema changes should be part of deployment pipeline
- Runtime DDL can cause unexpected downtime
- Database connections may be blocked during DDL
- Application should not have DDL privileges in production

### 3. DDL Operations Can Cause Downtime and Data Loss

```sql
-- ❌ CRITICAL: DROP TABLE is irreversible
DROP TABLE users

-- ❌ CRITICAL: TRUNCATE TABLE is irreversible
TRUNCATE TABLE orders
```

**Real-World Impact:**
- Production incident: Developer accidentally runs `DROP TABLE` in production
- Result: Complete data loss for the table
- Recovery: Restore from backup (hours of downtime, potential data loss)
- Cost: $100K+ in lost revenue, customer trust, and engineering time

## Examples

### BAD: CREATE TABLE at Application Layer

```sql
-- ❌ CRITICAL: Creates table at runtime
CREATE TABLE temp_results (id INT, value VARCHAR(100))
```

**Violation Message:**
```
[CRITICAL] 检测到 CREATE DDL操作: CREATE TABLE。应用层不应执行DDL操作,请使用数据库迁移工具(如Flyway/Liquibase)
MapperId: com.example.DataMapper.createTempTable
SQL: CREATE TABLE temp_results (id INT, value VARCHAR(100))
Suggestion: 将CREATE语句移至数据库迁移脚本(Flyway/Liquibase),通过部署流程执行
```

### BAD: ALTER TABLE at Application Layer

```sql
-- ❌ CRITICAL: Modifies schema at runtime
ALTER TABLE users ADD COLUMN age INT
```

**Violation Message:**
```
[CRITICAL] 检测到 ALTER DDL操作: ALTER TABLE。应用层不应执行DDL操作,请使用数据库迁移工具(如Flyway/Liquibase)
MapperId: com.example.UserMapper.addAgeColumn
SQL: ALTER TABLE users ADD COLUMN age INT
Suggestion: 将ALTER语句移至数据库迁移脚本(Flyway/Liquibase),通过部署流程执行
```

### BAD: DROP TABLE at Application Layer

```sql
-- ❌ CRITICAL: Drops table at runtime (irreversible)
DROP TABLE deprecated_data
```

**Violation Message:**
```
[CRITICAL] 检测到 DROP DDL操作: DROP TABLE。应用层不应执行DDL操作,请使用数据库迁移工具(如Flyway/Liquibase)
MapperId: com.example.CleanupMapper.dropTable
SQL: DROP TABLE deprecated_data
Suggestion: 将DROP语句移至数据库迁移脚本(Flyway/Liquibase),通过部署流程执行。注意:DROP操作不可逆,请谨慎操作
```

### BAD: TRUNCATE TABLE at Application Layer

```sql
-- ❌ CRITICAL: Truncates table at runtime (irreversible)
TRUNCATE TABLE logs
```

**Violation Message:**
```
[CRITICAL] 检测到 TRUNCATE DDL操作: TRUNCATE TABLE。应用层不应执行DDL操作,请使用数据库迁移工具(如Flyway/Liquibase)
MapperId: com.example.LogMapper.truncateLogs
SQL: TRUNCATE TABLE logs
Suggestion: 将TRUNCATE语句移至数据库迁移脚本或使用DELETE语句替代。注意:TRUNCATE操作不可逆,请谨慎操作
```

### BAD: CREATE INDEX at Application Layer

```sql
-- ❌ CRITICAL: Creates index at runtime (can cause table locks)
CREATE INDEX idx_users_email ON users (email)
```

### BAD: DROP INDEX at Application Layer

```sql
-- ❌ CRITICAL: Drops index at runtime (can affect query performance)
DROP INDEX idx_users_email
```

### GOOD: DML Operations (SELECT/INSERT/UPDATE/DELETE)

```sql
-- ✅ SAFE: Standard data query
SELECT * FROM users WHERE id = ?

-- ✅ SAFE: Standard data insertion
INSERT INTO users (name, email) VALUES (?, ?)

-- ✅ SAFE: Standard data update
UPDATE users SET status = 'active' WHERE id = ?

-- ✅ SAFE: Standard data deletion
DELETE FROM users WHERE id = ? AND status = 'inactive'
```

### GOOD: Proper Migration Script Workflow

**Step 1: Create migration script (Flyway example)**
```sql
-- File: V2024_01_15__add_age_column.sql
ALTER TABLE users ADD COLUMN age INT;
```

**Step 2: Deploy via CI/CD pipeline**
```bash
# Flyway migration in deployment pipeline
flyway migrate
```

**Step 3: Application code uses new column**
```java
// Application code only performs DML operations
@Select("SELECT id, name, age FROM users WHERE id = #{id}")
User selectById(@Param("id") Long id);
```

## Configuration

### Enable/Disable

```yaml
sql-guard:
  rules:
    ddl-operation:
      enabled: true  # Default: true
```

### Allow Specific DDL Operations

For special cases (e.g., migration scripts executed via application), you can allow specific DDL types:

```yaml
sql-guard:
  rules:
    ddl-operation:
      enabled: true
      allowed-operations:
        - CREATE  # Allow CREATE statements
        - ALTER   # Allow ALTER statements
        # DROP and TRUNCATE remain blocked
```

**⚠️ Warning:** Only enable specific DDL operations if absolutely necessary. The default (block all DDL) is the safest configuration.

### Full Configuration Example

```yaml
sql-guard:
  rules:
    ddl-operation:
      enabled: true
      risk-level: CRITICAL  # Default: CRITICAL
      allowed-operations: []  # Default: empty (block all DDL)
```

### Programmatic Configuration

```java
// Default: Block all DDL operations
DdlOperationConfig config = new DdlOperationConfig();
DdlOperationChecker checker = new DdlOperationChecker(config);

// Allow specific operations for migration scripts
DdlOperationConfig migrationConfig = new DdlOperationConfig();
migrationConfig.setAllowedOperations(Arrays.asList("CREATE", "ALTER"));
DdlOperationChecker migrationChecker = new DdlOperationChecker(migrationConfig);

// Disable checker (not recommended for production)
DdlOperationConfig disabledConfig = new DdlOperationConfig(false);
```

## Edge Cases

### Case 1: Temporary Tables

```sql
-- Still flagged as DDL (CREATE operation)
CREATE TEMPORARY TABLE temp_data (id INT, value VARCHAR(100))
```

**Behavior:** Flagged as CRITICAL violation  
**Rationale:** Even temporary tables should be created via proper channels, not runtime application code

**Alternative:** Use application-level caching or in-memory data structures instead of temporary tables

### Case 2: CREATE TABLE IF NOT EXISTS

```sql
-- Still flagged as DDL (CREATE operation)
CREATE TABLE IF NOT EXISTS cache_data (key VARCHAR(100), value TEXT)
```

**Behavior:** Flagged as CRITICAL violation  
**Rationale:** The "IF NOT EXISTS" clause doesn't make runtime DDL acceptable

### Case 3: DROP TABLE IF EXISTS

```sql
-- Still flagged as DDL (DROP operation)
DROP TABLE IF EXISTS temp_data
```

**Behavior:** Flagged as CRITICAL violation  
**Rationale:** DROP operations should never be executed via application code

### Case 4: Database-Specific DDL Syntax

```sql
-- MySQL syntax
CREATE TABLE users (id INT AUTO_INCREMENT PRIMARY KEY)

-- PostgreSQL syntax
CREATE TABLE users (id SERIAL PRIMARY KEY)

-- Oracle syntax
CREATE TABLE users (id NUMBER PRIMARY KEY)
```

**Behavior:** All flagged as CRITICAL violations  
**Detection:** Multi-dialect support covers MySQL, PostgreSQL, Oracle, and other databases

## Best Practices

### 1. Use Database Migration Tools

**Recommended Tools:**
- **Flyway** - Version-based migrations with SQL scripts
- **Liquibase** - XML/YAML/JSON based migrations with rollback support

```bash
# Flyway migration
flyway migrate

# Liquibase migration
liquibase update
```

### 2. Separate DDL Privileges

```sql
-- Application database user should NOT have DDL privileges
GRANT SELECT, INSERT, UPDATE, DELETE ON mydb.* TO 'app_user'@'%';

-- Migration user has DDL privileges (used only in deployment pipeline)
GRANT ALL PRIVILEGES ON mydb.* TO 'migration_user'@'%';
```

### 3. Schema Changes in CI/CD Pipeline

```yaml
# GitHub Actions example
deploy:
  steps:
    - name: Run database migrations
      run: flyway migrate
    - name: Deploy application
      run: kubectl apply -f deployment.yaml
```

### 4. Code Review for Schema Changes

- All migration scripts should go through code review
- Test migrations in staging environment first
- Have rollback scripts ready for critical changes

## Production Incidents Prevented

### Incident 1: Accidental Table Drop

**Company:** E-commerce platform  
**Incident:** Developer ran `DROP TABLE orders` via application debug endpoint  
**Impact:** Complete loss of order data (500K records)  
**Recovery:** 4 hours downtime, restored from backup, lost 2 hours of orders  
**Prevention:** SQL Guard would have blocked the DROP statement

### Incident 2: Runtime Schema Change Causing Downtime

**Company:** SaaS platform  
**Incident:** Application code ran `ALTER TABLE` on 100M row table  
**Impact:** Table locked for 45 minutes, all queries blocked  
**Recovery:** Killed ALTER operation, deployed hotfix  
**Prevention:** SQL Guard would have blocked the ALTER statement

### Incident 3: Unauthorized Index Creation

**Company:** Financial services  
**Incident:** Junior developer added `CREATE INDEX` in application code  
**Impact:** Index creation blocked production queries for 30 minutes  
**Recovery:** Dropped index, created during maintenance window  
**Prevention:** SQL Guard would have blocked the CREATE INDEX statement

## Testing

### Unit Test Example

```java
@Test
public void testDdlOperationDetection() {
    // Given
    String sql = "CREATE TABLE users (id INT PRIMARY KEY)";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.UNKNOWN)
        .mapperId("DataMapper.createTable")
        .build();
    
    // When
    ValidationResult result = validator.validate(context);
    
    // Then
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    assertTrue(result.getViolations().get(0).getMessage()
        .contains("CREATE"));
}
```

### Integration Test Example

```java
@Test
public void testCreateTableBlocked() {
    // Given: BLOCK strategy configured
    
    // When/Then: SQLException thrown
    assertThrows(SQLException.class, () -> {
        dataMapper.createTable("test_table");
    });
    
    // Verify: Table was not created
    assertFalse(tableExists("test_table"));
}
```

## Related Rules

- **[No WHERE Clause](no-where-clause.md)** - Detects missing WHERE clauses
- **[Multi-Statement](multi-statement.md)** - Detects SQL injection attempts with multiple statements
- **[Dummy Condition](dummy-condition.md)** - Detects ineffective WHERE clauses

## Next Steps

- **[Configuration Reference](../configuration-reference.md)** - Configure rule settings
- **[Deployment Guide](../deployment.md)** - Roll out to production safely
- **[Troubleshooting Guide](../troubleshooting.md)** - Resolve common issues

---

**Questions?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).
