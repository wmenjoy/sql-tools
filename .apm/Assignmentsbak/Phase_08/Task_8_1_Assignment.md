# Task Assignment: 8.1 - AuditLogWriter Interface & JSON Schema Design

**Assigned:** 2025-12-17
**Status:** Pending
**Agent Type:** Agent_Audit_Infrastructure

---

## Context

- **Phase:** 8 - Audit Log Output Layer
- **Dependencies:** Phase 1 Task 1.2 (ValidationResult model for violation metadata) - ALREADY COMPLETE
- **Downstream Consumers:** Tasks 8.2, 8.3, 8.3.5, 8.4, 8.5, 8.6 (ALL depend on this task)
- **Module to Create:** `sql-guard-audit-api` (Java 8 compatible)

---

## Objective

Design unified audit log interface and JSON schema defining structured audit event format with SQL context, execution results, timing metrics, and violation metadata, establishing contract between runtime interceptors and audit service for consistent log-based communication.

---

## Deliverables

1. **AuditLogWriter interface** with `writeAuditLog(AuditEvent)` method
2. **AuditEvent data model** with fields:
   - `sqlId` (MD5 hash of SQL for deduplication)
   - `sql`, `sqlType`, `mapperId`, `datasource`
   - `params` (parameter bindings, nullable)
   - `executionTimeMs`, `rowsAffected` (-1 if not applicable)
   - `errorMessage` (nullable), `timestamp`
   - `violations` (from pre-execution validator, nullable)
3. **JSON schema specification** (`docs/audit-log-schema.json`)
4. **Jackson ObjectMapper configuration** for ISO-8601 date/time formatting
5. **Validation** ensuring required fields populated before writing

---

## Implementation Steps

### Step 1: Interface Design TDD
Write test class `AuditLogWriterTest` with test methods:
- `testWriteAuditLog_withCompleteEvent_shouldSucceed()`
- `testWriteAuditLog_withMissingRequiredFields_shouldThrowException()`
- `testAuditEventSerialization_shouldProduceValidJson()`

Implement `AuditLogWriter` interface in `com.footstone.sqlguard.audit` package:
```java
public interface AuditLogWriter {
    void writeAuditLog(AuditEvent event) throws AuditLogException;
}
```

Required fields validation: `sql`, `sqlType`, `mapperId`, `timestamp` - throw `IllegalArgumentException` if null.

### Step 2: AuditEvent Model TDD
Write test class `AuditEventTest` with test methods:
- `testBuilder_withAllFields_shouldConstruct()`
- `testBuilder_withMissingRequired_shouldThrowException()`
- `testEquals_withSameContent_shouldBeEqual()`
- `testToString_shouldContainAllFields()`

Implement `AuditEvent` as immutable class with builder pattern:
```java
public final class AuditEvent {
    private final String sqlId;
    private final String sql;
    private final SqlCommandType sqlType;
    // ... other final fields

    public static Builder builder() { ... }
}
```

### Step 3: JSON Schema & Serialization TDD
Write test class `AuditEventSerializationTest` with test methods:
- `testJsonSerialization_shouldProduceExpectedFormat()`
- `testJsonDeserialization_shouldRecreateEvent()`
- `testDateTimeSerialization_shouldUseIso8601()`
- `testNullFields_shouldSerializeAsNull()`

Create `docs/audit-log-schema.json` with:
- Required: `["sqlId", "sql", "sqlType", "mapperId", "timestamp"]`
- Timestamp format: ISO-8601 (`"2024-01-15T10:30:45.123Z"`)

Configure Jackson:
```java
ObjectMapper mapper = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .setSerializationInclusion(JsonInclude.Include.ALWAYS);
```

### Step 4: Validation Implementation
In `AuditEvent.Builder.build()`:
- Check required fields not null
- Validate `sqlId` matches MD5 hash of `sql`
- Validate `executionTimeMs >= 0`
- Validate `rowsAffected >= -1`
- Validate `timestamp` not in future (+5s tolerance)

### Step 5: Integration Testing
Write `AuditLogWriterIntegrationTest` with scenarios:
- Successful SELECT (executionTimeMs, rowsAffected=0)
- Successful UPDATE (rowsAffected > 0)
- Failed execution (errorMessage, rowsAffected=-1)
- Event with pre-execution violations

Verify JSON round-trip serialization for each scenario.

---

## Module Setup

Create new Maven module `sql-guard-audit-api`:

```xml
<project>
    <parent>
        <groupId>com.footstone</groupId>
        <artifactId>sql-guard-parent</artifactId>
        <version>${project.version}</version>
    </parent>
    <artifactId>sql-guard-audit-api</artifactId>
    <name>SQL Guard Audit API</name>

    <properties>
        <java.version>1.8</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.footstone</groupId>
            <artifactId>sql-guard-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
        </dependency>
    </dependencies>
</project>
```

---

## Acceptance Criteria

- [ ] All tests pass (`mvn test -pl sql-guard-audit-api`)
- [ ] Code coverage > 80%
- [ ] JSON schema created and documented
- [ ] Thread-safety requirements documented in Javadoc
- [ ] Validation rules documented in Javadoc

---

## References

- Implementation Plan: `.apm/Implementation_Plan.md`, Task 8.1
- Design Document: `.apm/SQL_Audit_Platform_Design.md`
- Existing Pattern: `sql-guard-core/src/main/java/com/footstone/sqlguard/core/model/ValidationResult.java`

---

## Notes for Downstream Tasks

After completion, the following tasks can proceed in parallel:
- Task 8.2: Logback Async Appender (uses AuditEvent serialization)
- Task 8.3: Druid SqlAuditFilter (uses AuditLogWriter interface)
- Task 8.3.5: HikariCP SqlAuditProxyFactory (uses AuditLogWriter interface)
- Task 8.4: MyBatis SqlAuditInterceptor (uses AuditLogWriter interface)
- Task 8.5: MyBatis-Plus InnerAuditInterceptor (uses AuditLogWriter interface)
- Task 8.6: P6Spy Audit Listener (uses AuditLogWriter interface)
