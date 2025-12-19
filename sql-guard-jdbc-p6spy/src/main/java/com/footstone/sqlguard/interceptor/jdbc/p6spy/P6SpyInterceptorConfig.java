package com.footstone.sqlguard.interceptor.jdbc.p6spy;

import com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorConfig;

/**
 * P6Spy-specific configuration interface extending the common JDBC interceptor config.
 *
 * <p>P6SpyInterceptorConfig adds P6Spy-specific configuration properties to the base
 * JdbcInterceptorConfig. Due to P6Spy's architecture, configuration must be provided
 * through system properties or spy.properties file rather than programmatic APIs.</p>
 *
 * <h2>Configuration Methods</h2>
 * <ol>
 *   <li><strong>System Properties</strong>: -Dsqlguard.p6spy.enabled=true</li>
 *   <li><strong>spy.properties</strong>: Place on classpath with configuration</li>
 * </ol>
 *
 * <h2>P6Spy-Specific Properties</h2>
 * <ul>
 *   <li>{@link #getPropertyPrefix()} - System property prefix (default: sqlguard.p6spy)</li>
 *   <li>{@link #isLogParameterizedSql()} - Whether to log SQL with parameter values</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // System properties
 * System.setProperty("sqlguard.p6spy.enabled", "true");
 * System.setProperty("sqlguard.p6spy.strategy", "WARN");
 *
 * // Or spy.properties file
 * sqlguard.p6spy.enabled=true
 * sqlguard.p6spy.strategy=WARN
 * sqlguard.p6spy.audit.enabled=false
 * sqlguard.p6spy.logParameterizedSql=true
 * }</pre>
 *
 * @since 2.0.0
 * @see JdbcInterceptorConfig
 */
public interface P6SpyInterceptorConfig extends JdbcInterceptorConfig {

    /**
     * Default property prefix for P6Spy SQL Guard configuration.
     */
    String DEFAULT_PROPERTY_PREFIX = "sqlguard.p6spy";

    /**
     * Returns the system property prefix for P6Spy configuration.
     *
     * <p>P6Spy does NOT support programmatic configuration APIs. All configuration
     * must be provided through system properties or the spy.properties file. This
     * prefix is used to namespace SQL Guard properties.</p>
     *
     * <p><strong>Property Format:</strong></p>
     * <ul>
     *   <li>{prefix}.enabled - Enable/disable SQL Guard interception</li>
     *   <li>{prefix}.strategy - Violation handling strategy (BLOCK/WARN/LOG)</li>
     *   <li>{prefix}.audit.enabled - Enable/disable audit logging</li>
     *   <li>{prefix}.logParameterizedSql - Log SQL with parameter values</li>
     * </ul>
     *
     * @return property prefix (default: "sqlguard.p6spy")
     */
    String getPropertyPrefix();

    /**
     * Returns whether to log actual SQL with parameter values substituted.
     *
     * <p>P6Spy provides two methods for SQL extraction:</p>
     * <ul>
     *   <li>{@code getSqlWithValues()} - Returns SQL with parameter values substituted</li>
     *   <li>{@code getSql()} - Returns SQL template with ? placeholders</li>
     * </ul>
     *
     * <p>When true, SQL Guard uses {@code getSqlWithValues()} for validation,
     * providing more accurate detection of SQL injection and dummy conditions.</p>
     *
     * <p><strong>Security Consideration:</strong> Parameter values may contain sensitive
     * data. Enable only in secure logging environments.</p>
     *
     * @return true to log parameterized SQL with values, false for template SQL
     */
    boolean isLogParameterizedSql();

    // ========== Default Implementations ==========

    /**
     * Returns the default property prefix.
     *
     * <p>Override this method to use a custom property prefix.</p>
     *
     * @return default property prefix
     */
    default String getDefaultPropertyPrefix() {
        return DEFAULT_PROPERTY_PREFIX;
    }

    /**
     * Builds the full property key with prefix.
     *
     * @param property the property name without prefix
     * @return full property key (e.g., "sqlguard.p6spy.enabled")
     */
    default String buildPropertyKey(String property) {
        return getPropertyPrefix() + "." + property;
    }
}
