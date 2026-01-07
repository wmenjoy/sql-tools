package com.footstone.sqlguard.interceptor.mybatis;

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
 * MyBatis interceptor for post-execution audit logging.
 *
 * <p>This interceptor captures SQL execution results, timing, and errors after
 * statement execution completes. It complements SqlSafetyInterceptor which
 * performs pre-execution validation.</p>
 *
 * <p><strong>Intercepted Methods:</strong></p>
 * <ul>
 *   <li>Executor.update(MappedStatement, Object) - INSERT/UPDATE/DELETE</li>
 *   <li>Executor.query(MappedStatement, Object, RowBounds, ResultHandler) - SELECT</li>
 * </ul>
 *
 * <p><strong>ThreadLocal Coordination:</strong></p>
 * <p>Retrieves pre-execution ValidationResult from SqlSafetyInterceptor via
 * shared SqlInterceptorContext.VALIDATION_RESULT ThreadLocal.</p>
 *
 * <p><strong>Compatibility:</strong> MyBatis 3.4.x and 3.5.x</p>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>{@code
 * SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(config);
 * factory.getConfiguration().addInterceptor(new SqlAuditInterceptor(auditLogWriter));
 * }</pre>
 *
 * <p><strong>Thread Safety:</strong> This interceptor is thread-safe. The AuditLogWriter
 * is shared across threads and must be thread-safe.</p>
 *
 * @see SqlSafetyInterceptor
 * @see SqlInterceptorContext
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
public class SqlAuditInterceptor implements Interceptor {

    private static final Logger logger = LoggerFactory.getLogger(SqlAuditInterceptor.class);

    private final AuditLogWriter auditLogWriter;

    /**
     * Constructs SqlAuditInterceptor with specified AuditLogWriter.
     *
     * @param auditLogWriter the audit log writer (must not be null)
     * @throws NullPointerException if auditLogWriter is null
     */
    public SqlAuditInterceptor(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = Objects.requireNonNull(auditLogWriter, "auditLogWriter must not be null");
    }

    /**
     * Intercepts Executor method calls to capture execution results and timing.
     *
     * <p>This method:</p>
     * <ol>
     *   <li>Records start time using System.nanoTime()</li>
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
            // Execute original method (pre-execution validation happens in SqlSafetyInterceptor)
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
                // This must be done after audit event is written
                SqlInterceptorContext.VALIDATION_RESULT.remove();
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

            // Get mapper ID
            String mapperId = ms.getId();

            // Extract rows affected
            int rowsAffected = extractRowsAffected(invocation.getMethod().getName(), result, exception);

            // Retrieve pre-execution validation result from ThreadLocal
            ValidationResult validationResult = SqlInterceptorContext.VALIDATION_RESULT.get();

            // Get datasource name from Environment
            String datasourceName = null;
            try {
                if (ms.getConfiguration() != null && ms.getConfiguration().getEnvironment() != null) {
                    datasourceName = ms.getConfiguration().getEnvironment().getId();
                }
            } catch (Exception e) {
                // Ignore - datasource will be null
                logger.debug("Failed to extract datasource name", e);
            }

            // Build audit event
            AuditEvent.Builder eventBuilder = AuditEvent.builder()
                .sql(sql)
                .sqlType(sqlType)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId(mapperId)
                .datasource(datasourceName)
                .timestamp(Instant.now())
                .executionTimeMs(durationMs)
                .rowsAffected(rowsAffected);

            // Add parameter bindings if available
            Map<String, Object> params = extractParameters(parameter);
            if (params != null && !params.isEmpty()) {
                eventBuilder.params(params);
            }

            // Add error message if exception occurred
            if (exception != null) {
                Throwable cause = (exception.getCause() != null) ? exception.getCause() : exception;
                eventBuilder.errorMessage(cause.getMessage());
            }

            // Add pre-execution violations if available (only if validation failed)
            if (validationResult != null && !validationResult.isPassed()) {
                eventBuilder.violations(validationResult);
            }

            // Write audit log
            auditLogWriter.writeAuditLog(eventBuilder.build());

        } catch (Exception e) {
            logger.error("Failed to write audit log for invocation: {}", invocation, e);
            // Don't throw - audit failure should not break SQL execution
        }
    }

    /**
     * Extracts rows affected from execution result.
     *
     * @param methodName the intercepted method name ("update" or "query")
     * @param result the execution result
     * @param exception the exception if execution failed
     * @return the number of rows affected, or -1 if not applicable
     */
    private int extractRowsAffected(String methodName, Object result, Throwable exception) {
        if (exception != null) {
            return -1; // Error case
        }

        try {
            if ("update".equals(methodName)) {
                // update() returns Integer (rows affected)
                return (result instanceof Integer) ? (Integer) result : -1;

            } else if ("query".equals(methodName)) {
                // query() returns List<?> (result set)
                if (result instanceof List) {
                    return ((List<?>) result).size();
                }
            }

        } catch (Exception e) {
            logger.debug("Failed to extract rows affected: {}", e.getMessage());
        }

        return -1; // Default
    }

    /**
     * Extracts parameter bindings from parameter object.
     *
     * <p>Handles different parameter types:</p>
     * <ul>
     *   <li>Map - @Param annotated parameters</li>
     *   <li>Single value - primitive or simple object</li>
     *   <li>null - no parameters</li>
     * </ul>
     *
     * @param parameter the MyBatis parameter object
     * @return parameter map, or null if no parameters
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractParameters(Object parameter) {
        if (parameter == null) {
            return null;
        }

        Map<String, Object> params = new HashMap<>();

        try {
            if (parameter instanceof Map) {
                // MyBatis wraps parameters in Map
                Map<?, ?> paramMap = (Map<?, ?>) parameter;
                for (Map.Entry<?, ?> entry : paramMap.entrySet()) {
                    if (entry.getKey() instanceof String) {
                        params.put((String) entry.getKey(), entry.getValue());
                    }
                }
            } else {
                // Single parameter
                params.put("param", parameter);
            }

        } catch (Exception e) {
            logger.debug("Failed to extract parameters: {}", e.getMessage());
        }

        return params.isEmpty() ? null : params;
    }

    /**
     * Wraps the target object with this interceptor.
     *
     * <p>Only wraps Executor instances; other objects are returned unchanged.</p>
     *
     * @param target the target object (should be Executor)
     * @return the wrapped proxy if target is Executor, otherwise the original object
     */
    @Override
    public Object plugin(Object target) {
        // Wrap target if it's an Executor
        return (target instanceof Executor) ? Plugin.wrap(target, this) : target;
    }

    /**
     * Sets properties from MyBatis configuration.
     *
     * <p>This interceptor does not require any configuration properties.
     * This method is provided for MyBatis XML configuration compatibility.</p>
     *
     * @param properties the configuration properties (ignored)
     */
    @Override
    public void setProperties(Properties properties) {
        // No configuration properties needed
    }
}













