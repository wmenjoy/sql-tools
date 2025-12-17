# Filebeat Integration for SQL Audit Logs

## Overview

This guide explains how to configure Filebeat to collect audit logs from the SQL Guard system and forward them to Kafka for processing by the Audit Service.

## Architecture

```
SQL Guard Application
    ↓ (writes JSON logs)
Logback AsyncAppender
    ↓ (rolling files)
logs/audit/yyyy-MM-dd/audit.*.log
    ↓ (reads)
Filebeat
    ↓ (forwards)
Kafka Topic: sql-audit-events
    ↓ (consumes)
Audit Service
```

## Filebeat Configuration

### Input Configuration

Configure Filebeat to read from the date-based audit log directories:

```yaml
filebeat.inputs:
  - type: log
    enabled: true
    paths:
      - /app/logs/audit/*/*.log  # Matches date-directory wildcard
    
    # JSON parsing
    json.keys_under_root: true
    json.add_error_key: true
    
    # Metadata
    fields:
      audit_source: "sql-guard"
      environment: "${ENVIRONMENT:dev}"
    fields_under_root: true
    
    # Multiline handling (not needed - one JSON per line)
    multiline.type: count
    multiline.count_lines: 1
    
    # File handling
    close_inactive: 5m
    clean_inactive: 24h
    ignore_older: 72h
```

### Kafka Output Configuration

Forward audit events to Kafka for downstream processing:

```yaml
output.kafka:
  hosts: ["kafka-1:9092", "kafka-2:9092", "kafka-3:9092"]
  topic: "sql-audit-events"
  partition.round_robin:
    reachable_only: false
  
  # Compression
  compression: gzip
  compression_level: 5
  
  # Batching
  bulk_max_size: 2048
  
  # Reliability
  required_acks: 1
  max_retries: 3
  
  # Codec (JSON events are already serialized)
  codec.format:
    string: '%{[message]}'
```

### Registry Management

Configure Filebeat registry to track file positions:

```yaml
filebeat.registry.path: /var/lib/filebeat/registry
filebeat.registry.file_permissions: 0600
filebeat.registry.flush: 1s
```

### Complete Configuration Example

```yaml
# filebeat.yml
filebeat.inputs:
  - type: log
    enabled: true
    paths:
      - /app/logs/audit/*/*.log
    json.keys_under_root: true
    json.add_error_key: true
    fields:
      audit_source: "sql-guard"
      environment: "${ENVIRONMENT:production}"
    fields_under_root: true
    close_inactive: 5m
    clean_inactive: 24h
    ignore_older: 72h

output.kafka:
  hosts: ["kafka-1:9092", "kafka-2:9092", "kafka-3:9092"]
  topic: "sql-audit-events"
  partition.round_robin:
    reachable_only: false
  compression: gzip
  compression_level: 5
  bulk_max_size: 2048
  required_acks: 1
  max_retries: 3

filebeat.registry.path: /var/lib/filebeat/registry
filebeat.registry.file_permissions: 0600
filebeat.registry.flush: 1s

logging.level: info
logging.to_files: true
logging.files:
  path: /var/log/filebeat
  name: filebeat
  keepfiles: 7
  permissions: 0644
```

## Deployment

### Docker Compose

Deploy Filebeat alongside the SQL Guard application:

```yaml
version: '3.8'

services:
  sql-guard-app:
    image: sql-guard-app:latest
    volumes:
      - audit-logs:/app/logs/audit
    environment:
      - SPRING_PROFILES_ACTIVE=production

  filebeat:
    image: docker.elastic.co/beats/filebeat:8.11.0
    user: root
    volumes:
      - ./filebeat.yml:/usr/share/filebeat/filebeat.yml:ro
      - audit-logs:/app/logs/audit:ro  # Shared volume with app
      - filebeat-registry:/var/lib/filebeat
    environment:
      - ENVIRONMENT=production
    depends_on:
      - kafka

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092

volumes:
  audit-logs:
  filebeat-registry:
```

### Kubernetes Sidecar

Deploy Filebeat as a sidecar container in the same pod:

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: sql-guard-app
  labels:
    app: sql-guard
spec:
  containers:
    # Main application container
    - name: app
      image: sql-guard-app:latest
      volumeMounts:
        - name: audit-logs
          mountPath: /app/logs/audit
      env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
    
    # Filebeat sidecar container
    - name: filebeat
      image: docker.elastic.co/beats/filebeat:8.11.0
      volumeMounts:
        - name: filebeat-config
          mountPath: /usr/share/filebeat/filebeat.yml
          subPath: filebeat.yml
        - name: audit-logs
          mountPath: /app/logs/audit
          readOnly: true
        - name: filebeat-registry
          mountPath: /var/lib/filebeat
      env:
        - name: ENVIRONMENT
          value: "production"
  
  volumes:
    - name: audit-logs
      emptyDir: {}
    - name: filebeat-registry
      emptyDir: {}
    - name: filebeat-config
      configMap:
        name: filebeat-config
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: filebeat-config
data:
  filebeat.yml: |
    filebeat.inputs:
      - type: log
        enabled: true
        paths:
          - /app/logs/audit/*/*.log
        json.keys_under_root: true
        json.add_error_key: true
        fields:
          audit_source: "sql-guard"
          environment: "${ENVIRONMENT:production}"
        fields_under_root: true

    output.kafka:
      hosts: ["kafka-service:9092"]
      topic: "sql-audit-events"
      compression: gzip
      bulk_max_size: 2048
      required_acks: 1
```

## Monitoring

### Check Filebeat Status

Monitor Filebeat ingestion lag and performance:

```bash
# Check registry for last processed position
cat /var/lib/filebeat/registry/filebeat/log.json | jq '.[] | {source, offset}'

# Monitor Filebeat metrics
curl http://localhost:5066/stats | jq '.filebeat.harvester'

# View Filebeat logs
tail -f /var/log/filebeat/filebeat

# Check active harvesters
curl http://localhost:5066/stats | jq '.filebeat.harvester.open_files'
```

### Kafka Topic Monitoring

Verify events are reaching Kafka:

```bash
# Check topic lag
kafka-consumer-groups.sh --bootstrap-server kafka:9092 \
  --group audit-service-group \
  --describe

# Consume sample events
kafka-console-consumer.sh --bootstrap-server kafka:9092 \
  --topic sql-audit-events \
  --from-beginning \
  --max-messages 10
```

### Metrics to Monitor

| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `filebeat.harvester.open_files` | Number of active file harvesters | > 100 |
| `filebeat.harvester.running` | Number of running harvesters | < 1 |
| `filebeat.events.added` | Events added to queue | Rate decreasing |
| `filebeat.events.done` | Events successfully sent | < events.added |
| Kafka consumer lag | Events waiting to be processed | > 10,000 |

## Troubleshooting

### Issue 1: Filebeat Not Reading Files

**Symptoms:**
- No events in Kafka
- `filebeat.harvester.open_files` = 0

**Solutions:**
1. Check file permissions:
   ```bash
   ls -la /app/logs/audit/
   # Filebeat user must have read access
   ```

2. Verify path pattern matches files:
   ```bash
   ls /app/logs/audit/*/*.log
   ```

3. Check Filebeat logs for errors:
   ```bash
   tail -f /var/log/filebeat/filebeat
   ```

### Issue 2: High Ingestion Lag

**Symptoms:**
- Kafka consumer lag increasing
- Old audit logs not processed

**Solutions:**
1. Increase Filebeat bulk size:
   ```yaml
   output.kafka:
     bulk_max_size: 4096  # Increase from 2048
   ```

2. Add more Filebeat instances (horizontal scaling)

3. Check Kafka broker performance

### Issue 3: Duplicate Events

**Symptoms:**
- Same audit events appearing multiple times in Kafka

**Solutions:**
1. Verify registry is persisted:
   ```yaml
   filebeat.registry.path: /var/lib/filebeat/registry
   # Ensure this path is on a persistent volume
   ```

2. Check for multiple Filebeat instances reading same files

3. Verify `close_inactive` and `clean_inactive` settings

### Issue 4: JSON Parsing Errors

**Symptoms:**
- `json_error` field present in Kafka events
- Malformed events in Kafka

**Solutions:**
1. Verify log format is valid JSON:
   ```bash
   head -1 /app/logs/audit/2024-01-15/audit.2024-01-15-10.0.log | jq .
   ```

2. Check for multiline events (should not occur with current config)

3. Verify encoder pattern in `logback-audit.xml`:
   ```xml
   <pattern>%msg%n</pattern>  <!-- Should output raw JSON -->
   ```

## Performance Tuning

### Filebeat Configuration

| Scenario | Configuration | Rationale |
|----------|--------------|-----------|
| High-volume (>10,000 events/sec) | `bulk_max_size: 4096`<br>`worker: 4` | Larger batches, more workers |
| Low-latency requirement | `bulk_max_size: 512`<br>`flush_interval: 1s` | Smaller batches, faster flush |
| Network-constrained | `compression: gzip`<br>`compression_level: 9` | Maximum compression |

### Kafka Configuration

```yaml
output.kafka:
  # Increase throughput
  bulk_max_size: 4096
  worker: 4
  
  # Reduce latency
  max_message_bytes: 1000000
  
  # Improve reliability
  required_acks: 1  # Wait for leader acknowledgment
  max_retries: 3
  backoff.init: 1s
  backoff.max: 60s
```

## Security Considerations

### TLS/SSL Configuration

Secure Kafka connections with TLS:

```yaml
output.kafka:
  hosts: ["kafka-1:9093", "kafka-2:9093", "kafka-3:9093"]
  ssl.enabled: true
  ssl.certificate_authorities: ["/etc/filebeat/ca.crt"]
  ssl.certificate: "/etc/filebeat/client.crt"
  ssl.key: "/etc/filebeat/client.key"
  ssl.verification_mode: full
```

### SASL Authentication

Authenticate to Kafka using SASL:

```yaml
output.kafka:
  hosts: ["kafka-1:9093", "kafka-2:9093", "kafka-3:9093"]
  username: "filebeat-user"
  password: "${KAFKA_PASSWORD}"
  sasl.mechanism: "PLAIN"
  ssl.enabled: true
```

### File Permissions

Ensure audit logs are readable only by authorized users:

```bash
# Set restrictive permissions on audit logs
chmod 640 /app/logs/audit/*/*.log
chown app-user:filebeat-group /app/logs/audit/*/*.log

# Run Filebeat with appropriate user
docker run --user filebeat-user:filebeat-group ...
```

## Best Practices

1. **Use Persistent Volumes**: Ensure Filebeat registry is on a persistent volume to prevent duplicate events after restarts

2. **Monitor Registry Size**: Clean up old registry entries periodically to prevent unbounded growth

3. **Set Appropriate Retention**: Configure `ignore_older` to match your audit log retention policy

4. **Use Compression**: Enable gzip compression to reduce network bandwidth

5. **Batch Events**: Use appropriate `bulk_max_size` to balance latency and throughput

6. **Health Checks**: Implement health checks to detect Filebeat failures:
   ```yaml
   http.enabled: true
   http.host: "0.0.0.0"
   http.port: 5066
   ```

7. **Log Rotation Coordination**: Ensure Filebeat's `close_inactive` is less than Logback's rotation period

## Integration Testing

Test the complete pipeline:

```bash
# 1. Generate test audit event
curl -X POST http://sql-guard-app:8080/api/test/audit

# 2. Wait for Filebeat to process
sleep 5

# 3. Verify event in Kafka
kafka-console-consumer.sh --bootstrap-server kafka:9092 \
  --topic sql-audit-events \
  --from-beginning \
  --max-messages 1 | jq .

# 4. Check Filebeat metrics
curl http://filebeat:5066/stats | jq '.filebeat.events.done'
```

## References

- [Filebeat Reference Documentation](https://www.elastic.co/guide/en/beats/filebeat/current/index.html)
- [Kafka Output Configuration](https://www.elastic.co/guide/en/beats/filebeat/current/kafka-output.html)
- [Filebeat Performance Tuning](https://www.elastic.co/guide/en/beats/filebeat/current/performance-tuning.html)
- SQL Guard Logback Configuration: `sql-guard-audit-api/src/main/resources/logback-audit.xml`
