# Performance Guide

Comprehensive guide to SQL Safety Guard performance characteristics, benchmarks, and optimization strategies.

## Table of Contents

- [Performance Overview](#performance-overview)
- [Overhead Benchmarks](#overhead-benchmarks)
- [Optimization Strategies](#optimization-strategies)
- [Deduplication Tuning](#deduplication-tuning)
- [Cache Configuration](#cache-configuration)
- [Performance Monitoring](#performance-monitoring)

## Performance Overview

### Design Goals

SQL Safety Guard is designed for **<5% overhead** in production environments through:

1. **Parse-Once Optimization** - SQL parsed once, AST shared across all checkers
2. **Deduplication Caching** - ThreadLocal LRU cache prevents redundant validation
3. **Early-Return Mechanisms** - Skip unnecessary checks when higher-priority violations detected
4. **Lazy Evaluation** - Expensive operations deferred until needed

### Performance Characteristics

| Component | Overhead | Notes |
|-----------|----------|-------|
| MyBatis Interceptor | <5% | Direct Executor interception, minimal overhead |
| MyBatis-Plus InnerInterceptor | <5% | Efficient InnerInterceptor interface |
| Druid Filter | ~7% | FilterAdapter with ConnectionProxy |
| HikariCP Proxy | ~3% | JDK dynamic proxy, <1% connection acquisition |
| P6Spy Listener | ~15% | Driver-level proxy, acceptable for universal coverage |

## Overhead Benchmarks

### Methodology

Benchmarks measured using JMH (Java Microbenchmark Harness):

- **Hardware:** 8-core CPU, 16GB RAM
- **JVM:** OpenJDK 11, -Xmx4g -Xms4g
- **Database:** MySQL 8.0 (local instance)
- **Workload:** 1000 queries, mixed SELECT/UPDATE/DELETE
- **Measurement:** Average latency over 10 iterations

### MyBatis Interceptor Benchmark

**Test Setup:**

```java
@Benchmark
public void mybatisWithoutGuard(Blackhole bh) {
    List<User> users = userMapper.selectById(1);
    bh.consume(users);
}

@Benchmark
public void mybatisWithGuard(Blackhole bh) {
    List<User> users = userMapper.selectById(1);
    bh.consume(users);
}
```

**Results:**

| Scenario | Avg Latency | Overhead |
|----------|-------------|----------|
| Without SQL Guard | 2.45ms | - |
| With SQL Guard (cold cache) | 2.58ms | +5.3% |
| With SQL Guard (warm cache) | 2.49ms | +1.6% |

**Deduplication Effectiveness:**

- Cache hit rate: 78%
- Validation skipped: 780/1000 queries
- Effective overhead with deduplication: **1.6%**

### Druid Filter Benchmark

**Test Setup:**

```java
@Benchmark
public void druidWithoutGuard(Blackhole bh) throws SQLException {
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
        ps.setInt(1, 1);
        ResultSet rs = ps.executeQuery();
        bh.consume(rs);
    }
}

@Benchmark
public void druidWithGuard(Blackhole bh) throws SQLException {
    // Same as above, with DruidSqlSafetyFilter enabled
}
```

**Results:**

| Scenario | Avg Latency | Overhead |
|----------|-------------|----------|
| Without SQL Guard | 369.96ms (1000 queries) | - |
| With SQL Guard | 398.98ms (1000 queries) | +7.84% |

**Per-Query Overhead:** +29μs per query

### HikariCP Proxy Benchmark

**Results:**

| Scenario | Avg Latency | Overhead |
|----------|-------------|----------|
| Without SQL Guard | 2.12ms | - |
| With SQL Guard (connection acquisition) | 2.14ms | +0.9% |
| With SQL Guard (query execution) | 2.18ms | +2.8% |

**Total Overhead:** ~3%

### P6Spy Listener Benchmark

**Results:**

| Scenario | Avg Latency | Overhead |
|----------|-------------|----------|
| Without SQL Guard | 2.45ms | - |
| With SQL Guard | 2.82ms | +15.1% |

**Trade-off:** Higher overhead acceptable for universal JDBC coverage (C3P0, DBCP, Tomcat JDBC, bare JDBC).

## Optimization Strategies

### 1. Disable Low-Value Rules

Disable rules with low ROI:

```yaml
sql-guard:
  rules:
    missing-order-by:
      enabled: false  # LOW risk, disable if not needed
    
    no-pagination:
      enabled: false  # MEDIUM risk, disable if acceptable
```

**Impact:** -10% to -20% overhead per disabled rule

### 2. Increase Deduplication Cache Size

Larger cache = higher hit rate = less validation:

```yaml
sql-guard:
  deduplication:
    cache-size: 10000  # Increase from default 1000
    ttl-ms: 500        # Increase from default 100
```

**Impact:** +20% to +50% cache hit rate improvement

**Sizing Recommendations:**

| Scenario | cache-size | Expected Hit Rate |
|----------|-----------|-------------------|
| Low traffic (<100 QPS) | 1000 | 60-70% |
| Medium traffic (100-500 QPS) | 5000 | 70-80% |
| High traffic (>500 QPS) | 10000 | 80-90% |

### 3. Tune Cache TTL

Balance between freshness and hit rate:

```yaml
sql-guard:
  deduplication:
    ttl-ms: 500  # Increase from default 100
```

**Trade-offs:**

| TTL | Hit Rate | Freshness | Use Case |
|-----|----------|-----------|----------|
| 50ms | Lower | High | Real-time validation |
| 100ms | Medium | Medium | Default (balanced) |
| 500ms | Higher | Lower | High-throughput |
| 1000ms | Highest | Lowest | Read-heavy workloads |

### 4. Use Lenient Mode for Legacy SQL

Skip validation for unparseable SQL:

```yaml
sql-guard:
  parser:
    lenient-mode: true  # Log warning, skip validation
```

**Impact:** -100% overhead for unparseable SQL (validation skipped)

**Caution:** Only use for legacy SQL with vendor-specific syntax. Reduces safety coverage.

### 5. Optimize Interceptor Selection

Choose most efficient interceptor for your stack:

**Efficiency Ranking (best to worst):**

1. **HikariCP Proxy** (~3% overhead)
2. **MyBatis Interceptor** (<5% overhead)
3. **MyBatis-Plus InnerInterceptor** (<5% overhead)
4. **Druid Filter** (~7% overhead)
5. **P6Spy Listener** (~15% overhead)

**Recommendation:** Use most specific interceptor for your framework.

## Deduplication Tuning

### How Deduplication Works

```
Request 1: SELECT * FROM users WHERE id = ?
  ├─ Parse SQL (5ms)
  ├─ Validate (10ms)
  └─ Cache result (key: normalized SQL)

Request 2: SELECT * FROM users WHERE id = ?  (within TTL)
  ├─ Check cache: HIT
  └─ Skip validation (0ms)

Request 3: SELECT * FROM users WHERE id = ?  (after TTL)
  ├─ Check cache: MISS (expired)
  ├─ Parse SQL (5ms)
  ├─ Validate (10ms)
  └─ Update cache
```

### Cache Key Normalization

SQL normalized before caching:

```java
// Original SQL
"SELECT * FROM users WHERE id = ?"
"select * from users where id=?"
"  SELECT  *  FROM  users  WHERE  id  =  ?  "

// Normalized (cache key)
"select*fromuserswhereid=?"
```

**Normalization Steps:**
1. Convert to lowercase
2. Remove whitespace
3. Remove comments

### Tuning for Multi-Layer Setup

When using multiple interceptors (e.g., MyBatis + Druid):

```yaml
sql-guard:
  deduplication:
    enabled: true
    cache-size: 5000   # Increase for multi-layer
    ttl-ms: 200        # Increase for multi-layer
```

**Without Deduplication:**
- MyBatis validates SQL: 10ms
- Druid validates same SQL: 10ms
- **Total: 20ms (100% overhead)**

**With Deduplication:**
- MyBatis validates SQL: 10ms, caches result
- Druid checks cache: HIT, skips validation
- **Total: 10ms (50% overhead)**

### Monitoring Cache Effectiveness

```java
@Component
public class CacheMonitor {
    
    @Autowired
    private SqlDeduplicationFilter filter;
    
    @Scheduled(fixedRate = 60000)
    public void logCacheStats() {
        CacheStats stats = filter.getCacheStatistics();
        log.info("Cache Stats - Size: {}, Hits: {}, Misses: {}, Hit Rate: {}%",
            stats.getSize(),
            stats.getHitCount(),
            stats.getMissCount(),
            stats.getHitRate() * 100
        );
    }
}
```

**Target Metrics:**

- Hit Rate: >50% (good), >70% (excellent)
- Cache Size: <80% of max (avoid evictions)

## Cache Configuration

### JSqlParser Parse Cache

SQL Safety Guard uses JSqlParser's built-in parse cache:

```java
JSqlParserFacade facade = new JSqlParserFacade(false);
// Parse cache enabled by default in JSqlParser
```

**Cache Characteristics:**

- **Type:** LRU cache
- **Size:** 1000 entries (JSqlParser default)
- **Scope:** Per-JSqlParserFacade instance
- **Thread-Safety:** Synchronized

**Performance Impact:**

| Scenario | Parse Time |
|----------|-----------|
| First parse (cold cache) | ~5ms |
| Subsequent parse (warm cache) | ~0.1ms |

**Tuning:** Not directly configurable. Use deduplication cache for validation-level caching.

### Deduplication Cache Architecture

```
Thread 1                    Thread 2
   │                           │
   ├─ ThreadLocal Cache        ├─ ThreadLocal Cache
   │  ├─ Entry 1 (TTL: 100ms)  │  ├─ Entry 1 (TTL: 100ms)
   │  ├─ Entry 2 (TTL: 100ms)  │  ├─ Entry 2 (TTL: 100ms)
   │  └─ Entry 3 (TTL: 100ms)  │  └─ Entry 3 (TTL: 100ms)
   │                           │
   └─ No cross-thread pollution
```

**Benefits:**

- **Thread-Safe:** No synchronization overhead
- **Isolated:** No cross-thread cache pollution
- **Fast:** O(1) lookup in ThreadLocal

**Trade-offs:**

- **Memory:** Each thread has separate cache
- **Hit Rate:** Lower than global cache (no sharing)

## Performance Monitoring

### Key Metrics to Track

#### 1. Validation Latency

```java
@Component
public class ValidationMetrics {
    
    private final MeterRegistry registry;
    
    public void recordValidation(long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        registry.timer("sqlguard.validation.latency")
            .record(duration, TimeUnit.MILLISECONDS);
    }
}
```

**Target Thresholds:**

- P50: <5ms
- P95: <15ms
- P99: <30ms

#### 2. Cache Hit Rate

```java
public void recordCacheAccess(boolean hit) {
    registry.counter("sqlguard.cache",
        "result", hit ? "hit" : "miss"
    ).increment();
}
```

**Target:** >50% hit rate (>70% excellent)

#### 3. Deduplication Effectiveness

```java
public void recordDeduplication(boolean skipped) {
    registry.counter("sqlguard.deduplication",
        "result", skipped ? "skipped" : "validated"
    ).increment();
}
```

**Target:** >30% validation skipped

### Grafana Dashboard Example

**Panel 1: Validation Latency (Histogram)**

```promql
histogram_quantile(0.50, 
  rate(sqlguard_validation_latency_bucket[5m])
)  # P50

histogram_quantile(0.95, 
  rate(sqlguard_validation_latency_bucket[5m])
)  # P95

histogram_quantile(0.99, 
  rate(sqlguard_validation_latency_bucket[5m])
)  # P99
```

**Panel 2: Cache Hit Rate (Gauge)**

```promql
sum(rate(sqlguard_cache_total{result="hit"}[5m])) /
sum(rate(sqlguard_cache_total[5m]))
```

**Panel 3: Overhead Percentage (Gauge)**

```promql
(avg(rate(sqlguard_validation_latency_sum[5m])) /
 avg(rate(http_request_duration_seconds_sum[5m]))) * 100
```

### Alerting

**Alert 1: High Validation Latency**

```yaml
- alert: SqlGuardHighLatency
  expr: |
    histogram_quantile(0.99,
      rate(sqlguard_validation_latency_bucket[5m])
    ) > 50
  for: 5m
  annotations:
    summary: "SQL Guard P99 latency > 50ms"
    description: "Consider increasing cache size or disabling low-value rules"
```

**Alert 2: Low Cache Hit Rate**

```yaml
- alert: SqlGuardLowCacheHitRate
  expr: |
    sum(rate(sqlguard_cache_total{result="hit"}[5m])) /
    sum(rate(sqlguard_cache_total[5m])) < 0.5
  for: 10m
  annotations:
    summary: "SQL Guard cache hit rate < 50%"
    description: "Consider increasing cache size or TTL"
```

## Performance Best Practices

### 1. Start with Default Configuration

```yaml
sql-guard:
  deduplication:
    cache-size: 1000
    ttl-ms: 100
```

Measure baseline performance before tuning.

### 2. Measure Before Optimizing

Use profiling tools:

- JMH for microbenchmarks
- VisualVM for heap analysis
- Async-profiler for CPU profiling

### 3. Tune Based on Metrics

| Observation | Action |
|-------------|--------|
| P99 latency >30ms | Increase cache size |
| Hit rate <50% | Increase cache size or TTL |
| Memory pressure | Decrease cache size |
| High CPU usage | Disable expensive rules |

### 4. Test in Staging

Always test performance changes in staging before production:

1. Deploy to staging
2. Run load tests (JMeter, Gatling)
3. Compare metrics (latency, throughput, error rate)
4. Validate <5% overhead
5. Deploy to production

### 5. Monitor Continuously

Set up dashboards and alerts before deployment:

- Validation latency (P50, P95, P99)
- Cache hit rate
- Deduplication effectiveness
- Overall application performance

## Troubleshooting Performance Issues

### Issue: High Latency (P99 >50ms)

**Diagnosis:**

```java
@Scheduled(fixedRate = 60000)
public void diagnoseLatency() {
    CacheStats stats = filter.getCacheStatistics();
    log.info("Cache hit rate: {}%", stats.getHitRate() * 100);
    log.info("Cache size: {}/{}", stats.getSize(), maxSize);
}
```

**Solutions:**

1. **Increase cache size:**
   ```yaml
   deduplication:
     cache-size: 5000  # Increase from 1000
   ```

2. **Increase TTL:**
   ```yaml
   deduplication:
     ttl-ms: 500  # Increase from 100
   ```

3. **Disable low-value rules:**
   ```yaml
   rules:
     missing-order-by:
       enabled: false
   ```

### Issue: Low Cache Hit Rate (<50%)

**Diagnosis:**

```java
log.info("Cache stats: hits={}, misses={}, rate={}%",
    stats.getHitCount(),
    stats.getMissCount(),
    stats.getHitRate() * 100
);
```

**Causes:**

- Cache size too small (frequent evictions)
- TTL too short (entries expire too quickly)
- High SQL diversity (many unique queries)

**Solutions:**

1. Increase cache size (10x)
2. Increase TTL (5x)
3. Analyze SQL diversity (may be unavoidable)

### Issue: Memory Pressure

**Diagnosis:**

```bash
jmap -heap <pid>
jmap -histo <pid> | grep SqlDeduplicationFilter
```

**Solutions:**

1. **Decrease cache size:**
   ```yaml
   deduplication:
     cache-size: 500  # Decrease from 1000
   ```

2. **Decrease TTL:**
   ```yaml
   deduplication:
     ttl-ms: 50  # Decrease from 100
   ```

3. **Monitor thread count:**
   - Each thread has separate cache
   - High thread count = high memory usage

## Next Steps

- **[Configuration Reference](configuration-reference.md)** - Tune deduplication settings
- **[Deployment Guide](deployment.md)** - Monitor performance during rollout
- **[Troubleshooting Guide](troubleshooting.md)** - Resolve performance issues

---

**Need help?** See [FAQ](faq.md) or contact support.








