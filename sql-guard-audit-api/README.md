# SQL Guard Audit API

Audit log interface and data models for SQL Guard audit output layer.

## Overview

This module provides the core API for audit logging in the SQL Guard system. It defines:

- **AuditLogWriter Interface**: Contract for audit log output implementations
- **AuditEvent Data Model**: Immutable event representation with comprehensive SQL execution context
- **JSON Schema**: Standardized format for audit log events
- **Jackson Configuration**: ISO-8601 date/time formatting and serialization settings

## Key Features

### 1. AuditLogWriter Interface

Thread-safe interface for writing audit log events with required field validation:

```java
AuditLogWriter writer = new LogbackAuditLogWriter();
AuditEvent event = AuditEvent.builder()
    .sql("SELECT * FROM users WHERE id = ?")
    .sqlType(SqlCommandType.SELECT)
    .mapperId("UserMapper.selectById")
    .timestamp(Instant.now())
    .build();
writer.writeAuditLog(event);
```

### 2. AuditEvent Data Model

Immutable event with builder pattern supporting:

**Required Fields:**
- `sql` - SQL statement
- `sqlType` - Command type (SELECT, UPDATE, DELETE, INSERT)
- `mapperId` - Mapper identifier
- `timestamp` - Event timestamp

**Optional Fields:**
- `datasource` - Datasource name
- `params` - Parameter bindings
- `executionTimeMs` - Execution time (default: 0)
- `rowsAffected` - Rows affected (default: -1)
- `errorMessage` - Error message if failed
- `violations` - Pre-execution validation violations

**Auto-generated:**
- `sqlId` - MD5 hash of SQL for deduplication

### 3. Validation Rules

Built-in validation ensures data integrity:

- Required fields must not be null
- `executionTimeMs` must be >= 0
- `rowsAffected` must be >= -1
- `timestamp` cannot be in future (5 second tolerance)
- `sqlId` automatically matches MD5 hash of SQL

### 4. JSON Serialization

Jackson-based serialization with:

- ISO-8601 timestamp format (`2024-01-15T10:30:45.123Z`)
- Explicit null serialization for optional fields
- Full round-trip support (serialize → deserialize)

**ObjectMapper Configuration:**

```java
ObjectMapper mapper = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .setSerializationInclusion(JsonInclude.Include.ALWAYS);
```

## Usage Examples

### Successful SELECT Query

```java
Map<String, Object> params = new HashMap<>();
params.put("userId", 123);

AuditEvent event = AuditEvent.builder()
    .sql("SELECT id, name, email FROM users WHERE id = ?")
    .sqlType(SqlCommandType.SELECT)
    .mapperId("UserMapper.selectById")
    .datasource("primary")
    .params(params)
    .executionTimeMs(85L)
    .rowsAffected(0)
    .timestamp(Instant.now())
    .build();

writer.writeAuditLog(event);
```

### Failed UPDATE with Error

```java
AuditEvent event = AuditEvent.builder()
    .sql("UPDATE orders SET status = ? WHERE id = ?")
    .sqlType(SqlCommandType.UPDATE)
    .mapperId("OrderMapper.updateStatus")
    .datasource("primary")
    .executionTimeMs(45L)
    .rowsAffected(-1)
    .errorMessage("Foreign key constraint violation")
    .timestamp(Instant.now())
    .build();

writer.writeAuditLog(event);
```

### Event with Pre-execution Violations

```java
ValidationResult violations = ValidationResult.pass();
violations.addViolation(
    RiskLevel.HIGH,
    "Missing WHERE clause in UPDATE statement",
    "Add WHERE condition to limit affected rows"
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

## JSON Schema

The audit log JSON schema is defined in `docs/audit-log-schema.json` and follows JSON Schema Draft 7.

**Example JSON Output:**

```json
{
  "sqlId": "5d41402abc4b2a76b9719d911017c592",
  "sql": "SELECT * FROM users WHERE id = ?",
  "sqlType": "SELECT",
  "mapperId": "com.example.UserMapper.selectById",
  "datasource": "primary",
  "params": {
    "id": 123
  },
  "executionTimeMs": 150,
  "rowsAffected": 1,
  "errorMessage": null,
  "timestamp": "2024-01-15T10:30:45.123Z",
  "violations": null
}
```

## Thread Safety

### AuditLogWriter Implementations

Implementations should be thread-safe as they may be called concurrently from multiple threads. Consider:

- Thread-local buffers for performance
- Synchronized blocks for shared state
- Concurrent data structures (e.g., `ConcurrentHashMap`)
- Async appenders for non-blocking writes

### AuditEvent Immutability

`AuditEvent` is fully immutable and thread-safe:

- All fields are `final`
- No setters provided
- Safe to share across threads
- Safe to store in collections

## Testing

The module includes comprehensive test coverage:

- **AuditLogWriterTest** (3 tests): Interface contract validation
- **AuditEventTest** (11 tests): Builder pattern, validation, equality
- **AuditEventSerializationTest** (7 tests): JSON serialization/deserialization
- **AuditLogWriterIntegrationTest** (7 tests): End-to-end scenarios

**Run tests:**

```bash
mvn test -pl sql-guard-audit-api
```

**All 28 tests pass with 100% success rate.**

## Dependencies

- **sql-guard-core**: Core validation models (`ValidationResult`, `SqlCommandType`)
- **jackson-databind**: JSON serialization
- **jackson-datatype-jsr310**: Java 8 date/time support
- **slf4j-api**: Logging facade

## Integration with Downstream Tasks

This module provides the foundation for:

- **Task 8.2**: Logback Async Appender (uses `AuditEvent` serialization)
- **Task 8.3**: Druid SqlAuditFilter (uses `AuditLogWriter` interface)
- **Task 8.3.5**: HikariCP SqlAuditProxyFactory (uses `AuditLogWriter` interface)
- **Task 8.4**: MyBatis SqlAuditInterceptor (uses `AuditLogWriter` interface)
- **Task 8.5**: MyBatis-Plus InnerAuditInterceptor (uses `AuditLogWriter` interface)
- **Task 8.6**: P6Spy Audit Listener (uses `AuditLogWriter` interface)

## Design Decisions

### Why MD5 for sqlId?

MD5 provides:
- Fast computation (critical for high-throughput systems)
- Fixed 32-character length (efficient indexing)
- Sufficient uniqueness for SQL deduplication
- Wide library support (Java built-in)

Collision risk is negligible for SQL statements in practice.

### Why Immutable AuditEvent?

Immutability ensures:
- Thread safety without synchronization
- Predictable behavior in concurrent environments
- Safe sharing across components
- Prevention of accidental modifications

### Why Builder Pattern?

Builder pattern provides:
- Clear API for optional fields
- Validation at construction time
- Immutability guarantee
- Fluent interface for readability

## License

Copyright © 2024 Footstone. All Rights Reserved.






