package com.footstone.sqlguard.interceptor.jdbc.common;

import java.util.List;

/**
 * Configuration interface for JDBC interceptors.
 *
 * <p>JdbcInterceptorConfig defines the common configuration properties required by all
 * JDBC interceptors (Druid, HikariCP, P6Spy). Pool-specific configurations should extend
 * this interface to add their own properties.</p>
 *
 * <h2>Core Configuration Properties</h2>
 * <ul>
 *   <li>{@link #isEnabled()} - Enable/disable the interceptor</li>
 *   <li>{@link #getStrategy()} - Violation handling strategy (BLOCK/WARN/LOG)</li>
 *   <li>{@link #isAuditEnabled()} - Enable/disable audit logging</li>
 *   <li>{@link #getExcludePatterns()} - SQL patterns to exclude from validation</li>
 * </ul>
 *
 * <h2>Pool-Specific Extensions</h2>
 * <p>Each connection pool module should define its own config interface extending this one:</p>
 * <ul>
 *   <li>{@code DruidInterceptorConfig} - adds filterPosition</li>
 *   <li>{@code HikariInterceptorConfig} - adds leakDetectionThreshold</li>
 *   <li>{@code P6SpyInterceptorConfig} - adds systemPropertyConfig</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * public class MyInterceptorConfig implements JdbcInterceptorConfig {
 *     private boolean enabled = true;
 *     private ViolationStrategy strategy = ViolationStrategy.WARN;
 *     private boolean auditEnabled = true;
 *     private List<String> excludePatterns = Collections.emptyList();
 *
 *     // ... getters and setters
 * }
 * }</pre>
 *
 * @since 2.0.0
 * @see ViolationStrategy
 * @see JdbcInterceptorBase
 */
public interface JdbcInterceptorConfig {

    /**
     * Returns whether the interceptor is enabled.
     *
     * <p>When disabled, the interceptor should pass through all SQL
     * without validation.</p>
     *
     * @return true if interceptor is enabled, false otherwise
     */
    boolean isEnabled();

    /**
     * Returns the violation handling strategy.
     *
     * <p>Determines how violations are handled when detected:</p>
     * <ul>
     *   <li>{@link ViolationStrategy#BLOCK} - Block execution</li>
     *   <li>{@link ViolationStrategy#WARN} - Log error and continue</li>
     *   <li>{@link ViolationStrategy#LOG} - Log warning and continue</li>
     * </ul>
     *
     * @return the violation strategy, never null
     */
    ViolationStrategy getStrategy();

    /**
     * Returns whether audit logging is enabled.
     *
     * <p>When enabled, all SQL executions (both passed and failed)
     * will be recorded to the audit log.</p>
     *
     * @return true if audit logging is enabled, false otherwise
     */
    boolean isAuditEnabled();

    /**
     * Returns patterns for SQL to exclude from validation.
     *
     * <p>SQL matching any of these patterns will be allowed without
     * validation. Useful for excluding system queries, health checks,
     * or known safe queries.</p>
     *
     * <p>Pattern syntax depends on the implementation (glob, regex, etc.)</p>
     *
     * @return list of exclude patterns, never null (may be empty)
     */
    List<String> getExcludePatterns();

    // ========== Default Methods for Backward Compatibility ==========

    /**
     * Returns the default violation strategy.
     *
     * <p>Implementations can override this to provide a different default.</p>
     *
     * @return default strategy (WARN)
     */
    default ViolationStrategy getDefaultStrategy() {
        return ViolationStrategy.WARN;
    }

    /**
     * Checks if a SQL statement should be excluded from validation.
     *
     * <p>Default implementation checks if SQL matches any exclude pattern.</p>
     *
     * @param sql the SQL to check
     * @return true if SQL should be excluded, false otherwise
     */
    default boolean shouldExclude(String sql) {
        if (sql == null || sql.isEmpty()) {
            return false;
        }
        List<String> patterns = getExcludePatterns();
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (sql.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}







