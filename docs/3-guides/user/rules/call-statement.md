# Call Statement Rule

**Risk Level:** 🟠 HIGH  
**Default Strategy:** ⚠️ WARN (监控但不阻止)

## Overview

The Call Statement rule detects stored procedure calls (CALL/EXECUTE/EXEC) that introduce opaque logic and potential permission escalation risks. Unlike other security rules that default to BLOCK, this rule defaults to WARN because stored procedures may be legitimate architecture in some systems.

## What It Detects

SQL statements that call stored procedures:

- **MySQL CALL** - `CALL procedure_name(params)`
- **Oracle EXECUTE** - `EXECUTE procedure_name(params)`
- **SQL Server EXEC** - `EXEC procedure_name params`

## Why Potentially Dangerous

### Opaque Logic

```sql
-- ❌ HIGH: Business logic hidden in database
CALL sp_process_order(123)

-- What does this do? We can't see:
-- - What tables are accessed?
-- - What data is modified?
-- - What permissions are used?
```

**Security Concern:**
- Procedure implementation not visible in application code
- Difficult to audit and review
- Changes to procedures can silently affect application behavior

### Permission Escalation

```sql
-- ❌ HIGH: Procedure may execute with elevated privileges
CALL sp_admin_operation()

-- Procedures execute with DEFINER privileges by default
-- A user with limited permissions can execute privileged operations
```

**Real-World Impact:**
- Developer creates procedure with admin privileges
- Regular users can call the procedure
- Result: Privilege escalation vulnerability

### Hidden Complexity

```sql
-- ❌ HIGH: Complex logic hidden from code review
EXEC sp_complex_business_logic @order_id = 1, @action = 'process'

-- This could:
-- - Modify multiple tables
-- - Send external notifications
-- - Trigger cascading operations
```

**Audit Difficulty:**
- Code reviews miss database-side logic
- Security scans don't analyze procedure code
- Compliance audits incomplete

## Examples

### BAD: Stored Procedure Calls

```sql
-- ❌ HIGH: MySQL CALL
CALL sp_user_create(1, 'test', 'test@example.com')

-- ❌ HIGH: Oracle EXECUTE
EXECUTE sp_process_order(100)

-- ❌ HIGH: SQL Server EXEC
EXEC sp_delete_user @user_id = 1

-- ❌ HIGH: Schema-qualified procedure
CALL schema_name.sp_admin_action()
```

**Violation Message:**
```
[HIGH] 检测到存储过程调用: CALL sp_user_create (存储过程引入不透明逻辑，可能存在权限提升风险)
MapperId: com.example.UserMapper.createUser
SQL: CALL sp_user_create(1, 'test', 'test@example.com')
Suggestion: 将存储过程逻辑迁移到应用层，使用直接SQL语句代替存储过程调用
```

### GOOD: Direct SQL Statements

```sql
-- ✅ SAFE: Direct INSERT
INSERT INTO users (id, name, email) VALUES (1, 'test', 'test@example.com')

-- ✅ SAFE: Direct UPDATE
UPDATE orders SET status = 'processed' WHERE id = 100

-- ✅ SAFE: Direct DELETE
DELETE FROM users WHERE id = 1

-- ✅ SAFE: SELECT with functions (functions ≠ procedures)
SELECT MAX(id), COUNT(*), UPPER(name) FROM users
```

## When Procedures Are Acceptable

### Legacy Systems

```yaml
# Accept procedures in legacy modules
sql-guard:
  rules:
    call-statement:
      enabled: true
      violation-strategy: WARN  # Monitor but don't block
      whitelist-mapper-ids:
        - "LegacyModule.*"
```

**Rationale:**
- Extensive procedure-based architecture
- Rewriting would be costly and risky
- Use WARN to monitor and gradually migrate

### Performance-Critical Operations

```sql
-- Acceptable: Batch processing with significant performance benefit
CALL sp_batch_process_millions_of_records()
```

**Rationale:**
- Reduces network round-trips
- Database-side processing more efficient
- Document and audit these exceptions

### Database-Specific Optimizations

```sql
-- Acceptable: Using database-specific features
CALL mysql_specific_optimization_procedure()
```

**Rationale:**
- Leverages database-specific capabilities
- Performance benefits justify complexity
- Well-documented and audited

## When Procedures Are Problematic

### New Applications

```sql
-- ❌ PROBLEMATIC: New code using procedures
CALL sp_new_feature_logic()

-- Should be:
INSERT INTO new_table (col1, col2) VALUES (?, ?)
UPDATE related_table SET status = ? WHERE id = ?
```

**Rationale:**
- All logic should be in application layer
- Easier to test, audit, and maintain
- No hidden complexity

### Bypassing Application Security

```sql
-- ❌ CRITICAL: Bypassing application authorization
CALL sp_direct_data_access()

-- Application security checks bypassed
-- Audit logs incomplete
```

**Rationale:**
- Security controls should be in application
- Procedures can bypass authorization
- Creates security blind spots

### Sensitive Data Operations

```sql
-- ❌ HIGH RISK: Procedures accessing sensitive data
CALL sp_export_user_data()
CALL sp_modify_permissions()
```

**Rationale:**
- Sensitive operations need full visibility
- Audit requirements demand transparency
- Compliance may prohibit opaque operations

## How to Fix

### Option 1: Migrate to Direct SQL (Recommended)

```sql
-- Before (procedure)
CALL sp_create_user(1, 'test', 'test@example.com')

-- After (direct SQL)
INSERT INTO users (id, name, email) VALUES (?, ?, ?)
INSERT INTO user_roles (user_id, role) VALUES (?, 'default')
INSERT INTO audit_log (action, user_id) VALUES ('user_created', ?)
```

```java
// Before (opaque)
@Select("CALL sp_create_user(#{id}, #{name}, #{email})")
void createUser(User user);

// After (transparent)
@Transactional
public void createUser(User user) {
    userMapper.insertUser(user);
    userRoleMapper.insertDefaultRole(user.getId());
    auditLogMapper.logUserCreation(user.getId());
}
```

### Option 2: Document and Whitelist (Legacy Systems)

```yaml
sql-guard:
  rules:
    call-statement:
      enabled: true
      violation-strategy: WARN
      whitelist-mapper-ids:
        - "LegacyOrderModule.processOrder"
        - "BatchJobModule.*"
```

**Requirements:**
- Document why procedure is necessary
- Regular security review of procedure code
- Plan for eventual migration

### Option 3: Use BLOCK for Strict Enforcement

```yaml
sql-guard:
  rules:
    call-statement:
      enabled: true
      violation-strategy: BLOCK  # Strict: no procedures allowed
```

**Use when:**
- New application with no legacy constraints
- High security requirements
- Compliance mandates transparency

## Configuration

### Enable/Disable

```yaml
sql-guard:
  rules:
    call-statement:
      enabled: true  # Default: true
```

### Adjust Violation Strategy

```yaml
sql-guard:
  rules:
    call-statement:
      violation-strategy: WARN  # Default: WARN (not BLOCK)
      # Options: BLOCK, WARN, LOG
```

**Strategy Recommendations:**
- **WARN** (default): Production systems with potential procedure usage
- **BLOCK**: New applications with strict no-procedure policy
- **LOG**: Monitoring phase before enforcing

### Whitelist Specific Mappers

```yaml
sql-guard:
  rules:
    call-statement:
      whitelist-mapper-ids:
        - "LegacyModule.legacyProcedure"
        - "BatchJob.*"
```

## Edge Cases

### Case 1: Function Calls vs Procedure Calls

```sql
-- ✅ PASS: Function calls in expressions
SELECT MAX(id), COUNT(*), UPPER(name) FROM users

-- ❌ FAIL: Procedure calls (standalone statements)
CALL sp_process()
```

**Behavior:** Functions in SELECT/WHERE are allowed; only standalone CALL/EXECUTE/EXEC are flagged.

### Case 2: Case Sensitivity

```sql
-- All detected (case-insensitive):
CALL sp_test()
call sp_test()
Call Sp_Test()
```

**Behavior:** Detection is case-insensitive.

### Case 3: Schema-Qualified Names

```sql
-- All detected:
CALL sp_test()
CALL schema.sp_test()
CALL db.schema.sp_test()
```

**Behavior:** Schema-qualified procedure names are detected.

## Comparison with Other Rules

| Rule | Risk Level | Default Strategy | Rationale |
|------|------------|------------------|-----------|
| No WHERE Clause | CRITICAL | BLOCK | Data loss risk |
| INTO OUTFILE | CRITICAL | BLOCK | File write attack |
| **Call Statement** | **HIGH** | **WARN** | **May be legitimate** |
| Multi-Statement | HIGH | BLOCK | Injection risk |

## Security Best Practices

### 1. Prefer Application Logic

```java
// ❌ Procedure-based logic
@Select("CALL sp_business_logic(#{id})")
void processOrder(Long id);

// ✅ Application-based logic
@Transactional
public void processOrder(Long id) {
    Order order = orderMapper.findById(id);
    validateOrder(order);
    updateInventory(order);
    notifyCustomer(order);
    auditLog.record("order_processed", id);
}
```

### 2. Audit Procedure Usage

```yaml
# Enable logging to track procedure calls
sql-guard:
  rules:
    call-statement:
      enabled: true
      violation-strategy: LOG  # Log all procedure calls
```

### 3. Review Procedure Code

For whitelisted procedures:
- Regular security reviews
- Document privilege requirements
- Monitor for changes

### 4. Gradual Migration

```yaml
# Phase 1: Monitor
violation-strategy: LOG

# Phase 2: Warn
violation-strategy: WARN

# Phase 3: Block (after migration complete)
violation-strategy: BLOCK
```

## Testing

### Unit Test Example

```java
@Test
public void testCallStatementDetection() {
    // Given
    String sql = "CALL sp_user_create(1, 'test')";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .mapperId("UserMapper.createUser")
        .build();
    
    // When
    ValidationResult result = validator.validate(context);
    
    // Then
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertTrue(result.getViolations().get(0).getMessage()
        .contains("sp_user_create"));
}
```

## Related Rules

- **[Multi-Statement Rule](multi-statement.md)** - Detects multiple statements (related injection risk)
- **[No WHERE Clause Rule](no-where-clause.md)** - Detects missing WHERE clauses
- **[INTO OUTFILE Rule](into-outfile.md)** - Detects file write operations

## Next Steps

- **[Configuration Reference](../configuration-reference.md)** - Configure rule settings
- **[Deployment Guide](../deployment.md)** - Roll out to production safely
- **[FAQ](../faq.md)** - Common questions about procedure detection

---

**Questions?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).
