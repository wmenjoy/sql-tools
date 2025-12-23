# Prometheus Metrics for SQL Audit Service

## Overview

This document describes the metrics exported by the SQL Audit Service for monitoring with Prometheus.

## Metrics Endpoint

The service exposes Prometheus metrics at:
```
http://<service-host>:8091/actuator/prometheus
```

## Metric Categories

### 1. Kafka Consumer Metrics

#### Consumer Lag
```promql
# Current consumer lag (messages behind)
kafka_consumer_lag{group="audit-service-group",topic="sql-audit-events",partition="0"}

# Consumer lag rate of change
rate(kafka_consumer_lag[5m])
```

#### Consumption Rate
```promql
# Messages consumed per second
rate(kafka_consumer_records_consumed_total[5m])

# Consumption bytes per second
rate(kafka_consumer_bytes_consumed_total[5m])
```

#### Consumer Errors
```promql
# Consumer errors total
kafka_consumer_errors_total

# Error rate
rate(kafka_consumer_errors_total[5m])
```

### 2. Checker Processing Metrics

#### Checker Duration
```promql
# P95 latency for SlowQueryChecker
histogram_quantile(0.95, rate(audit_checker_duration_seconds_bucket{checker="SlowQueryChecker"}[5m]))

# P99 latency for all checkers
histogram_quantile(0.99, rate(audit_checker_duration_seconds_bucket[5m]))

# Average duration by checker
rate(audit_checker_duration_seconds_sum[5m]) / rate(audit_checker_duration_seconds_count[5m])
```

#### Checker Results
```promql
# Total checks performed
audit_checker_checks_total{checker="SlowQueryChecker"}

# Violations detected
audit_checker_violations_total{checker="SlowQueryChecker",severity="HIGH"}

# Violation rate
rate(audit_checker_violations_total[5m])
```

### 3. Event Processing Metrics

#### Processing Throughput
```promql
# Events processed per second
rate(audit_events_processed_total[5m])

# Events processed by severity
rate(audit_events_processed_total{severity="CRITICAL"}[5m])
```

#### Processing Errors
```promql
# Total processing errors
audit_processing_errors_total

# Error rate
rate(audit_processing_errors_total[5m])

# Errors by type
audit_processing_errors_total{error_type="database_error"}
```

#### Processing Latency
```promql
# End-to-end processing latency (from Kafka to ClickHouse)
histogram_quantile(0.99, rate(audit_event_processing_duration_seconds_bucket[5m]))
```

### 4. Database Metrics

#### PostgreSQL Connection Pool
```promql
# Active connections
hikaricp_connections_active{pool="postgres"}

# Idle connections
hikaricp_connections_idle{pool="postgres"}

# Pending threads waiting for connection
hikaricp_connections_pending{pool="postgres"}

# Connection timeout total
hikaricp_connections_timeout_total{pool="postgres"}
```

#### ClickHouse Metrics
```promql
# ClickHouse write operations
clickhouse_write_operations_total

# ClickHouse write duration
histogram_quantile(0.99, rate(clickhouse_write_duration_seconds_bucket[5m]))

# ClickHouse batch size
clickhouse_batch_size

# ClickHouse write errors
clickhouse_write_errors_total
```

### 5. JVM Metrics

#### Memory Usage
```promql
# Heap memory used
jvm_memory_used_bytes{area="heap"}

# Heap memory max
jvm_memory_max_bytes{area="heap"}

# Memory usage percentage
100 * jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}
```

#### Garbage Collection
```promql
# GC pause time
rate(jvm_gc_pause_seconds_sum[5m])

# GC count
rate(jvm_gc_pause_seconds_count[5m])

# Average GC duration
rate(jvm_gc_pause_seconds_sum[5m]) / rate(jvm_gc_pause_seconds_count[5m])
```

#### Threads
```promql
# Live threads
jvm_threads_live_threads

# Daemon threads
jvm_threads_daemon_threads

# Peak threads
jvm_threads_peak_threads
```

### 6. HTTP API Metrics

#### Request Rate
```promql
# Requests per second
rate(http_server_requests_seconds_count[5m])

# Requests by endpoint
rate(http_server_requests_seconds_count{uri="/api/audit/reports"}[5m])
```

#### Response Time
```promql
# P95 response time
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# P99 response time by endpoint
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket{uri="/api/audit/reports"}[5m]))
```

#### Error Rate
```promql
# 4xx errors
rate(http_server_requests_seconds_count{status=~"4.."}[5m])

# 5xx errors
rate(http_server_requests_seconds_count{status=~"5.."}[5m])

# Error rate percentage
100 * sum(rate(http_server_requests_seconds_count{status=~"[45].."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))
```

### 7. Application Health Metrics

#### Liveness State
```promql
# 1 = UP, 0 = DOWN
application_liveness_state{state="UP"}
```

#### Readiness State
```promql
# 1 = READY, 0 = NOT_READY
application_readiness_state{state="READY"}
```

## Prometheus Scrape Configuration

```yaml
scrape_configs:
  - job_name: 'sql-audit-service'
    scrape_interval: 15s
    scrape_timeout: 10s
    metrics_path: '/actuator/prometheus'
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names:
            - sqlguard
    relabel_configs:
      # Keep only pods with app=sql-audit-service label
      - source_labels: [__meta_kubernetes_pod_label_app]
        action: keep
        regex: sql-audit-service

      # Add pod name as instance label
      - source_labels: [__meta_kubernetes_pod_name]
        target_label: pod_name

      # Add node name
      - source_labels: [__meta_kubernetes_pod_node_name]
        target_label: node_name

      # Add namespace
      - source_labels: [__meta_kubernetes_namespace]
        target_label: namespace

      # Use pod IP and port
      - source_labels: [__address__]
        action: replace
        regex: ([^:]+)(?::\d+)?
        replacement: $1:8091
        target_label: __address__
```

## ServiceMonitor (Prometheus Operator)

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: sql-audit-service
  namespace: sqlguard
  labels:
    app: sql-audit-service
spec:
  selector:
    matchLabels:
      app: sql-audit-service
  endpoints:
  - port: actuator
    path: /actuator/prometheus
    interval: 15s
    scrapeTimeout: 10s
```

## Custom Metrics Implementation

### Example: Custom Checker Metric

```java
package com.footstone.audit.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class CheckerMetrics {

    private final Counter checksTotal;
    private final Counter violationsTotal;
    private final Timer checkerDuration;

    public CheckerMetrics(MeterRegistry registry) {
        this.checksTotal = Counter.builder("audit.checker.checks.total")
            .description("Total number of checks performed")
            .tag("application", "sql-audit-service")
            .register(registry);

        this.violationsTotal = Counter.builder("audit.checker.violations.total")
            .description("Total number of violations detected")
            .tag("application", "sql-audit-service")
            .register(registry);

        this.checkerDuration = Timer.builder("audit.checker.duration.seconds")
            .description("Time taken to execute checker")
            .tag("application", "sql-audit-service")
            .register(registry);
    }

    public void recordCheck(String checkerName) {
        checksTotal.increment();
    }

    public void recordViolation(String checkerName, String severity) {
        violationsTotal.increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start();
    }

    public void recordDuration(Timer.Sample sample, String checkerName) {
        sample.stop(checkerDuration);
    }
}
```

## Metric Retention

- **Short-term**: Prometheus retains metrics for 15 days
- **Long-term**: Metrics sent to Thanos or VictoriaMetrics for 1+ year retention

## Recording Rules

```yaml
groups:
  - name: audit_service_rules
    interval: 30s
    rules:
    # Pre-aggregate consumer lag
    - record: audit:kafka_consumer_lag:avg
      expr: avg(kafka_consumer_lag{group="audit-service-group"})

    # Pre-aggregate processing rate
    - record: audit:events_processed:rate5m
      expr: rate(audit_events_processed_total[5m])

    # Pre-aggregate error rate
    - record: audit:error:rate5m
      expr: rate(audit_processing_errors_total[5m])

    # Pre-aggregate P99 latency
    - record: audit:processing_latency:p99
      expr: histogram_quantile(0.99, rate(audit_event_processing_duration_seconds_bucket[5m]))
```

## Useful Queries

### Dashboard Queries

```promql
# Total events processed today
increase(audit_events_processed_total[24h])

# Current throughput (events/sec)
rate(audit_events_processed_total[1m])

# Top 5 slowest checkers
topk(5, avg by (checker) (rate(audit_checker_duration_seconds_sum[5m]) / rate(audit_checker_duration_seconds_count[5m])))

# Violations by severity (last hour)
sum by (severity) (increase(audit_checker_violations_total[1h]))

# Consumer lag trend (last 6 hours)
kafka_consumer_lag{group="audit-service-group"}[6h]
```

## References

- [Micrometer Documentation](https://micrometer.io/docs)
- [Spring Boot Actuator Metrics](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics)
- [Prometheus Query Language](https://prometheus.io/docs/prometheus/latest/querying/basics/)

---

**Document Version**: 1.0
**Last Updated**: 2025-01-01
**Maintainer**: Observability Team
