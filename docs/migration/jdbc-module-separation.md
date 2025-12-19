# JDBC Module Separation Migration Guide

**Version**: 2.0.0  
**Date**: 2025-12-19

## Overview

Starting with version 2.0.0, the `sql-guard-jdbc` module has been reorganized to improve modularity and reduce dependency pollution. Common JDBC abstractions have been extracted into a new `sql-guard-jdbc-common` module.

### Key Changes

1. **New `sql-guard-jdbc-common` Module** - Contains shared abstractions
2. **Unified `ViolationStrategy` Enum** - Single source of truth
3. **`JdbcInterceptorBase` Abstract Class** - Template method pattern for interceptors
4. **`JdbcInterceptorConfig` Interface** - Configuration abstraction

### Benefits

- **Smaller Dependencies**: Users only need the pool-specific module they use (Druid/HikariCP/P6Spy)
- **No Transitive Pollution**: Using `sql-guard-jdbc-druid` won't pull in HikariCP or P6Spy
- **Consistent API**: All interceptors share the same configuration and violation handling patterns
- **Future-Proof**: Easier to add support for new connection pools

---

## Migration Steps

### Step 1: Update Dependencies (Optional)

The deprecated APIs still work without changes. However, for new projects, prefer:

```xml
<!-- For Druid users (future) -->
<dependency>
    <groupId>com.footstone</groupId>
    <artifactId>sql-guard-jdbc-druid</artifactId>
    <version>2.0.0</version>
</dependency>

<!-- For HikariCP users (future) -->
<dependency>
    <groupId>com.footstone</groupId>
    <artifactId>sql-guard-jdbc-hikari</artifactId>
    <version>2.0.0</version>
</dependency>

<!-- For P6Spy users (future) -->
<dependency>
    <groupId>com.footstone</groupId>
    <artifactId>sql-guard-jdbc-p6spy</artifactId>
    <version>2.0.0</version>
</dependency>
```

### Step 2: Update ViolationStrategy Imports

**Old imports (deprecated but still working):**

```java
// Druid
import com.footstone.sqlguard.interceptor.druid.ViolationStrategy;

// HikariCP
import com.footstone.sqlguard.interceptor.hikari.ViolationStrategy;

// P6Spy
import com.footstone.sqlguard.interceptor.p6spy.ViolationStrategy;
```

**New import (recommended):**

```java
import com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy;
```

### Step 3: Update Code (Optional)

**Before (deprecated):**

```java
import com.footstone.sqlguard.interceptor.druid.ViolationStrategy;

DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(
    validator,
    ViolationStrategy.WARN
);
```

**After (recommended):**

```java
import com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy;

DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(
    validator,
    ViolationStrategy.WARN  // Same usage!
);
```

---

## ViolationStrategy Migration

### Enum Values (Unchanged)

| Value | Behavior | Use Case |
|-------|----------|----------|
| `BLOCK` | Throw SQLException, prevent execution | Zero tolerance for violations |
| `WARN` | Log error, continue execution | Gradual rollout |
| `LOG` | Log warning, continue execution | Observation mode |

### New Helper Methods

The unified `ViolationStrategy` provides helper methods:

```java
ViolationStrategy strategy = ViolationStrategy.BLOCK;

// Check behavior
strategy.shouldBlock();  // true for BLOCK
strategy.shouldLog();    // true for all
strategy.getLogLevel();  // "ERROR" for BLOCK/WARN, "WARN" for LOG
```

### Converting Between Old and New

If you need to convert between deprecated and unified enums:

```java
// Deprecated -> Unified
com.footstone.sqlguard.interceptor.druid.ViolationStrategy oldStrategy = 
    com.footstone.sqlguard.interceptor.druid.ViolationStrategy.BLOCK;
com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy newStrategy = 
    oldStrategy.toCommon();

// Unified -> Deprecated (for legacy API compatibility)
com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy newStrategy = 
    com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy.WARN;
com.footstone.sqlguard.interceptor.druid.ViolationStrategy oldStrategy = 
    com.footstone.sqlguard.interceptor.druid.ViolationStrategy.fromCommon(newStrategy);
```

---

## Configuration Migration

### YAML Configuration (Unchanged)

Your existing configuration files continue to work:

```yaml
sql-guard:
  jdbc:
    druid:
      enabled: true
      violation-strategy: WARN
    hikari:
      enabled: false
    p6spy:
      enabled: false
```

### Properties Configuration (Unchanged)

```properties
sql-guard.jdbc.druid.enabled=true
sql-guard.jdbc.druid.violation-strategy=BLOCK
```

---

## New Abstractions

### JdbcInterceptorConfig Interface

For custom configuration implementations:

```java
import com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorConfig;
import com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy;

public class MyCustomConfig implements JdbcInterceptorConfig {
    @Override
    public boolean isEnabled() { return true; }
    
    @Override
    public ViolationStrategy getStrategy() { return ViolationStrategy.WARN; }
    
    @Override
    public boolean isAuditEnabled() { return true; }
    
    @Override
    public List<String> getExcludePatterns() { return Collections.emptyList(); }
}
```

### JdbcInterceptorBase Abstract Class

For creating custom interceptors with consistent lifecycle:

```java
import com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorBase;

public class MyCustomInterceptor extends JdbcInterceptorBase {
    
    @Override
    protected SqlContext buildSqlContext(String sql, Object... params) {
        // Build context from your pool's API
    }
    
    @Override
    protected ValidationResult validate(SqlContext context) {
        // Delegate to validator
    }
    
    @Override
    protected void handleViolation(ValidationResult result) {
        // Handle based on strategy
    }
}
```

---

## Breaking Changes

**None.** All existing code continues to work without modifications.

The deprecated APIs generate compile-time warnings but remain fully functional.

---

## FAQ

### Q: Do I need to migrate immediately?

No. The deprecated APIs will not be removed. Migrate at your convenience.

### Q: Will my existing tests break?

No. All existing functionality is preserved with 100% backward compatibility.

### Q: What if I use multiple connection pools?

Each pool module is independent. You can use any combination without dependency conflicts.

---

## Support

For migration assistance:
- Open an issue: [GitHub Issues](https://github.com/footstone/sql-safety-guard/issues)
- Check documentation: `docs/user-guide/`
