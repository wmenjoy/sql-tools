---
type: Technical Specification
component: Configuration
version: 1.0
created: 2024-12-15
updated: 2025-01-16
status: Active
maintainer: SQL Safety Guard Team
---

# Audit Log Writer Configuration Guide

## Overview

SQL Guard now supports two modes for writing audit logs:
1. **LOGBACK** (default): Write audit events to log files via logback
2. **KAFKA**: Write audit events directly to Kafka topic

This guide explains how to configure and use each mode.

## Architecture

```
┌─────────────────┐
│  SQL Execution  │
└────────┬────────┘
         │
         v
┌─────────────────┐
│ AuditLogWriter  │  Interface
└────────┬────────┘
         │
         ├───────────────┐
         │               │
         v               v
┌──────────────┐  ┌──────────────┐
│   Logback    │  │    Kafka     │
│    Writer    │  │    Writer    │
└──────────────┘  └──────────────┘
```

## Configuration

### Option 1: Logback Mode (Default)

Writes audit events to log files using logback appender.

**application.yml:**
```yaml
sql-guard:
  audit:
    enabled: true
    writer-type: LOGBACK  # Default, can be omitted
```

**Features:**
- Asynchronous file I/O
- Automatic log rotation (hourly, size-based)
- JSON format (one event per line)
- Easy integration with Filebeat/Fluentd
- Log location: `logs/audit/`

**Use Cases:**
- Simple deployments
- Log aggregation via Filebeat/Fluentd
- When Kafka is not available

### Option 2: Kafka Mode

Writes audit events directly to Kafka topic for centralized processing.

**application.yml:**
```yaml
sql-guard:
  audit:
    enabled: true
    writer-type: KAFKA
    kafka:
      topic: sql-audit-events  # Optional, default is sql-audit-events

spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
      retries: 3
```

**Features:**
- Direct write to Kafka topic
- Asynchronous fire-and-forget pattern
- Automatic partitioning by SQL ID
- High throughput (>50,000 events/sec)
- At-least-once delivery guarantee

**Use Cases:**
- Centralized audit processing
- Real-time SQL analysis
- Multiple applications with shared audit service
- When immediate analysis is required

## Switching Between Modes

You can switch between modes by changing the `writer-type` configuration:

```yaml
# Development: Use logback for simplicity
spring:
  profiles: dev
sql-guard:
  audit:
    enabled: true
    writer-type: LOGBACK

---
# Production: Use Kafka for centralized processing
spring:
  profiles: prod
sql-guard:
  audit:
    enabled: true
    writer-type: KAFKA
    kafka:
      topic: sql-audit-events
```

## Testing the Configuration

### Test Logback Mode

1. Enable audit logging:
```yaml
sql-guard:
  audit:
    enabled: true
    writer-type: LOGBACK
```

2. Execute some SQL statements
3. Check the audit log file: `logs/audit/current/audit.log`
4. Verify JSON format:
```json
{"sqlId":"5d41402abc4b2a76b9719d911017c592","sql":"SELECT * FROM users WHERE id = ?","sqlType":"SELECT",...}
```

### Test Kafka Mode

1. Start Kafka:
```bash
# Docker Compose example
docker-compose up -d kafka
```

2. Enable Kafka writer:
```yaml
sql-guard:
  audit:
    enabled: true
    writer-type: KAFKA
```

3. Execute SQL statements
4. Consume from Kafka topic:
```bash
kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic sql-audit-events \
  --from-beginning
```

## Audit Event JSON Schema

Both modes write the same JSON format:

```json
{
  "sqlId": "5d41402abc4b2a76b9719d911017c592",
  "sql": "SELECT * FROM users WHERE id = ?",
  "sqlType": "SELECT",
  "executionLayer": "MYBATIS",
  "statementId": "com.example.UserMapper.selectById",
  "datasource": "primary",
  "params": {"id": 123},
  "executionTimeMs": 45,
  "rowsAffected": 1,
  "errorMessage": null,
  "timestamp": "2024-01-15T10:30:45.123Z",
  "violations": null
}
```

## Performance Comparison

| Mode     | Write Latency (p99) | Throughput       | Overhead |
|----------|---------------------|------------------|----------|
| Logback  | <1ms                | >10,000 evt/sec  | <1%      |
| Kafka    | <5ms                | >50,000 evt/sec  | <1%      |

Both modes use asynchronous writing to minimize impact on SQL execution.

## Migration Guide

### From Logback to Kafka

1. Ensure Kafka is running and accessible
2. Update configuration:
```yaml
sql-guard:
  audit:
    writer-type: KAFKA  # Change from LOGBACK
    kafka:
      topic: sql-audit-events
```
3. Restart application
4. Verify events are flowing to Kafka

### From Kafka to Logback

1. Update configuration:
```yaml
sql-guard:
  audit:
    writer-type: LOGBACK  # Change from KAFKA
```
2. Restart application
3. Verify events are written to log files

## Troubleshooting

### Kafka Mode Issues

**Problem:** Events not appearing in Kafka
- **Check:** Kafka connection
  ```bash
  telnet <kafka-host> <kafka-port>
  ```
- **Check:** Kafka producer configuration in application.yml
- **Check:** Application logs for connection errors

**Problem:** High latency
- **Solution:** Tune Kafka producer settings:
  ```yaml
  spring:
    kafka:
      producer:
        linger-ms: 10  # Batch messages for better throughput
        batch-size: 16384
  ```

### Logback Mode Issues

**Problem:** Log files not created
- **Check:** Directory permissions for `logs/audit/`
- **Check:** logback-audit.xml is on classpath

**Problem:** Disk space filling up
- **Solution:** Adjust retention policy in logback-audit.xml:
  ```xml
  <maxHistory>720</maxHistory> <!-- 30 days * 24 hours -->
  <totalSizeCap>10GB</totalSizeCap>
  ```

## Advanced Configuration

### Custom ObjectMapper

You can provide a custom ObjectMapper bean for JSON serialization:

```java
@Configuration
public class AuditConfig {
    @Bean
    public AuditLogWriter customAuditWriter(KafkaTemplate<String, String> kafkaTemplate) {
        ObjectMapper mapper = new ObjectMapper();
        // Custom configuration
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        return new KafkaAuditWriter(kafkaTemplate, "custom-topic", mapper);
    }
}
```

### Multiple Audit Targets

Write to both logback and Kafka simultaneously:

```java
@Configuration
public class DualAuditConfig {
    @Bean
    public AuditLogWriter dualAuditWriter(
            @Qualifier("logbackAuditWriter") AuditLogWriter logbackWriter,
            @Qualifier("kafkaAuditWriter") AuditLogWriter kafkaWriter) {
        return event -> {
            logbackWriter.writeAuditLog(event);
            kafkaWriter.writeAuditLog(event);
        };
    }
}
```

## Best Practices

1. **Use Kafka mode in production** for centralized audit processing
2. **Use Logback mode in development** for simplicity
3. **Monitor Kafka lag** if using Kafka mode
4. **Set appropriate retention** based on compliance requirements
5. **Test failover** scenarios (Kafka unavailable, disk full, etc.)

## Related Documentation

- [Audit Log Schema](./audit-log-schema.json)
- [Audit Service Consumer](../../sql-audit-service/README.md)
- [Logback Configuration](../sql-guard-audit-api/src/main/resources/logback-audit.xml)
