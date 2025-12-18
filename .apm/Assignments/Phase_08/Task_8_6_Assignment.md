# Task Assignment: 8.6 - P6Spy Audit Listener Implementation

**Generated:** 2025-12-17  
**Agent Type:** Agent_Audit_Infrastructure  
**Phase:** Phase 8 - Audit Log Output Layer  
**Dependencies:** Task 8.1 ✅ (AuditLogWriter), Task 4.5 (P6SpySqlSafetyListener pattern)

---

## Context

You are implementing the **P6Spy Audit Listener** for universal JDBC audit logging. P6Spy provides fallback audit capability for environments without native interceptor support (C3P0, DBCP, Tomcat JDBC, etc.), working across any JDBC driver or framework.

**Key Design Decisions:**
- JdbcEventListener for universal JDBC interception
- onAfterAnyExecute() callback captures post-execution results
- StatementInformation provides SQL with parameter substitution
- Batch execution result aggregation
- P6SpyModule for SPI auto-discovery
- Works with any JDBC driver/pool/framework

---

## Objective

Implement P6Spy JdbcEventListener for universal JDBC audit logging across any connection pool or framework, capturing SQL execution results via onAfterAnyExecute callback with parameter-substituted SQL and JDBC result metadata, registering as P6Spy module for auto-discovery, and providing fallback audit capability for environments without native interceptor support.

---

## Deliverables

### Primary Outputs
1. **P6SpySqlAuditListener** class extending `JdbcEventListener`
2. **onAfterAnyExecute()** method capturing post-execution results and timing
3. **SQL extraction** from StatementInformation with parameter substitution
4. **Batch execution** result aggregation for executeBatch()
5. **P6SpySqlAuditModule** for listener registration via SPI
6. **spy.properties** configuration for module activation
7. **Integration tests** with P6Spy-wrapped datasources across multiple JDBC drivers

### Test Requirements
- Comprehensive unit tests (20+ tests)
- Integration tests with multiple JDBC drivers (MySQL, PostgreSQL, H2)
- Batch execution support validation
- ThreadLocal coordination with safety listener
- Performance overhead measurement (expected 12-18%)

---

## Implementation Steps

### Step 1: Listener Implementation (TDD)

**Test First:**
```java
// P6SpySqlAuditListenerTest.java
@Test
void testOnAfterAnyExecute_shouldLogAudit() {
    // Execute statement via P6Spy
    // Verify onAfterAnyExecute() called
    // Verify audit event written
}

@Test
void testExecuteTime_shouldCaptureAccurately() {
    // Execute statement
    // Verify timing from StatementInformation accurate
}

@Test
void testUpdateCount_shouldCaptureAffectedRows() {
    // Execute UPDATE
    // Verify rows affected captured
}

@Test
void testResultSetSize_shouldCaptureForQuery() {
    // Execute SELECT
    // Verify result set size captured (if available)
}

@Test
void testBatchExecution_shouldAggregateResults() {
    // Execute executeBatch()
    // Verify per-statement results aggregated
}

@Test
void testPreExecutionViolations_shouldCorrelate() {
    // Execute SQL with violations
    // Verify violations from P6SpySqlSafetyListener included
}
```

**Implementation:**
Create `P6SpySqlAuditListener.java` in `com.footstone.sqlguard.interceptor.p6spy`:

```java
package com.footstone.sqlguard.interceptor.p6spy;

import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.JdbcEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Instant;

/**
 * P6Spy listener for universal JDBC audit logging.
 *
 * <p>This listener captures SQL execution results, timing, and errors via
 * P6Spy's JdbcEventListener interface. It provides fallback audit capability
 * for environments without native interceptor support.</p>
 *
 * <p><strong>Universal Compatibility:</strong></p>
 * <ul>
 *   <li>Works with any JDBC driver (MySQL, PostgreSQL, Oracle, SQL Server, H2, etc.)</li>
 *   <li>Works with any connection pool (C3P0, DBCP, Tomcat JDBC, etc.)</li>
 *   <li>Works with any framework (MyBatis, JPA, JdbcTemplate, raw JDBC)</li>
 * </ul>
 *
 * <p><strong>ThreadLocal Coordination:</strong></p>
 * <p>Retrieves pre-execution ValidationResult from P6SpySqlSafetyListener via
 * shared ThreadLocal for violation correlation.</p>
 *
 * <p><strong>Performance:</strong> 12-18% overhead (higher than native solutions)</p>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>
 * # spy.properties
 * modulelist=com.footstone.sqlguard.interceptor.p6spy.P6SpySqlSafetyModule,\
 *            com.footstone.sqlguard.interceptor.p6spy.P6SpySqlAuditModule
 * </pre>
 */
public class P6SpySqlAuditListener extends JdbcEventListener {

    private static final Logger logger = LoggerFactory.getLogger(P6SpySqlAuditListener.class);
    
    private final AuditLogWriter auditLogWriter;
    
    /**
     * Constructs P6SpySqlAuditListener with specified AuditLogWriter.
     *
     * @param auditLogWriter the audit log writer (must not be null)
     */
    public P6SpySqlAuditListener(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = Objects.requireNonNull(auditLogWriter, "auditLogWriter must not be null");
    }
    
    @Override
    public void onAfterAnyExecute(StatementInformation statementInformation) {
        try {
            // Extract SQL with parameter substitution
            // WARNING: Contains actual parameter values - sanitize for production
            String sql = statementInformation.getSqlWithValues();
            
            if (sql == null || sql.isEmpty()) {
                return; // No SQL to audit
            }
            
            // Extract execution time (P6Spy tracks timing automatically)
            long durationMs = statementInformation.getExecuteTime();
            
            // Determine SQL type
            SqlCommandType sqlType = determineSqlType(sql);
            
            // Extract rows affected
            int rowsAffected = extractRowsAffected(statementInformation);
            
            // Check for errors
            SQLException exception = statementInformation.getStatementInformation().getException();
            
            // Retrieve pre-execution validation result from ThreadLocal
            ValidationResult validationResult = P6SpySqlSafetyListener.getValidationResult();
            
            // Build audit event
            AuditEvent.Builder eventBuilder = AuditEvent.builder()
                .sql(sql)
                .sqlType(sqlType)
                .mapperId("p6spy-jdbc")
                .timestamp(Instant.now())
                .executionTimeMs(durationMs)
                .rowsAffected(rowsAffected);
            
            // Add error message if exception occurred
            if (exception != null) {
                eventBuilder.errorMessage(exception.getMessage());
            }
            
            // Add pre-execution violations if available
            if (validationResult != null && !validationResult.isPassed()) {
                eventBuilder.violations(validationResult);
            }
            
            // Write audit log
            auditLogWriter.writeAuditLog(eventBuilder.build());
            
        } catch (Exception e) {
            logger.error("Failed to write audit log for P6Spy statement", e);
            // Don't throw - audit failure should not break SQL execution
        }
    }
    
    /**
     * Extracts rows affected from statement information.
     */
    private int extractRowsAffected(StatementInformation statementInformation) {
        try {
            // Check if batch execution
            if (statementInformation.isBatch()) {
                // Aggregate batch results
                int[] batchResults = statementInformation.getBatchResults();
                if (batchResults != null && batchResults.length > 0) {
                    int total = 0;
                    for (int result : batchResults) {
                        if (result >= 0) {
                            total += result;
                        }
                    }
                    return total;
                }
            }
            
            // Single statement - try to get update count
            // Note: P6Spy doesn't directly expose update count, need to access via reflection
            // or track in onAfterExecuteUpdate callback
            // For now, return -1 for SELECT, attempt extraction for DML
            
            String sql = statementInformation.getSql();
            if (sql != null) {
                String trimmed = sql.trim().toUpperCase();
                if (trimmed.startsWith("SELECT")) {
                    return -1; // SELECT doesn't have rows affected
                }
            }
            
            // Try to extract from statement result
            // This is driver-specific and may not always work
            return -1; // Default
            
        } catch (Exception e) {
            logger.debug("Failed to extract rows affected: {}", e.getMessage());
            return -1;
        }
    }
    
    /**
     * Determines SQL command type from SQL string.
     */
    private SqlCommandType determineSqlType(String sql) {
        if (sql == null || sql.isEmpty()) {
            return SqlCommandType.SELECT;
        }
        
        String trimmed = sql.trim().toUpperCase();
        if (trimmed.startsWith("SELECT")) {
            return SqlCommandType.SELECT;
        } else if (trimmed.startsWith("UPDATE")) {
            return SqlCommandType.UPDATE;
        } else if (trimmed.startsWith("DELETE")) {
            return SqlCommandType.DELETE;
        } else if (trimmed.startsWith("INSERT")) {
            return SqlCommandType.INSERT;
        }
        
        return SqlCommandType.SELECT; // Default
    }
}
```

### Step 2: Module Registration

**Implementation:**
Create `P6SpySqlAuditModule.java` for SPI registration:

```java
package com.footstone.sqlguard.interceptor.p6spy;

import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.audit.LogbackAuditWriter;
import com.p6spy.engine.spy.P6ModuleManager;
import com.p6spy.engine.spy.option.P6OptionsRepository;

/**
 * P6Spy module for audit listener registration.
 *
 * <p>This module registers P6SpySqlAuditListener with P6Spy for automatic
 * discovery via SPI (Service Provider Interface).</p>
 *
 * <p><strong>SPI Registration:</strong></p>
 * <p>Create file: META-INF/services/com.p6spy.engine.spy.P6Factory</p>
 * <p>Content: com.footstone.sqlguard.interceptor.p6spy.P6SpySqlAuditModule</p>
 */
public class P6SpySqlAuditModule extends com.p6spy.engine.spy.P6Factory {

    private static AuditLogWriter auditLogWriter;
    
    /**
     * Sets the AuditLogWriter for this module.
     * Must be called before P6Spy initialization.
     */
    public static void setAuditLogWriter(AuditLogWriter writer) {
        auditLogWriter = writer;
    }
    
    @Override
    public P6ModuleManager getModuleManager() {
        return new P6ModuleManager() {
            @Override
            public void loadOptions(P6OptionsRepository optionsRepository) {
                // No custom options needed
            }
            
            @Override
            public void init() {
                // Initialize audit listener
                if (auditLogWriter == null) {
                    // Default to Logback implementation
                    auditLogWriter = new LogbackAuditWriter();
                }
                
                P6SpySqlAuditListener listener = new P6SpySqlAuditListener(auditLogWriter);
                
                // Register listener with P6Spy
                com.p6spy.engine.event.JdbcEventListenerFactory.addListener(listener);
            }
        };
    }
}
```

**spy.properties Configuration:**
```properties
# P6Spy Configuration for SQL Guard Audit

# Module list (append audit module to existing safety module)
modulelist=com.footstone.sqlguard.interceptor.p6spy.P6SpySqlSafetyModule,\
          com.footstone.sqlguard.interceptor.p6spy.P6SpySqlAuditModule

# Logging
appender=com.p6spy.engine.spy.appender.Slf4JLogger
logMessageFormat=com.p6spy.engine.spy.appender.SingleLineFormat

# Drivers (example for MySQL)
driverlist=com.mysql.cj.jdbc.Driver

# Filter (optional - exclude specific statements)
# filter=false
# exclude=
# include=
```

### Step 3: Integration Testing

**Test Implementation:**
```java
// P6SpyAuditIntegrationTest.java
@Test
void testSuccessfulSelect_shouldLogAudit() {
    // Configure P6Spy datasource
    Class.forName("com.p6spy.engine.spy.P6SpyDriver");
    String url = "jdbc:p6spy:h2:mem:testdb";
    Connection conn = DriverManager.getConnection(url);
    
    // Execute SELECT
    Statement stmt = conn.createStatement();
    ResultSet rs = stmt.executeQuery("SELECT * FROM users");
    
    // Verify audit event written
    // Verify timing captured
    // Verify SQL with parameters
}

@Test
void testSuccessfulUpdate_shouldCaptureRowsAffected() {
    // Execute UPDATE
    int rows = stmt.executeUpdate("UPDATE users SET status = 'active' WHERE id = 1");
    
    // Verify rowsAffected captured
}

@Test
void testBatchExecution_shouldAggregateResults() {
    // Execute executeBatch()
    stmt.addBatch("UPDATE users SET status = 'active' WHERE id = 1");
    stmt.addBatch("UPDATE users SET status = 'active' WHERE id = 2");
    stmt.addBatch("UPDATE users SET status = 'active' WHERE id = 3");
    int[] results = stmt.executeBatch();
    
    // Verify total rows affected aggregated
}

@Test
void testMultiDriver_shouldWorkAcrossDrivers() {
    // Test with MySQL driver
    testWithDriver("com.mysql.cj.jdbc.Driver", "jdbc:p6spy:mysql://localhost/test");
    
    // Test with PostgreSQL driver
    testWithDriver("org.postgresql.Driver", "jdbc:p6spy:postgresql://localhost/test");
    
    // Test with H2 driver
    testWithDriver("org.h2.Driver", "jdbc:p6spy:h2:mem:testdb");
}

@Test
void testPreExecutionViolations_shouldCorrelate() {
    // Enable both safety and audit listeners
    // Execute SQL violating safety rules
    // Verify audit event includes violations
}
```

### Step 4: Performance Testing

**Test Implementation:**
```java
// P6SpyAuditPerformanceTest.java
@Test
void testOverhead_shouldBeMeasurable() {
    // Execute 10000 SQL statements with P6Spy audit enabled
    long startWithAudit = System.nanoTime();
    for (int i = 0; i < 10000; i++) {
        stmt.executeQuery("SELECT * FROM users WHERE id = " + i);
    }
    long durationWithAudit = (System.nanoTime() - startWithAudit) / 1_000_000;
    
    // Execute 10000 SQL statements with raw JDBC
    long startRaw = System.nanoTime();
    for (int i = 0; i < 10000; i++) {
        rawStmt.executeQuery("SELECT * FROM users WHERE id = " + i);
    }
    long durationRaw = (System.nanoTime() - startRaw) / 1_000_000;
    
    // Calculate overhead
    double overhead = ((double)(durationWithAudit - durationRaw) / durationRaw) * 100;
    
    // Document findings (expected 12-18% overhead)
    logger.info("P6Spy audit overhead: {}%", overhead);
    
    // Compare to native solutions
    // Druid audit: ~7%
    // MyBatis audit: ~5%
    // P6Spy audit: 12-18% (tradeoff for universal compatibility)
}
```

### Step 5: Documentation

Create `docs/integration/p6spy-audit-setup.md`:

```markdown
# P6Spy Audit Setup Guide

## When to Use P6Spy Audit

**Use P6Spy when:**
- Connection pool lacks native interceptor support (C3P0, DBCP, Tomcat JDBC)
- Need unified audit across mixed frameworks (MyBatis + JPA + JdbcTemplate)
- Rapid deployment without application code changes
- Testing/debugging scenarios requiring comprehensive SQL capture

**Use native solutions when:**
- Performance critical (P6Spy has 12-18% overhead vs 5-7% for native)
- Connection pool has native support (Druid, HikariCP)
- Framework has native support (MyBatis, MyBatis-Plus)

## Configuration

### 1. Add P6Spy Dependency

```xml
<dependency>
    <groupId>p6spy</groupId>
    <artifactId>p6spy</artifactId>
    <version>3.9.1</version>
</dependency>
```

### 2. Configure spy.properties

Place in `src/main/resources/spy.properties`:

```properties
modulelist=com.footstone.sqlguard.interceptor.p6spy.P6SpySqlAuditModule
appender=com.p6spy.engine.spy.appender.Slf4JLogger
driverlist=com.mysql.cj.jdbc.Driver
```

### 3. Update JDBC URL

Change from:
```
jdbc:mysql://localhost:3306/mydb
```

To:
```
jdbc:p6spy:mysql://localhost:3306/mydb
```

### 4. Update Driver Class

Change from:
```
com.mysql.cj.jdbc.Driver
```

To:
```
com.p6spy.engine.spy.P6SpyDriver
```

## Spring Boot Integration

```yaml
spring:
  datasource:
    driver-class-name: com.p6spy.engine.spy.P6SpyDriver
    url: jdbc:p6spy:mysql://localhost:3306/mydb
    username: root
    password: password
```

## Limitations

1. **Parameter Substitution:** SQL contains actual parameter values (sanitize sensitive data)
2. **Higher Overhead:** 12-18% vs 5-7% for native solutions
3. **Limited DI Support:** Configuration via static methods or properties file
4. **Batch Result Detail:** May not provide per-statement detail for all drivers
```

---

## Acceptance Criteria

- [ ] All tests pass: `mvn test -pl sql-guard-jdbc`
- [ ] Code coverage > 80% for P6SpySqlAuditListener
- [ ] Integration test: successful SELECT logged
- [ ] Integration test: successful UPDATE with rows affected
- [ ] Integration test: batch execution aggregation
- [ ] Integration test: multi-driver compatibility (MySQL, PostgreSQL, H2)
- [ ] Integration test: pre-execution violations correlated
- [ ] Performance: overhead measured and documented (12-18%)
- [ ] Documentation: P6Spy setup guide complete
- [ ] Module registration via SPI working

---

## References

### Implementation Plan
- `.apm/Implementation_Plan.md` - Task 8.6 (lines 933-954)

### Design Document
- `.apm/SQL_Audit_Platform_Design.md` - Section 8.6 (P6Spy Integration)

### Dependencies
- Task 8.1: `AuditLogWriter`, `AuditEvent`
- Task 4.5: `P6SpySqlSafetyListener` pattern reference

### Existing Code
- `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/p6spy/P6SpySqlSafetyListener.java`

---

## Testing Strategy

### Unit Tests (20+ tests)
1. **Listener Execution:**
   - onAfterAnyExecute() called
   - Timing capture
   - SQL extraction
   - Rows affected extraction

2. **Batch Execution:**
   - executeBatch() aggregation
   - Per-statement results
   - Error handling

3. **ThreadLocal Coordination:**
   - Retrieve ValidationResult
   - Violations in audit event

### Integration Tests (10+ tests)
1. Real P6Spy with H2
2. Multi-driver compatibility
3. Batch execution
4. Pre-execution violation correlation
5. Performance overhead measurement

---

## Next Steps

After completing this task:
1. Update memory file: `.apm/Memory/Phase_08_Audit_Output/Task_8_6_P6Spy.md`
2. **Create Phase 8 Summary:** `.apm/Memory/Phase_08_Audit_Output/Phase_Summary.md`
3. Verify all 6 Batch 2 tasks complete
4. Proceed to Batch 3 (Phase 9 - Audit Checkers)

---

**Good luck! P6Spy provides universal fallback - critical for broad compatibility.**

