# SQL Guard Audit API - Usage Examples

This document provides practical examples for using the SQL Guard Audit API.

## Table of Contents

1. [Basic Usage](#basic-usage)
2. [Advanced Scenarios](#advanced-scenarios)
3. [Custom Implementation](#custom-implementation)
4. [JSON Serialization](#json-serialization)
5. [Error Handling](#error-handling)

## Basic Usage

### Creating a Simple Audit Event

```java
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.core.model.SqlCommandType;
import java.time.Instant;

AuditEvent event = AuditEvent.builder()
    .sql("SELECT * FROM users WHERE id = ?")
    .sqlType(SqlCommandType.SELECT)
    .mapperId("UserMapper.selectById")
    .timestamp(Instant.now())
    .build();

// The sqlId is automatically generated as MD5 hash
String sqlId = event.getSqlId(); // e.g., "5d41402abc4b2a76b9719d911017c592"
```

### Writing Audit Logs

```java
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.audit.AuditLogException;

AuditLogWriter writer = new LogbackAuditLogWriter(); // Your implementation

try {
    writer.writeAuditLog(event);
} catch (AuditLogException e) {
    logger.error("Failed to write audit log", e);
}
```

## Advanced Scenarios

### Scenario 1: SELECT Query with Parameters

```java
Map<String, Object> params = new HashMap<>();
params.put("userId", 123);
params.put("status", "active");

AuditEvent event = AuditEvent.builder()
    .sql("SELECT * FROM users WHERE id = ? AND status = ?")
    .sqlType(SqlCommandType.SELECT)
    .mapperId("UserMapper.selectByIdAndStatus")
    .datasource("primary")
    .params(params)
    .executionTimeMs(95L)
    .rowsAffected(0) // SELECT doesn't affect rows
    .timestamp(Instant.now())
    .build();

writer.writeAuditLog(event);
```

### Scenario 2: UPDATE with Rows Affected

```java
Map<String, Object> params = new HashMap<>();
params.put("status", "inactive");
params.put("userId", 456);

AuditEvent event = AuditEvent.builder()
    .sql("UPDATE users SET status = ? WHERE id = ?")
    .sqlType(SqlCommandType.UPDATE)
    .mapperId("UserMapper.updateStatus")
    .datasource("primary")
    .params(params)
    .executionTimeMs(120L)
    .rowsAffected(1)
    .timestamp(Instant.now())
    .build();

writer.writeAuditLog(event);
```

### Scenario 3: Failed Execution with Error

```java
AuditEvent event = AuditEvent.builder()
    .sql("DELETE FROM orders WHERE id = ?")
    .sqlType(SqlCommandType.DELETE)
    .mapperId("OrderMapper.deleteById")
    .datasource("primary")
    .executionTimeMs(45L)
    .rowsAffected(-1) // Not applicable due to error
    .errorMessage("Foreign key constraint violation: order has related items")
    .timestamp(Instant.now())
    .build();

writer.writeAuditLog(event);
```

### Scenario 4: Event with Pre-execution Violations

```java
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.core.model.RiskLevel;

// Create validation result with violations
ValidationResult violations = ValidationResult.pass();
violations.addViolation(
    RiskLevel.HIGH,
    "Missing WHERE clause in UPDATE statement",
    "Add WHERE condition to limit affected rows"
);
violations.addViolation(
    RiskLevel.MEDIUM,
    "Missing index on user_id column",
    "Add index: CREATE INDEX idx_user_id ON users(user_id)"
);

AuditEvent event = AuditEvent.builder()
    .sql("UPDATE users SET last_login = NOW()")
    .sqlType(SqlCommandType.UPDATE)
    .mapperId("UserMapper.updateLastLogin")
    .datasource("primary")
    .executionTimeMs(350L)
    .rowsAffected(1500)
    .timestamp(Instant.now())
    .violations(violations)
    .build();

writer.writeAuditLog(event);
```

## Custom Implementation

### Implementing AuditLogWriter

```java
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomAuditLogWriter implements AuditLogWriter {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomAuditLogWriter.class);
    private final ObjectMapper objectMapper;
    
    public CustomAuditLogWriter() {
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }
    
    @Override
    public void writeAuditLog(AuditEvent event) throws AuditLogException {
        // Validate required fields
        validateEvent(event);
        
        try {
            // Serialize to JSON
            String json = objectMapper.writeValueAsString(event);
            
            // Write to your backend (e.g., Kafka, file, database)
            writeToBackend(json);
            
        } catch (JsonProcessingException e) {
            throw new AuditLogException("Failed to serialize audit event", e);
        } catch (Exception e) {
            throw new AuditLogException("Failed to write audit log", e);
        }
    }
    
    private void validateEvent(AuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("AuditEvent cannot be null");
        }
        if (event.getSql() == null) {
            throw new IllegalArgumentException("sql field is required");
        }
        if (event.getSqlType() == null) {
            throw new IllegalArgumentException("sqlType field is required");
        }
        if (event.getMapperId() == null) {
            throw new IllegalArgumentException("mapperId field is required");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("timestamp field is required");
        }
    }
    
    private void writeToBackend(String json) {
        // Your implementation here
        logger.info("Audit log: {}", json);
    }
}
```

## JSON Serialization

### Configuring ObjectMapper

```java
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

ObjectMapper mapper = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .setSerializationInclusion(JsonInclude.Include.ALWAYS);
```

### Serialization Example

```java
AuditEvent event = AuditEvent.builder()
    .sql("SELECT * FROM users WHERE id = ?")
    .sqlType(SqlCommandType.SELECT)
    .mapperId("UserMapper.selectById")
    .datasource("primary")
    .executionTimeMs(150L)
    .rowsAffected(1)
    .timestamp(Instant.parse("2024-01-15T10:30:45.123Z"))
    .build();

String json = mapper.writeValueAsString(event);
System.out.println(json);
```

**Output:**

```json
{
  "sqlId": "5d41402abc4b2a76b9719d911017c592",
  "sql": "SELECT * FROM users WHERE id = ?",
  "sqlType": "SELECT",
  "mapperId": "UserMapper.selectById",
  "datasource": "primary",
  "params": null,
  "executionTimeMs": 150,
  "rowsAffected": 1,
  "errorMessage": null,
  "timestamp": "2024-01-15T10:30:45.123Z",
  "violations": null
}
```

### Deserialization Example

```java
String json = "{\"sqlId\":\"abc123\",\"sql\":\"SELECT * FROM users\",\"sqlType\":\"SELECT\",\"mapperId\":\"UserMapper.selectAll\",\"datasource\":\"primary\",\"params\":null,\"executionTimeMs\":100,\"rowsAffected\":0,\"errorMessage\":null,\"timestamp\":\"2024-01-15T10:30:45.123Z\",\"violations\":null}";

AuditEvent event = mapper.readValue(json, AuditEvent.class);

System.out.println("SQL: " + event.getSql());
System.out.println("Type: " + event.getSqlType());
System.out.println("Execution Time: " + event.getExecutionTimeMs() + "ms");
```

## Error Handling

### Handling Validation Errors

```java
try {
    AuditEvent event = AuditEvent.builder()
        .sql("SELECT * FROM users")
        .sqlType(SqlCommandType.SELECT)
        // Missing required field: mapperId
        .timestamp(Instant.now())
        .build();
} catch (IllegalArgumentException e) {
    logger.error("Invalid audit event: {}", e.getMessage());
    // Handle validation error
}
```

### Handling Write Errors

```java
try {
    writer.writeAuditLog(event);
} catch (AuditLogException e) {
    logger.error("Failed to write audit log", e);
    
    // Implement retry logic
    retryWrite(event, 3);
    
    // Or fallback to alternative storage
    fallbackWriter.writeAuditLog(event);
}
```

### Handling Serialization Errors

```java
try {
    String json = objectMapper.writeValueAsString(event);
} catch (JsonProcessingException e) {
    logger.error("Failed to serialize audit event", e);
    // Handle serialization error
}
```

## Best Practices

### 1. Reuse ObjectMapper

```java
// Create once and reuse (thread-safe)
private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .setSerializationInclusion(JsonInclude.Include.ALWAYS);
```

### 2. Use Try-with-Resources for Cleanup

```java
try (BufferedWriter writer = new BufferedWriter(new FileWriter("audit.log", true))) {
    String json = OBJECT_MAPPER.writeValueAsString(event);
    writer.write(json);
    writer.newLine();
} catch (IOException e) {
    throw new AuditLogException("Failed to write to file", e);
}
```

### 3. Implement Async Writing for Performance

```java
private final ExecutorService executor = Executors.newSingleThreadExecutor();

@Override
public void writeAuditLog(AuditEvent event) throws AuditLogException {
    validateEvent(event);
    
    executor.submit(() -> {
        try {
            String json = objectMapper.writeValueAsString(event);
            writeToBackend(json);
        } catch (Exception e) {
            logger.error("Async write failed", e);
        }
    });
}
```

### 4. Add Circuit Breaker for Resilience

```java
private final CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("auditLog");

@Override
public void writeAuditLog(AuditEvent event) throws AuditLogException {
    validateEvent(event);
    
    try {
        circuitBreaker.executeSupplier(() -> {
            String json = objectMapper.writeValueAsString(event);
            writeToBackend(json);
            return null;
        });
    } catch (Exception e) {
        throw new AuditLogException("Circuit breaker open", e);
    }
}
```

## Performance Considerations

1. **ObjectMapper Reuse**: Create once and reuse (thread-safe after configuration)
2. **Async Writing**: Use async appenders to avoid blocking application threads
3. **Batching**: Batch multiple events for better throughput
4. **Connection Pooling**: Use connection pools for database/network backends
5. **Buffer Size**: Tune buffer sizes based on throughput requirements

## Thread Safety

- `AuditEvent` is immutable and thread-safe
- `ObjectMapper` is thread-safe after configuration
- `AuditLogWriter` implementations should be thread-safe
- Use thread-local buffers for performance if needed

## See Also

- [README.md](../README.md) - Module overview and API documentation
- [audit-log-schema.json](audit-log-schema.json) - JSON schema specification
- [SQL Guard Design Document](../../.apm/SQL_Audit_Platform_Design.md) - System architecture
