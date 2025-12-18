# SQL Safety Guard Rules

SQL Safety Guard includes 10 specialized rule checkers detecting dangerous SQL patterns. Each rule is documented with risk level, detection logic, examples, and remediation guidance.

## Rule Index

| Rule | Risk Level | Description | Documentation |
|------|-----------|-------------|---------------|
| **No WHERE Clause** | 🔴 CRITICAL | Detects DELETE/UPDATE/SELECT without WHERE | [Details](no-where-clause.md) |
| **Dummy Condition** | 🟠 HIGH | Detects meaningless conditions (1=1, true) | [Details](dummy-condition.md) |
| **Blacklist Fields** | 🟠 HIGH | Detects WHERE using only low-cardinality fields | [Details](blacklist-whitelist.md#blacklist-fields) |
| **Whitelist Fields** | 🟠 HIGH | Enforces mandatory high-selectivity fields | [Details](blacklist-whitelist.md#whitelist-fields) |
| **Logical Pagination** | 🔴 CRITICAL | Detects in-memory pagination (OOM risk) | [Details](logical-pagination.md) |
| **No Condition Pagination** | 🔴 CRITICAL | Detects LIMIT without WHERE | [Details](pagination-abuse.md#no-condition-pagination) |
| **Deep Pagination** | 🟡 MEDIUM | Detects high OFFSET values | [Details](pagination-abuse.md#deep-pagination) |
| **Large Page Size** | 🟡 MEDIUM | Detects excessive LIMIT values | [Details](pagination-abuse.md#large-page-size) |
| **Missing ORDER BY** | 🟢 LOW | Detects pagination without ORDER BY | [Details](missing-orderby.md) |
| **No Pagination** | 🟡 VARIABLE | Detects SELECT without LIMIT | [Details](no-pagination.md) |

## Risk Level Definitions

### 🔴 CRITICAL

**Impact:** Severe security or data integrity issues  
**Examples:** Data loss, memory exhaustion, production outages  
**Action:** Block execution immediately (BLOCK strategy)

**Rules:**
- No WHERE Clause
- Logical Pagination
- No Condition Pagination

### 🟠 HIGH

**Impact:** Serious issues requiring immediate attention  
**Examples:** Performance degradation, security vulnerabilities  
**Action:** Warn and monitor (WARN strategy), block after validation (BLOCK strategy)

**Rules:**
- Dummy Condition
- Blacklist Fields
- Whitelist Fields

### 🟡 MEDIUM

**Impact:** Moderate issues that should be addressed  
**Examples:** Suboptimal performance, potential scalability issues  
**Action:** Log and track (LOG strategy), warn in staging (WARN strategy)

**Rules:**
- Deep Pagination
- Large Page Size
- No Pagination (with WHERE clause)

### 🟢 LOW

**Impact:** Minor issues, informational only  
**Examples:** Code quality concerns, best practice violations  
**Action:** Log for awareness (LOG strategy)

**Rules:**
- Missing ORDER BY

## Rule Categories

### Data Safety Rules

Prevent data loss and corruption:

- **[No WHERE Clause](no-where-clause.md)** - Blocks DELETE/UPDATE without WHERE
- **[Dummy Condition](dummy-condition.md)** - Detects ineffective WHERE clauses

### Performance Rules

Prevent performance degradation:

- **[Logical Pagination](logical-pagination.md)** - Prevents in-memory pagination
- **[No Condition Pagination](pagination-abuse.md#no-condition-pagination)** - Requires WHERE with LIMIT
- **[Deep Pagination](pagination-abuse.md#deep-pagination)** - Limits OFFSET values
- **[Large Page Size](pagination-abuse.md#large-page-size)** - Limits LIMIT values
- **[No Pagination](no-pagination.md)** - Encourages LIMIT usage

### Security Rules

Prevent data leakage and unauthorized access:

- **[Blacklist Fields](blacklist-whitelist.md#blacklist-fields)** - Prevents low-selectivity queries
- **[Whitelist Fields](blacklist-whitelist.md#whitelist-fields)** - Enforces high-selectivity fields

### Code Quality Rules

Improve code maintainability:

- **[Missing ORDER BY](missing-orderby.md)** - Ensures stable pagination ordering

## Common Scenarios

### Scenario 1: Accidental Full Table Delete

**Problem:** Developer forgets WHERE clause in DELETE statement.

```sql
-- ❌ DANGEROUS: Deletes all users
DELETE FROM users
```

**Detection:** No WHERE Clause checker (CRITICAL)

**Prevention:** SQL Guard blocks execution with BLOCK strategy.

**Remediation:**

```sql
-- ✅ SAFE: Deletes specific user
DELETE FROM users WHERE id = ?
```

### Scenario 2: Logical Pagination OOM

**Problem:** Using RowBounds without pagination plugin loads entire table into memory.

```java
// ❌ DANGEROUS: Loads all users into memory, then skips 10000 rows
List<User> users = userMapper.selectAll(new RowBounds(10000, 100));
```

**Detection:** Logical Pagination checker (CRITICAL)

**Prevention:** SQL Guard blocks execution with BLOCK strategy.

**Remediation:**

```java
// ✅ SAFE: Configure PageHelper plugin
// SQL becomes: SELECT * FROM users LIMIT 100 OFFSET 10000
List<User> users = userMapper.selectAll(new RowBounds(10000, 100));
```

### Scenario 3: Dummy Condition Full Table Scan

**Problem:** Dummy condition makes WHERE clause ineffective.

```sql
-- ❌ DANGEROUS: WHERE clause present but ineffective
SELECT * FROM users WHERE 1=1 AND status = ?
```

**Detection:** Dummy Condition checker (HIGH)

**Prevention:** SQL Guard warns or blocks based on strategy.

**Remediation:**

```sql
-- ✅ SAFE: Use dynamic SQL or remove dummy condition
<select id="selectUsers">
    SELECT * FROM users
    <where>
        <if test="status != null">
            AND status = #{status}
        </if>
    </where>
</select>
```

### Scenario 4: Deep Pagination Performance

**Problem:** High OFFSET values cause database to scan and discard many rows.

```sql
-- ❌ SLOW: Database scans 10100 rows, returns 100
SELECT * FROM users ORDER BY id LIMIT 100 OFFSET 10000
```

**Detection:** Deep Pagination checker (MEDIUM)

**Prevention:** SQL Guard warns about performance impact.

**Remediation:**

```sql
-- ✅ FAST: Use cursor-based pagination
SELECT * FROM users WHERE id > ? ORDER BY id LIMIT 100
```

### Scenario 5: Blacklist-Only Query

**Problem:** WHERE clause uses only low-cardinality fields enabling data leakage.

```sql
-- ❌ DANGEROUS: deleted=0 matches 99% of rows
SELECT * FROM users WHERE deleted = 0
```

**Detection:** Blacklist Fields checker (HIGH)

**Prevention:** SQL Guard warns or blocks based on strategy.

**Remediation:**

```sql
-- ✅ SAFE: Include high-selectivity field
SELECT * FROM users WHERE id = ? AND deleted = 0
```

## Configuration Examples

### Strict Configuration (Production)

```yaml
sql-guard:
  active-strategy: BLOCK
  rules:
    no-where-clause:
      enabled: true
      risk-level: CRITICAL
    dummy-condition:
      enabled: true
      risk-level: HIGH
    logical-pagination:
      enabled: true
      risk-level: CRITICAL
```

### Lenient Configuration (Development)

```yaml
sql-guard:
  active-strategy: LOG
  rules:
    no-where-clause:
      enabled: true
      risk-level: HIGH  # Downgrade to HIGH
    missing-order-by:
      enabled: false  # Disable low-priority rules
```

### Custom Blacklist

```yaml
sql-guard:
  rules:
    blacklist-fields:
      enabled: true
      blacklist-fields:
        - deleted
        - del_flag
        - is_deleted
        - status
        - enabled
        - type
        - create_time
        - update_time
        - create_by
        - update_by
```

### Table-Specific Whitelist

```yaml
sql-guard:
  rules:
    whitelist-fields:
      enabled: true
      whitelist-fields:
        user:
          - id
          - user_id
          - email
        order:
          - id
          - order_id
          - user_id
        payment:
          - id
          - payment_id
          - order_id
```

## Rule Interaction

### Checker Execution Order

Rules execute in priority order (highest risk first):

1. **No WHERE Clause** (CRITICAL)
2. **Logical Pagination** (CRITICAL)
3. **No Condition Pagination** (CRITICAL)
4. **Dummy Condition** (HIGH)
5. **Blacklist Fields** (HIGH)
6. **Whitelist Fields** (HIGH)
7. **Deep Pagination** (MEDIUM)
8. **Large Page Size** (MEDIUM)
9. **No Pagination** (VARIABLE)
10. **Missing ORDER BY** (LOW)

### Early Return Mechanism

**No Condition Pagination** checker uses early-return to prevent misleading violations:

```sql
-- Example: SELECT * FROM users LIMIT 100
-- Violations detected:
-- 1. No Condition Pagination (CRITICAL) - early return triggered
-- 2. No Pagination (skipped due to early return)
-- 3. Missing ORDER BY (still checked, LOW priority)
```

### Multiple Violations

SQL can violate multiple rules simultaneously:

```sql
-- Example: SELECT * FROM users WHERE 1=1
-- Violations detected:
-- 1. Dummy Condition (HIGH)
-- 2. No Pagination (MEDIUM)
-- Final risk level: HIGH (max of all violations)
```

## Disabling Rules

### Disable Specific Rule

```yaml
sql-guard:
  rules:
    missing-order-by:
      enabled: false  # Disable rule entirely
```

### Disable for Specific Environments

```yaml
# application-dev.yml
sql-guard:
  rules:
    no-pagination:
      enabled: false  # Disable in development
```

### Disable Temporarily

```java
// Not recommended - use configuration instead
@Autowired
private NoWhereClauseChecker checker;

@PostConstruct
public void disableChecker() {
    checker.setEnabled(false);
}
```

## Next Steps

- **[No WHERE Clause](no-where-clause.md)** - CRITICAL rule preventing data loss
- **[Logical Pagination](logical-pagination.md)** - CRITICAL rule preventing OOM
- **[Pagination Abuse](pagination-abuse.md)** - MEDIUM rules optimizing performance
- **[Configuration Reference](../configuration-reference.md)** - Complete property documentation

---

**Need help?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).



