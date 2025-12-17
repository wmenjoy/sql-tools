# Configuration Reference

Complete reference for all SQL Safety Guard configuration properties.

## Table of Contents

- [Configuration Format](#configuration-format)
- [Global Settings](#global-settings)
- [Interceptor Configuration](#interceptor-configuration)
- [Deduplication Configuration](#deduplication-configuration)
- [Rule Configuration](#rule-configuration)
- [Parser Configuration](#parser-configuration)
- [Complete Example](#complete-example)

## Configuration Format

SQL Safety Guard uses YAML configuration files. Configuration can be provided via:

- **Spring Boot:** `application.yml` or `application.properties` with `sql-guard.*` prefix
- **Standalone:** YAML file loaded via `YamlConfigLoader.loadFromFile()`
- **CLI Tool:** `--config-file` parameter

### Property Naming Conventions

SQL Safety Guard supports multiple naming conventions:

```yaml
# Kebab-case (recommended for YAML)
sql-guard:
  active-strategy: BLOCK
  
# Snake_case
sql_guard:
  active_strategy: BLOCK
  
# camelCase
sqlGuard:
  activeStrategy: BLOCK
```

## Global Settings

### `enabled`

**Type:** `boolean`  
**Default:** `true`  
**Description:** Master switch to enable/disable SQL Safety Guard entirely.

```yaml
sql-guard:
  enabled: true  # Enable SQL Safety Guard
```

**When to disable:**
- Development environments where validation interferes with debugging
- Temporary disable during incident response
- Testing scenarios requiring unrestricted SQL

### `active-strategy`

**Type:** `string`  
**Default:** `LOG`  
**Valid Values:** `LOG`, `WARN`, `BLOCK`  
**Description:** Violation handling strategy for runtime validation.

```yaml
sql-guard:
  active-strategy: BLOCK  # Block dangerous SQL execution
```

**Strategy Behaviors:**

| Strategy | Behavior | Use Case |
|----------|----------|----------|
| `LOG` | Log violation, allow execution | Initial deployment, observation mode |
| `WARN` | Log warning, allow execution | Pre-production validation |
| `BLOCK` | Throw SQLException, prevent execution | Production enforcement |

**Phased Deployment:**
1. Start with `LOG` (1-2 weeks) - Monitor violations
2. Move to `WARN` (1-2 weeks) - Validate warnings
3. Deploy `BLOCK` - Enforce safety

See [Deployment Guide](deployment.md) for complete strategy.

## Interceptor Configuration

### MyBatis Interceptor

**Path:** `sql-guard.interceptors.mybatis`

```yaml
sql-guard:
  interceptors:
    mybatis:
      enabled: true  # Enable MyBatis interceptor
```

**Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable MyBatis interceptor |

**When to use:**
- Applications using MyBatis XML mappers
- Applications using MyBatis annotations (@Select, @Insert, etc.)
- Need to validate resolved dynamic SQL (after `<if>`, `<where>`, `<foreach>`)

### MyBatis-Plus Interceptor

**Path:** `sql-guard.interceptors.mybatis-plus`

```yaml
sql-guard:
  interceptors:
    mybatis-plus:
      enabled: true  # Enable MyBatis-Plus interceptor
```

**Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable MyBatis-Plus interceptor |

**When to use:**
- Applications using MyBatis-Plus BaseMapper
- Applications using QueryWrapper/LambdaQueryWrapper
- Need to validate IPage pagination

### JDBC Interceptor

**Path:** `sql-guard.interceptors.jdbc`

```yaml
sql-guard:
  interceptors:
    jdbc:
      enabled: true  # Enable JDBC interceptor
```

**Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable JDBC layer interceptor |

**Supported Connection Pools:**
- Druid (DruidSqlSafetyFilter)
- HikariCP (HikariSqlSafetyProxyFactory)
- C3P0, DBCP, Tomcat JDBC (via P6Spy)

## Deduplication Configuration

**Path:** `sql-guard.deduplication`

```yaml
sql-guard:
  deduplication:
    enabled: true
    cache-size: 1000
    ttl-ms: 100
```

**Properties:**

| Property | Type | Default | Min | Max | Description |
|----------|------|---------|-----|-----|-------------|
| `enabled` | boolean | `true` | - | - | Enable deduplication filter |
| `cache-size` | int | `1000` | 1 | 100000 | LRU cache size (number of SQL statements) |
| `ttl-ms` | long | `100` | 1 | 60000 | Cache TTL in milliseconds |

**How it works:**
- ThreadLocal LRU cache stores recently validated SQL
- Within TTL window, identical SQL skips re-validation
- Prevents ~50% overhead in multi-layer setups (e.g., MyBatis + Druid)

**Tuning Recommendations:**

| Scenario | cache-size | ttl-ms | Rationale |
|----------|-----------|--------|-----------|
| Low traffic | 1000 | 100 | Default, minimal memory |
| Medium traffic | 5000 | 200 | Balance memory/hit rate |
| High traffic | 10000 | 500 | Maximize hit rate |
| Multi-layer | 5000 | 200 | Prevent double validation |

## Rule Configuration

### No WHERE Clause

**Path:** `sql-guard.rules.no-where-clause`  
**Risk Level:** `CRITICAL`

```yaml
sql-guard:
  rules:
    no-where-clause:
      enabled: true
      risk-level: CRITICAL
```

**Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable checker |
| `risk-level` | RiskLevel | `CRITICAL` | Violation severity |

**What it detects:** DELETE/UPDATE/SELECT statements without WHERE clause.

**Example violations:**
- `DELETE FROM users` ❌
- `UPDATE users SET status=1` ❌
- `SELECT * FROM users` ❌

See [no-where-clause.md](rules/no-where-clause.md) for details.

### Dummy Condition

**Path:** `sql-guard.rules.dummy-condition`  
**Risk Level:** `HIGH`

```yaml
sql-guard:
  rules:
    dummy-condition:
      enabled: true
      risk-level: HIGH
      patterns:
        - "1=1"
        - "true"
        - "'a'='a'"
```

**Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable checker |
| `risk-level` | RiskLevel | `HIGH` | Violation severity |
| `patterns` | List<String> | `["1=1", "true", "'a'='a'"]` | Dummy condition patterns |

**What it detects:** Meaningless WHERE conditions that make clause ineffective.

**Example violations:**
- `WHERE 1=1` ❌
- `WHERE true` ❌
- `WHERE status='active' AND 1=1` ❌

See [dummy-condition.md](rules/dummy-condition.md) for details.

### Blacklist Fields

**Path:** `sql-guard.rules.blacklist-fields`  
**Risk Level:** `HIGH`

```yaml
sql-guard:
  rules:
    blacklist-fields:
      enabled: true
      risk-level: HIGH
      blacklist-fields:
        - deleted
        - del_flag
        - status
        - enabled
        - type
```

**Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable checker |
| `risk-level` | RiskLevel | `HIGH` | Violation severity |
| `blacklist-fields` | List<String> | `["deleted", "status", "enabled"]` | Low-cardinality fields |

**What it detects:** WHERE clauses using ONLY blacklisted low-cardinality fields.

**Example violations:**
- `WHERE deleted=0` ❌
- `WHERE status='active' AND enabled=1` ❌

**Example passes:**
- `WHERE id=1 AND deleted=0` ✅ (has high-selectivity field)

See [blacklist-whitelist.md](rules/blacklist-whitelist.md) for details.

### Whitelist Fields

**Path:** `sql-guard.rules.whitelist-fields`  
**Risk Level:** `HIGH`

```yaml
sql-guard:
  rules:
    whitelist-fields:
      enabled: true
      risk-level: HIGH
      whitelist-fields:
        user:
          - id
          - user_id
        order:
          - id
          - order_id
          - user_id
```

**Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable checker |
| `risk-level` | RiskLevel | `HIGH` | Violation severity |
| `whitelist-fields` | Map<String, List<String>> | `{}` | Table-specific required fields |

**What it detects:** Queries on whitelisted tables missing mandatory high-selectivity fields.

**Example violations:**
- `SELECT * FROM user WHERE status='active'` ❌ (missing id/user_id)

**Example passes:**
- `SELECT * FROM user WHERE id=1` ✅
- `SELECT * FROM user WHERE user_id=?` ✅

See [blacklist-whitelist.md](rules/blacklist-whitelist.md) for details.

### Logical Pagination

**Path:** `sql-guard.rules.logical-pagination`  
**Risk Level:** `CRITICAL`

```yaml
sql-guard:
  rules:
    logical-pagination:
      enabled: true
      risk-level: CRITICAL
```

**Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable checker |
| `risk-level` | RiskLevel | `CRITICAL` | Violation severity |

**What it detects:** RowBounds/IPage pagination without pagination plugin (in-memory pagination).

**Example violations:**
- `RowBounds(100, 20)` without PageHelper ❌
- `IPage` parameter without PaginationInnerInterceptor ❌

See [logical-pagination.md](rules/logical-pagination.md) for details.

### No Condition Pagination

**Path:** `sql-guard.rules.no-condition-pagination`  
**Risk Level:** `CRITICAL`

```yaml
sql-guard:
  rules:
    no-condition-pagination:
      enabled: true
      risk-level: CRITICAL
```

**Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable checker |
| `risk-level` | RiskLevel | `CRITICAL` | Violation severity |

**What it detects:** LIMIT queries without WHERE clause (still full table scan).

**Example violations:**
- `SELECT * FROM users LIMIT 100` ❌

See [pagination-abuse.md](rules/pagination-abuse.md) for details.

### Deep Pagination

**Path:** `sql-guard.rules.deep-pagination`  
**Risk Level:** `MEDIUM`

```yaml
sql-guard:
  rules:
    deep-pagination:
      enabled: true
      risk-level: MEDIUM
      max-offset: 10000
```

**Properties:**

| Property | Type | Default | Min | Description |
|----------|------|---------|-----|-------------|
| `enabled` | boolean | `true` | - | Enable checker |
| `risk-level` | RiskLevel | `MEDIUM` | - | Violation severity |
| `max-offset` | int | `10000` | 1 | Maximum allowed OFFSET value |

**What it detects:** High OFFSET values causing performance degradation.

**Example violations:**
- `SELECT * FROM users LIMIT 100 OFFSET 15000` ❌

See [pagination-abuse.md](rules/pagination-abuse.md) for details.

### Large Page Size

**Path:** `sql-guard.rules.large-page-size`  
**Risk Level:** `MEDIUM`

```yaml
sql-guard:
  rules:
    large-page-size:
      enabled: true
      risk-level: MEDIUM
      max-page-size: 1000
```

**Properties:**

| Property | Type | Default | Min | Description |
|----------|------|---------|-----|-------------|
| `enabled` | boolean | `true` | - | Enable checker |
| `risk-level` | RiskLevel | `MEDIUM` | - | Violation severity |
| `max-page-size` | int | `1000` | 1 | Maximum allowed LIMIT value |

**What it detects:** Excessive LIMIT values causing memory issues.

**Example violations:**
- `SELECT * FROM users LIMIT 5000` ❌

See [pagination-abuse.md](rules/pagination-abuse.md) for details.

### Missing ORDER BY

**Path:** `sql-guard.rules.missing-order-by`  
**Risk Level:** `LOW`

```yaml
sql-guard:
  rules:
    missing-order-by:
      enabled: true
      risk-level: LOW
```

**Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable checker |
| `risk-level` | RiskLevel | `LOW` | Violation severity |

**What it detects:** Pagination queries without ORDER BY (unstable result ordering).

**Example violations:**
- `SELECT * FROM users WHERE status='active' LIMIT 100` ❌

See [missing-orderby.md](rules/missing-orderby.md) for details.

### No Pagination

**Path:** `sql-guard.rules.no-pagination`  
**Risk Level:** `MEDIUM` (variable)

```yaml
sql-guard:
  rules:
    no-pagination:
      enabled: true
      risk-level: MEDIUM
      estimated-rows-threshold: 10000
```

**Properties:**

| Property | Type | Default | Min | Description |
|----------|------|---------|-----|-------------|
| `enabled` | boolean | `true` | - | Enable checker |
| `risk-level` | RiskLevel | `MEDIUM` | - | Base violation severity |
| `estimated-rows-threshold` | long | `10000` | 1 | Row count threshold for violations |

**What it detects:** SELECT queries without LIMIT that may return large result sets.

**Risk Stratification:**
- CRITICAL: No WHERE clause
- HIGH: Blacklist-only WHERE
- MEDIUM: Other conditions

See [no-pagination.md](rules/no-pagination.md) for details.

## Parser Configuration

**Path:** `sql-guard.parser`

```yaml
sql-guard:
  parser:
    lenient-mode: false
```

**Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `lenient-mode` | boolean | `false` | Enable lenient SQL parsing |

**Lenient Mode Behavior:**

| Mode | Parse Failure Behavior | Use Case |
|------|----------------------|----------|
| Strict (`false`) | Throw SqlParseException | Production (fail-fast) |
| Lenient (`true`) | Log warning, skip validation | Legacy SQL with vendor extensions |

**When to enable lenient mode:**
- Database-specific syntax not supported by JSqlParser
- Legacy SQL with non-standard syntax
- Gradual migration from unsafe SQL

## Complete Example

### Minimal Configuration (Development)

```yaml
sql-guard:
  enabled: true
  active-strategy: LOG
```

### Standard Configuration (Staging)

```yaml
sql-guard:
  enabled: true
  active-strategy: WARN
  
  deduplication:
    enabled: true
    cache-size: 5000
    ttl-ms: 200
  
  rules:
    no-where-clause:
      enabled: true
      risk-level: CRITICAL
    
    dummy-condition:
      enabled: true
      risk-level: HIGH
    
    blacklist-fields:
      enabled: true
      risk-level: HIGH
      blacklist-fields:
        - deleted
        - del_flag
        - status
        - enabled
    
    logical-pagination:
      enabled: true
      risk-level: CRITICAL
    
    deep-pagination:
      enabled: true
      risk-level: MEDIUM
      max-offset: 10000
```

### Production Configuration (Full)

```yaml
sql-guard:
  enabled: true
  active-strategy: BLOCK
  
  interceptors:
    mybatis:
      enabled: true
    mybatis-plus:
      enabled: true
    jdbc:
      enabled: true
  
  deduplication:
    enabled: true
    cache-size: 10000
    ttl-ms: 500
  
  parser:
    lenient-mode: false
  
  rules:
    no-where-clause:
      enabled: true
      risk-level: CRITICAL
    
    dummy-condition:
      enabled: true
      risk-level: HIGH
      patterns:
        - "1=1"
        - "true"
        - "'a'='a'"
        - "1<>1"
    
    blacklist-fields:
      enabled: true
      risk-level: HIGH
      blacklist-fields:
        - deleted
        - del_flag
        - is_deleted
        - status
        - enabled
        - type
        - create_time
        - update_time
    
    whitelist-fields:
      enabled: true
      risk-level: HIGH
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
    
    logical-pagination:
      enabled: true
      risk-level: CRITICAL
    
    no-condition-pagination:
      enabled: true
      risk-level: CRITICAL
    
    deep-pagination:
      enabled: true
      risk-level: MEDIUM
      max-offset: 10000
    
    large-page-size:
      enabled: true
      risk-level: MEDIUM
      max-page-size: 1000
    
    missing-order-by:
      enabled: true
      risk-level: LOW
    
    no-pagination:
      enabled: true
      risk-level: MEDIUM
      estimated-rows-threshold: 10000
```

### Profile-Specific Configuration

**application.yml:**

```yaml
sql-guard:
  enabled: true
  active-strategy: ${SQL_GUARD_STRATEGY:LOG}
```

**application-dev.yml:**

```yaml
sql-guard:
  active-strategy: LOG
  deduplication:
    cache-size: 1000
```

**application-staging.yml:**

```yaml
sql-guard:
  active-strategy: WARN
  deduplication:
    cache-size: 5000
```

**application-prod.yml:**

```yaml
sql-guard:
  active-strategy: BLOCK
  deduplication:
    cache-size: 10000
    ttl-ms: 500
```

## Environment Variable Overrides

All properties support environment variable overrides:

```bash
# Override active strategy
export SQL_GUARD_ACTIVE_STRATEGY=BLOCK

# Override cache size
export SQL_GUARD_DEDUPLICATION_CACHE_SIZE=5000

# Disable specific rule
export SQL_GUARD_RULES_NO_WHERE_CLAUSE_ENABLED=false
```

## Next Steps

- **[Rule Documentation](rules/README.md)** - Learn about all 10 validation rules
- **[Deployment Guide](deployment.md)** - Phased rollout strategy
- **[Performance Guide](performance.md)** - Tuning recommendations

---

**Need help?** See [FAQ](faq.md) or [Troubleshooting Guide](troubleshooting.md).
