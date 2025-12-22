package com.footstone.sqlguard.interceptor.jdbc.druid;

import com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorConfig;

/**
 * Configuration interface for Druid-specific SQL interceptors.
 *
 * <p>DruidInterceptorConfig extends the common {@link JdbcInterceptorConfig} interface
 * with Druid-specific configuration properties such as filter position and connection
 * proxy settings.</p>
 *
 * <h2>Inherited Properties</h2>
 * <ul>
 *   <li>{@link #isEnabled()} - Enable/disable the interceptor</li>
 *   <li>{@link #getStrategy()} - Violation handling strategy (BLOCK/WARN/LOG)</li>
 *   <li>{@link #isAuditEnabled()} - Enable/disable audit logging</li>
 *   <li>{@link #getExcludePatterns()} - SQL patterns to exclude from validation</li>
 * </ul>
 *
 * <h2>Druid-Specific Properties</h2>
 * <ul>
 *   <li>{@link #getFilterPosition()} - Position in Druid's filter chain</li>
 *   <li>{@link #isConnectionProxyEnabled()} - Enable connection-level interception</li>
 * </ul>
 *
 * <h2>Filter Position Strategy</h2>
 * <p>Druid executes filters in the order they appear in the filter list. The filter
 * position determines when SQL Guard validation occurs relative to other Druid filters:</p>
 * <ul>
 *   <li><strong>Position 0</strong>: Execute first (before StatFilter, WallFilter)</li>
 *   <li><strong>Position -1</strong>: Execute last (after all other filters)</li>
 *   <li><strong>Custom position</strong>: Fine-grained control for specific use cases</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * @ConfigurationProperties("sqlguard.druid")
 * public class DruidSqlGuardProperties implements DruidInterceptorConfig {
 *     private boolean enabled = true;
 *     private ViolationStrategy strategy = ViolationStrategy.WARN;
 *     private boolean auditEnabled = true;
 *     private List<String> excludePatterns = new ArrayList<>();
 *     private int filterPosition = 0;
 *     private boolean connectionProxyEnabled = true;
 *
 *     // getters and setters...
 * }
 * }</pre>
 *
 * @since 2.0.0
 * @see JdbcInterceptorConfig
 * @see DruidJdbcInterceptor
 * @see DruidSqlSafetyFilter
 */
public interface DruidInterceptorConfig extends JdbcInterceptorConfig {

    /**
     * Returns the filter position in Druid's filter chain.
     *
     * <p>This determines when the SQL Guard filter executes relative to other
     * Druid filters (e.g., StatFilter, WallFilter).</p>
     *
     * <p><strong>Position values:</strong></p>
     * <ul>
     *   <li><strong>0</strong>: Execute first (before other filters)</li>
     *   <li><strong>-1</strong>: Execute last (after all other filters)</li>
     *   <li><strong>Positive</strong>: Specific position in filter list</li>
     * </ul>
     *
     * <p><strong>Default:</strong> 0 (execute first)</p>
     *
     * @return filter position in Druid's filter chain
     */
    int getFilterPosition();

    /**
     * Returns whether connection-level proxy interception is enabled.
     *
     * <p>When enabled, SQL Guard will intercept at the ConnectionProxy level,
     * catching SQL before Statement/PreparedStatement creation. This provides
     * earlier interception but may impact performance.</p>
     *
     * <p><strong>Default:</strong> true</p>
     *
     * @return true if connection proxy interception is enabled, false otherwise
     */
    boolean isConnectionProxyEnabled();

    // ========== Default Methods for Common Defaults ==========

    /**
     * Returns the default filter position.
     *
     * @return default position (0 = first)
     */
    default int getDefaultFilterPosition() {
        return 0;
    }

    /**
     * Returns whether connection proxy interception is enabled by default.
     *
     * @return true by default
     */
    default boolean isConnectionProxyEnabledByDefault() {
        return true;
    }
}

