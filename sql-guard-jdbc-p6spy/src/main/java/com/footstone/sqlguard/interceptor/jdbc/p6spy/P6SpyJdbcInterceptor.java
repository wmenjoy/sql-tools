package com.footstone.sqlguard.interceptor.jdbc.p6spy;

import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorBase;
import com.footstone.sqlguard.interceptor.jdbc.common.SqlContextBuilder;
import com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.footstone.sqlguard.validator.SqlDeduplicationFilter;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator;
import com.footstone.sqlguard.validator.rule.impl.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * P6Spy-specific implementation of JdbcInterceptorBase.
 *
 * <p>P6SpyJdbcInterceptor implements the template method pattern defined by
 * JdbcInterceptorBase, providing P6Spy-specific SQL context building and
 * violation handling.</p>
 *
 * <h2>Lifecycle</h2>
 * <pre>
 * interceptSql() [inherited from JdbcInterceptorBase]
 *   ├─→ beforeValidation()     - P6Spy pre-validation (deduplication)
 *   ├─→ buildSqlContext()      - Build context with P6Spy metadata
 *   ├─→ validate()             - Delegate to DefaultSqlSafetyValidator
 *   ├─→ handleViolation()      - Apply strategy (BLOCK/WARN/LOG)
 *   └─→ afterValidation()      - P6Spy post-validation (metrics)
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. All instance fields are final and immutable.
 * The deduplication filter uses ThreadLocal for thread isolation.</p>
 *
 * @since 2.0.0
 * @see JdbcInterceptorBase
 * @see P6SpySqlSafetyListener
 * @see ViolationStrategy
 */
public class P6SpyJdbcInterceptor extends JdbcInterceptorBase {

    private static final Logger logger = LoggerFactory.getLogger(P6SpyJdbcInterceptor.class);

    /**
     * Interceptor type identifier for P6Spy.
     */
    private static final String INTERCEPTOR_TYPE = "p6spy";

    /**
     * Configuration for this interceptor.
     */
    private final P6SpyInterceptorConfig config;

    /**
     * SQL safety validator.
     */
    private final DefaultSqlSafetyValidator validator;

    /**
     * Deduplication filter to prevent redundant validation.
     */
    private final SqlDeduplicationFilter deduplicationFilter;

    /**
     * Current datasource name (extracted from connection URL).
     */
    private String currentDatasource = "default";

    /**
     * ThreadLocal to store the last validation result.
     */
    private static final ThreadLocal<ValidationResult> LAST_RESULT = new ThreadLocal<>();

    /**
     * ThreadLocal to track if an exception should be thrown after validation.
     */
    private static final ThreadLocal<SQLException> PENDING_EXCEPTION = new ThreadLocal<>();

    /**
     * Constructs a P6SpyJdbcInterceptor with the specified configuration.
     *
     * @param config the P6Spy interceptor configuration
     * @throws IllegalArgumentException if config is null
     */
    public P6SpyJdbcInterceptor(P6SpyInterceptorConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        this.config = config;
        this.validator = createDefaultValidator();
        this.deduplicationFilter = new SqlDeduplicationFilter();
    }

    /**
     * Constructs a P6SpyJdbcInterceptor with custom validator.
     *
     * @param config the P6Spy interceptor configuration
     * @param validator the SQL safety validator
     * @throws IllegalArgumentException if any parameter is null
     */
    public P6SpyJdbcInterceptor(P6SpyInterceptorConfig config, DefaultSqlSafetyValidator validator) {
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        if (validator == null) {
            throw new IllegalArgumentException("validator cannot be null");
        }
        this.config = config;
        this.validator = validator;
        this.deduplicationFilter = new SqlDeduplicationFilter();
    }

    /**
     * Sets the current datasource name (extracted from connection URL).
     *
     * @param datasource the datasource name
     */
    public void setCurrentDatasource(String datasource) {
        this.currentDatasource = datasource != null ? datasource : "default";
    }

    /**
     * Gets the last validation result for the current thread.
     *
     * @return the last ValidationResult, or null if none
     */
    public static ValidationResult getLastResult() {
        return LAST_RESULT.get();
    }

    /**
     * Clears the last validation result for the current thread.
     */
    public static void clearLastResult() {
        LAST_RESULT.remove();
    }

    /**
     * Checks if there's a pending exception to throw.
     *
     * @return the pending SQLException, or null if none
     */
    public static SQLException getPendingException() {
        return PENDING_EXCEPTION.get();
    }

    /**
     * Clears any pending exception for the current thread.
     */
    public static void clearPendingException() {
        PENDING_EXCEPTION.remove();
    }

    // ========== Template Method Implementations ==========

    @Override
    protected void beforeValidation(String sql, Object... params) {
        // Check if interceptor is enabled
        if (!config.isEnabled()) {
            logger.debug("P6Spy interceptor disabled, skipping validation");
            return;
        }

        // Clear any previous results
        LAST_RESULT.remove();
        PENDING_EXCEPTION.remove();

        logger.debug("P6Spy beforeValidation: SQL length={}", sql != null ? sql.length() : 0);
    }

    @Override
    protected SqlContext buildSqlContext(String sql, Object... params) {
        // Handle null or empty SQL
        if (sql == null || sql.trim().isEmpty()) {
            logger.debug("Empty SQL, skipping context build");
            return null;
        }

        // Check deduplication
        if (!deduplicationFilter.shouldCheck(sql)) {
            logger.debug("SQL deduplicated, skipping validation");
            return null;
        }

        // Check exclude patterns
        if (config.shouldExclude(sql)) {
            logger.debug("SQL matches exclude pattern, skipping validation");
            return null;
        }

        // Build context using common SqlContextBuilder
        return SqlContextBuilder.buildContext(sql, params, currentDatasource, INTERCEPTOR_TYPE);
    }

    @Override
    protected ValidationResult validate(SqlContext context) {
        if (context == null) {
            return ValidationResult.pass();
        }

        ValidationResult result = validator.validate(context);
        LAST_RESULT.set(result);
        return result;
    }

    @Override
    protected void handleViolation(ValidationResult result) {
        if (result == null || result.isPassed()) {
            return;
        }

        ViolationStrategy strategy = config.getStrategy();
        String violationMsg = formatViolation(result);

        switch (strategy) {
            case BLOCK:
                logger.error("[BLOCK] SQL Safety Violation: {}", violationMsg);
                // Store exception to be thrown by caller
                PENDING_EXCEPTION.set(new SQLException(
                    "SQL Safety Violation (BLOCK): " + violationMsg,
                    "42000" // SQLState for syntax/access rule violation
                ));
                break;

            case WARN:
                logger.error("[WARN] SQL Safety Violation: {}", violationMsg);
                break;

            case LOG:
                logger.warn("[LOG] SQL Safety Violation: {}", violationMsg);
                break;

            default:
                logger.warn("[UNKNOWN] SQL Safety Violation: {}", violationMsg);
                break;
        }
    }

    @Override
    protected void afterValidation(ValidationResult result) {
        if (result != null) {
            logger.debug("P6Spy afterValidation: passed={}, violations={}",
                result.isPassed(),
                result.getViolations() != null ? result.getViolations().size() : 0);
        }
    }

    @Override
    protected void onError(Exception e) {
        logger.error("Error during P6Spy SQL interception", e);
    }

    // ========== Helper Methods ==========

    /**
     * Formats violation information for logging.
     *
     * @param result the validation result
     * @return formatted violation message
     */
    private String formatViolation(ValidationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Risk: ").append(result.getRiskLevel());
        sb.append(" | Violations: [");

        if (result.getViolations() != null) {
            boolean first = true;
            for (com.footstone.sqlguard.core.model.ViolationInfo violation : result.getViolations()) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(violation.getMessage());
                if (violation.getSuggestion() != null && !violation.getSuggestion().isEmpty()) {
                    sb.append(" (Suggestion: ").append(violation.getSuggestion()).append(")");
                }
                first = false;
            }
        }

        sb.append("]");
        return sb.toString();
    }

    /**
     * Creates a DefaultSqlSafetyValidator with default configuration.
     *
     * @return configured validator instance
     */
    private static DefaultSqlSafetyValidator createDefaultValidator() {
        // Create JSqlParser facade
        JSqlParserFacade facade = new JSqlParserFacade(true); // lenient mode

        // Create all available rule checkers with default configs
        List<RuleChecker> checkers = new ArrayList<>();
        checkers.add(new NoWhereClauseChecker(new NoWhereClauseConfig(true)));
        checkers.add(new DummyConditionChecker(new DummyConditionConfig(true)));
        checkers.add(new BlacklistFieldChecker(new BlacklistFieldsConfig()));
        checkers.add(new WhitelistFieldChecker(new WhitelistFieldsConfig()));

        // Create orchestrator
        RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);

        // Create deduplication filter
        SqlDeduplicationFilter deduplicationFilter = new SqlDeduplicationFilter();

        // Create validator
        return new DefaultSqlSafetyValidator(facade, checkers, orchestrator, deduplicationFilter);
    }

    /**
     * Gets the configuration.
     *
     * @return the P6Spy interceptor configuration
     */
    public P6SpyInterceptorConfig getConfig() {
        return config;
    }
}

