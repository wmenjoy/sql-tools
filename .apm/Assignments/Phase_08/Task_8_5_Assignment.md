# Task Assignment: 8.5 - MyBatis-Plus InnerAuditInterceptor Implementation

**Generated:** 2025-12-17  
**Agent Type:** Agent_Audit_Infrastructure  
**Phase:** Phase 8 - Audit Log Output Layer  
**Dependencies:** Task 8.1 ✅ (AuditLogWriter), Task 4.2 (MpSqlSafetyInnerInterceptor pattern)

---

## Context

You are implementing the **MyBatis-Plus InnerAuditInterceptor** for audit logging with MyBatis-Plus features. This interceptor captures IPage pagination results, QueryWrapper-generated SQL execution metrics, and MyBatis-Plus plugin chain context.

**Key Design Decisions:**
- InnerInterceptor with dual-phase interception (before* + after*)
- IPage pagination metadata capture (total records, current page, page size)
- QueryWrapper SQL correlation tracking wrapper-generated queries
- Plugin chain ordering: PaginationInnerInterceptor → MpSqlSafetyInnerInterceptor → MpSqlAuditInnerInterceptor
- ThreadLocal pattern for before/after phase correlation

---

## Objective

Implement MyBatis-Plus InnerInterceptor for audit logging capturing IPage pagination results, QueryWrapper-generated SQL execution metrics, and MyBatis-Plus plugin chain context, coordinating with MpSqlSafetyInnerInterceptor for violation correlation, and integrating with PaginationInnerInterceptor for accurate page metadata.

---

## Deliverables

### Primary Outputs
1. **MpSqlAuditInnerInterceptor** class implementing `InnerInterceptor`
2. **beforeQuery()** and **beforeUpdate()** methods for pre-execution context capture
3. **afterQuery()** and **afterUpdate()** methods for post-execution result capture
4. **IPage result extraction** capturing pagination metadata
5. **QueryWrapper correlation** tracking wrapper-generated queries
6. **Plugin chain ordering** ensuring audit runs after pagination and safety
7. **Integration tests** with IPage pagination and QueryWrapper

### Test Requirements
- Comprehensive unit tests (25+ tests)
- Integration tests with IPage pagination
- QueryWrapper usage tests
- Plugin chain ordering verification
- ThreadLocal coordination with safety interceptor

---

## Implementation Steps

### Step 1: InnerInterceptor Implementation (TDD)

**Test First:**
```java
// MpSqlAuditInnerInterceptorTest.java
@Test
void testBeforeQueryAfterQuery_shouldLogAudit() {
    // Execute query via interceptor
    // Verify beforeQuery() and afterQuery() called
    // Verify audit event written
}

@Test
void testBeforeUpdateAfterUpdate_shouldLogAudit() {
    // Execute update via interceptor
    // Verify audit event written with rows affected
}

@Test
void testIPageExtraction_shouldCaptureMetadata() {
    // Execute IPage query
    // Verify total/current/size captured in audit event
}

@Test
void testQueryWrapperCorrelation_shouldIdentifyWrapper() {
    // Execute QueryWrapper-generated SQL
    // Verify wrapper flag in audit event
}

@Test
void testPluginOrdering_shouldRunAfterPagination() {
    // Configure full plugin chain
    // Verify audit runs after PaginationInnerInterceptor
}
```

**Implementation:**
Create `MpSqlAuditInnerInterceptor.java` in `com.footstone.sqlguard.interceptor.mp`:

```java
package com.footstone.sqlguard.interceptor.mp;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MyBatis-Plus InnerInterceptor for post-execution audit logging.
 *
 * <p>This interceptor captures SQL execution results, timing, IPage pagination
 * metadata, and QueryWrapper usage for audit logging. It uses dual-phase
 * interception (before* + after*) to measure timing and capture results.</p>
 *
 * <p><strong>Plugin Chain Ordering:</strong></p>
 * <ul>
 *   <li>Order 1: PaginationInnerInterceptor (adds LIMIT clause)</li>
 *   <li>Order 2: MpSqlSafetyInnerInterceptor (pre-execution validation)</li>
 *   <li>Order 3: MpSqlAuditInnerInterceptor (post-execution audit) ← This interceptor</li>
 * </ul>
 *
 * <p><strong>IPage Metadata Capture:</strong></p>
 * <p>When IPage parameter detected, captures pagination metadata:</p>
 * <ul>
 *   <li>pagination.total - Total records from count query</li>
 *   <li>pagination.current - Current page number</li>
 *   <li>pagination.size - Page size</li>
 * </ul>
 *
 * <p><strong>QueryWrapper Detection:</strong></p>
 * <p>Sets flag when QueryWrapper/LambdaQueryWrapper detected in parameters.</p>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>{@code
 * MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
 * interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
 * interceptor.addInnerInterceptor(new MpSqlSafetyInnerInterceptor(validator));
 * interceptor.addInnerInterceptor(new MpSqlAuditInnerInterceptor(auditLogWriter));
 * }</pre>
 */
public class MpSqlAuditInnerInterceptor implements InnerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(MpSqlAuditInnerInterceptor.class);
    
    private final AuditLogWriter auditLogWriter;
    
    /**
     * ThreadLocal storage for before-phase context.
     */
    private static final ThreadLocal<AuditContext> AUDIT_CONTEXT = new ThreadLocal<>();
    
    /**
     * Constructs MpSqlAuditInnerInterceptor with specified AuditLogWriter.
     *
     * @param auditLogWriter the audit log writer (must not be null)
     */
    public MpSqlAuditInnerInterceptor(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = Objects.requireNonNull(auditLogWriter, "auditLogWriter must not be null");
    }
    
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        // Capture before-phase context
        AuditContext context = new AuditContext();
        context.startNano = System.nanoTime();
        context.sql = boundSql.getSql();
        context.mapperId = ms.getId();
        context.sqlType = SqlCommandType.SELECT;
        
        // Detect IPage parameter
        context.iPage = extractIPage(parameter);
        
        // Detect QueryWrapper
        context.isQueryWrapper = isQueryWrapper(parameter);
        
        AUDIT_CONTEXT.set(context);
    }
    
    @Override
    public void afterQuery(Executor executor, MappedStatement ms, Object parameter,
                           RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql,
                           List<?> result) throws SQLException {
        try {
            AuditContext context = AUDIT_CONTEXT.get();
            if (context == null) {
                logger.warn("AuditContext not found in ThreadLocal for query: {}", ms.getId());
                return;
            }
            
            // Calculate execution time
            long durationMs = (System.nanoTime() - context.startNano) / 1_000_000;
            
            // Extract rows affected (result set size)
            int rowsAffected = (result != null) ? result.size() : 0;
            
            // Retrieve pre-execution validation result
            ValidationResult validationResult = MpSqlSafetyInnerInterceptor.getValidationResult();
            
            // Build audit event
            AuditEvent.Builder eventBuilder = AuditEvent.builder()
                .sql(context.sql)
                .sqlType(context.sqlType)
                .mapperId(context.mapperId)
                .timestamp(Instant.now())
                .executionTimeMs(durationMs)
                .rowsAffected(rowsAffected);
            
            // Add IPage metadata if available
            if (context.iPage != null) {
                Map<String, Object> details = new HashMap<>();
                details.put("pagination.total", context.iPage.getTotal());
                details.put("pagination.current", context.iPage.getCurrent());
                details.put("pagination.size", context.iPage.getSize());
                details.put("pagination.pages", context.iPage.getPages());
                eventBuilder.params(details);
            }
            
            // Add QueryWrapper flag if detected
            if (context.isQueryWrapper) {
                Map<String, Object> details = eventBuilder.build().getParams();
                if (details == null) {
                    details = new HashMap<>();
                }
                details.put("queryWrapper", true);
                eventBuilder.params(details);
            }
            
            // Add pre-execution violations if available
            if (validationResult != null && !validationResult.isPassed()) {
                eventBuilder.violations(validationResult);
            }
            
            // Write audit log
            auditLogWriter.writeAuditLog(eventBuilder.build());
            
        } catch (Exception e) {
            logger.error("Failed to write audit log for query: {}", ms.getId(), e);
            // Don't throw - audit failure should not break SQL execution
            
        } finally {
            AUDIT_CONTEXT.remove(); // Clean up ThreadLocal
        }
    }
    
    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter)
            throws SQLException {
        // Capture before-phase context
        AuditContext context = new AuditContext();
        context.startNano = System.nanoTime();
        
        BoundSql boundSql = ms.getBoundSql(parameter);
        context.sql = boundSql.getSql();
        context.mapperId = ms.getId();
        context.sqlType = SqlCommandType.valueOf(ms.getSqlCommandType().name());
        
        // Detect QueryWrapper
        context.isQueryWrapper = isQueryWrapper(parameter);
        
        AUDIT_CONTEXT.set(context);
    }
    
    @Override
    public void afterUpdate(Executor executor, MappedStatement ms, Object parameter, int result)
            throws SQLException {
        try {
            AuditContext context = AUDIT_CONTEXT.get();
            if (context == null) {
                logger.warn("AuditContext not found in ThreadLocal for update: {}", ms.getId());
                return;
            }
            
            // Calculate execution time
            long durationMs = (System.nanoTime() - context.startNano) / 1_000_000;
            
            // Retrieve pre-execution validation result
            ValidationResult validationResult = MpSqlSafetyInnerInterceptor.getValidationResult();
            
            // Build audit event
            AuditEvent.Builder eventBuilder = AuditEvent.builder()
                .sql(context.sql)
                .sqlType(context.sqlType)
                .mapperId(context.mapperId)
                .timestamp(Instant.now())
                .executionTimeMs(durationMs)
                .rowsAffected(result);
            
            // Add QueryWrapper flag if detected
            if (context.isQueryWrapper) {
                Map<String, Object> details = new HashMap<>();
                details.put("queryWrapper", true);
                eventBuilder.params(details);
            }
            
            // Add pre-execution violations if available
            if (validationResult != null && !validationResult.isPassed()) {
                eventBuilder.violations(validationResult);
            }
            
            // Write audit log
            auditLogWriter.writeAuditLog(eventBuilder.build());
            
        } catch (Exception e) {
            logger.error("Failed to write audit log for update: {}", ms.getId(), e);
            
        } finally {
            AUDIT_CONTEXT.remove(); // Clean up ThreadLocal
        }
    }
    
    /**
     * Extracts IPage from parameter object.
     */
    private IPage<?> extractIPage(Object parameter) {
        if (parameter instanceof IPage) {
            return (IPage<?>) parameter;
        }
        
        if (parameter instanceof Map) {
            Map<?, ?> paramMap = (Map<?, ?>) parameter;
            for (Object value : paramMap.values()) {
                if (value instanceof IPage) {
                    return (IPage<?>) value;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Checks if parameter contains QueryWrapper.
     */
    private boolean isQueryWrapper(Object parameter) {
        if (parameter instanceof AbstractWrapper) {
            return true;
        }
        
        if (parameter instanceof Map) {
            Map<?, ?> paramMap = (Map<?, ?>) parameter;
            for (Object value : paramMap.values()) {
                if (value instanceof AbstractWrapper) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Context holder for before/after phase correlation.
     */
    private static class AuditContext {
        long startNano;
        String sql;
        String mapperId;
        SqlCommandType sqlType;
        IPage<?> iPage;
        boolean isQueryWrapper;
    }
}
```

### Step 2: Plugin Chain Integration Testing

**Test Implementation:**
```java
// MpPluginChainIntegrationTest.java
@Test
void testFullPluginChain_shouldExecuteInOrder() {
    // Create MybatisPlusInterceptor
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    
    // Add interceptors in order
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
    interceptor.addInnerInterceptor(new MpSqlSafetyInnerInterceptor(validator));
    interceptor.addInnerInterceptor(new MpSqlAuditInnerInterceptor(auditLogWriter));
    
    // Configure SqlSessionFactory
    Configuration config = new Configuration();
    config.addInterceptor(interceptor);
    SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(config);
    
    // Execute IPage query
    IPage<User> page = new Page<>(1, 10);
    List<User> users = mapper.selectPage(page, null);
    
    // Verify execution order:
    // 1. PaginationInnerInterceptor adds LIMIT
    // 2. MpSqlSafetyInnerInterceptor validates
    // 3. MpSqlAuditInnerInterceptor logs audit
    
    // Verify audit event contains:
    // - SQL with LIMIT clause (after pagination)
    // - Pagination metadata (total, current, size)
    // - Pre-execution violations (if any)
}
```

### Step 3: IPage and QueryWrapper Testing

**Test Implementation:**
```java
// MpSqlAuditInnerInterceptorIntegrationTest.java
@Test
void testIPageQuery_shouldCapturePaginationMetadata() {
    // Execute IPage query with page size 20
    IPage<User> page = new Page<>(2, 20);
    mapper.selectPage(page, null);
    
    // Verify audit event contains:
    // - pagination.total (from count query)
    // - pagination.current = 2
    // - pagination.size = 20
}

@Test
void testQueryWrapper_shouldSetWrapperFlag() {
    // Execute QueryWrapper.eq("status", "active")
    QueryWrapper<User> wrapper = new QueryWrapper<>();
    wrapper.eq("status", "active");
    mapper.selectList(wrapper);
    
    // Verify audit event contains:
    // - queryWrapper = true
    // - SQL generated by wrapper
}

@Test
void testLambdaQueryWrapper_shouldDetect() {
    // Execute LambdaQueryWrapper
    LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(User::getStatus, "active");
    mapper.selectList(wrapper);
    
    // Verify wrapper flag set
}

@Test
void testUpdateWrapper_shouldCaptureResults() {
    // Execute UpdateWrapper.set("status", "inactive")
    UpdateWrapper<User> wrapper = new UpdateWrapper<>();
    wrapper.set("status", "inactive").eq("id", 1);
    int rows = mapper.update(null, wrapper);
    
    // Verify audit event contains:
    // - queryWrapper = true
    // - rowsAffected = actual rows updated
}
```

---

## Acceptance Criteria

- [ ] All tests pass: `mvn test -pl sql-guard-mp`
- [ ] Code coverage > 80% for MpSqlAuditInnerInterceptor
- [ ] Integration test: IPage pagination metadata captured
- [ ] Integration test: QueryWrapper flag set correctly
- [ ] Integration test: LambdaQueryWrapper detected
- [ ] Integration test: UpdateWrapper results captured
- [ ] Integration test: plugin chain ordering verified
- [ ] Integration test: pre-execution violations correlated
- [ ] ThreadLocal coordination working correctly
- [ ] Before/after phase correlation validated

---

## References

### Implementation Plan
- `.apm/Implementation_Plan.md` - Task 8.5 (lines 910-931)

### Design Document
- `.apm/SQL_Audit_Platform_Design.md` - Section 8.5 (MyBatis-Plus Integration)

### Dependencies
- Task 8.1: `AuditLogWriter`, `AuditEvent`
- Task 4.2: `MpSqlSafetyInnerInterceptor` pattern reference

### Existing Code
- `sql-guard-mp/src/main/java/com/footstone/sqlguard/interceptor/mp/MpSqlSafetyInnerInterceptor.java`

---

## Testing Strategy

### Unit Tests (25+ tests)
1. **Dual-Phase Interception:**
   - beforeQuery/afterQuery coordination
   - beforeUpdate/afterUpdate coordination
   - ThreadLocal context management

2. **IPage Extraction:**
   - IPage as direct parameter
   - IPage in Map parameter
   - Missing IPage handling

3. **QueryWrapper Detection:**
   - QueryWrapper detection
   - LambdaQueryWrapper detection
   - UpdateWrapper detection
   - Missing wrapper handling

4. **Plugin Chain:**
   - Ordering verification
   - Pagination integration
   - Safety interceptor coordination

### Integration Tests (10+ tests)
1. Real MyBatis-Plus with H2
2. IPage pagination queries
3. QueryWrapper usage
4. UpdateWrapper usage
5. Plugin chain ordering
6. Pre-execution violation correlation

---

## Next Steps

After completing this task:
1. Update memory file: `.apm/Memory/Phase_08_Audit_Output/Task_8_5_MyBatisPlus.md`
2. Verify integration with Task 8.2 (LogbackAuditWriter)
3. Test with real MyBatis-Plus application
4. Proceed to Task 8.6 (P6Spy) or other Batch 2 tasks

---

**Good luck! MyBatis-Plus pagination and QueryWrapper are key features to audit.**











