# Task Assignment: 8.3.5 - HikariCP SqlAuditProxyFactory Implementation

**Generated:** 2025-12-17  
**Agent Type:** Agent_Audit_Infrastructure  
**Phase:** Phase 8 - Audit Log Output Layer  
**Dependencies:** Task 8.1 ✅ (AuditLogWriter), Task 4.4 (HikariSqlSafetyProxyFactory pattern)

---

## Context

You are implementing **HikariCP SqlAuditProxyFactory** for audit logging with HikariCP connection pool (Spring Boot 2.x+ default). This task provides audit capabilities for HikariCP using JDK dynamic proxy pattern, complementing the existing HikariSqlSafetyProxyFactory (pre-execution validation).

**Key Design Decisions:**
- JDK dynamic proxy for Connection/Statement wrapping
- Post-execution interception captures results after execute*() methods
- ThreadLocal coordination with HikariSqlSafetyProxyFactory for violation correlation
- Timing measurement using System.nanoTime() for microsecond precision
- Batch execution support with aggregated row counts

---

## Objective

为HikariCP连接池提供审计日志采集能力，复用HikariSqlSafetyProxyFactory模式实现Statement代理，在SQL执行完成后捕获执行结果、耗时和错误信息写入审计日志。

---

## Deliverables

### Primary Outputs
1. **HikariSqlAuditProxyFactory** class wrapping Connection with audit proxy
2. **StatementAuditInvocationHandler** for Statement/PreparedStatement proxy
3. **Execution timing measurement** using nanoTime
4. **Error handling** capturing SQLException details
5. **ThreadLocal coordination** with HikariSqlSafetyProxyFactory
6. **Integration tests** with HikariCP datasource

### Test Requirements
- Comprehensive unit tests (20+ tests)
- Integration tests with HikariCP and H2 database
- ThreadLocal coordination verification
- Batch execution support
- Performance validation (<5% overhead)

---

## Implementation Steps

### Step 1: Proxy Factory Implementation (TDD)

**Test First:**
```java
// HikariSqlAuditProxyFactoryTest.java
@Test
void testConnectionProxy_shouldWrapStatements() {
    // Create proxy connection
    // Call createStatement()
    // Verify returned Statement is proxy
}

@Test
void testStatementProxy_shouldCaptureExecuteResult() {
    // Execute statement via proxy
    // Verify audit event written with timing and result
}

@Test
void testPreparedStatementProxy_shouldCaptureUpdateCount() {
    // Execute PreparedStatement.executeUpdate()
    // Verify rows affected captured
}

@Test
void testExecutionTiming_shouldBeMicrosecondPrecise() {
    // Execute statement
    // Verify timing accurate to microseconds
}

@Test
void testErrorCapture_shouldLogSQLException() {
    // Execute SQL causing exception
    // Verify exception captured in audit event
    // Verify exception re-thrown
}
```

**Implementation:**
Create `HikariSqlAuditProxyFactory.java` in `com.footstone.sqlguard.interceptor.hikari`:

```java
package com.footstone.sqlguard.interceptor.hikari;

import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;

/**
 * Proxy factory for HikariCP audit logging.
 *
 * <p>Wraps HikariCP connections with JDK dynamic proxy to intercept
 * Statement execution and capture post-execution audit data.</p>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>{@code
 * HikariDataSource dataSource = new HikariDataSource(config);
 * HikariSqlAuditProxyFactory auditFactory = new HikariSqlAuditProxyFactory(auditLogWriter);
 * Connection conn = auditFactory.wrapConnection(dataSource.getConnection());
 * }</pre>
 *
 * <p><strong>ThreadLocal Coordination:</strong></p>
 * <p>Retrieves pre-execution ValidationResult from HikariSqlSafetyProxyFactory
 * via shared ThreadLocal for violation correlation.</p>
 *
 * <p><strong>Performance:</strong> <5% overhead on SQL execution</p>
 */
public class HikariSqlAuditProxyFactory {

    private static final Logger logger = LoggerFactory.getLogger(HikariSqlAuditProxyFactory.class);
    
    private final AuditLogWriter auditLogWriter;
    
    /**
     * Constructs HikariSqlAuditProxyFactory with specified AuditLogWriter.
     *
     * @param auditLogWriter the audit log writer (must not be null)
     */
    public HikariSqlAuditProxyFactory(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = Objects.requireNonNull(auditLogWriter, "auditLogWriter must not be null");
    }
    
    /**
     * Wraps connection with audit proxy.
     *
     * @param connection the original connection
     * @return proxied connection that intercepts statement creation
     */
    public Connection wrapConnection(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
            connection.getClass().getClassLoader(),
            new Class<?>[]{Connection.class},
            new ConnectionAuditHandler(connection, auditLogWriter)
        );
    }
    
    /**
     * InvocationHandler for Connection proxy.
     * Intercepts createStatement/prepareStatement to wrap with audit proxy.
     */
    private static class ConnectionAuditHandler implements InvocationHandler {
        
        private final Connection target;
        private final AuditLogWriter auditLogWriter;
        
        ConnectionAuditHandler(Connection target, AuditLogWriter auditLogWriter) {
            this.target = target;
            this.auditLogWriter = auditLogWriter;
        }
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            
            // Intercept statement creation methods
            if ("createStatement".equals(methodName)) {
                Statement stmt = (Statement) method.invoke(target, args);
                return wrapStatement(stmt, null);
                
            } else if ("prepareStatement".equals(methodName)) {
                PreparedStatement pstmt = (PreparedStatement) method.invoke(target, args);
                String sql = (args != null && args.length > 0) ? (String) args[0] : null;
                return wrapPreparedStatement(pstmt, sql);
                
            } else if ("prepareCall".equals(methodName)) {
                // CallableStatement support (optional)
                return method.invoke(target, args);
            }
            
            // Delegate other methods
            return method.invoke(target, args);
        }
        
        /**
         * Wraps Statement with audit proxy.
         */
        private Statement wrapStatement(Statement stmt, String sql) {
            return (Statement) Proxy.newProxyInstance(
                stmt.getClass().getClassLoader(),
                new Class<?>[]{Statement.class},
                new StatementAuditHandler(stmt, sql, auditLogWriter)
            );
        }
        
        /**
         * Wraps PreparedStatement with audit proxy.
         */
        private PreparedStatement wrapPreparedStatement(PreparedStatement pstmt, String sql) {
            return (PreparedStatement) Proxy.newProxyInstance(
                pstmt.getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                new StatementAuditHandler(pstmt, sql, auditLogWriter)
            );
        }
    }
    
    /**
     * InvocationHandler for Statement/PreparedStatement proxy.
     * Intercepts execute* methods to capture post-execution audit data.
     */
    private static class StatementAuditHandler implements InvocationHandler {
        
        private final Statement target;
        private final String sql;
        private final AuditLogWriter auditLogWriter;
        
        StatementAuditHandler(Statement target, String sql, AuditLogWriter auditLogWriter) {
            this.target = target;
            this.sql = sql;
            this.auditLogWriter = auditLogWriter;
        }
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            
            // Intercept execute* methods
            if (methodName.startsWith("execute")) {
                return interceptExecute(method, args);
            }
            
            // Delegate other methods
            return method.invoke(target, args);
        }
        
        /**
         * Intercepts execute* methods and writes audit event.
         */
        private Object interceptExecute(Method method, Object[] args) throws Throwable {
            long startNano = System.nanoTime();
            Object result = null;
            Throwable exception = null;
            
            try {
                // Execute original method
                result = method.invoke(target, args);
                return result;
                
            } catch (Throwable t) {
                exception = t;
                throw t;
                
            } finally {
                // Write audit event
                long durationMs = (System.nanoTime() - startNano) / 1_000_000;
                writeAuditEvent(method.getName(), args, result, durationMs, exception);
            }
        }
        
        /**
         * Writes audit event based on execution result.
         */
        private void writeAuditEvent(String methodName, Object[] args, Object result,
                                      long durationMs, Throwable exception) {
            try {
                // Determine SQL (from PreparedStatement or Statement.execute(sql))
                String executedSql = sql;
                if (executedSql == null && args != null && args.length > 0 && args[0] instanceof String) {
                    executedSql = (String) args[0];
                }
                
                if (executedSql == null) {
                    return; // No SQL to audit
                }
                
                // Determine SQL type
                SqlCommandType sqlType = determineSqlType(executedSql);
                
                // Extract rows affected
                int rowsAffected = extractRowsAffected(methodName, result, exception);
                
                // Retrieve pre-execution validation result from ThreadLocal
                ValidationResult validationResult = HikariSqlSafetyProxyFactory.getValidationResult();
                
                // Build audit event
                AuditEvent.Builder eventBuilder = AuditEvent.builder()
                    .sql(executedSql)
                    .sqlType(sqlType)
                    .mapperId("hikari-jdbc")
                    .timestamp(Instant.now())
                    .executionTimeMs(durationMs)
                    .rowsAffected(rowsAffected);
                
                // Add error message if exception occurred
                if (exception != null) {
                    Throwable cause = exception.getCause();
                    String errorMsg = (cause != null) ? cause.getMessage() : exception.getMessage();
                    eventBuilder.errorMessage(errorMsg);
                }
                
                // Add pre-execution violations if available
                if (validationResult != null && !validationResult.isPassed()) {
                    eventBuilder.violations(validationResult);
                }
                
                // Write audit log
                auditLogWriter.writeAuditLog(eventBuilder.build());
                
            } catch (Exception e) {
                logger.error("Failed to write audit log", e);
                // Don't throw - audit failure should not break SQL execution
            }
        }
        
        /**
         * Extracts rows affected from execution result.
         */
        private int extractRowsAffected(String methodName, Object result, Throwable exception) {
            if (exception != null) {
                return -1; // Error case
            }
            
            try {
                if ("executeUpdate".equals(methodName)) {
                    // executeUpdate() returns int (rows affected)
                    return (result instanceof Integer) ? (Integer) result : -1;
                    
                } else if ("executeBatch".equals(methodName)) {
                    // executeBatch() returns int[] (per-statement rows)
                    if (result instanceof int[]) {
                        int[] batchResults = (int[]) result;
                        return Arrays.stream(batchResults).sum();
                    }
                    
                } else if ("execute".equals(methodName)) {
                    // execute() returns boolean, need to call getUpdateCount()
                    if (result instanceof Boolean && !(Boolean) result) {
                        // false = update count available
                        return target.getUpdateCount();
                    }
                }
                
            } catch (Exception e) {
                logger.debug("Failed to extract rows affected: {}", e.getMessage());
            }
            
            return -1; // Default for SELECT or unknown
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
}
```

### Step 2: ThreadLocal Coordination with Safety Proxy

**Implementation:**
Update `HikariSqlSafetyProxyFactory` to expose ValidationResult via ThreadLocal:

```java
public class HikariSqlSafetyProxyFactory {
    
    private static final ThreadLocal<ValidationResult> VALIDATION_RESULT = new ThreadLocal<>();
    
    /**
     * Gets pre-execution validation result for current thread.
     * Used by HikariSqlAuditProxyFactory for violation correlation.
     */
    public static ValidationResult getValidationResult() {
        return VALIDATION_RESULT.get();
    }
    
    // In StatementSafetyHandler.interceptExecute():
    try {
        ValidationResult result = validator.validate(sqlContext);
        VALIDATION_RESULT.set(result);
        
        // ... validation logic ...
        
    } finally {
        VALIDATION_RESULT.remove(); // Clean up
    }
}
```

### Step 3: Integration Testing

**Test Implementation:**
```java
// HikariSqlAuditIntegrationTest.java
@Test
void testSuccessfulSelect_shouldLogAudit() {
    // Configure HikariCP with audit proxy
    // Execute SELECT
    // Verify audit event written
}

@Test
void testSuccessfulUpdate_shouldCaptureRowsAffected() {
    // Execute UPDATE affecting 5 rows
    // Verify rowsAffected=5 in audit event
}

@Test
void testPreparedStatement_shouldCaptureParameters() {
    // Execute PreparedStatement with parameters
    // Verify SQL and timing captured
}

@Test
void testBatchExecution_shouldAggregateResults() {
    // Execute executeBatch() with 3 statements
    // Verify total rows affected aggregated
}

@Test
void testPreExecutionViolations_shouldCorrelate() {
    // Execute SQL violating safety rules
    // Verify audit event includes violations from safety proxy
}

@Test
void testPerformance_shouldBeLowOverhead() {
    // Execute 1000 SQL statements with audit proxy
    // Measure overhead vs raw JDBC
    // Verify <5% overhead
}
```

---

## Acceptance Criteria

- [ ] All tests pass: `mvn test -pl sql-guard-jdbc`
- [ ] Code coverage > 80% for HikariSqlAuditProxyFactory
- [ ] Integration test: successful SELECT logged
- [ ] Integration test: successful UPDATE with rows affected
- [ ] Integration test: PreparedStatement support
- [ ] Integration test: batch execution aggregation
- [ ] Integration test: pre-execution violations correlated
- [ ] Performance: <5% overhead on SQL execution
- [ ] ThreadLocal coordination with HikariSqlSafetyProxyFactory working
- [ ] Batch execution support validated

---

## References

### Implementation Plan
- `.apm/Implementation_Plan.md` - Task 8.3.5 (lines 867-886)

### Design Document
- `.apm/SQL_Audit_Platform_Design.md` - Section 8.3.5 (HikariCP Integration)

### Dependencies
- Task 8.1: `AuditLogWriter`, `AuditEvent`
- Task 4.4: `HikariSqlSafetyProxyFactory` pattern reference

### Existing Code
- `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/hikari/HikariSqlSafetyProxyFactory.java`

---

## Testing Strategy

### Unit Tests (20+ tests)
1. **Proxy Creation:**
   - Connection wrapping
   - Statement wrapping
   - PreparedStatement wrapping

2. **Execution Interception:**
   - execute() with result set
   - executeQuery() success
   - executeUpdate() with rows affected
   - executeBatch() aggregation

3. **Error Handling:**
   - SQLException capture
   - Exception re-throw
   - Audit on error

4. **ThreadLocal Coordination:**
   - Retrieve ValidationResult
   - Handle missing ValidationResult
   - Violations included in audit event

### Integration Tests (8+ tests)
1. Real HikariCP datasource with H2
2. All SQL types (SELECT, UPDATE, DELETE, INSERT)
3. PreparedStatement with parameters
4. Batch execution
5. Pre-execution violation correlation
6. Performance overhead measurement

---

## Next Steps

After completing this task:
1. Update memory file: `.apm/Memory/Phase_08_Audit_Output/Task_8_3_5_HikariCP.md`
2. Verify integration with Task 8.2 (LogbackAuditWriter)
3. Test with Spring Boot application
4. Proceed to Task 8.4 (MyBatis) or other Batch 2 tasks

---

**Good luck! Remember HikariCP is Spring Boot's default - this is critical infrastructure.**













