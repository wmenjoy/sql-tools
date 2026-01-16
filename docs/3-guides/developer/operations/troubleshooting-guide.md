# SQL Audit Service - Troubleshooting Guide

## Overview

This guide provides solutions to common issues encountered when running the SQL Audit Service in production.

## Table of Contents

1. [Common Issues](#common-issues)
2. [Diagnostic Flowchart](#diagnostic-flowchart)
3. [Emergency Procedures](#emergency-procedures)
4. [Performance Tuning](#performance-tuning)

---

## 1. Common Issues

### Issue 1: Kafka Consumer Lag Increasing

**Symptoms:**
```
kafka_consumer_lag{group="audit-service-group"} > 10000
```

**Diagnostic Steps:**

1. **Check Checker Performance**
   ```bash
   kubectl logs sql-audit-service-0 -n sqlguard | grep "checker.duration"
   ```

   Look for slow checkers (> 100ms):
   ```
   2025-01-01 12:00:00 INFO  [checker.duration] SlowQueryChecker completed in 250ms
   ```

2. **Check Database Connection Pool**
   ```bash
   curl http://sql-audit-service:8091/actuator/metrics/hikaricp.connections.active
   ```

   If active connections == max pool size, pool is exhausted.

3. **Check ClickHouse Write Performance**
   ```bash
   kubectl logs sql-audit-service-0 -n sqlguard | grep "clickhouse.write"
   ```

**Solutions:**

**Option A: Scale Horizontally**
```bash
# Increase replicas
kubectl scale statefulset sql-audit-service --replicas=5 -n sqlguard

# Verify consumer rebalance
kubectl logs sql-audit-service-0 -n sqlguard | grep "Rebalancing"
```

**Option B: Increase Kafka Partitions**
```bash
# Current partition count
kafka-topics.sh --bootstrap-server kafka:9092 \
  --describe --topic sql-audit-events

# Increase partitions
kafka-topics.sh --bootstrap-server kafka:9092 \
  --alter --topic sql-audit-events --partitions 24
```

**Option C: Optimize Slow Checkers**
```yaml
# Update ConfigMap to disable slow checkers temporarily
kubectl patch configmap audit-config -n sqlguard \
  --patch '{"data":{"checker.slowQuery.enabled":"false"}}'

# Restart pods
kubectl rollout restart statefulset/sql-audit-service -n sqlguard
```

**Option D: Increase Resource Limits**
```yaml
# Edit statefulset.yaml
resources:
  requests:
    cpu: "4"
    memory: "8Gi"
  limits:
    cpu: "8"
    memory: "16Gi"

# Apply changes
kubectl apply -f docs/deployment/k8s/statefulset.yaml
```

**Expected Resolution Time:** 10-30 minutes
**Prevention:** Set up auto-scaling based on consumer lag metric

---

### Issue 2: ClickHouse Write Timeout

**Symptoms:**
```
2025-01-01 12:00:00 ERROR ClickHouseWriter - Write timeout after 5000ms
```

**Diagnostic Steps:**

1. **Check Network Latency**
   ```bash
   kubectl exec -n sqlguard sql-audit-service-0 -- ping clickhouse
   ```

2. **Check ClickHouse Load**
   ```sql
   SELECT * FROM system.processes WHERE query NOT LIKE '%system%';
   ```

3. **Check ClickHouse Merge Activity**
   ```sql
   SELECT
     table,
     count() AS merges_in_progress
   FROM system.merges
   GROUP BY table;
   ```

**Solutions:**

**Option A: Increase Batch Size**
```yaml
# Update ConfigMap
kubectl patch configmap audit-config -n sqlguard \
  --patch '{"data":{"clickhouse.batch.size":"2000"}}'

# Restart pods
kubectl rollout restart statefulset/sql-audit-service -n sqlguard
```

**Option B: Increase Flush Interval**
```yaml
# Update ConfigMap
kubectl patch configmap audit-config -n sqlguard \
  --patch '{"data":{"clickhouse.flush.interval":"30000"}}'
```

**Option C: Optimize ClickHouse Table**
```sql
-- Check parts count
SELECT
  database,
  table,
  count() AS parts
FROM system.parts
WHERE database = 'audit' AND active
GROUP BY database, table;

-- If parts > 100, force merge
OPTIMIZE TABLE audit.audit_logs FINAL;
```

**Option D: Add ClickHouse Nodes**
```bash
# Scale ClickHouse cluster
kubectl scale statefulset clickhouse --replicas=5 -n sqlguard
```

**Expected Resolution Time:** 5-15 minutes
**Prevention:** Monitor ClickHouse write latency, tune batch size

---

### Issue 3: Audit Service OOM (Out of Memory)

**Symptoms:**
```
java.lang.OutOfMemoryError: Java heap space
Pod status: CrashLoopBackOff
```

**Diagnostic Steps:**

1. **Generate Heap Dump** (if pod is still running)
   ```bash
   kubectl exec -n sqlguard sql-audit-service-0 -- \
     jmap -dump:live,format=b,file=/app/heap-dumps/heap.bin 1

   # Copy heap dump locally
   kubectl cp sqlguard/sql-audit-service-0:/app/heap-dumps/heap.bin ./heap.bin
   ```

2. **Analyze Heap Dump**
   Use Eclipse MAT (Memory Analyzer Tool):
   - Open heap.bin
   - Look for "Leak Suspects"
   - Check Dominator Tree for large objects

3. **Check Memory Metrics**
   ```bash
   curl http://sql-audit-service:8091/actuator/metrics/jvm.memory.used
   curl http://sql-audit-service:8091/actuator/metrics/jvm.memory.max
   ```

**Solutions:**

**Option A: Increase Heap Size**
```yaml
# Edit statefulset.yaml
env:
- name: JAVA_OPTS
  value: >-
    -Xms4g
    -Xmx8g
    -XX:+UseG1GC

# Apply changes
kubectl apply -f docs/deployment/k8s/statefulset.yaml
```

**Option B: Optimize Checker Memory Usage**
```yaml
# Reduce batch processing
kubectl patch configmap audit-config -n sqlguard \
  --patch '{"data":{"kafka.consumer.maxPollRecords":"100"}}'
```

**Option C: Fix Memory Leak**
```java
// Common causes:
// 1. Unbounded cache - set max size
// 2. Event listeners not deregistered
// 3. Thread-local variables not cleaned up

// Example fix for cache:
@Bean
public CacheManager cacheManager() {
  CaffeineCacheManager cacheManager = new CaffeineCacheManager();
  cacheManager.setCaffeine(Caffeine.newBuilder()
    .maximumSize(10000)  // Limit cache size
    .expireAfterWrite(5, TimeUnit.MINUTES));
  return cacheManager;
}
```

**Expected Resolution Time:** 30 minutes to 2 hours
**Prevention:** Set up memory usage alerts, regular heap dump analysis

---

### Issue 4: Configuration Not Taking Effect

**Symptoms:**
```
Updated checker threshold in ConfigMap, but service still using old value
```

**Diagnostic Steps:**

1. **Check ConfigMap**
   ```bash
   kubectl get configmap audit-config -n sqlguard -o yaml
   ```

2. **Check Pod Environment Variables**
   ```bash
   kubectl exec -n sqlguard sql-audit-service-0 -- env | grep CHECKER
   ```

3. **Check Configuration Properties**
   ```bash
   curl http://sql-audit-service:8091/actuator/configprops | jq '.contexts.application.beans.checkerConfig'
   ```

**Solutions:**

**Option A: Restart Pods** (ConfigMaps are not auto-reloaded)
```bash
# Rolling restart
kubectl rollout restart statefulset/sql-audit-service -n sqlguard

# Verify new config
kubectl logs sql-audit-service-0 -n sqlguard | grep "Configuration loaded"
```

**Option B: Use Spring Cloud Config** (for dynamic config reload)
```yaml
# Enable refresh endpoint
management:
  endpoints:
    web:
      exposure:
        include: refresh

# Trigger refresh after ConfigMap update
curl -X POST http://sql-audit-service:8091/actuator/refresh
```

**Option C: Use Configuration Watcher**
```java
@Configuration
@EnableScheduling
public class ConfigWatcher {

  @Scheduled(fixedDelay = 60000)
  public void checkConfigUpdates() {
    // Re-read ConfigMap
    // Apply new configuration
  }
}
```

**Expected Resolution Time:** < 5 minutes
**Prevention:** Document that ConfigMap changes require pod restart

---

## 2. Diagnostic Flowchart

```
┌─────────────────────────────────────┐
│  Is the service responding?         │
└───────────┬─────────────────────────┘
            │
     ┌──────┴──────┐
     │ NO          │ YES
     ▼             ▼
┌─────────────┐ ┌──────────────────────────┐
│ Check Pods  │ │ Check Consumer Lag       │
└──────┬──────┘ └───────────┬──────────────┘
       │                    │
       ▼             ┌──────┴──────┐
┌─────────────┐     │ HIGH        │ NORMAL
│ Pods Down?  │     ▼             ▼
└──────┬──────┘ ┌─────────────┐ ┌──────────────────┐
       │        │ Check       │ │ Check Error Rate │
┌──────┴──────┐ │ Throughput  │ └────────┬─────────┘
│ YES        │ └──────┬──────┘          │
▼            │        │          ┌──────┴──────┐
┌────────────┴──┐     ▼          │ HIGH       │ LOW
│ Check Logs    │ ┌───────────┐  ▼            ▼
│ - Image Pull  │ │ Slow      │ ┌──────────┐ ┌─────────┐
│ - OOM         │ │ Checker?  │ │ Check DB │ │ Healthy │
│ - Crash       │ └───────────┘ │ Health   │ └─────────┘
└───────────────┘                └──────────┘
```

### Step-by-Step Diagnostic Process

**Step 1: Is the service responding?**
```bash
curl http://audit.example.com/actuator/health
```
- **200 OK**: Proceed to Step 2
- **5xx or timeout**: Check pods (Step 1a)

**Step 1a: Check Pods**
```bash
kubectl get pods -n sqlguard
```
- **Not Running**: Check logs → Fix image/config
- **Running**: Check service endpoint → Fix network

**Step 2: Check Consumer Lag**
```bash
kubectl exec -n sqlguard sql-audit-service-0 -- \
  curl -s localhost:8091/actuator/metrics/kafka.consumer.lag
```
- **> 10000**: See Issue 1 (Kafka Consumer Lag)
- **< 1000**: Proceed to Step 3

**Step 3: Check Error Rate**
```bash
kubectl logs sql-audit-service-0 -n sqlguard | grep ERROR | wc -l
```
- **> 10 errors/minute**: See database issues
- **< 1 error/minute**: Check performance

**Step 4: Check Database Health**
```bash
# PostgreSQL
kubectl exec -n sqlguard postgres-0 -- pg_isready

# ClickHouse
kubectl exec -n sqlguard clickhouse-0 -- clickhouse-client --query "SELECT 1"
```

---

## 3. Emergency Procedures

### Procedure 1: Emergency Degradation

**When to use:**
- Audit service impacting main application
- Critical production outage
- Need to restore service quickly

**Steps:**

1. **Disable Audit Layer** (keeps runtime defense active)
   ```yaml
   # Update ConfigMap
   kubectl patch configmap audit-config -n sqlguard \
     --patch '{"data":{"sqlguard.audit.enabled":"false"}}'
   ```

2. **Stop Consuming from Kafka** (preserve events)
   ```bash
   # Scale down to 0
   kubectl scale statefulset sql-audit-service --replicas=0 -n sqlguard

   # Events will accumulate in Kafka (retention: 7 days)
   ```

3. **Notify Stakeholders**
   ```
   Subject: Audit Service Degraded
   Body: Audit logging temporarily disabled due to [REASON].
         Runtime SQL safety checks remain active.
         ETA for restoration: [TIME]
   ```

4. **Restore Service** (after issue resolved)
   ```bash
   # Re-enable audit
   kubectl patch configmap audit-config -n sqlguard \
     --patch '{"data":{"sqlguard.audit.enabled":"true"}}'

   # Scale up
   kubectl scale statefulset sql-audit-service --replicas=3 -n sqlguard
   ```

---

### Procedure 2: Data Recovery

**Scenario:** ClickHouse data corrupted or lost

**Steps:**

1. **Stop Audit Service** (prevent further writes)
   ```bash
   kubectl scale statefulset sql-audit-service --replicas=0 -n sqlguard
   ```

2. **Restore from Backup**
   ```bash
   # Download latest backup
   clickhouse-backup download audit-latest

   # Restore
   clickhouse-backup restore audit-latest
   ```

3. **Replay Kafka Messages** (for events after backup)
   ```bash
   # Calculate offset to replay from
   # Use backup timestamp to find Kafka offset

   # Reset consumer group offset
   kafka-consumer-groups.sh --bootstrap-server kafka:9092 \
     --group audit-service-group \
     --topic sql-audit-events \
     --reset-offsets --to-datetime 2025-01-01T02:00:00.000 \
     --execute
   ```

4. **Restart Audit Service**
   ```bash
   kubectl scale statefulset sql-audit-service --replicas=3 -n sqlguard

   # Monitor replay
   watch -n 5 'kubectl exec -n sqlguard sql-audit-service-0 -- \
     curl -s localhost:8091/actuator/metrics/kafka.consumer.lag'
   ```

---

### Procedure 3: Rollback Deployment

**Scenario:** New version causing issues

**Steps:**

1. **Verify Current Version**
   ```bash
   kubectl get statefulset sql-audit-service -n sqlguard -o yaml | grep image:
   ```

2. **Rollback to Previous Version**
   ```bash
   kubectl rollout undo statefulset/sql-audit-service -n sqlguard

   # Or rollback to specific revision
   kubectl rollout undo statefulset/sql-audit-service --to-revision=5 -n sqlguard
   ```

3. **Verify Rollback**
   ```bash
   kubectl rollout status statefulset/sql-audit-service -n sqlguard

   # Check version
   kubectl get pods -n sqlguard -o jsonpath='{.items[0].spec.containers[0].image}'
   ```

4. **Monitor Health**
   ```bash
   # Check consumer lag
   # Check error rate
   # Check API response time
   ```

---

## 4. Performance Tuning

### 4.1 JVM Tuning

**Default Configuration:**
```bash
-Xms2g -Xmx4g -XX:+UseG1GC
```

**For High Throughput:**
```bash
JAVA_OPTS="-Xms8g -Xmx16g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:InitiatingHeapOccupancyPercent=45 \
  -XX:G1HeapRegionSize=16M \
  -XX:+ParallelRefProcEnabled \
  -XX:+UseStringDeduplication"
```

**For Low Latency:**
```bash
JAVA_OPTS="-Xms4g -Xmx4g \
  -XX:+UseZGC \
  -XX:ZCollectionInterval=5 \
  -XX:ZFragmentationLimit=5"
```

### 4.2 Database Connection Pool Tuning

**Symptoms:**
- Connection timeouts
- High database latency
- Threads waiting for connections

**Current Settings:**
```yaml
spring.datasource.hikari.maximum-pool-size: 20
spring.datasource.hikari.minimum-idle: 5
```

**Recommended Settings:**

**Small Scale (< 10k events/day):**
```yaml
spring.datasource.hikari.maximum-pool-size: 10
spring.datasource.hikari.minimum-idle: 3
spring.datasource.hikari.connection-timeout: 30000
```

**Medium Scale (< 100k events/day):**
```yaml
spring.datasource.hikari.maximum-pool-size: 20
spring.datasource.hikari.minimum-idle: 5
spring.datasource.hikari.connection-timeout: 30000
```

**Large Scale (< 1M events/day):**
```yaml
spring.datasource.hikari.maximum-pool-size: 50
spring.datasource.hikari.minimum-idle: 10
spring.datasource.hikari.connection-timeout: 30000
spring.datasource.hikari.idle-timeout: 300000
```

### 4.3 Kafka Consumer Tuning

**For Higher Throughput:**
```yaml
spring.kafka.consumer.max-poll-records: 500
spring.kafka.consumer.fetch-min-size: 1048576  # 1MB
spring.kafka.listener.concurrency: 5
```

**For Lower Latency:**
```yaml
spring.kafka.consumer.max-poll-records: 100
spring.kafka.consumer.fetch-min-size: 1024  # 1KB
spring.kafka.listener.concurrency: 3
```

### 4.4 ClickHouse Batch Tuning

**For Higher Throughput:**
```yaml
clickhouse.batch.size: 5000
clickhouse.batch.timeout: 10000
clickhouse.flush.interval: 30000
```

**For Lower Latency:**
```yaml
clickhouse.batch.size: 500
clickhouse.batch.timeout: 1000
clickhouse.flush.interval: 5000
```

---

## 5. Useful Commands

### Check System Health
```bash
# All-in-one health check
kubectl get pods,svc,statefulset -n sqlguard

# Detailed pod status
kubectl describe pod sql-audit-service-0 -n sqlguard

# Resource usage
kubectl top pods -n sqlguard

# Events (errors, warnings)
kubectl get events -n sqlguard --sort-by='.lastTimestamp'
```

### Performance Metrics
```bash
# Consumer lag
kubectl exec -n sqlguard sql-audit-service-0 -- \
  curl -s localhost:8091/actuator/metrics/kafka.consumer.lag | jq

# Processing rate
kubectl exec -n sqlguard sql-audit-service-0 -- \
  curl -s localhost:8091/actuator/metrics/audit.events.processed | jq

# Memory usage
kubectl exec -n sqlguard sql-audit-service-0 -- \
  curl -s localhost:8091/actuator/metrics/jvm.memory.used | jq
```

### Log Analysis
```bash
# Real-time logs
kubectl logs -f sql-audit-service-0 -n sqlguard

# Search for errors
kubectl logs sql-audit-service-0 -n sqlguard | grep ERROR

# Last 100 lines
kubectl logs sql-audit-service-0 -n sqlguard --tail=100

# Logs from all pods
kubectl logs -l app=sql-audit-service -n sqlguard --tail=50
```

---

## 6. Contact and Escalation

| Issue Severity | Response Time | Escalation Path |
|----------------|---------------|-----------------|
| P1 - Critical | 15 minutes | On-call → Manager → VP |
| P2 - High | 1 hour | On-call → Team Lead |
| P3 - Medium | 4 hours | Team member |
| P4 - Low | Next business day | Team member |

**On-Call Rotation:** See PagerDuty schedule
**Slack Channel:** #sql-audit-service
**Documentation:** https://wiki.example.com/sql-audit-service

---

**Document Version**: 1.0
**Last Updated**: 2025-01-01
**Maintainer**: Platform Team
