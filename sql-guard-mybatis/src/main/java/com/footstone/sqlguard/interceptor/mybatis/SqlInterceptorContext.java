package com.footstone.sqlguard.interceptor.mybatis;

import com.footstone.sqlguard.core.model.ValidationResult;

/**
 * Shared context for MyBatis interceptor coordination.
 *
 * <p>Provides ThreadLocal storage for cross-interceptor communication between
 * SqlSafetyInterceptor (pre-execution) and SqlAuditInterceptor (post-execution).</p>
 *
 * <p><strong>Usage Pattern:</strong></p>
 * <pre>{@code
 * // In SqlSafetyInterceptor (pre-execution):
 * ValidationResult result = validator.validate(sqlContext);
 * SqlInterceptorContext.VALIDATION_RESULT.set(result);
 * try {
 *     return invocation.proceed();
 * } finally {
 *     SqlInterceptorContext.VALIDATION_RESULT.remove();
 * }
 *
 * // In SqlAuditInterceptor (post-execution):
 * ValidationResult validation = SqlInterceptorContext.VALIDATION_RESULT.get();
 * if (validation != null && !validation.isPassed()) {
 *     auditEvent.violations(validation);
 * }
 * }</pre>
 *
 * <p><strong>Thread Safety:</strong></p>
 * <p>ThreadLocal ensures each thread has its own isolated copy of the validation
 * result, making this safe for concurrent use in multi-threaded environments.</p>
 *
 * <p><strong>Memory Management:</strong></p>
 * <p>Always call {@code remove()} in a finally block to prevent memory leaks.
 * The SqlSafetyInterceptor is responsible for cleanup after SQL execution completes.</p>
 */
public class SqlInterceptorContext {

    /**
     * ThreadLocal storage for pre-execution validation result.
     * Set by SqlSafetyInterceptor, read by SqlAuditInterceptor.
     */
    public static final ThreadLocal<ValidationResult> VALIDATION_RESULT = new ThreadLocal<>();

    private SqlInterceptorContext() {
        // Utility class - prevent instantiation
    }
}





