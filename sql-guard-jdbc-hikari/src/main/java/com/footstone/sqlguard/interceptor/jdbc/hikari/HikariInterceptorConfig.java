package com.footstone.sqlguard.interceptor.jdbc.hikari;

import com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorConfig;
import com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy;

import java.util.List;

/**
 * Configuration interface for HikariCP JDBC interceptors.
 *
 * <p>HikariInterceptorConfig extends the common JdbcInterceptorConfig interface
 * to add HikariCP-specific configuration properties. This allows users to customize
 * HikariCP interceptor behavior beyond the standard JDBC options.</p>
 *
 * <h2>HikariCP-Specific Properties</h2>
 * <ul>
 *   <li>{@link #isProxyConnectionEnabled()} - Enable/disable proxy-based interception</li>
 *   <li>{@link #getLeakDetectionThreshold()} - Connection leak detection threshold</li>
 * </ul>
 *
 * <h2>Inherited Properties</h2>
 * <ul>
 *   <li>{@link #isEnabled()} - Enable/disable the interceptor</li>
 *   <li>{@link #getStrategy()} - Violation handling strategy (BLOCK/WARN/LOG)</li>
 *   <li>{@link #isAuditEnabled()} - Enable/disable audit logging</li>
 *   <li>{@link #getExcludePatterns()} - SQL patterns to exclude from validation</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * public class MyHikariConfig implements HikariInterceptorConfig {
 *     private boolean enabled = true;
 *     private ViolationStrategy strategy = ViolationStrategy.WARN;
 *     private boolean auditEnabled = true;
 *     private List<String> excludePatterns = Collections.emptyList();
 *     private boolean proxyConnectionEnabled = true;
 *     private long leakDetectionThreshold = 0L;
 *
 *     // ... implement all methods
 * }
 * }</pre>
 *
 * @since 2.0.0
 * @see JdbcInterceptorConfig
 * @see ViolationStrategy
 */
public interface HikariInterceptorConfig extends JdbcInterceptorConfig {

    /**
     * Returns whether proxy-based connection interception is enabled.
     *
     * <p>When enabled, HikariCP connections are wrapped with JDK dynamic proxies
     * to intercept SQL execution. When disabled, no proxying occurs and SQL
     * passes through without validation.</p>
     *
     * <p>Default is {@code true}.</p>
     *
     * @return true if proxy interception is enabled, false otherwise
     */
    boolean isProxyConnectionEnabled();

    /**
     * Returns the connection leak detection threshold in milliseconds.
     *
     * <p>This mirrors HikariCP's own leak detection mechanism. When set to a
     * positive value, the interceptor will log a warning if a connection is
     * held open longer than this threshold.</p>
     *
     * <p>Set to 0 to disable leak detection (default).</p>
     *
     * <p><strong>Note:</strong> This is separate from HikariCP's built-in
     * leak detection. Both can be used together for additional monitoring.</p>
     *
     * @return threshold in milliseconds, 0 means disabled
     */
    long getLeakDetectionThreshold();

    // ========== Default Implementations for Convenience ==========

    /**
     * Creates a simple configuration with default values.
     *
     * <p>Default values:</p>
     * <ul>
     *   <li>enabled: true</li>
     *   <li>strategy: WARN</li>
     *   <li>auditEnabled: false</li>
     *   <li>excludePatterns: empty list</li>
     *   <li>proxyConnectionEnabled: true</li>
     *   <li>leakDetectionThreshold: 0 (disabled)</li>
     * </ul>
     *
     * @return default configuration instance
     */
    static HikariInterceptorConfig defaults() {
        return new DefaultHikariInterceptorConfig();
    }

    /**
     * Creates a configuration builder.
     *
     * @return new builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Default implementation of HikariInterceptorConfig.
     */
    class DefaultHikariInterceptorConfig implements HikariInterceptorConfig {
        private final boolean enabled;
        private final ViolationStrategy strategy;
        private final boolean auditEnabled;
        private final List<String> excludePatterns;
        private final boolean proxyConnectionEnabled;
        private final long leakDetectionThreshold;

        DefaultHikariInterceptorConfig() {
            this(true, ViolationStrategy.WARN, false, 
                 java.util.Collections.emptyList(), true, 0L);
        }

        DefaultHikariInterceptorConfig(boolean enabled, ViolationStrategy strategy,
                                        boolean auditEnabled, List<String> excludePatterns,
                                        boolean proxyConnectionEnabled, long leakDetectionThreshold) {
            this.enabled = enabled;
            this.strategy = strategy;
            this.auditEnabled = auditEnabled;
            this.excludePatterns = excludePatterns;
            this.proxyConnectionEnabled = proxyConnectionEnabled;
            this.leakDetectionThreshold = leakDetectionThreshold;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public ViolationStrategy getStrategy() {
            return strategy;
        }

        @Override
        public boolean isAuditEnabled() {
            return auditEnabled;
        }

        @Override
        public List<String> getExcludePatterns() {
            return excludePatterns;
        }

        @Override
        public boolean isProxyConnectionEnabled() {
            return proxyConnectionEnabled;
        }

        @Override
        public long getLeakDetectionThreshold() {
            return leakDetectionThreshold;
        }
    }

    /**
     * Builder for HikariInterceptorConfig.
     */
    class Builder {
        private boolean enabled = true;
        private ViolationStrategy strategy = ViolationStrategy.WARN;
        private boolean auditEnabled = false;
        private List<String> excludePatterns = java.util.Collections.emptyList();
        private boolean proxyConnectionEnabled = true;
        private long leakDetectionThreshold = 0L;

        /**
         * Sets whether the interceptor is enabled.
         *
         * @param enabled true to enable
         * @return this builder
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Sets the violation strategy.
         *
         * @param strategy the strategy to use
         * @return this builder
         */
        public Builder strategy(ViolationStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        /**
         * Sets whether audit logging is enabled.
         *
         * @param auditEnabled true to enable audit
         * @return this builder
         */
        public Builder auditEnabled(boolean auditEnabled) {
            this.auditEnabled = auditEnabled;
            return this;
        }

        /**
         * Sets SQL patterns to exclude from validation.
         *
         * @param excludePatterns patterns to exclude
         * @return this builder
         */
        public Builder excludePatterns(List<String> excludePatterns) {
            this.excludePatterns = excludePatterns;
            return this;
        }

        /**
         * Sets whether proxy-based connection interception is enabled.
         *
         * @param proxyConnectionEnabled true to enable proxy
         * @return this builder
         */
        public Builder proxyConnectionEnabled(boolean proxyConnectionEnabled) {
            this.proxyConnectionEnabled = proxyConnectionEnabled;
            return this;
        }

        /**
         * Sets the leak detection threshold.
         *
         * @param leakDetectionThreshold threshold in milliseconds
         * @return this builder
         */
        public Builder leakDetectionThreshold(long leakDetectionThreshold) {
            this.leakDetectionThreshold = leakDetectionThreshold;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return configured HikariInterceptorConfig
         */
        public HikariInterceptorConfig build() {
            return new DefaultHikariInterceptorConfig(enabled, strategy, auditEnabled,
                    excludePatterns, proxyConnectionEnabled, leakDetectionThreshold);
        }
    }
}








