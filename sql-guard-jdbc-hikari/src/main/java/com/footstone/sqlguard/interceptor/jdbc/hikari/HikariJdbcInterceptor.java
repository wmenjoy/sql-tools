package com.footstone.sqlguard.interceptor.jdbc.hikari;

import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorBase;
import com.footstone.sqlguard.interceptor.jdbc.common.StatementIdGenerator;
import com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * HikariCP-specific implementation of JdbcInterceptorBase.
 *
 * <p>HikariJdbcInterceptor provides the concrete implementation of the template method
 * pattern for HikariCP connection pools. It handles SQL context building, validation,
 * and violation handling specific to HikariCP's proxy-based interception model.</p>
 *
 * <h2>Template Method Implementation</h2>
 * <p>This class implements the required abstract methods from JdbcInterceptorBase:</p>
 * <ul>
 *   <li>{@link #buildSqlContext(String, Object...)} - Creates HikariCP-specific SqlContext</li>
 *   <li>{@link #validate(SqlContext)} - Delegates to DefaultSqlSafetyValidator</li>
 *   <li>{@link #handleViolation(ValidationResult)} - Handles violations per strategy</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is designed to be thread-safe and stateless. The validator and config
 * are immutable after construction. Per-request state is not stored.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator();
 * HikariInterceptorConfig config = HikariInterceptorConfig.builder()
 *     .strategy(ViolationStrategy.WARN)
 *     .build();
 * 
 * HikariJdbcInterceptor interceptor = new HikariJdbcInterceptor(
 *     validator, config, "myDataSource");
 * 
 * // In proxy handler
 * interceptor.interceptSql("SELECT * FROM users WHERE id = ?");
 * }</pre>
 *
 * @since 2.0.0
 * @see JdbcInterceptorBase
 * @see HikariInterceptorConfig
 * @see ViolationStrategy
 */
public class HikariJdbcInterceptor extends JdbcInterceptorBase {

    private static final Logger logger = LoggerFactory.getLogger(HikariJdbcInterceptor.class);

    private final DefaultSqlSafetyValidator validator;
    private final HikariInterceptorConfig config;
    private final String datasourceName;

    /**
     * ThreadLocal storage for the last validation result.
     * <p>Used to coordinate with HikariSqlAuditProxyFactory for violation correlation.</p>
     */
    private static final ThreadLocal<ValidationResult> LAST_VALIDATION_RESULT = new ThreadLocal<>();

    /**
     * ThreadLocal storage for any SQLException to be thrown after interception.
     * <p>Used to propagate BLOCK strategy exceptions from template method.</p>
     */
    private static final ThreadLocal<SQLException> PENDING_EXCEPTION = new ThreadLocal<>();

    /**
     * Constructs a HikariJdbcInterceptor with the specified configuration.
     *
     * @param validator the SQL safety validator
     * @param config the interceptor configuration
     * @param datasourceName the name of the datasource being intercepted
     * @throws IllegalArgumentException if validator or config is null
     */
    public HikariJdbcInterceptor(DefaultSqlSafetyValidator validator,
                                  HikariInterceptorConfig config,
                                  String datasourceName) {
        if (validator == null) {
            throw new IllegalArgumentException("validator cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        this.validator = validator;
        this.config = config;
        this.datasourceName = datasourceName != null ? datasourceName : "unknown";
    }

    /**
     * Constructs a HikariJdbcInterceptor with a simple violation strategy.
     *
     * @param validator the SQL safety validator
     * @param strategy the violation handling strategy
     * @param datasourceName the name of the datasource
     */
    public HikariJdbcInterceptor(DefaultSqlSafetyValidator validator,
                                  ViolationStrategy strategy,
                                  String datasourceName) {
        this(validator, 
             HikariInterceptorConfig.builder().strategy(strategy).build(),
             datasourceName);
    }

    /**
     * Gets the last validation result for the current thread.
     * <p>Used by audit proxy to correlate pre-execution violations.</p>
     *
     * @return the last validation result, or null if none
     */
    public static ValidationResult getLastValidationResult() {
        return LAST_VALIDATION_RESULT.get();
    }

    /**
     * Clears the last validation result for the current thread.
     * <p>Should be called after audit logging to prevent memory leaks.</p>
     */
    public static void clearLastValidationResult() {
        LAST_VALIDATION_RESULT.remove();
    }

    /**
     * Gets any pending exception to be thrown.
     *
     * @return the pending SQLException, or null if none
     */
    public static SQLException getPendingException() {
        return PENDING_EXCEPTION.get();
    }

    /**
     * Clears any pending exception.
     */
    public static void clearPendingException() {
        PENDING_EXCEPTION.remove();
    }

    /**
     * Checks if interception should be skipped for the given SQL.
     *
     * @param sql the SQL to check
     * @return true if interception should be skipped
     */
    public boolean shouldSkip(String sql) {
        if (!config.isEnabled()) {
            return true;
        }
        if (!config.isProxyConnectionEnabled()) {
            return true;
        }
        if (config.shouldExclude(sql)) {
            logger.debug("SQL excluded from validation: {}", truncateSql(sql));
            return true;
        }
        return false;
    }

    @Override
    protected SqlContext buildSqlContext(String sql, Object... params) {
        // Handle null SQL gracefully
        String safeSql = sql != null ? sql : "";

        // Detect SQL type
        SqlCommandType type = detectSqlType(safeSql);

        // Generate unique statementId using StatementIdGenerator
        // Format: jdbc.hikari:{datasourceName}:{sqlHash}
        String statementId = StatementIdGenerator.generate("hikari", datasourceName, safeSql);

        return SqlContext.builder()
                .sql(safeSql)
                .type(type)
                .executionLayer(ExecutionLayer.JDBC)
                .statementId(statementId)
                .datasource(datasourceName)
                .build();
    }

    @Override
    protected ValidationResult validate(SqlContext context) {
        ValidationResult result = validator.validate(context);
        
        // Store in ThreadLocal for audit correlation
        LAST_VALIDATION_RESULT.set(result);
        
        return result;
    }

    @Override
    protected void handleViolation(ValidationResult result) {
        if (result == null || result.isPassed()) {
            return;
        }

        ViolationStrategy strategy = config.getStrategy();
        String message = formatViolationMessage(result);

        // Log based on strategy
        if (strategy.shouldLog()) {
            if ("ERROR".equals(strategy.getLogLevel())) {
                logger.error(message);
            } else {
                logger.warn(message);
            }
        }

        // Throw exception if blocking
        if (strategy.shouldBlock()) {
            SQLException ex = new SQLException(message, "42000");
            PENDING_EXCEPTION.set(ex);
            // Note: The actual throw happens in the proxy handler after interceptSql() returns
        }
    }

    @Override
    protected void beforeValidation(String sql, Object... params) {
        // Clear any previous exception
        PENDING_EXCEPTION.remove();
        
        // HikariCP pre-validation hook
        if (logger.isDebugEnabled()) {
            logger.debug("Intercepting SQL [datasource={}]: {}", 
                        datasourceName, truncateSql(sql));
        }
    }

    @Override
    protected void afterValidation(ValidationResult result) {
        // HikariCP post-validation hook
        if (result != null && !result.isPassed() && logger.isDebugEnabled()) {
            logger.debug("Validation failed with {} violations", 
                        result.getViolations().size());
        }
    }

    @Override
    protected void onError(Exception e) {
        logger.error("Error during SQL interception [datasource={}]: {}", 
                    datasourceName, e.getMessage());
        // Don't propagate errors from validation - let SQL execution proceed
    }

    /**
     * Detects SQL command type from SQL string.
     *
     * @param sql the SQL statement
     * @return the detected SqlCommandType
     */
    private SqlCommandType detectSqlType(String sql) {
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
        } else if (trimmed.startsWith("CALL") || trimmed.startsWith("{CALL")) {
            return SqlCommandType.SELECT; // Stored procedure
        }
        return SqlCommandType.SELECT;
    }

    /**
     * Formats violation message for logging/exception.
     *
     * @param result the validation result
     * @return formatted message
     */
    private String formatViolationMessage(ValidationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("SQL safety violation detected [datasource=").append(datasourceName)
          .append(", riskLevel=").append(result.getRiskLevel()).append("]: ");

        for (ViolationInfo violation : result.getViolations()) {
            sb.append(violation.getMessage()).append("; ");
        }

        return sb.toString();
    }

    /**
     * Truncates SQL for logging (first 200 characters).
     *
     * @param sql the SQL statement
     * @return truncated SQL
     */
    private String truncateSql(String sql) {
        if (sql == null) {
            return "null";
        }
        if (sql.length() <= 200) {
            return sql;
        }
        return sql.substring(0, 200) + "...";
    }
}







