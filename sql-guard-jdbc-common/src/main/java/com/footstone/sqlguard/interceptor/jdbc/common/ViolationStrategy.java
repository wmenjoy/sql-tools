package com.footstone.sqlguard.interceptor.jdbc.common;

/**
 * Unified strategy for handling SQL validation violations at runtime across all JDBC interceptors.
 *
 * <p>ViolationStrategy defines how JDBC interceptors (Druid, HikariCP, P6Spy) respond when SQL
 * validation violations are detected. This unified enum replaces the previously duplicated enums
 * in each pool-specific package, providing consistent behavior across all connection pool
 * implementations.</p>
 *
 * <h2>Strategy Behaviors</h2>
 * <ul>
 *   <li><strong>BLOCK</strong> - Throw SQLException, prevent SQL execution (fail-closed)</li>
 *   <li><strong>WARN</strong> - Log error level message, continue execution (fail-open)</li>
 *   <li><strong>LOG</strong> - Log warning level message, continue execution (observation mode)</li>
 * </ul>
 *
 * <h2>Deployment Recommendations</h2>
 * <ul>
 *   <li><strong>Development</strong>: BLOCK - catch issues early</li>
 *   <li><strong>Staging</strong>: WARN - identify violations without blocking</li>
 *   <li><strong>Production (initial rollout)</strong>: LOG - observe without impact</li>
 *   <li><strong>Production (mature)</strong>: BLOCK or WARN - enforce safety rules</li>
 * </ul>
 *
 * <h2>Migration from Legacy Enums</h2>
 * <p>This enum unifies the following legacy enums (all identical):</p>
 * <ul>
 *   <li>{@code com.footstone.sqlguard.interceptor.druid.ViolationStrategy} - Deprecated</li>
 *   <li>{@code com.footstone.sqlguard.interceptor.hikari.ViolationStrategy} - Deprecated</li>
 *   <li>{@code com.footstone.sqlguard.interceptor.p6spy.ViolationStrategy} - Deprecated</li>
 * </ul>
 *
 * <h3>Migration Example</h3>
 * <pre>{@code
 * // Old import (deprecated)
 * import com.footstone.sqlguard.interceptor.druid.ViolationStrategy;
 *
 * // New import (recommended)
 * import com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy;
 *
 * // Usage remains identical
 * ViolationStrategy strategy = ViolationStrategy.BLOCK;
 * }</pre>
 *
 * @since 2.0.0
 * @see JdbcInterceptorBase
 * @see JdbcInterceptorConfig
 */
public enum ViolationStrategy {

    /**
     * Block SQL execution by throwing SQLException.
     *
     * <p>When violations are detected:</p>
     * <ul>
     *   <li>Log error message with violation details</li>
     *   <li>Throw SQLException with SQLState "42000" (syntax/access rule violation)</li>
     *   <li>SQL execution is prevented</li>
     *   <li>Transaction is rolled back (if active)</li>
     * </ul>
     *
     * <p><strong>Use when:</strong> Zero tolerance for SQL safety violations</p>
     */
    BLOCK(true, true, "ERROR"),

    /**
     * Log error and continue SQL execution.
     *
     * <p>When violations are detected:</p>
     * <ul>
     *   <li>Log error message with violation details</li>
     *   <li>SQL execution continues normally</li>
     *   <li>No exception thrown</li>
     *   <li>Suitable for gradual rollout</li>
     * </ul>
     *
     * <p><strong>Use when:</strong> Need visibility but can't block production traffic</p>
     */
    WARN(false, true, "ERROR"),

    /**
     * Log warning and continue SQL execution.
     *
     * <p>When violations are detected:</p>
     * <ul>
     *   <li>Log warning message with violation details</li>
     *   <li>SQL execution continues normally</li>
     *   <li>No exception thrown</li>
     *   <li>Minimal logging noise</li>
     * </ul>
     *
     * <p><strong>Use when:</strong> Observation mode, collecting metrics</p>
     */
    LOG(false, true, "WARN");

    private final boolean shouldBlock;
    private final boolean shouldLog;
    private final String logLevel;

    /**
     * Constructs a ViolationStrategy with specified behavior.
     *
     * @param shouldBlock whether to block SQL execution by throwing exception
     * @param shouldLog whether to log the violation
     * @param logLevel the log level to use (ERROR or WARN)
     */
    ViolationStrategy(boolean shouldBlock, boolean shouldLog, String logLevel) {
        this.shouldBlock = shouldBlock;
        this.shouldLog = shouldLog;
        this.logLevel = logLevel;
    }

    /**
     * Returns whether this strategy should block SQL execution.
     *
     * <p>When true, the interceptor will throw a SQLException to prevent
     * the violating SQL from executing.</p>
     *
     * @return true if SQL execution should be blocked, false otherwise
     */
    public boolean shouldBlock() {
        return shouldBlock;
    }

    /**
     * Returns whether this strategy should log violations.
     *
     * <p>All strategies log violations by default. The log level
     * is determined by {@link #getLogLevel()}.</p>
     *
     * @return true if violations should be logged, false otherwise
     */
    public boolean shouldLog() {
        return shouldLog;
    }

    /**
     * Returns the log level for this strategy.
     *
     * @return "ERROR" for BLOCK and WARN strategies, "WARN" for LOG strategy
     */
    public String getLogLevel() {
        return logLevel;
    }
}






