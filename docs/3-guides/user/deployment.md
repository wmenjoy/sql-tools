# Deployment Guide

This guide provides a comprehensive strategy for safely deploying SQL Safety Guard to production using a phased rollout approach.

## Table of Contents

- [Deployment Philosophy](#deployment-philosophy)
- [Phase 1: Observation Mode](#phase-1-observation-mode-log)
- [Phase 2: Warning Mode](#phase-2-warning-mode-warn)
- [Phase 3: Blocking Mode](#phase-3-blocking-mode-block)
- [Environment-Specific Configuration](#environment-specific-configuration)
- [Rollback Plan](#rollback-plan)
- [Monitoring and Metrics](#monitoring-and-metrics)

## Deployment Philosophy

SQL Safety Guard uses a **three-phase deployment strategy** to minimize risk and maximize confidence:

```
Phase 1: LOG (1-2 weeks)    →    Phase 2: WARN (1-2 weeks)    →    Phase 3: BLOCK (gradual)
   Observe violations              Validate warnings                  Enforce safety
   Tune configurations             Refine rules                       Monitor errors
   Identify false positives        Prepare for enforcement            Rollback if needed
```

### Key Principles

1. **Start Passive** - Observe before enforcing
2. **Tune Incrementally** - Adjust rules based on real data
3. **Validate Thoroughly** - Ensure warnings don't disrupt UX
4. **Deploy Gradually** - Use canary/percentage-based rollout
5. **Monitor Continuously** - Track metrics at each phase

## Phase 1: Observation Mode (LOG)

### Duration
**1-2 weeks** (longer for high-traffic applications)

### Objective
Observe SQL violations in production without impacting users, collect data for tuning, and identify false positives.

### Configuration

```yaml
# application-prod.yml
sql-guard:
  enabled: true
  active-strategy: LOG  # Log violations, don't block
  
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
```

### Activities

#### Week 1: Initial Deployment

**Day 1-2: Deploy to Production**

1. Deploy configuration with `active-strategy: LOG`
2. Verify SQL Guard initialization in logs:
   ```
   INFO  SqlGuardAutoConfiguration - SQL Safety Guard initialized
   INFO  SqlGuardAutoConfiguration - Active strategy: LOG
   INFO  SqlGuardAutoConfiguration - Enabled checkers: 10
   ```
3. Monitor application health metrics (no impact expected)

**Day 3-7: Collect Violation Data**

1. Aggregate violations by rule type:
   ```bash
   # Example log aggregation
   grep "SQL Safety Violation" application.log | \
     awk '{print $10}' | sort | uniq -c | sort -rn
   ```

2. Identify top violators:
   ```bash
   # Find most frequent violations
   grep "SQL Safety Violation" application.log | \
     grep "mapperId" | awk '{print $12}' | sort | uniq -c | sort -rn | head -20
   ```

3. Analyze violation patterns:
   - CRITICAL violations: Require immediate attention
   - HIGH violations: Review for false positives
   - MEDIUM/LOW violations: Track for trends

#### Week 2: Analysis and Tuning

**Analyze Violation Frequency**

Create violation report:

| Rule | Count | % of Total | Action Required |
|------|-------|-----------|----------------|
| No WHERE Clause | 5 | 2% | Fix immediately |
| Dummy Condition | 15 | 6% | Review and fix |
| Blacklist Fields | 120 | 48% | Tune blacklist |
| Deep Pagination | 80 | 32% | Optimize queries |
| Missing ORDER BY | 30 | 12% | Low priority |

**Identify False Positives**

Review violations flagged as false positives:

```yaml
# Example: Whitelist specific mappers
sql-guard:
  rules:
    no-pagination:
      enabled: true
      whitelist-mapper-ids:
        - "ConfigMapper.selectAll"  # Config table is small
        - "MetadataMapper.selectAll"  # Metadata table is small
```

**Tune Configurations**

Adjust thresholds based on data:

```yaml
sql-guard:
  rules:
    deep-pagination:
      max-offset: 5000  # Reduce from 10000 based on analysis
    
    large-page-size:
      max-page-size: 500  # Reduce from 1000 based on analysis
    
    blacklist-fields:
      blacklist-fields:
        - deleted
        - status
        # Remove 'enabled' - too many false positives
```

### Decision Criteria for Phase 2

Proceed to Phase 2 when:

- ✅ **Zero CRITICAL violations** in last 3 days
- ✅ **<10 HIGH violations per day** (all reviewed)
- ✅ **False positive rate <5%**
- ✅ **Configuration tuned** based on production data
- ✅ **Team trained** on violation remediation

## Phase 2: Warning Mode (WARN)

### Duration
**1-2 weeks**

### Objective
Validate that warnings don't disrupt user experience, refine rules further, and prepare for enforcement.

### Configuration

```yaml
# application-prod.yml
sql-guard:
  enabled: true
  active-strategy: WARN  # Warn but allow execution
  
  # ... (same rule configuration as Phase 1)
```

### Activities

#### Week 1: Deploy Warning Mode

**Day 1-2: Gradual Rollout**

1. Deploy to 10% of production traffic (canary):
   ```yaml
   # Use feature flag or environment variable
   sql-guard:
     active-strategy: ${SQL_GUARD_STRATEGY:LOG}
   ```
   
   ```bash
   # Set environment variable for canary instances
   export SQL_GUARD_STRATEGY=WARN
   ```

2. Monitor error rates and latency:
   - Error rate should remain stable
   - P99 latency increase <5%

3. Expand to 50% after 24 hours if stable

4. Expand to 100% after 48 hours if stable

**Day 3-7: Monitor User Impact**

1. Check for user-reported issues related to warnings
2. Monitor application logs for warning frequency:
   ```bash
   grep "SQL Safety Warning" application.log | wc -l
   ```
3. Verify warnings don't leak to user-facing errors

#### Week 2: Final Validation

**Validate Warning Handling**

1. Ensure warnings are logged but execution continues:
   ```
   WARN  SqlSafetyInterceptor - SQL Safety Warning: [CRITICAL] No WHERE clause
   WARN  SqlSafetyInterceptor - MapperId: com.example.UserMapper.deleteAll
   WARN  SqlSafetyInterceptor - SQL: DELETE FROM users WHERE id = ?
   WARN  SqlSafetyInterceptor - Execution allowed (WARN strategy)
   ```

2. Verify no SQLException thrown
3. Confirm deduplication reduces log volume

**Final Configuration Tuning**

1. Review remaining violations
2. Fix legitimate issues in code
3. Whitelist unavoidable edge cases
4. Document known violations

### Decision Criteria for Phase 3

Proceed to Phase 3 when:

- ✅ **Zero user-impacting issues** from warnings
- ✅ **All CRITICAL violations fixed** in code
- ✅ **HIGH violations <5 per day** (all whitelisted or fixed)
- ✅ **Performance impact <5%**
- ✅ **Rollback plan tested** and ready
- ✅ **On-call team trained** on SQL Guard errors

## Phase 3: Blocking Mode (BLOCK)

### Duration
**Gradual rollout over 1-2 weeks**

### Objective
Enforce SQL safety by blocking dangerous operations, with gradual rollout and continuous monitoring.

### Configuration

```yaml
# application-prod.yml
sql-guard:
  enabled: true
  active-strategy: BLOCK  # Block dangerous SQL
  
  # ... (same rule configuration as Phase 2)
```

### Rollout Strategy

#### Option 1: Canary Deployment

**Week 1: Canary Instances**

1. Deploy BLOCK strategy to 1-2 canary instances:
   ```bash
   # Canary instances
   export SQL_GUARD_STRATEGY=BLOCK
   ```

2. Monitor for 48 hours:
   - SQLException count
   - Error rate increase
   - User-reported issues

3. Expand to 10% after validation

**Week 2: Gradual Expansion**

1. Day 1-2: 10% → 25%
2. Day 3-4: 25% → 50%
3. Day 5-6: 50% → 75%
4. Day 7: 75% → 100%

At each step:
- Monitor error rates
- Check for user impact
- Validate rollback capability

#### Option 2: Percentage-Based Rollout

Use feature flag to control percentage:

```java
@Configuration
public class SqlGuardConfig {
    
    @Value("${sql-guard.block-percentage:0}")
    private int blockPercentage;
    
    @Bean
    public ViolationStrategy violationStrategy() {
        // Randomly block based on percentage
        if (ThreadLocalRandom.current().nextInt(100) < blockPercentage) {
            return ViolationStrategy.BLOCK;
        }
        return ViolationStrategy.WARN;
    }
}
```

Gradual increase:
- Day 1-2: 10%
- Day 3-4: 25%
- Day 5-6: 50%
- Day 7-8: 75%
- Day 9: 100%

### Monitoring During Rollout

**Key Metrics**

| Metric | Threshold | Action if Exceeded |
|--------|-----------|-------------------|
| SQLException rate | <0.1% of requests | Rollback immediately |
| Error rate increase | <1% | Investigate, consider rollback |
| P99 latency increase | <10% | Tune deduplication |
| User complaints | 0 | Rollback and investigate |

**Dashboards**

Create monitoring dashboards tracking:

1. **Violation Metrics:**
   - Violations by rule type
   - Violations by risk level
   - Top violating mappers

2. **Performance Metrics:**
   - Validation latency (P50, P95, P99)
   - Cache hit rate
   - Deduplication effectiveness

3. **Error Metrics:**
   - SQLException count
   - Error rate by endpoint
   - User-reported issues

**Alerts**

Configure alerts for:

```yaml
# Example Prometheus alerts
- alert: SqlGuardHighErrorRate
  expr: rate(sqlguard_exceptions_total[5m]) > 0.001
  for: 5m
  annotations:
    summary: "SQL Guard blocking too many queries"
    
- alert: SqlGuardLowCacheHitRate
  expr: sqlguard_cache_hit_rate < 0.5
  for: 10m
  annotations:
    summary: "SQL Guard cache hit rate below 50%"
```

## Environment-Specific Configuration

### Development Environment

```yaml
# application-dev.yml
sql-guard:
  enabled: true
  active-strategy: LOG
  
  parser:
    lenient-mode: true  # Allow experimental SQL
  
  rules:
    missing-order-by:
      enabled: false  # Disable low-priority rules
    no-pagination:
      enabled: false  # Allow unrestricted queries
```

### Staging Environment

```yaml
# application-staging.yml
sql-guard:
  enabled: true
  active-strategy: WARN
  
  deduplication:
    cache-size: 5000
  
  rules:
    # Same as production configuration
```

### Production Environment

```yaml
# application-prod.yml
sql-guard:
  enabled: true
  active-strategy: ${SQL_GUARD_STRATEGY:BLOCK}
  
  deduplication:
    enabled: true
    cache-size: 10000
    ttl-ms: 500
  
  rules:
    # Strict configuration with all rules enabled
```

### Canary Environment

```yaml
# application-canary.yml
sql-guard:
  enabled: true
  active-strategy: BLOCK  # Always BLOCK in canary
  
  # Same as production configuration
```

## Rollback Plan

### Immediate Rollback (Emergency)

If critical issues occur:

1. **Disable SQL Guard entirely:**
   ```bash
   # Set environment variable
   export SQL_GUARD_ENABLED=false
   
   # Or update configuration
   kubectl set env deployment/app SQL_GUARD_ENABLED=false
   ```

2. **Restart affected instances**

3. **Verify error rate returns to normal**

4. **Investigate root cause**

### Graceful Rollback

If non-critical issues occur:

1. **Downgrade strategy:**
   ```bash
   # BLOCK → WARN
   export SQL_GUARD_STRATEGY=WARN
   ```

2. **Monitor for 30 minutes**

3. **If stable, investigate and fix issues**

4. **Retry BLOCK deployment after fixes**

### Rollback Testing

Test rollback procedure before Phase 3:

1. Deploy BLOCK to canary
2. Simulate issue (inject test violation)
3. Execute rollback procedure
4. Verify recovery time <5 minutes
5. Document lessons learned

## Monitoring and Metrics

### Violation Metrics

Track violations over time:

```java
@Component
public class SqlGuardMetrics {
    
    private final MeterRegistry registry;
    
    public void recordViolation(RiskLevel riskLevel, String ruleType) {
        registry.counter("sqlguard.violations",
            "risk_level", riskLevel.name(),
            "rule_type", ruleType
        ).increment();
    }
}
```

### Performance Metrics

Track validation performance:

```java
@Component
public class SqlGuardPerformanceMetrics {
    
    private final MeterRegistry registry;
    
    public void recordValidationLatency(long milliseconds) {
        registry.timer("sqlguard.validation.latency")
            .record(milliseconds, TimeUnit.MILLISECONDS);
    }
    
    public void recordCacheHit(boolean hit) {
        registry.counter("sqlguard.cache",
            "result", hit ? "hit" : "miss"
        ).increment();
    }
}
```

### Dashboard Example

Create Grafana dashboard with panels:

1. **Violations Over Time** (line chart)
   - CRITICAL violations
   - HIGH violations
   - MEDIUM/LOW violations

2. **Top Violating Mappers** (table)
   - Mapper ID
   - Violation count
   - Risk level

3. **Validation Performance** (histogram)
   - P50, P95, P99 latency
   - Cache hit rate

4. **Error Rate** (line chart)
   - SQLException rate
   - Error rate by endpoint

## Best Practices

### 1. Start Conservative

- Begin with LOG strategy
- Enable all rules initially
- Tune based on real data

### 2. Communicate Clearly

- Announce deployment to team
- Document known violations
- Provide remediation guide

### 3. Monitor Continuously

- Set up dashboards before deployment
- Configure alerts for anomalies
- Review metrics daily during rollout

### 4. Fix Root Causes

- Don't just whitelist violations
- Fix dangerous SQL in code
- Educate team on best practices

### 5. Document Everything

- Record configuration changes
- Document false positives
- Maintain runbook for issues

## Troubleshooting

### Issue: High False Positive Rate

**Symptoms:** Many violations flagged incorrectly

**Solutions:**

1. Review violation logs for patterns
2. Tune rule configurations (blacklist, whitelist, thresholds)
3. Whitelist specific mappers if unavoidable
4. Consider disabling low-value rules

### Issue: Performance Degradation

**Symptoms:** Latency increase >5%

**Solutions:**

1. Increase deduplication cache size
2. Increase cache TTL
3. Verify cache hit rate >50%
4. Consider disabling expensive rules

### Issue: User-Reported Errors

**Symptoms:** Users see SQLException messages

**Solutions:**

1. Immediate rollback to WARN strategy
2. Investigate specific SQL causing errors
3. Fix SQL or whitelist if legitimate
4. Retry deployment after fixes

## Next Steps

- **[Configuration Reference](configuration-reference.md)** - Tune rule configurations
- **[Performance Guide](performance.md)** - Optimize validation performance
- **[Troubleshooting Guide](troubleshooting.md)** - Resolve common issues

---

**Need help?** See [FAQ](faq.md) or contact support.















