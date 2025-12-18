# Task Assignment: 8.3 - Druid SqlAuditFilter Implementation

**Generated:** 2025-12-17  
**Agent Type:** Agent_Audit_Infrastructure  
**Phase:** Phase 8 - Audit Log Output Layer  
**Dependencies:** Task 8.1 ✅ (AuditLogWriter), Task 4.3 (DruidSqlSafetyFilter pattern)

---

## Context

You are implementing the **Druid SqlAuditFilter** for JDBC-layer audit capture. This filter complements the existing DruidSqlSafetyFilter (pre-execution validation) by capturing post-execution results: execution duration, rows affected, and errors.

**Key Design Decisions:**
- Extends Druid's FilterAdapter for JDBC interception
- Runs AFTER statement execution to capture results
- Filter ordering: audit filter (order=10) runs after StatFilter (order=9)
- ThreadLocal coordination with DruidSqlSafetyFilter for violation correlation
- Timing measurement using System.nanoTime() for microsecond precision

---

## Objective

Implement Druid SqlAuditFilter extending FilterAdapter to capture SQL execution results, timing, and errors for audit logging, integrating with Druid's filter chain to intercept statement execution completion, and writing audit events via AuditLogWriter without impacting Druid's connection pooling or monitoring features.

---

## Deliverables

### Primary Outputs
1. **DruidSqlAuditFilter** class extending `FilterAdapter`
2. Method overrides: `statement_execute()`, `statement_executeQuery()`, `statement_executeUpdate()`
3. **Error handling** capturing SQLException details
4. **Timing measurement** using nanoTime for microsecond precision
5. **Filter ordering** configuration ensuring audit runs after StatFilter
6. **Integration tests** with Druid datasource

### Test Requirements
- Comprehensive unit tests (25+ tests)
- Integration tests with real Druid datasource and H2 database
- Filter ordering verification
- ThreadLocal coordination with safety filter
- Performance validation (<1% overhead)

---

## Implementation Steps

### Step 1: Filter Implementation (TDD)

**Test First:**
```java
// DruidSqlAuditFilterTest.java
@Test
void testStatementExecute_shouldLogAudit() {
    // Execute statement via filter
    // Verify AuditEvent written with timing and result
}

@Test
void testExecuteQuery_shouldCaptureRowCount() {
    // Execute SELECT
    // Verify result set size captured (if available)
}

@Test
void testExecuteUpdate_shouldCaptureAffectedRows() {
    // Execute UPDATE
    // Verify rows affected from result
}

@Test
void testExecutionError_shouldLogException() {
    // Execute SQL causing SQLException
    // Verify exception captured in errorMessage
    // Verify exception re-thrown
}

@Test
void testExecutionTiming_shouldBeMicrosecondPrecise() {
    // Execute statement
    // Verify timing accurate to microseconds
}

@Test
void testFilterOrdering_shouldRunAfterStat() {
    // Configure datasource with StatFilter + audit filter
    // Verify audit filter executes after StatFilter
}
```

**Implementation:**
Create `DruidSqlAuditFilter.java` in `com.footstone.sqlguard.interceptor.druid`:

```java
package com.footstone.sqlguard.interceptor.druid;

import com.alibaba.druid.filter.FilterAdapter;
import com.alibaba.druid.proxy.jdbc.ConnectionProxy;
import com.alibaba.druid.proxy.jdbc.ResultSetProxy;
import com.alibaba.druid.proxy.jdbc.StatementProxy;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogException;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Instant;

/**
 * Druid filter for post-execution audit logging.
 *
 * <p>This filter captures SQL execution results, timing, and errors after
 * statement execution completes. It complements DruidSqlSafetyFilter which
 * performs pre-execution validation.</p>
 *
 * <p><strong>Filter Ordering:</strong></p>
 * <ul>
 *   <li>Order 2: DruidSqlSafetyFilter (pre-execution validation)</li>
 *   <li>Order 9: StatFilter (Druid SQL statistics)</li>
 *   <li>Order 10: DruidSqlAuditFilter (post-execution audit) ← This filter</li>
 * </ul>
 *
 * <p><strong>ThreadLocal Coordination:</strong></p>
 * <p>Retrieves pre-execution ValidationResult from DruidSqlSafetyFilter via
 * shared ThreadLocal for violation correlation in audit events.</p>
 *
 * <p><strong>Performance:</strong> <1% overhead on SQL execution</p>
 */
public class DruidSqlAuditFilter extends FilterAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DruidSqlAuditFilter.class);
    
    private final AuditLogWriter auditLogWriter;
    
    /**
     * Constructs DruidSqlAuditFilter with specified AuditLogWriter.
     *
     * @param auditLogWriter the audit log writer (must not be null)
     */
    public DruidSqlAuditFilter(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = Objects.requireNonNull(auditLogWriter, "auditLogWriter must not be null");
    }
    
    @Override
    public boolean statement_execute(StatementProxy statement, String sql) throws SQLException {
        long startNano = System.nanoTime();
        boolean hasResultSet = false;
        SQLException exception = null;
        
        try {
            // Execute statement and capture result
            hasResultSet = super.statement_execute(statement, sql);
            return hasResultSet;
            
        } catch (SQLException e) {
            exception = e;
            throw e;
            
        } finally {
            // Write audit event (success or failure)
            long durationMs = (System.nanoTime() - startNano) / 1_000_000;
            writeAuditEvent(statement, sql, durationMs, hasResultSet, exception);
        }
    }
    
    @Override
    public ResultSetProxy statement_executeQuery(StatementProxy statement, String sql) throws SQLException {
        long startNano = System.nanoTime();
        ResultSetProxy resultSet = null;
        SQLException exception = null;
        
        try {
            // Execute query and capture result
            resultSet = super.statement_executeQuery(statement, sql);
            return resultSet;
            
        } catch (SQLException e) {
            exception = e;
            throw e;
            
        } finally {
            long durationMs = (System.nanoTime() - startNano) / 1_000_000;
            writeAuditEvent(statement, sql, durationMs, true, exception);
        }
    }
    
    @Override
    public int statement_executeUpdate(StatementProxy statement, String sql) throws SQLException {
        long startNano = System.nanoTime();
        int updateCount = 0;
        SQLException exception = null;
        
        try {
            // Execute update and capture affected rows
            updateCount = super.statement_executeUpdate(statement, sql);
            return updateCount;
            
        } catch (SQLException e) {
            exception = e;
            throw e;
            
        } finally {
            long durationMs = (System.nanoTime() - startNano) / 1_000_000;
            writeAuditEventWithUpdateCount(statement, sql, durationMs, updateCount, exception);
        }
    }
    
    /**
     * Writes audit event for statement execution.
     */
    private void writeAuditEvent(StatementProxy statement, String sql, long durationMs,
                                  boolean hasResultSet, SQLException exception) {
        try {
            // Extract datasource name
            String datasource = extractDatasourceName(statement);
            
            // Determine SQL type
            SqlCommandType sqlType = determineSqlType(sql);
            
            // Get rows affected (for DML)
            int rowsAffected = -1;
            if (!hasResultSet && exception == null) {
                try {
                    rowsAffected = statement.getUpdateCount();
                } catch (Exception e) {
                    logger.debug("Failed to get update count: {}", e.getMessage());
                }
            }
            
            // Retrieve pre-execution validation result from ThreadLocal
            ValidationResult validationResult = DruidSqlSafetyFilter.getValidationResult();
            
            // Build audit event
            AuditEvent.Builder eventBuilder = AuditEvent.builder()
                .sql(sql)
                .sqlType(sqlType)
                .mapperId("druid-jdbc")
                .datasource(datasource)
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
            
            AuditEvent event = eventBuilder.build();
            
            // Write audit log
            auditLogWriter.writeAuditLog(event);
            
        } catch (AuditLogException e) {
            logger.error("Failed to write audit log for SQL: {}", sql, e);
            // Don't throw - audit failure should not break SQL execution
        }
    }
    
    /**
     * Writes audit event with explicit update count.
     */
    private void writeAuditEventWithUpdateCount(StatementProxy statement, String sql,
                                                 long durationMs, int updateCount,
                                                 SQLException exception) {
        try {
            String datasource = extractDatasourceName(statement);
            SqlCommandType sqlType = determineSqlType(sql);
            ValidationResult validationResult = DruidSqlSafetyFilter.getValidationResult();
            
            AuditEvent.Builder eventBuilder = AuditEvent.builder()
                .sql(sql)
                .sqlType(sqlType)
                .mapperId("druid-jdbc")
                .datasource(datasource)
                .timestamp(Instant.now())
                .executionTimeMs(durationMs)
                .rowsAffected(exception == null ? updateCount : -1);
            
            if (exception != null) {
                eventBuilder.errorMessage(exception.getMessage());
            }
            
            if (validationResult != null && !validationResult.isPassed()) {
                eventBuilder.violations(validationResult);
            }
            
            auditLogWriter.writeAuditLog(eventBuilder.build());
            
        } catch (AuditLogException e) {
            logger.error("Failed to write audit log for SQL: {}", sql, e);
        }
    }
    
    /**
     * Extracts datasource name from statement proxy.
     */
    private String extractDatasourceName(StatementProxy statement) {
        try {
            ConnectionProxy connection = statement.getConnectionProxy();
            if (connection != null && connection.getDirectDataSource() != null) {
                return connection.getDirectDataSource().getName();
            }
        } catch (Exception e) {
            logger.debug("Failed to extract datasource name: {}", e.getMessage());
        }
        return "unknown";
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

### Step 2: Filter Registration and Ordering

**Implementation:**
Update `DruidSqlSafetyFilterConfiguration` to register both filters:

```java
public class DruidDataSourceConfiguration {
    
    public DataSource configureDruidDataSource(AuditLogWriter auditLogWriter) {
        DruidDataSource dataSource = new DruidDataSource();
        
        // Configure filters
        List<Filter> filters = new ArrayList<>();
        
        // Safety filter (order=2, pre-execution)
        DruidSqlSafetyFilter safetyFilter = new DruidSqlSafetyFilter(validatorEngine);
        filters.add(safetyFilter);
        
        // Audit filter (order=10, post-execution)
        DruidSqlAuditFilter auditFilter = new DruidSqlAuditFilter(auditLogWriter);
        filters.add(auditFilter);
        
        dataSource.setProxyFilters(filters);
        
        return dataSource;
    }
}
```

### Step 3: Integration Testing

**Test Implementation:**
```java
// DruidSqlAuditFilterIntegrationTest.java
@Test
void testSuccessfulSelect_shouldLogAudit() {
    // Execute SELECT via Druid datasource
    // Verify audit event written
    // Verify timing, datasource, SQL type captured
}

@Test
void testSuccessfulUpdate_shouldCaptureRowsAffected() {
    // Execute UPDATE affecting 5 rows
    // Verify rowsAffected=5 in audit event
}

@Test
void testFailedExecution_shouldCaptureError() {
    // Execute invalid SQL
    // Verify errorMessage captured
    // Verify exception re-thrown
}

@Test
void testPreExecutionViolations_shouldCorrelate() {
    // Execute SQL violating safety rules (no WHERE clause)
    // Verify audit event includes violations from DruidSqlSafetyFilter
}

@Test
void testFilterOrdering_shouldRunAfterStat() {
    // Configure datasource with StatFilter + safety + audit
    // Execute SQL
    // Verify execution order via logs
}
```

---

## Acceptance Criteria

- [ ] All tests pass: `mvn test -pl sql-guard-jdbc`
- [ ] Code coverage > 80% for DruidSqlAuditFilter
- [ ] Integration test: successful SELECT logged
- [ ] Integration test: successful UPDATE with rows affected
- [ ] Integration test: failed execution with error captured
- [ ] Integration test: pre-execution violations correlated
- [ ] Integration test: filter ordering verified
- [ ] Performance: <1% overhead on SQL execution
- [ ] ThreadLocal coordination with DruidSqlSafetyFilter working
- [ ] Timing measurement accurate to milliseconds

---

## References

### Implementation Plan
- `.apm/Implementation_Plan.md` - Task 8.3 (lines 845-865)

### Design Document
- `.apm/SQL_Audit_Platform_Design.md` - Section 8.3 (Druid Integration)

### Dependencies
- Task 8.1: `AuditLogWriter`, `AuditEvent`
- Task 4.3: `DruidSqlSafetyFilter` pattern reference

### Existing Code
- `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/druid/DruidSqlSafetyFilter.java`

---

## Testing Strategy

### Unit Tests (25+ tests)
1. **Statement Execution:**
   - execute() with result set
   - execute() with update count
   - executeQuery() success
   - executeUpdate() success
   - Timing measurement accuracy

2. **Error Handling:**
   - SQLException capture
   - Exception re-throw
   - Partial audit on error
   - Audit failure doesn't break execution

3. **Context Extraction:**
   - Datasource name extraction
   - SQL type determination
   - Rows affected extraction
   - Update count handling

4. **ThreadLocal Coordination:**
   - Retrieve ValidationResult from safety filter
   - Handle missing ValidationResult
   - Violations included in audit event

### Integration Tests (10+ tests)
1. Real Druid datasource with H2
2. All SQL types (SELECT, UPDATE, DELETE, INSERT)
3. Filter ordering verification
4. StatFilter compatibility
5. Pre-execution violation correlation

---

## Common Issues and Solutions

### Issue 1: Filter Not Executing

**Symptoms:** Audit events not written

**Solutions:**
1. Verify filter registered in datasource.setProxyFilters()
2. Check filter ordering (audit after StatFilter)
3. Verify AuditLogWriter injected correctly
4. Check logs for AuditLogException

### Issue 2: Timing Inaccurate

**Symptoms:** executionTimeMs always 0 or very large

**Solutions:**
1. Verify System.nanoTime() used (not currentTimeMillis)
2. Check division by 1_000_000 for ms conversion
3. Verify timing measured around super.statement_execute()

### Issue 3: Rows Affected Always -1

**Symptoms:** rowsAffected=-1 for UPDATE/DELETE

**Solutions:**
1. Verify statement.getUpdateCount() called
2. Check hasResultSet flag (false for DML)
3. Verify executeUpdate() override captures updateCount

---

## Next Steps

After completing this task:
1. Update memory file: `.apm/Memory/Phase_08_Audit_Output/Task_8_3_Druid.md`
2. Verify integration with Task 8.2 (LogbackAuditWriter)
3. Test with real application workload
4. Proceed to Task 8.3.5 (HikariCP) or other Batch 2 tasks

---

**Good luck! Remember to follow TDD and test filter ordering carefully.**

