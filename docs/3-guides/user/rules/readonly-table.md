# Read-Only Table Rule

**Risk Level:** 🟠 HIGH

## Overview

The Read-Only Table rule protects designated tables from write operations (INSERT/UPDATE/DELETE), ensuring data integrity for audit logs, historical records, and reference data that must remain immutable at the application level.

## What It Detects

Write operations targeting read-only tables:

- **INSERT** into read-only table - Adds new records to protected table
- **UPDATE** on read-only table - Modifies existing records in protected table
- **DELETE** from read-only table - Removes records from protected table

## Why Important

### Audit Log Integrity

Audit logs must be immutable for compliance, forensics, and security:

```sql
-- ❌ HIGH: Modifying audit records destroys evidence
UPDATE audit_log SET action = 'login' WHERE action = 'unauthorized_access'

-- ❌ HIGH: Deleting audit records hides malicious activity
DELETE FROM audit_log WHERE user_id = 12345
```

**Real-World Impact:**
- Compliance violation: SOX, GDPR, HIPAA require immutable audit trails
- Security incident: Attackers modify logs to hide intrusion evidence
- Legal liability: Tampered audit logs invalidate forensic evidence

### Historical Data Preservation

Historical records must be preserved for data integrity:

```sql
-- ❌ HIGH: Modifying historical data corrupts analytics
UPDATE history_orders SET total = 0 WHERE order_date < '2023-01-01'

-- ❌ HIGH: Deleting historical data breaks reports
DELETE FROM history_users WHERE created_at < '2020-01-01'
```

**Real-World Impact:**
- Data integrity: Historical snapshots become unreliable
- Analytics corruption: Reports based on historical data become inaccurate
- Audit failure: Cannot trace data lineage

### Reference Data Protection

Reference/lookup tables should not change without proper process:

```sql
-- ❌ HIGH: Modifying reference data affects all dependent records
UPDATE sys_config SET value = 'disabled' WHERE key = 'security_check'

-- ❌ HIGH: Deleting reference data breaks foreign key relationships
DELETE FROM country_codes WHERE code = 'US'
```

## Examples

### BAD: INSERT into Read-Only Table

```sql
-- ❌ HIGH: Inserting into audit log (should be system-generated only)
INSERT INTO audit_log (user_id, action, timestamp) 
VALUES (1, 'fake_login', NOW())
```

**Violation Message:**
```
[HIGH] Write operation INSERT on read-only table: audit_log
Suggestion: Read-only tables cannot be modified. Remove INSERT operation or use a different table.
```

### BAD: UPDATE Read-Only Table

```sql
-- ❌ HIGH: Modifying historical records
UPDATE history_users SET email = 'new@example.com' WHERE id = 1
```

**Violation Message:**
```
[HIGH] Write operation UPDATE on read-only table: history_users
Suggestion: Read-only tables cannot be modified. Remove UPDATE operation or use a different table.
```

### BAD: DELETE from Read-Only Table

```sql
-- ❌ HIGH: Deleting audit records
DELETE FROM audit_log WHERE created_at < '2023-01-01'
```

**Violation Message:**
```
[HIGH] Write operation DELETE on read-only table: audit_log
Suggestion: Read-only tables cannot be modified. Remove DELETE operation or use a different table.
```

### GOOD: SELECT from Read-Only Table

```sql
-- ✅ SAFE: Reading audit logs is allowed
SELECT * FROM audit_log WHERE user_id = 1

-- ✅ SAFE: Reading historical data is allowed
SELECT * FROM history_orders WHERE order_date > '2023-01-01'
```

### GOOD: Read-Only Table in WHERE Clause Only

```sql
-- ✅ SAFE: audit_log is only in WHERE subquery, not the target
UPDATE users SET status = 'active' 
WHERE id IN (SELECT user_id FROM audit_log WHERE action = 'verified')

-- ✅ SAFE: history_orders is only in WHERE, users is the target
DELETE FROM users 
WHERE id NOT IN (SELECT DISTINCT user_id FROM history_orders)
```

### GOOD: Write to Non-Read-Only Tables

```sql
-- ✅ SAFE: users is not in readonly list
INSERT INTO users (name, email) VALUES ('John', 'john@example.com')

-- ✅ SAFE: orders is not in readonly list
UPDATE orders SET status = 'shipped' WHERE id = 1
```

## Wildcard Pattern Support

Use `*` at the end of patterns to match multiple tables:

| Pattern | Matches | Does NOT Match |
|---------|---------|----------------|
| `audit_log` | `audit_log` | `audit_logs`, `audit_log_2024` |
| `history_*` | `history_users`, `history_orders`, `history_payments` | `historydata`, `user_history` |
| `audit_*` | `audit_log`, `audit_events`, `audit_trail` | `auditlog`, `user_audit` |
| `sys_*` | `sys_config`, `sys_users`, `sys_permissions` | `system_config` |

**Note:** The wildcard `*` only matches at the end of a pattern. `*_log` is NOT supported.

## Configuration

### Basic Configuration

```yaml
sql-guard:
  rules:
    readonly-table:
      enabled: true
      readonly-tables:
        - audit_log
        - history_*
```

### Enable/Disable

```yaml
sql-guard:
  rules:
    readonly-table:
      enabled: true  # Default: true
```

### Configure Read-Only Tables

```yaml
sql-guard:
  rules:
    readonly-table:
      readonly-tables:
        # Exact matches
        - audit_log
        - audit_events
        - sys_config
        
        # Wildcard patterns
        - history_*      # Protects all history_xxx tables
        - archive_*      # Protects all archive_xxx tables
        - reference_*    # Protects all reference_xxx tables
```

### Comprehensive Example

```yaml
sql-guard:
  rules:
    readonly-table:
      enabled: true
      readonly-tables:
        # Audit tables (immutable for compliance)
        - audit_log
        - audit_events
        - audit_*
        
        # Historical snapshots (data integrity)
        - history_*
        - snapshot_*
        
        # Reference/lookup tables
        - country_codes
        - currency_codes
        - sys_config
        
        # Archive tables
        - archive_*
```

## Edge Cases

### Case 1: Schema-Qualified Names

```sql
-- Schema prefix is stripped, table name is checked
INSERT INTO mydb.audit_log (user_id) VALUES (1)
-- Detected as: audit_log (readonly)
```

**Behavior:** Schema prefix is ignored; only table name is matched against patterns.

### Case 2: Quoted Table Names

```sql
-- MySQL backticks
INSERT INTO `audit_log` (user_id) VALUES (1)

-- PostgreSQL/Oracle double quotes
INSERT INTO "audit_log" (user_id) VALUES (1)
```

**Behavior:** Quotes are stripped before matching.

### Case 3: Case Sensitivity

```sql
-- All of these match "audit_log" pattern
INSERT INTO AUDIT_LOG (user_id) VALUES (1)
INSERT INTO Audit_Log (user_id) VALUES (1)
INSERT INTO audit_log (user_id) VALUES (1)
```

**Behavior:** Matching is case-insensitive.

### Case 4: Empty Configuration

```yaml
sql-guard:
  rules:
    readonly-table:
      enabled: true
      readonly-tables: []  # Empty list
```

**Behavior:** No tables are protected; all writes pass.

## Design Rationale

### Why HIGH Risk Level (Not CRITICAL)?

- **CRITICAL** is reserved for structure-altering operations (DDL) that can cause irreversible schema damage
- **HIGH** is appropriate for data modification operations that violate business rules
- Write operations on readonly tables are serious but don't affect database structure
- Application-level protection complements (not replaces) database-level permissions

### Application-Level vs Database-Level Protection

| Layer | Protection | Use Case |
|-------|------------|----------|
| **Database** | GRANT/REVOKE permissions | Hard enforcement, DBA-controlled |
| **Application** | SQL Guard readonly-table rule | Soft enforcement, developer-controlled |

**Best Practice:** Use both layers:
1. Database permissions for hard enforcement
2. SQL Guard for early detection during development/testing

### Why Only Check Target Table?

```sql
-- Only users table is checked (the UPDATE target)
-- audit_log in WHERE is NOT flagged
UPDATE users SET status = 1 
WHERE id IN (SELECT user_id FROM audit_log)
```

**Rationale:**
- The WHERE clause only reads data (SELECT)
- Only the target table receives write operations
- Flagging WHERE clause tables would cause false positives

## Related Rules

- **[No WHERE Clause](no-where-clause.md)** - Detects DELETE/UPDATE without WHERE
- **[DDL Operation](ddl-operation.md)** - Detects structure-altering statements (CRITICAL)
- **[Denied Table](denied-table.md)** - Blocks access to specific tables entirely

## Best Practices

### 1. Define Clear Readonly Categories

```yaml
readonly-tables:
  # Category: Audit (immutable for compliance)
  - audit_*
  
  # Category: Historical (preserved for analytics)
  - history_*
  - snapshot_*
  
  # Category: Reference (stable lookup data)
  - ref_*
  - sys_config
```

### 2. Use Consistent Naming Conventions

```sql
-- Good: Clear prefix indicates readonly nature
audit_log, audit_events
history_users, history_orders
ref_countries, ref_currencies

-- Bad: No clear indication of readonly status
user_actions  -- Is this readonly?
old_orders    -- Is this readonly?
```

### 3. Document Readonly Tables

```java
/**
 * Audit log table - READ ONLY
 * 
 * This table is protected by SQL Guard readonly-table rule.
 * All writes must go through AuditService.log() method.
 * 
 * @see ReadOnlyTableChecker
 */
@Table("audit_log")
public class AuditLog { ... }
```

### 4. Combine with Database Permissions

```sql
-- Database-level protection (DBA)
REVOKE INSERT, UPDATE, DELETE ON audit_log FROM app_user;
GRANT SELECT ON audit_log TO app_user;

-- Application-level protection (SQL Guard)
# Already configured in sql-guard.yml
```

## Testing

### Unit Test Example

```java
@Test
public void testWriteToReadonlyTableBlocked() {
    // Given
    String sql = "DELETE FROM audit_log WHERE id = 1";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))
        .type(SqlCommandType.DELETE)
        .build();
    
    // When
    ValidationResult result = validator.validate(context);
    
    // Then
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    assertTrue(result.getViolations().get(0).getMessage()
        .contains("read-only table"));
}
```

### Integration Test Example

```java
@Test
public void testReadFromReadonlyTableAllowed() {
    // Given: readonly-table rule configured
    
    // When: SELECT from audit_log
    List<AuditLog> logs = auditMapper.selectByUserId(1);
    
    // Then: Query succeeds
    assertNotNull(logs);
    // No exception thrown
}

@Test
public void testWriteToReadonlyTableBlocked() {
    // Given: readonly-table rule with BLOCK strategy
    
    // When/Then: INSERT into audit_log throws exception
    assertThrows(SQLException.class, () -> {
        auditMapper.insert(new AuditLog(1, "fake_action"));
    });
}
```

## Troubleshooting

### Issue: False Positives on Similar Table Names

**Problem:** `history` table is blocked when only `history_*` pattern is configured.

**Solution:** The wildcard pattern `history_*` requires the underscore. `history` without underscore will NOT match. If you want to protect `history` table, add it explicitly:

```yaml
readonly-tables:
  - history      # Exact match for "history"
  - history_*    # Pattern match for "history_xxx"
```

### Issue: Schema-Qualified Names Not Detected

**Problem:** `mydb.audit_log` is not detected as readonly.

**Solution:** The checker strips schema prefix and matches only the table name. `mydb.audit_log` will match `audit_log` pattern. If this doesn't work, check for typos in the pattern.

### Issue: Wildcard Not Working

**Problem:** `*_log` pattern doesn't match `audit_log`.

**Solution:** Wildcards only work at the END of patterns. `*_log` is NOT supported. Use exact matches instead:

```yaml
readonly-tables:
  - audit_log
  - access_log
  - error_log
```

---

**Questions?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).
