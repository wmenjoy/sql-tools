# Task Assignment: 8.2 - Logback Async Appender Configuration

**Generated:** 2025-12-17  
**Agent Type:** Agent_Audit_Infrastructure  
**Phase:** Phase 8 - Audit Log Output Layer  
**Dependencies:** Task 8.1 ✅ (AuditLogWriter interface, AuditEvent model)

---

## Context

You are implementing the **Logback Async Appender** for high-throughput audit log writing. This task provides the foundational logging infrastructure that all audit interceptors (Tasks 8.3-8.6) will use to write audit events asynchronously without blocking SQL execution.

**Key Design Decisions:**
- AsyncAppender with queue-based buffering prevents audit logging from blocking SQL execution (<1% overhead target)
- RollingFileAppender with time/size policies prevents disk exhaustion
- Dedicated audit logger separates audit events from application logs
- File-based buffering enables Filebeat collection for Kafka ingestion

---

## Objective

Configure Logback AsyncAppender for high-throughput audit log writing with file rolling strategy, implementing async I/O pattern preventing audit logging from blocking SQL execution, and file-based buffering enabling Filebeat collection for Kafka ingestion.

---

## Deliverables

### Primary Outputs
1. **LogbackAuditWriter** class implementing `AuditLogWriter` interface
2. **logback-audit.xml** configuration with AsyncAppender and RollingFileAppender
3. **docs/audit-log-filebeat.md** documentation for Filebeat integration
4. **Performance benchmarks** validating <1ms write latency

### Test Requirements
- Comprehensive unit tests (20+ tests)
- Integration tests with async behavior validation
- Performance benchmarks demonstrating <1ms p99 latency
- Rolling policy tests with time/size triggers

---

## Implementation Steps

### Step 1: Logback Configuration Design (TDD)

**Test First:**
```java
// LogbackAuditAppenderTest.java
@Test
void testAsyncAppender_shouldNotBlock() {
    // Measure write latency for 1000 events
    // Verify <1ms p99 latency
}

@Test
void testAuditLogger_shouldIsolateFromRoot() {
    // Verify audit events not propagated to root logger
    // Verify events appear only in audit files
}

@Test
void testRollingPolicy_shouldCreateDateDirectories() {
    // Verify files created in logs/audit/yyyy-MM-dd/ structure
}

@Test
void testQueueFull_shouldBlock() {
    // Verify queue saturation blocks caller when discardingThreshold=0
}
```

**Implementation:**
Create `logback-audit.xml` in `sql-guard-audit-api/src/main/resources`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Dedicated Audit Logger Configuration -->
    <appender name="ASYNC_AUDIT" class="ch.qos.logback.classic.AsyncAppender">
        <!-- Queue Configuration -->
        <queueSize>8192</queueSize>
        <discardingThreshold>0</discardingThreshold> <!-- Block when full, no event loss -->
        <includeCallerData>false</includeCallerData> <!-- Performance optimization -->
        <neverBlock>false</neverBlock> <!-- Block to prevent audit loss -->
        
        <!-- Nested File Appender -->
        <appender-ref ref="AUDIT_FILE"/>
    </appender>
    
    <appender name="AUDIT_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/audit/current/audit.log</file>
        
        <!-- JSON-per-line format (AuditEvent already JSON-serialized) -->
        <encoder>
            <pattern>%msg%n</pattern>
            <charset>UTF-8</charset>
        </encoder>
        
        <!-- Time and Size Based Rolling Policy -->
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <!-- Hourly rotation with date-based directories -->
            <fileNamePattern>logs/audit/%d{yyyy-MM-dd}/audit.%d{yyyy-MM-dd-HH}.%i.log</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>720</maxHistory> <!-- 30 days * 24 hours -->
            <totalSizeCap>10GB</totalSizeCap>
        </rollingPolicy>
    </appender>
    
    <!-- Audit Logger (isolated from root) -->
    <logger name="com.footstone.sqlguard.audit.AUDIT" level="INFO" additivity="false">
        <appender-ref ref="ASYNC_AUDIT"/>
    </logger>
</configuration>
```

### Step 2: LogbackAuditWriter Implementation (TDD)

**Test First:**
```java
// LogbackAuditWriterTest.java
@Test
void testWriteAuditLog_withCompleteEvent_shouldSucceed() {
    // Create complete AuditEvent
    // Call writeAuditLog()
    // Verify JSON written to logger
}

@Test
void testWriteAuditLog_withNullEvent_shouldThrowException() {
    // Verify IllegalArgumentException thrown
}

@Test
void testJsonSerialization_shouldUseIso8601() {
    // Verify Instant serialized to ISO-8601 format
}

@Test
void testThreadSafety_shouldHandleConcurrentWrites() {
    // Execute 100 concurrent writes
    // Verify all events written successfully
}
```

**Implementation:**
Create `LogbackAuditWriter.java` in `com.footstone.sqlguard.audit`:

```java
package com.footstone.sqlguard.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logback-based implementation of AuditLogWriter.
 *
 * <p>Uses dedicated audit logger with AsyncAppender for high-throughput
 * non-blocking audit log writing.</p>
 *
 * <p><strong>Performance Characteristics:</strong></p>
 * <ul>
 *   <li>Write latency: <1ms p99 (async queue-based)</li>
 *   <li>Throughput: >10,000 events/sec</li>
 *   <li>Overhead: <1% on SQL execution</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe.</p>
 */
public class LogbackAuditWriter implements AuditLogWriter {

    private static final Logger AUDIT_LOGGER = 
        LoggerFactory.getLogger("com.footstone.sqlguard.audit.AUDIT");
    
    private final ObjectMapper objectMapper;
    
    /**
     * Constructs LogbackAuditWriter with default ObjectMapper configuration.
     */
    public LogbackAuditWriter() {
        this.objectMapper = createObjectMapper();
    }
    
    /**
     * Constructs LogbackAuditWriter with custom ObjectMapper.
     *
     * @param objectMapper custom ObjectMapper for JSON serialization
     */
    public LogbackAuditWriter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }
    
    @Override
    public void writeAuditLog(AuditEvent event) throws AuditLogException {
        if (event == null) {
            throw new IllegalArgumentException("AuditEvent must not be null");
        }
        
        try {
            // Serialize AuditEvent to JSON
            String json = objectMapper.writeValueAsString(event);
            
            // Write to async appender (non-blocking)
            AUDIT_LOGGER.info(json);
            
        } catch (Exception e) {
            throw new AuditLogException("Failed to write audit log: " + e.getMessage(), e);
        }
    }
    
    /**
     * Creates ObjectMapper with audit-specific configuration.
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Register JavaTimeModule for java.time support
        mapper.registerModule(new JavaTimeModule());
        
        // Serialize Instant as ISO-8601 string (not timestamp)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Include null values for clarity
        mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        
        return mapper;
    }
}
```

### Step 3: Rolling Policy Integration Testing

**Test First:**
```java
// AuditLogRollingIntegrationTest.java
@Test
void testHourlyRolling_shouldCreateNewFiles() {
    // Mock clock advancing hours
    // Verify new files created
}

@Test
void testSizeBasedSplit_shouldCreateMultipleFiles() {
    // Write >100MB events
    // Verify split into audit.2024-01-15-10.0.log, audit.2024-01-15-10.1.log
}

@Test
void testMaxHistory_shouldDeleteOldLogs() {
    // Write logs spanning >30 days
    // Verify oldest deleted
}
```

**Implementation:**
Use Logback's test utilities to verify rolling behavior programmatically.

### Step 4: Performance Tuning and Benchmarking

**Benchmark Test:**
```java
// AuditLogWriterBenchmark.java (JMH)
@Benchmark
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public void benchmarkWriteAuditLog(Blackhole blackhole) {
    AuditEvent event = createSampleEvent();
    writer.writeAuditLog(event);
    blackhole.consume(event);
}

@Test
void testOverhead_shouldBeLessThan1Percent() {
    // Measure SQL+validation+audit vs SQL+validation only
    // Verify <1% overhead
}
```

**Performance Targets:**
- Write latency: <1ms p99
- Throughput: >10,000 events/sec
- Overhead on SQL execution: <1%

### Step 5: Filebeat Integration Documentation

Create `docs/audit-log-filebeat.md`:

```markdown
# Filebeat Integration for SQL Audit Logs

## Overview

This guide explains how to configure Filebeat to collect audit logs from the
SQL Guard system and forward them to Kafka for processing by the Audit Service.

## Filebeat Configuration

### Input Configuration

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
```

### Kafka Output Configuration

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
```

### Registry Management

```yaml
filebeat.registry.path: /var/lib/filebeat/registry
filebeat.registry.file_permissions: 0600
filebeat.registry.flush: 1s
```

## Deployment

### Docker Compose

```yaml
services:
  filebeat:
    image: docker.elastic.co/beats/filebeat:8.11.0
    volumes:
      - ./filebeat.yml:/usr/share/filebeat/filebeat.yml:ro
      - ./logs/audit:/app/logs/audit:ro  # Shared volume with app
      - filebeat-registry:/var/lib/filebeat
    environment:
      - ENVIRONMENT=production
    depends_on:
      - kafka
```

### Kubernetes Sidecar

```yaml
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: app
      image: sql-guard-app:latest
      volumeMounts:
        - name: audit-logs
          mountPath: /app/logs/audit
    
    - name: filebeat
      image: docker.elastic.co/beats/filebeat:8.11.0
      volumeMounts:
        - name: filebeat-config
          mountPath: /usr/share/filebeat/filebeat.yml
          subPath: filebeat.yml
        - name: audit-logs
          mountPath: /app/logs/audit
          readOnly: true
  
  volumes:
    - name: audit-logs
      emptyDir: {}
    - name: filebeat-config
      configMap:
        name: filebeat-config
```

## Monitoring

Monitor Filebeat ingestion lag:

```bash
# Check registry for last processed position
cat /var/lib/filebeat/registry/filebeat/log.json | jq '.[] | {source, offset}'

# Monitor Filebeat metrics
curl http://localhost:5066/stats | jq '.filebeat.harvester'
```
```

---

## Acceptance Criteria

- [ ] All tests pass: `mvn test -pl sql-guard-audit-api`
- [ ] Code coverage > 80% for LogbackAuditWriter
- [ ] Performance benchmark: <1ms p99 write latency
- [ ] Performance benchmark: >10,000 events/sec throughput
- [ ] Performance benchmark: <1% overhead on SQL execution
- [ ] Integration test: async behavior validated
- [ ] Integration test: rolling policy creates date directories
- [ ] Integration test: 100MB size limit enforced
- [ ] Documentation: Filebeat integration guide complete
- [ ] Documentation: Performance tuning guidelines documented

---

## References

### Implementation Plan
- `.apm/Implementation_Plan.md` - Task 8.2 (lines 823-844)

### Design Document
- `.apm/SQL_Audit_Platform_Design.md` - Section 9.1 (Performance Requirements)

### Dependencies
- Task 8.1 Output: `AuditLogWriter` interface, `AuditEvent` model
- Logback Classic 1.2.12 (from parent POM)
- Jackson with JavaTimeModule (from Task 8.1)

### Existing Patterns
- Logging configuration: `sql-guard-core/src/main/resources/logback.xml`
- Test logging: `sql-guard-core/src/test/resources/logback-test.xml`

---

## Testing Strategy

### Unit Tests (20+ tests)
1. **LogbackAuditWriter Tests:**
   - Write with complete event
   - Write with null event (exception)
   - Write with missing required fields (exception)
   - JSON serialization format
   - ISO-8601 timestamp format
   - Thread safety (concurrent writes)

2. **Configuration Tests:**
   - Audit logger isolation from root
   - AsyncAppender queue behavior
   - Blocking when queue full
   - No event loss with discardingThreshold=0

### Integration Tests (10+ tests)
1. **Async Behavior:**
   - Write latency <1ms p99
   - Non-blocking writes
   - Queue saturation handling

2. **Rolling Policy:**
   - Hourly rotation
   - Date directory structure
   - 100MB size-based split
   - 30-day retention (maxHistory)
   - 10GB total size cap

### Performance Benchmarks (5+ tests)
1. Write latency (p50, p95, p99)
2. Throughput (events/sec)
3. Overhead on SQL execution
4. Queue size impact
5. Concurrent write performance

---

## Performance Tuning Guidelines

### Queue Size Configuration

| Scenario | Queue Size | Rationale |
|----------|-----------|-----------|
| Typical load (1000 SQL/s) | 8192 (default) | Balanced memory/throughput |
| High-volume (>5000 SQL/s) | 65536 | Higher burst capacity |
| Low-memory environment | 2048 | Reduced memory footprint |

### Discarding Threshold

| Value | Behavior | Use Case |
|-------|----------|----------|
| 0 (default) | Block when queue full | Zero audit loss (recommended) |
| 20% | Discard INFO when 80% full | Performance priority |

### Caller Data

| Setting | Performance | Use Case |
|---------|------------|----------|
| false (default) | Optimal | Production (no stack trace needed) |
| true | Slower | Debugging only |

---

## Common Issues and Solutions

### Issue 1: High Write Latency

**Symptoms:** p99 latency >5ms

**Solutions:**
1. Increase queue size (default 8192 → 65536)
2. Verify disk I/O not saturated
3. Check rolling policy not triggering too frequently
4. Verify no GC pauses during write

### Issue 2: Audit Events Lost

**Symptoms:** Missing events in audit logs

**Solutions:**
1. Verify discardingThreshold=0 (no discard)
2. Check queue size sufficient for burst load
3. Verify neverBlock=false (blocking enabled)
4. Check application logs for AuditLogException

### Issue 3: Disk Space Exhaustion

**Symptoms:** Disk full, application fails

**Solutions:**
1. Verify totalSizeCap configured (10GB default)
2. Verify maxHistory configured (30 days default)
3. Monitor disk usage with alerts
4. Configure Filebeat to clean processed logs

---

## Next Steps

After completing this task:
1. Update memory file: `.apm/Memory/Phase_08_Audit_Output/Task_8_2_Logback.md`
2. Verify all tests pass: `mvn test -pl sql-guard-audit-api`
3. Run performance benchmarks and document results
4. Proceed to Task 8.3 (Druid SqlAuditFilter) or other Batch 2 tasks

---

**Good luck! Remember to follow TDD: write tests first, then implement.**













