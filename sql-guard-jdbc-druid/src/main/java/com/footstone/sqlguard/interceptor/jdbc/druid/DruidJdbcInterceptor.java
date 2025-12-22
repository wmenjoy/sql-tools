package com.footstone.sqlguard.interceptor.jdbc.druid;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorBase;
import com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * Druid-specific implementation of {@link JdbcInterceptorBase}.
 *
 * <p>DruidJdbcInterceptor provides the concrete implementation of the template method
 * pattern for Druid connection pools. It handles SQL context building, validation,
 * and violation handling according to the configured strategy.</p>
 *
 * <h2>Template Method Implementation</h2>
 * <p>This class implements the required abstract methods from {@link JdbcInterceptorBase}:</p>
 * <ul>
 *   <li>{@link #buildSqlContext(String, Object...)} - Builds Druid-specific SqlContext</li>
 *   <li>{@link #validate(SqlContext)} - Delegates to DefaultSqlSafetyValidator</li>
 *   <li>{@link #handleViolation(ValidationResult)} - Handles violations per strategy</li>
 * </ul>
 *
 * <h2>Composition Pattern</h2>
 * <p>DruidJdbcInterceptor is designed to be composed by {@link DruidSqlSafetyFilter}:</p>
 * <pre>
 * FilterAdapter (Druid)
 *     ↑
 *     | extends
 *     |
 * DruidSqlSafetyFilter
 *     |
 *     | composes (has-a)
 *     ↓
 * DruidJdbcInterceptor
 *     ↑
 *     | extends
 *     |
 * JdbcInterceptorBase (sql-guard-jdbc-common)
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. Configuration and validator are immutable after
 * construction. ThreadLocal is used for storing datasource context per request.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * DruidInterceptorConfig config = new MyDruidConfig();
 * DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(rulesConfig);
 * DruidJdbcInterceptor interceptor = new DruidJdbcInterceptor(config, validator);
 *
 * // Use with DruidSqlSafetyFilter
 * DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(interceptor);
 * dataSource.getProxyFilters().add(filter);
 * }</pre>
 *
 * @since 2.0.0
 * @see JdbcInterceptorBase
 * @see DruidSqlSafetyFilter
 * @see DruidInterceptorConfig
 */
public class DruidJdbcInterceptor extends JdbcInterceptorBase {

    private static final Logger logger = LoggerFactory.getLogger(DruidJdbcInterceptor.class);

    /**
     * Druid-specific configuration.
     */
    private final DruidInterceptorConfig config;

    /**
     * SQL safety validator for rule checking.
     */
    private final DefaultSqlSafetyValidator validator;

    /**
     * ThreadLocal storage for datasource name context.
     */
    private static final ThreadLocal<String> datasourceNameContext = new ThreadLocal<>();

    /**
     * ThreadLocal storage for ValidationResult to enable coordination with audit filter.
     */
    private static final ThreadLocal<ValidationResult> validationResultContext = new ThreadLocal<>();

    /**
     * Constructs a DruidJdbcInterceptor with configuration and validator.
     *
     * @param config the Druid-specific configuration
     * @param validator the SQL safety validator
     * @throws IllegalArgumentException if config or validator is null
     */
    public DruidJdbcInterceptor(DruidInterceptorConfig config, DefaultSqlSafetyValidator validator) {
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        if (validator == null) {
            throw new IllegalArgumentException("validator cannot be null");
        }
        this.config = config;
        this.validator = validator;
    }

    // ========== Template Method Implementations ==========

    /**
     * Builds a SqlContext from the intercepted SQL and parameters.
     *
     * <p>Druid-specific implementation that:</p>
     * <ul>
     *   <li>Detects SQL command type from SQL prefix</li>
     *   <li>Uses datasource name from ThreadLocal context</li>
     *   <li>Sets mapperId with "jdbc.druid:" prefix</li>
     * </ul>
     *
     * @param sql the SQL statement
     * @param params optional prepared statement parameters
     * @return constructed SqlContext
     */
    @Override
    protected SqlContext buildSqlContext(String sql, Object... params) {
        if (sql == null || sql.trim().isEmpty()) {
            // Return a default context for null/empty SQL
            return SqlContext.builder()
                .sql("")
                .type(SqlCommandType.UNKNOWN)
                .mapperId("jdbc.druid:unknown")
                .datasource("unknown")
                .build();
        }

        SqlCommandType type = detectSqlType(sql);
        String datasourceName = getCurrentDatasourceName();

        return SqlContext.builder()
            .sql(sql)
            .type(type)
            .mapperId("jdbc.druid:" + datasourceName)
            .datasource(datasourceName)
            .build();
    }

    /**
     * Validates the SQL context against safety rules.
     *
     * <p>Delegates to {@link DefaultSqlSafetyValidator#validate(SqlContext)}.</p>
     *
     * @param context the SQL context to validate
     * @return validation result containing any violations
     */
    @Override
    protected ValidationResult validate(SqlContext context) {
        ValidationResult result = validator.validate(context);
        // Store result for audit filter coordination
        validationResultContext.set(result);
        return result;
    }

    /**
     * Handles validation violations according to the configured strategy.
     *
     * <p>Strategy behaviors:</p>
     * <ul>
     *   <li><strong>BLOCK</strong>: Throws RuntimeException (will be caught by filter)</li>
     *   <li><strong>WARN</strong>: Logs error level message, continues</li>
     *   <li><strong>LOG</strong>: Logs warning level message, continues</li>
     * </ul>
     *
     * @param result the validation result to handle
     */
    @Override
    protected void handleViolation(ValidationResult result) {
        if (result == null || result.isPassed()) {
            return;
        }

        ViolationStrategy strategy = config.getStrategy();
        String message = formatViolationMessage(result);

        if (strategy.shouldLog()) {
            if ("ERROR".equals(strategy.getLogLevel())) {
                logger.error(message);
            } else {
                logger.warn(message);
            }
        }

        if (strategy.shouldBlock()) {
            // Throw RuntimeException - will be caught and wrapped by filter
            throw new SqlSafetyViolationException(message, result);
        }
    }

    // ========== Optional Hook Overrides ==========

    /**
     * Pre-validation hook for Druid-specific logic.
     *
     * <p>Checks if interceptor is enabled before proceeding.</p>
     *
     * @param sql the SQL statement
     * @param params optional prepared statement parameters
     */
    @Override
    protected void beforeValidation(String sql, Object... params) {
        if (!config.isEnabled()) {
            logger.debug("Druid interceptor disabled, skipping validation");
        }
    }

    /**
     * Error handling hook.
     *
     * <p>Re-throws SqlSafetyViolationException, logs and ignores others.</p>
     *
     * @param e the exception that occurred
     */
    @Override
    protected void onError(Exception e) {
        if (e instanceof SqlSafetyViolationException) {
            throw (SqlSafetyViolationException) e;
        }
        logger.debug("Error during Druid SQL interception", e);
    }

    // ========== Druid-Specific Methods ==========

    /**
     * Sets the current datasource name context for this thread.
     *
     * <p>Called by DruidSqlSafetyFilter before invoking interceptSql().</p>
     *
     * @param datasourceName the datasource name
     */
    public static void setDatasourceName(String datasourceName) {
        datasourceNameContext.set(datasourceName);
    }

    /**
     * Gets the current datasource name from ThreadLocal context.
     *
     * @return datasource name, or "default" if not set
     */
    public static String getCurrentDatasourceName() {
        String name = datasourceNameContext.get();
        return name != null ? name : "default";
    }

    /**
     * Clears the datasource name context for this thread.
     */
    public static void clearDatasourceName() {
        datasourceNameContext.remove();
    }

    /**
     * Retrieves the ValidationResult from ThreadLocal for audit filter coordination.
     *
     * @return the ValidationResult from pre-execution validation, or null if not available
     */
    public static ValidationResult getValidationResult() {
        return validationResultContext.get();
    }

    /**
     * Clears the ValidationResult from ThreadLocal.
     */
    public static void clearValidationResult() {
        validationResultContext.remove();
    }

    /**
     * Returns the configuration.
     *
     * @return the Druid interceptor configuration
     */
    public DruidInterceptorConfig getConfig() {
        return config;
    }

    /**
     * Returns the validator.
     *
     * @return the SQL safety validator
     */
    public DefaultSqlSafetyValidator getValidator() {
        return validator;
    }

    // ========== Helper Methods ==========

    /**
     * Detects SQL command type from SQL prefix.
     *
     * @param sql the SQL to analyze
     * @return the detected SqlCommandType
     */
    private SqlCommandType detectSqlType(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return SqlCommandType.UNKNOWN;
        }

        String upperSql = sql.trim().toUpperCase();

        if (upperSql.startsWith("SELECT")) {
            return SqlCommandType.SELECT;
        } else if (upperSql.startsWith("UPDATE")) {
            return SqlCommandType.UPDATE;
        } else if (upperSql.startsWith("DELETE")) {
            return SqlCommandType.DELETE;
        } else if (upperSql.startsWith("INSERT")) {
            return SqlCommandType.INSERT;
        } else {
            return SqlCommandType.UNKNOWN;
        }
    }

    /**
     * Formats violation message for logging.
     *
     * @param result the validation result
     * @return formatted violation message
     */
    private String formatViolationMessage(ValidationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("SQL Safety Violation [Druid Interceptor] [Datasource: ")
            .append(getCurrentDatasourceName())
            .append("] [Risk: ")
            .append(result.getRiskLevel())
            .append("] - ");

        result.getViolations().forEach(v -> {
            sb.append(v.getMessage()).append("; ");
        });

        return sb.toString();
    }
}

