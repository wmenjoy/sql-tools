# Whitelist Fields Rule

**Risk Level:** 🟡 MEDIUM

## Overview

The Whitelist Fields rule enforces that critical tables require specific high-selectivity fields in WHERE clauses, providing an additional safety layer for multi-tenant systems, GDPR compliance, and critical business operations. Unlike the Blacklist Fields rule which detects low-selectivity fields, this rule mandates high-selectivity fields must be present.

## What It Detects

SELECT/UPDATE/DELETE statements on configured tables that lack required whitelist fields:

- **Table-Specific Whitelist** - Per-table required field lists (users table requires `user_id` or `email`)
- **Global Whitelist** - Default required fields for unknown tables (optional)
- **Mandatory Field Enforcement** - ANY ONE required field must be present in WHERE

**Common Use Cases:**
- Multi-tenant systems requiring `tenant_id` in all queries
- GDPR compliance requiring user identifiers (`user_id`, `email`)
- Critical business tables requiring transaction IDs
- Audit tables requiring correlation IDs

## Why Important

### Multi-Tenant Data Isolation

```sql
-- ❌ MEDIUM RISK: Missing tenant_id, could leak cross-tenant data
SELECT * FROM tenant_data WHERE status = 'active'
```

**Security Risk:**
- Without `tenant_id`, query may return data from all tenants
- Accidental cross-tenant data exposure
- GDPR/compliance violations
- Potential data breach

### GDPR Compliance

```sql
-- ❌ MEDIUM RISK: Missing user identifier for data deletion
DELETE FROM user_activity WHERE action = 'login'
```

**Compliance Risk:**
- GDPR requires ability to delete all user data
- Without `user_id`, cannot ensure complete deletion
- Audit trail cannot be correlated to specific user
- Legal liability

### Performance Safeguard

```sql
-- ❌ MEDIUM RISK: Missing primary key, slow query
UPDATE orders SET status = 'shipped' WHERE region = 'US'
```

**Performance Risk:**
- Without primary key (`order_id`), scans many rows
- Long-running UPDATE locks table
- Impacts production traffic
- Database connection pool exhaustion

## Examples

### BAD: Missing Required Field (Multi-Tenant)

```sql
-- ❌ MEDIUM RISK: Missing tenant_id
SELECT * FROM tenant_data WHERE status = 'active'
```

**Violation Message:**
```
[MEDIUM] 表tenant_data的WHERE条件必须包含以下字段之一:[tenant_id]
MapperId: com.example.TenantDataMapper.selectActive
SQL: SELECT * FROM tenant_data WHERE status = 'active'
Suggestion: Add tenant_id to WHERE clause
Risk: Cross-tenant data leak, compliance violation
```

### BAD: Missing Required Field (GDPR)

```sql
-- ❌ MEDIUM RISK: Missing user_id or email
DELETE FROM user_preferences WHERE theme = 'dark'
```

**Violation Message:**
```
[MEDIUM] 表user_preferences的WHERE条件必须包含以下字段之一:[user_id, email]
MapperId: com.example.UserMapper.deletePreferences
SQL: DELETE FROM user_preferences WHERE theme = 'dark'
Suggestion: Add user_id or email to WHERE clause
```

### BAD: Missing Primary Key

```sql
-- ❌ MEDIUM RISK: Missing order_id
UPDATE orders SET status = 'cancelled' WHERE user_id = ?
```

**Violation Message:**
```
[MEDIUM] 表orders的WHERE条件必须包含以下字段之一:[order_id, order_number]
MapperId: com.example.OrderMapper.cancelByUser
SQL: UPDATE orders SET status = 'cancelled' WHERE user_id = ?
Suggestion: Add order_id or order_number for precise targeting
```

### GOOD: Contains Required Field (tenant_id)

```sql
-- ✅ SAFE: tenant_id present
SELECT * FROM tenant_data
WHERE tenant_id = ?
  AND status = 'active'
```

### GOOD: Contains Required Field (user_id)

```sql
-- ✅ SAFE: user_id present
DELETE FROM user_preferences
WHERE user_id = ?
  AND theme = 'dark'
```

### GOOD: Contains Required Field (order_id)

```sql
-- ✅ SAFE: order_id present
UPDATE orders
WHERE order_id = ?
  AND user_id = ?
SET status = 'shipped'
```

## Expected Messages

### Table-Specific Whitelist Violation

```
[MEDIUM] 表{table_name}的WHERE条件必须包含以下字段之一:[{field1}, {field2}, ...]
MapperId: {namespace}.{methodId}
SQL: {sql}
Suggestion: Add at least one of: {required_fields}
Risk: Data isolation breach, compliance violation
```

### Example Messages

```
[MEDIUM] 表tenant_data的WHERE条件必须包含以下字段之一:[tenant_id]
[MEDIUM] 表user_activity的WHERE条件必须包含以下字段之一:[user_id, email]
[MEDIUM] 表orders的WHERE条件必须包含以下字段之一:[order_id, order_number]
```

## How to Fix

### Option 1: Add Required Field to WHERE (Recommended)

```sql
-- Before (dangerous)
SELECT * FROM tenant_data WHERE status = 'active'

-- After (safe)
SELECT * FROM tenant_data
WHERE tenant_id = ?
  AND status = 'active'
```

```java
// Before (dangerous)
@Select("SELECT * FROM tenant_data WHERE status = #{status}")
List<TenantData> selectByStatus(@Param("status") String status);

// After (safe)
@Select("SELECT * FROM tenant_data WHERE tenant_id = #{tenantId} AND status = #{status}")
List<TenantData> selectByTenantAndStatus(@Param("tenantId") Long tenantId,
                                          @Param("status") String status);
```

### Option 2: Use Service Layer Context (Multi-Tenant)

```java
// Before (dangerous)
public List<TenantData> searchData(String status) {
    QueryWrapper<TenantData> wrapper = new QueryWrapper<TenantData>()
        .eq("status", status);
    return tenantDataMapper.selectList(wrapper);
}

// After (safe)
public List<TenantData> searchData(String status) {
    // Get tenant_id from ThreadLocal security context
    Long tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
        throw new SecurityException("No tenant context available");
    }

    QueryWrapper<TenantData> wrapper = new QueryWrapper<TenantData>()
        .eq("tenant_id", tenantId)  // Required whitelist field
        .eq("status", status);
    return tenantDataMapper.selectList(wrapper);
}
```

### Option 3: Use MyBatis Interceptor for Auto-Injection

```java
// Interceptor automatically adds tenant_id to all queries
@Intercepts({
    @Signature(type = Executor.class, method = "query", args = {...}),
    @Signature(type = Executor.class, method = "update", args = {...})
})
public class TenantInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new SecurityException("No tenant context");
        }

        // Inject tenant_id into SQL WHERE clause
        BoundSql boundSql = ...;
        String sql = boundSql.getSql();
        String modifiedSql = injectTenantId(sql, tenantId);

        return invocation.proceed();
    }
}
```

### Option 4: Use MyBatis-Plus TenantLineInnerInterceptor

```java
// MyBatis-Plus built-in tenant isolation
@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // Add tenant line interceptor
        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
        tenantInterceptor.setTenantLineHandler(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                // Get tenant_id from context
                Long tenantId = TenantContext.getCurrentTenantId();
                return new LongValue(tenantId);
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }
        });

        interceptor.addInnerInterceptor(tenantInterceptor);
        return interceptor;
    }
}
```

## Configuration

### Enable/Disable

```yaml
sql-guard:
  rules:
    whitelist-fields:
      enabled: true  # Default: true
```

### Table-Specific Whitelist (Recommended)

```yaml
sql-guard:
  rules:
    whitelist-fields:
      enabled: true
      by-table:
        # Multi-tenant tables
        tenant_data:
          - tenant_id
        tenant_users:
          - tenant_id

        # User-related tables (GDPR compliance)
        user_activity:
          - user_id
          - email
        user_preferences:
          - user_id

        # Business-critical tables
        orders:
          - order_id
          - order_number
        payments:
          - payment_id
          - transaction_id

        # Audit tables
        audit_log:
          - correlation_id
          - request_id
```

**Configuration Explanation:**
- `by-table`: Map of table name to required field list
- ANY ONE field from the list must be present in WHERE
- Multiple fields = "OR" logic (user_id OR email)

### Global Whitelist for Unknown Tables (Optional)

```yaml
sql-guard:
  rules:
    whitelist-fields:
      enabled: true
      enforce-for-unknown-tables: true  # Default: false
      fields:
        - id
        - uuid
```

**Caution:** Setting `enforce-for-unknown-tables: true` applies global whitelist to ALL tables not in `by-table` map. Use sparingly.

### Adjust Risk Level

```yaml
sql-guard:
  rules:
    whitelist-fields:
      risk-level: MEDIUM  # Default: MEDIUM
```

**Not recommended to change:** MEDIUM is appropriate as this is opt-in enforcement.

### Whitelist Specific Mappers

For exceptional cases:

```yaml
sql-guard:
  rules:
    whitelist-fields:
      whitelist-mapper-ids:
        - "AdminMapper.selectAllTenants"  # Admin backdoor query
        - "ReportMapper.aggregateStats"   # Aggregate query
```

**Caution:** Only whitelist if business logic genuinely requires querying without required fields.

## Edge Cases

### Case 1: Multiple Required Fields (OR Logic)

```sql
-- Configuration: user_activity requires [user_id, email]

-- ✅ SAFE: user_id present
SELECT * FROM user_activity WHERE user_id = ?

-- ✅ SAFE: email present
SELECT * FROM user_activity WHERE email = ?

-- ✅ SAFE: Both present
SELECT * FROM user_activity WHERE user_id = ? AND email = ?

-- ❌ VIOLATES: Neither present
SELECT * FROM user_activity WHERE action = 'login'
```

**Behavior:** ANY ONE required field satisfies the rule

### Case 2: Unknown Table Without Global Whitelist

```sql
-- Configuration: enforce-for-unknown-tables = false (default)

-- ✅ SAFE: unknown_table not in by-table map
SELECT * FROM unknown_table WHERE status = 'active'
```

**Behavior:** Tables not in `by-table` map are skipped by default

### Case 3: Unknown Table WITH Global Whitelist

```sql
-- Configuration:
--   enforce-for-unknown-tables: true
--   fields: [id, uuid]

-- ✅ SAFE: id present
SELECT * FROM unknown_table WHERE id = ?

-- ❌ VIOLATES: Neither id nor uuid present
SELECT * FROM unknown_table WHERE status = 'active'
```

**Behavior:** Unknown tables must include global whitelist field

### Case 4: No WHERE Clause

```sql
-- ❌ No WHERE clause at all
SELECT * FROM tenant_data
```

**Behavior:** Not checked by WhitelistFieldChecker
**Reason:** Handled by NoWhereClauseChecker (CRITICAL)

### Case 5: Dynamic SQL with Optional Required Field

```xml
<select id="selectTenantData">
    SELECT * FROM tenant_data
    <where>
        <if test="tenantId != null">
            AND tenant_id = #{tenantId}
        </if>
        <if test="status != null">
            AND status = #{status}
        </if>
    </where>
</select>
```

**Behavior:**
- **Static Scanner:** Generates variant without `tenant_id` → flagged as violation
- **Runtime Validator:** If `tenantId=null`, SQL lacks required field → flagged as violation

**Fix:** Always include required field:

```xml
<select id="selectTenantData">
    SELECT * FROM tenant_data
    WHERE tenant_id = #{tenantId}  <!-- Always present -->
    <if test="status != null">
        AND status = #{status}
    </if>
</select>
```

## Relationship with Other Rules

### Complementary with Blacklist Fields

```sql
-- Example: tenant_data table
-- Whitelist: [tenant_id] (required)
-- Blacklist: [deleted, status] (low-selectivity)

-- ❌ Violates BOTH rules
SELECT * FROM tenant_data WHERE status = 'active'
-- Missing tenant_id (whitelist violation)
-- Only has status (blacklist violation)

-- ❌ Violates whitelist only
SELECT * FROM tenant_data WHERE id = ? AND status = 'active'
-- Missing tenant_id (whitelist violation)
-- Has id (non-blacklist field), so no blacklist violation

-- ✅ SAFE: Satisfies both rules
SELECT * FROM tenant_data
WHERE tenant_id = ?  -- Satisfies whitelist
  AND id = ?         -- Non-blacklist field
```

### Related Rules

- **[Blacklist Fields](blacklist-fields.md)** - Detects blacklist-only WHERE clauses
- **[No WHERE Clause](no-where-clause.md)** - Detects complete absence of WHERE
- **[Dummy Condition](dummy-condition.md)** - Detects meaningless conditions

## Production Incidents Prevented

### Incident 1: Cross-Tenant Data Leak

**Company:** SaaS platform (1000+ tenants)
**Incident:** Admin search executed `SELECT * FROM tenant_users WHERE role = 'admin'`
**Impact:**
- Returned admin users from ALL tenants (security breach)
- Exposed 50+ tenant admin emails and metadata
- GDPR violation notification required
- Customer churn (3 enterprises cancelled contracts)
- Legal settlement: $200K

**Root Cause:** Missing `tenant_id` filter
**Query Statistics:**
- Intended: 5 admins from tenant_id=123
- Actual: 50+ admins from all tenants

**Prevention:** SQL Guard whitelist enforcement would have BLOCKED execution

**Fix:**
```sql
-- Before (dangerous)
SELECT * FROM tenant_users WHERE role = 'admin'

-- After (safe)
SELECT * FROM tenant_users
WHERE tenant_id = ?
  AND role = 'admin'
```

### Incident 2: GDPR Deletion Incomplete

**Company:** E-commerce platform
**Incident:** GDPR deletion request executed `DELETE FROM user_activity WHERE ip_address = ?`
**Impact:**
- Deleted activities from wrong users (same IP address)
- Failed to delete target user's complete activity
- GDPR non-compliance
- Regulatory fine: €50K
- Audit failure

**Root Cause:** Used `ip_address` instead of `user_id`
**Affected Records:**
- Intended: Delete 1000 activities from user_id=456
- Actual: Deleted 500 activities from 20 different users with same IP

**Prevention:** SQL Guard whitelist requiring `user_id` or `email`

**Fix:**
```sql
-- Before (dangerous)
DELETE FROM user_activity WHERE ip_address = ?

-- After (safe)
DELETE FROM user_activity WHERE user_id = ?
```

### Incident 3: Mass Order Cancellation

**Company:** Logistics platform
**Incident:** Bug in admin tool executed `UPDATE orders SET status = 'cancelled' WHERE region = 'US'`
**Impact:**
- Cancelled 50,000 active orders
- Production outage (2 hours)
- Customer support flood (1000+ tickets)
- Revenue loss: $500K
- Emergency rollback required

**Root Cause:** Missing `order_id`, used `region` instead
**Query Statistics:**
- Intended: Cancel 1 order (order_id=12345)
- Actual: Cancelled 50,000 orders

**Prevention:** SQL Guard whitelist requiring `order_id` or `order_number`

**Fix:**
```sql
-- Before (dangerous)
UPDATE orders SET status = 'cancelled' WHERE region = ?

-- After (safe)
UPDATE orders
WHERE order_id = ?
  AND region = ?
SET status = 'cancelled'
```

## Best Practices

### 1. Define Whitelist for Critical Tables

```yaml
# ✅ Identify critical tables requiring whitelist
sql-guard:
  rules:
    whitelist-fields:
      by-table:
        # Multi-tenant tables
        tenant_*:
          - tenant_id

        # User data (GDPR)
        user_*:
          - user_id
          - email

        # Transactional tables
        orders:
          - order_id
        payments:
          - payment_id

        # Audit tables
        audit_log:
          - correlation_id
```

### 2. Use Service Layer Enforcement

```java
// ✅ Service layer enforces required field presence
@Service
public class TenantDataService {

    public List<TenantData> search(SearchDto search) {
        // Validate tenant_id is present
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new SecurityException("Tenant context required");
        }

        QueryWrapper<TenantData> wrapper = new QueryWrapper<TenantData>()
            .eq("tenant_id", tenantId)  // Always include required field
            .eq(search.getStatus() != null, "status", search.getStatus());

        return tenantDataMapper.selectList(wrapper);
    }
}
```

### 3. Document Whitelist Rationale

```yaml
# ✅ Document WHY each table requires specific fields
sql-guard:
  rules:
    whitelist-fields:
      by-table:
        # Multi-tenant isolation (security requirement)
        tenant_data:
          - tenant_id  # Required for data isolation

        # GDPR compliance (legal requirement)
        user_activity:
          - user_id    # Required for data deletion
          - email      # Alternative identifier

        # Transaction integrity (business requirement)
        orders:
          - order_id   # Required for precise targeting
```

### 4. Use "OR" Logic for Flexibility

```yaml
# ✅ Allow alternative identifiers
sql-guard:
  rules:
    whitelist-fields:
      by-table:
        users:
          - id        # Primary key
          - email     # Alternative unique identifier
          - username  # Another alternative
```

**Behavior:** Query must include ANY ONE of these fields

### 5. Combine with Auto-Injection Interceptor

```java
// ✅ Use interceptor for automatic field injection
@Component
public class TenantInterceptor implements InnerInterceptor {

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms,
                           Object parameter, RowBounds rowBounds,
                           ResultHandler resultHandler, BoundSql boundSql) {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            // Auto-inject tenant_id into SQL
            injectTenantId(boundSql, tenantId);
        }
    }
}
```

**Benefit:** Developers don't need to remember to add `tenant_id` manually

## Testing

### Unit Test Example

```java
@Test
public void testWhitelistFieldsDetection() {
    // Given
    String sql = "SELECT * FROM tenant_data WHERE status = 'active'";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .mapperId("TenantDataMapper.selectByStatus")
        .build();

    // When
    ValidationResult result = validator.validate(context);

    // Then
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertTrue(result.getViolations().get(0).getMessage()
        .contains("必须包含以下字段之一"));
    assertTrue(result.getViolations().get(0).getMessage()
        .contains("tenant_id"));
}

@Test
public void testRequiredFieldPresent() {
    // Given: Required field tenant_id is present
    String sql = "SELECT * FROM tenant_data WHERE tenant_id = 1 AND status = 'active'";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .mapperId("TenantDataMapper.selectByTenantAndStatus")
        .build();

    // When
    ValidationResult result = validator.validate(context);

    // Then
    assertTrue(result.isPassed());  // Should pass
}
```

### Integration Test Example

```java
@Test
public void testWhitelistFieldsBlocking() {
    // Given: BLOCK strategy configured
    // tenant_data requires tenant_id

    // When/Then: SQLException thrown
    assertThrows(SQLException.class, () -> {
        tenantDataMapper.selectByStatus("active");
    });
}

@Test
public void testWhitelistFieldsWithRequiredField() {
    // Given: BLOCK strategy configured
    // tenant_data requires tenant_id

    // When: Query includes tenant_id
    List<TenantData> data = tenantDataMapper.selectByTenantAndStatus(1L, "active");

    // Then: Returns data successfully
    assertNotNull(data);
}
```

## Next Steps

- **[Blacklist Fields Rule](blacklist-fields.md)** - Detects blacklist-only WHERE clauses
- **[No WHERE Clause Rule](no-where-clause.md)** - Broader protection against missing WHERE
- **[Multi-Tenant Architecture Guide](../../developer-guide/multi-tenant-architecture.md)** - Design patterns for tenant isolation
- **[Configuration Reference](../configuration-reference.md)** - Configure rule settings

---

**Questions?** See [FAQ](../faq.md) or [Troubleshooting Guide](../troubleshooting.md).
