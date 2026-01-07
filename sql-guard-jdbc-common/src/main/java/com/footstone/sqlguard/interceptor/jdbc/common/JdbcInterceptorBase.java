package com.footstone.sqlguard.interceptor.jdbc.common;

import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class implementing template method pattern for JDBC SQL interception.
 *
 * <p>JdbcInterceptorBase provides a standardized interception lifecycle that all JDBC
 * interceptors (Druid, HikariCP, P6Spy) should follow. This eliminates code duplication
 * and ensures consistent behavior across different connection pool implementations.</p>
 *
 * <h2>Template Method Pattern</h2>
 * <p>The {@link #interceptSql(String, Object...)} method defines the algorithm skeleton:</p>
 * <pre>
 * interceptSql() [final]
 *   ├─→ beforeValidation() [hook - optional]
 *   ├─→ buildSqlContext() [hook - required]
 *   ├─→ validate() [hook - required]
 *   ├─→ handleViolation() [hook - required]
 *   ├─→ afterValidation() [hook - optional]
 *   └─→ onError() [hook - optional]
 * </pre>
 *
 * <h2>Hook Methods</h2>
 * <ul>
 *   <li><strong>Required hooks</strong> (must implement):
 *     <ul>
 *       <li>{@link #buildSqlContext(String, Object...)} - Create SqlContext from SQL</li>
 *       <li>{@link #validate(SqlContext)} - Perform validation</li>
 *       <li>{@link #handleViolation(ValidationResult)} - Handle validation failures</li>
 *     </ul>
 *   </li>
 *   <li><strong>Optional hooks</strong> (override as needed):
 *     <ul>
 *       <li>{@link #beforeValidation(String, Object...)} - Pre-validation logic</li>
 *       <li>{@link #afterValidation(ValidationResult)} - Post-validation logic</li>
 *       <li>{@link #onError(Exception)} - Error handling</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This base class is designed to be stateless and thread-safe. Subclasses should:</p>
 * <ul>
 *   <li>Avoid storing mutable state in instance fields</li>
 *   <li>Use ThreadLocal for per-request context if needed</li>
 *   <li>Ensure validators and other dependencies are thread-safe</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * public class DruidInterceptor extends JdbcInterceptorBase {
 *
 *     private final DefaultSqlSafetyValidator validator;
 *
 *     @Override
 *     protected SqlContext buildSqlContext(String sql, Object... params) {
 *         return SqlContext.builder()
 *             .sql(sql)
 *             .type(detectSqlType(sql))
 *             .mapperId("jdbc.druid:" + datasourceName)
 *             .build();
 *     }
 *
 *     @Override
 *     protected ValidationResult validate(SqlContext context) {
 *         return validator.validate(context);
 *     }
 *
 *     @Override
 *     protected void handleViolation(ValidationResult result) {
 *         if (!result.isPassed() && strategy == ViolationStrategy.BLOCK) {
 *             throw new SQLException("Violation: " + result.getMessage());
 *         }
 *     }
 * }
 * }</pre>
 *
 * @since 2.0.0
 * @see ViolationStrategy
 * @see JdbcInterceptorConfig
 * @see SqlContext
 * @see ValidationResult
 */
public abstract class JdbcInterceptorBase {

    private static final Logger logger = LoggerFactory.getLogger(JdbcInterceptorBase.class);

    /**
     * Template method that orchestrates the SQL interception lifecycle.
     *
     * <p>This method is final to ensure the lifecycle is consistent across all
     * implementations. Subclasses customize behavior by overriding hook methods.</p>
     *
     * <p><strong>Lifecycle:</strong></p>
     * <ol>
     *   <li>Call {@link #beforeValidation(String, Object...)} hook</li>
     *   <li>Build SqlContext via {@link #buildSqlContext(String, Object...)}</li>
     *   <li>Validate via {@link #validate(SqlContext)}</li>
     *   <li>Handle violations via {@link #handleViolation(ValidationResult)}</li>
     *   <li>Call {@link #afterValidation(ValidationResult)} hook</li>
     * </ol>
     *
     * <p>If any exception occurs, {@link #onError(Exception)} is called.</p>
     *
     * @param sql the SQL statement to intercept
     * @param params optional parameters for prepared statements
     */
    public final void interceptSql(String sql, Object... params) {
        ValidationResult result = null;
        try {
            // Pre-validation hook
            beforeValidation(sql, params);

            // Build context
            SqlContext context = buildSqlContext(sql, params);
            
            // Handle null context gracefully
            if (context == null) {
                logger.debug("buildSqlContext returned null, skipping validation");
                result = ValidationResult.pass();
            } else {
                // Validate
                result = validate(context);
            }

            // Handle violations (even if passed, for consistent lifecycle)
            handleViolation(result);

            // Post-validation hook
            afterValidation(result);

        } catch (Exception e) {
            // Error handling hook
            onError(e);
        }
    }

    // ========== Required Abstract Methods (Must Override) ==========

    /**
     * Builds a SqlContext from the intercepted SQL and parameters.
     *
     * <p>Implementations should:</p>
     * <ul>
     *   <li>Detect SQL command type (SELECT, INSERT, UPDATE, DELETE)</li>
     *   <li>Extract datasource information if available</li>
     *   <li>Set appropriate mapperId for tracking</li>
     *   <li>Include any pool-specific metadata</li>
     * </ul>
     *
     * @param sql the SQL statement
     * @param params optional prepared statement parameters
     * @return constructed SqlContext, never null
     */
    protected abstract SqlContext buildSqlContext(String sql, Object... params);

    /**
     * Validates the SQL context against safety rules.
     *
     * <p>Implementations typically delegate to {@code DefaultSqlSafetyValidator}.</p>
     *
     * @param context the SQL context to validate
     * @return validation result containing any violations
     */
    protected abstract ValidationResult validate(SqlContext context);

    /**
     * Handles validation violations according to the configured strategy.
     *
     * <p>Implementations should:</p>
     * <ul>
     *   <li>Check if validation passed ({@code result.isPassed()})</li>
     *   <li>Log violations according to strategy's log level</li>
     *   <li>Throw SQLException if strategy is BLOCK</li>
     *   <li>Record audit events if audit is enabled</li>
     * </ul>
     *
     * @param result the validation result to handle
     */
    protected abstract void handleViolation(ValidationResult result);

    // ========== Optional Hook Methods (Override as Needed) ==========

    /**
     * Hook method called before validation begins.
     *
     * <p>Default implementation does nothing. Override to add pre-validation
     * logic such as:</p>
     * <ul>
     *   <li>Deduplication checks</li>
     *   <li>Exclude pattern matching</li>
     *   <li>Request context setup</li>
     * </ul>
     *
     * @param sql the SQL statement
     * @param params optional prepared statement parameters
     */
    protected void beforeValidation(String sql, Object... params) {
        // Default: no-op
    }

    /**
     * Hook method called after validation completes (success or failure).
     *
     * <p>Default implementation does nothing. Override to add post-validation
     * logic such as:</p>
     * <ul>
     *   <li>Metrics recording</li>
     *   <li>Audit logging</li>
     *   <li>Context cleanup</li>
     * </ul>
     *
     * @param result the validation result
     */
    protected void afterValidation(ValidationResult result) {
        // Default: no-op
    }

    /**
     * Hook method called when an exception occurs during interception.
     *
     * <p>Default implementation logs the error at debug level. Override to
     * add custom error handling such as:</p>
     * <ul>
     *   <li>Error metrics recording</li>
     *   <li>Alert triggering</li>
     *   <li>Fallback behavior</li>
     * </ul>
     *
     * @param e the exception that occurred
     */
    protected void onError(Exception e) {
        logger.debug("Error during SQL interception", e);
    }
}








