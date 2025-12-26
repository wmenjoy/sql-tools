package com.footstone.sqlguard.interceptor.mp;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * MyBatis interceptor for post-execution audit logging with MyBatis-Plus features.
 *
 * <p>This interceptor captures SQL execution results, timing, IPage pagination
 * metadata, and QueryWrapper usage for audit logging. It wraps the execution
 * to measure timing and capture results.</p>
 *
 * <p><strong>Intercepted Methods:</strong></p>
 * <ul>
 *   <li>Executor.update(MappedStatement, Object) - INSERT/UPDATE/DELETE</li>
 *   <li>Executor.query(MappedStatement, Object, RowBounds, ResultHandler) - SELECT</li>
 * </ul>
 *
 * <p><strong>Plugin Chain Ordering:</strong></p>
 * <p>This interceptor should be registered in the SqlSessionFactory configuration
 * <strong>after</strong> MpSqlSafetyInnerInterceptor to ensure validation results
 * are available via ThreadLocal.</p>
 *
 * <p><strong>IPage Metadata Capture:</strong></p>
 * <p>When IPage parameter detected, captures pagination metadata:</p>
 * <ul>
 *   <li>pagination.total - Total records from count query</li>
 *   <li>pagination.current - Current page number</li>
 *   <li>pagination.size - Page size</li>
 *   <li>pagination.pages - Total pages</li>
 * </ul>
 *
 * <p><strong>QueryWrapper Detection:</strong></p>
 * <p>Sets flag when QueryWrapper/LambdaQueryWrapper detected in parameters.</p>
 *
 * <p><strong>ThreadLocal Coordination:</strong></p>
 * <p>Retrieves pre-execution ValidationResult from MpSqlSafetyInnerInterceptor via
 * shared ThreadLocal.</p>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>{@code
 * // Configure MybatisPlusInterceptor first (for InnerInterceptors)
 * MybatisPlusInterceptor mpInterceptor = new MybatisPlusInterceptor();
 * mpInterceptor.addInnerInterceptor(new PaginationInnerInterceptor());
 * mpInterceptor.addInnerInterceptor(new MpSqlSafetyInnerInterceptor(validator));
 * sqlSessionFactory.getConfiguration().addInterceptor(mpInterceptor);
 * 
 * // Then add audit interceptor (standard MyBatis Interceptor)
 * sqlSessionFactory.getConfiguration().addInterceptor(new MpSqlAuditInnerInterceptor(auditLogWriter));
 * }</pre>
 *
 * @see MpSqlSafetyInnerInterceptor
 * @see AuditLogWriter
 */
@Intercepts({
    @Signature(
        type = Executor.class,
        method = "update",
        args = {MappedStatement.class, Object.class}
    ),
    @Signature(
        type = Executor.class,
        method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}
    )
})
public class MpSqlAuditInnerInterceptor implements Interceptor {

    private static final Logger logger = LoggerFactory.getLogger(MpSqlAuditInnerInterceptor.class);
    
    private final AuditLogWriter auditLogWriter;
    
    /**
     * Constructs MpSqlAuditInnerInterceptor with specified AuditLogWriter.
     *
     * @param auditLogWriter the audit log writer (must not be null)
     * @throws IllegalArgumentException if auditLogWriter is null
     */
    public MpSqlAuditInnerInterceptor(AuditLogWriter auditLogWriter) {
        if (auditLogWriter == null) {
            throw new IllegalArgumentException("auditLogWriter must not be null");
        }
        this.auditLogWriter = auditLogWriter;
    }
    
    /**
     * Intercepts Executor method calls to capture execution results and timing.
     *
     * <p>This method:</p>
     * <ol>
     *   <li>Records start time using System.nanoTime()</li>
     *   <li>Detects IPage and QueryWrapper parameters</li>
     *   <li>Executes the original method via invocation.proceed()</li>
     *   <li>Captures result (rows affected or result set size)</li>
     *   <li>Calculates execution duration</li>
     *   <li>Writes audit event with all metadata</li>
     *   <li>Re-throws any exceptions after logging</li>
     * </ol>
     *
     * @param invocation the method invocation
     * @return the result of the intercepted method
     * @throws Throwable if execution fails (exception is re-thrown after audit logging)
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startNano = System.nanoTime();
        Object result = null;
        Throwable exception = null;

        try {
            // Execute original method (pre-execution validation happens in MpSqlSafetyInnerInterceptor)
            result = invocation.proceed();
            return result;

        } catch (Throwable t) {
            exception = t;
            throw t;

        } finally {
            try {
                // Write audit event (success or failure)
                long durationMs = (System.nanoTime() - startNano) / 1_000_000;
                writeAuditEvent(invocation, result, durationMs, exception);
            } finally {
                // Clean up ThreadLocal to prevent memory leaks
                MpSqlSafetyInnerInterceptor.clearValidationResult();
            }
        }
    }

    /**
     * Writes audit event based on invocation result.
     *
     * @param invocation the method invocation
     * @param result the execution result (may be null if exception occurred)
     * @param durationMs the execution duration in milliseconds
     * @param exception the exception if execution failed (may be null)
     */
    private void writeAuditEvent(Invocation invocation, Object result,
                                  long durationMs, Throwable exception) {
        try {
            // Extract invocation context
            Object[] args = invocation.getArgs();
            MappedStatement ms = (MappedStatement) args[0];
            Object parameter = args[1];

            // Get BoundSql for SQL extraction
            BoundSql boundSql = ms.getBoundSql(parameter);
            String sql = boundSql.getSql();

            // Determine SQL type
            SqlCommandType sqlType = SqlCommandType.valueOf(ms.getSqlCommandType().name());

            // Calculate rows affected
            int rowsAffected = extractRowsAffected(invocation.getMethod().getName(), result);

            // Retrieve pre-execution ValidationResult
            ValidationResult validationResult = MpSqlSafetyInnerInterceptor.getValidationResult();

            // Build audit event
            AuditEvent.Builder eventBuilder = AuditEvent.builder()
                .sql(sql)
                .sqlType(sqlType)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId(ms.getId())
                .timestamp(Instant.now())
                .executionTimeMs(durationMs)
                .rowsAffected(rowsAffected);

            // Add error message if exception occurred
            if (exception != null) {
                // Unwrap InvocationTargetException to get the actual cause
                Throwable actualException = exception;
                if (exception instanceof java.lang.reflect.InvocationTargetException) {
                    Throwable cause = exception.getCause();
                    if (cause != null) {
                        actualException = cause;
                    }
                }
                eventBuilder.errorMessage(actualException.getMessage());
            }

            // Add IPage metadata and QueryWrapper flag
            Map<String, Object> details = new HashMap<>();
            
            // Detect and extract IPage metadata
            IPage<?> iPage = extractIPage(parameter);
            if (iPage != null) {
                details.put("pagination.total", iPage.getTotal());
                details.put("pagination.current", iPage.getCurrent());
                details.put("pagination.size", iPage.getSize());
                details.put("pagination.pages", iPage.getPages());
            }
            
            // Detect QueryWrapper
            if (isQueryWrapper(parameter)) {
                details.put("queryWrapper", true);
            }
            
            // Set params if any details were added
            if (!details.isEmpty()) {
                eventBuilder.params(details);
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
     * Extracts rows affected from method result.
     *
     * @param methodName the intercepted method name
     * @param result the execution result
     * @return rows affected (or result set size for SELECT)
     */
    private int extractRowsAffected(String methodName, Object result) {
        if (result == null) {
            return -1; // Indicates no result
        }

        if ("update".equals(methodName)) {
            // UPDATE/INSERT/DELETE returns Integer (rows affected)
            return (Integer) result;
        } else if ("query".equals(methodName)) {
            // SELECT returns List<?> (result set size)
            @SuppressWarnings("unchecked")
            List<?> resultList = (List<?>) result;
            return resultList.size();
        }

        return -1;
    }
    
    /**
     * Extracts IPage from parameter object.
     *
     * @param parameter the execution parameter
     * @return IPage object or null if not found
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
     *
     * @param parameter the execution parameter
     * @return true if wrapper is present
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
    
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // No configuration properties needed
    }
}
