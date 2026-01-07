# Missing Pagination in Large Result Queries

## Overview

**Rule ID:** `NO_PAGINATION`
**Severity:** VARIABLE (Context-Dependent)
**Category:** Performance & Resource Management

This rule detects queries that may return large result sets without pagination limits, which can lead to performance issues, memory exhaustion, and poor user experience.

## Problem Description

Queries without pagination controls (LIMIT, TOP, FETCH FIRST, etc.) can cause:

1. **Memory Exhaustion**: Loading entire result sets into application memory
2. **Network Overhead**: Transferring large amounts of data over network
3. **Poor Performance**: Slow response times due to large data transfers
4. **Database Load**: Full table scans and resource-intensive operations
5. **Application Timeouts**: Queries exceeding configured timeout thresholds
6. **Poor UX**: Users waiting for data they may never view

## Rule Configuration

```yaml
sqlguard:
  rules:
    no-pagination:
      enabled: true
      # Violation strategy when no pagination is detected
      # WARN: Log warning but allow query
      # BLOCK: Reject query execution
      # AUDIT: Log for audit without blocking
      strategy: WARN

      # Severity level for this violation
      # INFO, LOW, MEDIUM, HIGH, CRITICAL
      severity: MEDIUM
```

### Configuration Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable/disable this rule |
| `strategy` | enum | `WARN` | Action to take: `WARN`, `BLOCK`, `AUDIT` |
| `severity` | enum | `MEDIUM` | Severity level for violations |

## Why Severity is VARIABLE

This rule's severity depends on context:

- **CRITICAL**: Large transaction tables without pagination
- **HIGH**: User-facing queries on growing tables
- **MEDIUM**: Admin queries on moderately-sized tables
- **LOW**: Small lookup tables or configuration tables
- **INFO**: Aggregate queries (COUNT, SUM) or single-row queries

The default `MEDIUM` severity can be overridden based on your use case.

## Detection Logic

The checker identifies queries with:
- SELECT statements
- No LIMIT, TOP, FETCH FIRST, or similar pagination clauses
- Potential to return multiple rows (not aggregate-only queries)

### Pagination Patterns Recognized

The rule considers a query paginated if it includes:

```sql
-- MySQL/PostgreSQL
LIMIT ? [OFFSET ?]

-- SQL Server
TOP ? | TOP (?)
OFFSET ? ROWS FETCH NEXT ? ROWS ONLY

-- Oracle
FETCH FIRST ? ROWS ONLY
ROWNUM <= ?

-- DB2
FETCH FIRST ? ROWS ONLY
```

## Examples

### ❌ Bad: No Pagination on Large Tables

```sql
-- May return millions of rows
SELECT * FROM orders;

-- User list without limits
SELECT user_id, username, email
FROM users
WHERE status = 'active';

-- Transaction history
SELECT * FROM transactions
WHERE transaction_date >= '2024-01-01';

-- MyBatis example
<select id="listAllUsers" resultType="User">
  SELECT * FROM users
</select>
```

**Problem:** These queries can return unbounded result sets, causing performance and resource issues.

### ✅ Good: Pagination Applied

```sql
-- Limited result set
SELECT * FROM orders
ORDER BY order_id DESC
LIMIT 100;

-- Paginated user list
SELECT user_id, username, email
FROM users
WHERE status = 'active'
ORDER BY user_id
LIMIT 20 OFFSET 0;

-- Recent transactions with limit
SELECT * FROM transactions
WHERE transaction_date >= '2024-01-01'
ORDER BY transaction_id DESC
LIMIT 1000;

-- MyBatis example with pagination
<select id="listUsers" resultType="User">
  SELECT * FROM users
  ORDER BY user_id
  LIMIT #{pageSize} OFFSET #{offset}
</select>
```

### ⚠️ Acceptable Cases Without Pagination

Some queries legitimately don't need pagination:

```sql
-- Aggregate queries
SELECT COUNT(*) FROM users;
SELECT SUM(amount) FROM orders WHERE user_id = ?;

-- Small lookup tables
SELECT * FROM countries;  -- ~200 rows
SELECT * FROM status_codes;  -- ~10 rows

-- Single-row queries
SELECT * FROM users WHERE user_id = ?;
SELECT * FROM config WHERE config_key = 'app.version';

-- Queries with inherent limits
SELECT DISTINCT status FROM orders;  -- Limited by enum values
```

## Real-World Scenarios

### Scenario 1: Admin Dashboard User List

**Context:** Admin panel needs to display users

```java
// ❌ Problematic code
@Mapper
public interface UserMapper {
    @Select("SELECT * FROM users")
    List<User> findAllUsers();
}

@RestController
public class AdminController {
    @GetMapping("/admin/users")
    public List<User> listUsers() {
        return userMapper.findAllUsers();  // May load 1M+ users
    }
}
```

**Issues:**
- OOM with large user bases
- Slow API response
- High database load

**Fix:**
```java
// ✅ Fixed version
@Mapper
public interface UserMapper {
    @Select("SELECT * FROM users ORDER BY user_id LIMIT #{limit} OFFSET #{offset}")
    List<User> findUsers(@Param("limit") int limit, @Param("offset") int offset);
}

@RestController
public class AdminController {
    @GetMapping("/admin/users")
    public Page<User> listUsers(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        int offset = page * size;
        List<User> users = userMapper.findUsers(size, offset);
        return new Page<>(users, page, size);
    }
}
```

### Scenario 2: Report Generation

**Context:** Exporting sales data for analysis

```java
// ❌ Problematic batch export
@Service
public class ReportService {
    public void exportSalesReport(String startDate, String endDate) {
        // May load millions of rows into memory
        List<Sale> sales = saleMapper.findByDateRange(startDate, endDate);

        writeToExcel(sales);  // OOM risk
    }
}
```

**Fix with Streaming:**
```java
// ✅ Fixed version with batch processing
@Service
public class ReportService {
    private static final int BATCH_SIZE = 1000;

    public void exportSalesReport(String startDate, String endDate) {
        int offset = 0;
        List<Sale> batch;

        ExcelWriter writer = new ExcelWriter("sales-report.xlsx");

        do {
            // Process in batches
            batch = saleMapper.findByDateRange(
                startDate, endDate, BATCH_SIZE, offset
            );

            writer.writeRows(batch);
            offset += BATCH_SIZE;

        } while (batch.size() == BATCH_SIZE);

        writer.close();
    }
}
```

### Scenario 3: API Endpoint Without Pagination

**Context:** Public API returning product list

```java
// ❌ Problematic API
@RestController
@RequestMapping("/api/products")
public class ProductController {
    @GetMapping
    public List<Product> getProducts() {
        // Returns all products - could be 100K+
        return productService.findAll();
    }
}
```

**Fix:**
```java
// ✅ Fixed version with pagination
@RestController
@RequestMapping("/api/products")
public class ProductController {
    @GetMapping
    public PagedResponse<Product> getProducts(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "100") int maxSize
    ) {
        // Enforce maximum page size
        size = Math.min(size, maxSize);

        Page<Product> productPage = productService.findAll(page, size);

        return PagedResponse.of(productPage);
    }
}
```

## Best Practices

### 1. Always Paginate User-Facing Queries

```java
// ✅ Good pattern
public interface OrderMapper extends BaseMapper<Order> {
    @Select("SELECT * FROM orders WHERE user_id = #{userId} " +
            "ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<Order> findByUserId(
        @Param("userId") Long userId,
        @Param("limit") int limit,
        @Param("offset") int offset
    );
}
```

### 2. Use Framework Pagination Support

**MyBatis-Plus:**
```java
@Service
public class UserService extends ServiceImpl<UserMapper, User> {
    public IPage<User> listUsers(int pageNum, int pageSize) {
        Page<User> page = new Page<>(pageNum, pageSize);
        return this.page(page,
            new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)
                .orderByDesc(User::getCreatedAt)
        );
    }
}
```

**Spring Data JPA:**
```java
public interface UserRepository extends JpaRepository<User, Long> {
    Page<User> findByStatus(String status, Pageable pageable);
}

// Usage
Pageable pageable = PageRequest.of(0, 20, Sort.by("userId"));
Page<User> users = userRepository.findByStatus("active", pageable);
```

### 3. Set Default and Maximum Page Sizes

```java
@Configuration
public class PaginationConfig {
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    @Bean
    public PageableHandlerMethodArgumentResolver pageableResolver() {
        PageableHandlerMethodArgumentResolver resolver =
            new PageableHandlerMethodArgumentResolver();
        resolver.setMaxPageSize(MAX_PAGE_SIZE);
        resolver.setFallbackPageable(
            PageRequest.of(0, DEFAULT_PAGE_SIZE)
        );
        return resolver;
    }
}
```

### 4. Use Streaming for Large Exports

**MyBatis Cursor:**
```java
@Mapper
public interface OrderMapper {
    @Options(fetchSize = 1000)
    @Select("SELECT * FROM orders WHERE created_at >= #{startDate}")
    Cursor<Order> streamOrders(@Param("startDate") Date startDate);
}

@Service
public class ExportService {
    public void exportOrders(Date startDate) throws IOException {
        try (Cursor<Order> cursor = orderMapper.streamOrders(startDate)) {
            for (Order order : cursor) {
                // Process one at a time
                writeToFile(order);
            }
        }
    }
}
```

## Integration Examples

### Spring Boot with SqlGuard

```yaml
# application.yml
sqlguard:
  enabled: true
  rules:
    no-pagination:
      enabled: true
      strategy: WARN  # Start with warnings
      severity: MEDIUM

  # Global defaults
  violation-strategy: WARN
```

### MyBatis Configuration

```java
@Configuration
public class MyBatisConfig {
    @Bean
    public SqlSafetyInterceptor sqlSafetyInterceptor() {
        SqlGuardConfig config = new SqlGuardConfig();

        // Configure no-pagination rule
        NoPaginationConfig noPagConfig = config.getNoPagination();
        noPagConfig.setEnabled(true);
        noPagConfig.setStrategy(ViolationStrategy.WARN);

        return new SqlSafetyInterceptor(config);
    }
}
```

### Runtime Detection Example

```java
// SqlGuard will detect and warn about unpaginated queries
@Select("SELECT * FROM users")
List<User> findAll();  // ⚠️ Triggers NO_PAGINATION warning

// Log output:
// WARN - SQL Safety Violation: NO_PAGINATION
// SQL: SELECT * FROM users
// Location: UserMapper.findAll()
// Recommendation: Add LIMIT clause or use pagination
```

## Configuration Strategies

### Development Environment

```yaml
# application-dev.yml
sqlguard:
  rules:
    no-pagination:
      enabled: true
      strategy: WARN  # Don't block developers
      severity: LOW
```

### Staging Environment

```yaml
# application-staging.yml
sqlguard:
  rules:
    no-pagination:
      enabled: true
      strategy: WARN
      severity: MEDIUM  # Catch issues before production
```

### Production Environment

```yaml
# application-prod.yml
sqlguard:
  rules:
    no-pagination:
      enabled: true
      strategy: AUDIT  # Log without blocking
      severity: HIGH

  audit:
    enabled: true
    output: /var/log/sqlguard/audit.log
```

## Whitelist Configuration

For legitimate cases where pagination isn't needed:

```yaml
sqlguard:
  whitelist:
    - pattern: "SELECT COUNT\\(\\*\\) FROM.*"
      reason: "Aggregate queries don't need pagination"

    - pattern: "SELECT \\* FROM countries"
      reason: "Small lookup table with ~200 rows"

    - pattern: "SELECT \\* FROM users WHERE user_id = \\?"
      reason: "Single-row query by primary key"
```

## Performance Impact

### Without Pagination

| Rows | Memory | Network | Response Time |
|------|--------|---------|---------------|
| 1K | ~1 MB | ~1 MB | ~100ms |
| 10K | ~10 MB | ~10 MB | ~1s |
| 100K | ~100 MB | ~100 MB | ~10s |
| 1M+ | OOM Risk | Timeout | Failure |

### With Pagination (20 rows/page)

| Total Rows | Memory | Network | Response Time |
|------------|--------|---------|---------------|
| Any | ~20 KB | ~20 KB | ~50ms |

## Monitoring and Alerts

### Log Analysis

```bash
# Find queries triggering NO_PAGINATION
grep "NO_PAGINATION" /var/log/sqlguard/audit.log | \
  awk '{print $NF}' | sort | uniq -c | sort -rn

# Output:
# 1523 UserMapper.findAll()
#  892 OrderMapper.listOrders()
#  234 ProductMapper.search()
```

### Grafana Dashboard Query

```sql
-- Count NO_PAGINATION violations by mapper
SELECT
  mapper_interface,
  COUNT(*) as violation_count
FROM sqlguard_audit_log
WHERE rule_id = 'NO_PAGINATION'
  AND timestamp >= NOW() - INTERVAL '24 HOURS'
GROUP BY mapper_interface
ORDER BY violation_count DESC
LIMIT 10;
```

## Related Rules

- [large-page-size.md](large-page-size.md) - Prevents excessive page sizes
- [missing-orderby.md](missing-orderby.md) - Ensures deterministic pagination
- [deep-pagination.md](deep-pagination.md) - Detects inefficient OFFSET usage

## Troubleshooting

### False Positives on Small Tables

**Issue:** Rule triggers on small lookup tables

**Solution 1 - Whitelist:**
```yaml
sqlguard:
  whitelist:
    - sql-id: "CountryMapper.*"
      reason: "Countries table has <300 rows"
```

**Solution 2 - Adjust Strategy:**
```yaml
sqlguard:
  rules:
    no-pagination:
      strategy: AUDIT  # Log but don't warn
```

### Queries Legitimately Don't Need Pagination

**Issue:** Aggregate or single-row queries flagged

**Note:** Current implementation should skip:
- Queries with aggregate functions only (COUNT, SUM, AVG)
- Queries with equality on primary key
- DISTINCT queries with limited cardinality

If false positives persist, use whitelist configuration.

## Migration Guide

### Existing Codebase

1. **Assessment Phase:**
```bash
# Run static scanner to identify violations
java -jar sql-scanner-cli.jar \
  --scan-path ./src \
  --config sqlguard-config.yml \
  --report no-pagination-report.html
```

2. **Prioritize Fixes:**
   - HIGH: User-facing endpoints on large tables
   - MEDIUM: Admin endpoints, reports
   - LOW: Internal tools, development utilities

3. **Gradual Rollout:**
   - Week 1: Enable with `AUDIT` strategy
   - Week 2-4: Fix high-priority violations
   - Week 5: Switch to `WARN` strategy
   - Week 6+: Consider `BLOCK` for critical paths

## API Design Guidelines

### RESTful Pagination

```java
// Standard pagination parameters
@GetMapping("/api/v1/users")
public ResponseEntity<PagedResponse<User>> listUsers(
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(defaultValue = "userId") String sortBy,
    @RequestParam(defaultValue = "ASC") String sortDir
) {
    // Validate and enforce limits
    size = Math.max(1, Math.min(size, 100));

    PagedResponse<User> response = userService.findAll(
        page, size, sortBy, sortDir
    );

    // Include pagination metadata
    return ResponseEntity.ok()
        .header("X-Total-Count", String.valueOf(response.getTotal()))
        .header("X-Page-Count", String.valueOf(response.getPages()))
        .body(response);
}
```

### Cursor-Based Pagination

For better performance on large datasets:

```java
@GetMapping("/api/v1/orders")
public ResponseEntity<CursorResponse<Order>> listOrders(
    @RequestParam(required = false) String cursor,
    @RequestParam(defaultValue = "20") int limit
) {
    CursorResponse<Order> response = orderService.findByCursor(
        cursor, limit
    );

    return ResponseEntity.ok(response);
}
```

## References

- [RESTful API Pagination Best Practices](https://www.moesif.com/blog/technical/api-design/REST-API-Design-Filtering-Sorting-and-Pagination/)
- [MySQL: LIMIT Optimization](https://dev.mysql.com/doc/refman/8.0/en/limit-optimization.html)
- [PostgreSQL: Pagination Performance](https://www.postgresql.org/docs/current/queries-limit.html)
- [Spring Data: Pagination and Sorting](https://spring.io/guides/gs/accessing-data-jpa/)

## Changelog

- **v1.0.0** (2024-01): Initial rule implementation
- **v1.1.0** (2024-03): Added severity configuration
- **v1.2.0** (2024-06): Enhanced detection for aggregate queries
- **v1.3.0** (2024-09): Added whitelist pattern support
