package com.footstone.sqlguard.interceptor.jdbc.p6spy;

import com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.JdbcEventListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * P6Spy module for SQL safety validation via SPI registration.
 *
 * <p>P6SpySqlSafetyModule serves as the entry point for P6Spy's module system, extending
 * JdbcEventListener and delegating to P6SpySqlSafetyListener for actual SQL validation.
 * This module is automatically discovered and loaded by P6Spy through Java's ServiceLoader
 * mechanism when registered in META-INF/services/com.p6spy.engine.spy.P6Factory.</p>
 *
 * <h2>SPI Registration</h2>
 * <p>P6Spy uses Java SPI to automatically discover custom modules:</p>
 * <ol>
 *   <li>P6Spy engine calls {@code ServiceLoader.load(P6Factory.class)} on startup</li>
 *   <li>ServiceLoader reads META-INF/services/com.p6spy.engine.spy.P6Factory</li>
 *   <li>Finds P6SpySqlSafetyModule class name and instantiates it</li>
 *   <li>Our module is registered and active for all JDBC operations</li>
 * </ol>
 *
 * <h2>Configuration</h2>
 * <pre>
 * # spy.properties
 * modulelist=com.p6spy.engine.spy.P6SpyFactory,com.footstone.sqlguard.interceptor.jdbc.p6spy.P6SpySqlSafetyModule
 * appender=com.p6spy.engine.spy.appender.Slf4JLogger
 * driverlist=org.h2.Driver
 *
 * # System property for violation strategy (optional)
 * -Dsqlguard.p6spy.strategy=BLOCK
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. The static listener instance is initialized once and
 * shared across all threads.</p>
 *
 * @since 2.0.0
 * @see P6SpySqlSafetyListener
 * @see JdbcEventListener
 */
public class P6SpySqlSafetyModule extends JdbcEventListener {

    private static final Logger logger = LoggerFactory.getLogger(P6SpySqlSafetyModule.class);

    /**
     * System property key for enabled flag.
     */
    private static final String ENABLED_PROPERTY = "sqlguard.p6spy.enabled";

    /**
     * System property key for violation strategy.
     */
    private static final String STRATEGY_PROPERTY = "sqlguard.p6spy.strategy";

    /**
     * System property key for audit enabled flag.
     */
    private static final String AUDIT_PROPERTY = "sqlguard.p6spy.audit.enabled";

    /**
     * System property key for parameterized SQL logging.
     */
    private static final String LOG_PARAMS_PROPERTY = "sqlguard.p6spy.logParameterizedSql";

    /**
     * Shared listener instance for SQL validation.
     */
    private static volatile P6SpySqlSafetyListener listener;

    /**
     * Configuration loaded from system properties.
     */
    private static volatile P6SpyInterceptorConfig config;

    /**
     * Static initialization block to load configuration and create listener.
     */
    static {
        try {
            logger.info("Initializing P6Spy SQL Safety Module...");

            // Load configuration from system properties
            config = loadConfig();

            // Create interceptor and listener
            P6SpyJdbcInterceptor interceptor = new P6SpyJdbcInterceptor(config);
            listener = new P6SpySqlSafetyListener(interceptor, config);

            logger.info("P6Spy SQL Safety Module initialized successfully with strategy: {}",
                config.getStrategy());
        } catch (Exception e) {
            logger.error("Failed to initialize P6Spy SQL Safety Module", e);
            // Don't throw - allow P6Spy to continue without our module
            listener = null;
        }
    }

    /**
     * Default constructor required by SPI.
     */
    public P6SpySqlSafetyModule() {
        // Module instantiated by P6Spy via ServiceLoader
    }

    /**
     * Delegates to P6SpySqlSafetyListener for SQL validation.
     *
     * @param statementInfo P6Spy statement information
     * @throws SQLException if validation fails and strategy is BLOCK
     */
    public void onBeforeAnyExecute(StatementInformation statementInfo) throws SQLException {
        if (listener != null) {
            listener.onBeforeAnyExecute(statementInfo);
        }
    }

    /**
     * Delegates to P6SpySqlSafetyListener for post-execution handling.
     *
     * @param statementInfo P6Spy statement information
     * @param timeElapsedNanos execution time in nanoseconds
     * @param e exception if execution failed
     */
    public void onAfterAnyExecute(StatementInformation statementInfo, long timeElapsedNanos, SQLException e) {
        if (listener != null) {
            listener.onAfterAnyExecute(statementInfo, timeElapsedNanos, e);
        }
    }

    // ========== Configuration Loading ==========

    /**
     * Loads configuration from system properties.
     *
     * @return P6SpyInterceptorConfig instance
     */
    private static P6SpyInterceptorConfig loadConfig() {
        return new P6SpyInterceptorConfig() {
            private final boolean enabled = Boolean.parseBoolean(
                System.getProperty(ENABLED_PROPERTY, "true"));
            private final ViolationStrategy strategy = loadStrategy();
            private final boolean auditEnabled = Boolean.parseBoolean(
                System.getProperty(AUDIT_PROPERTY, "false"));
            private final boolean logParameterizedSql = Boolean.parseBoolean(
                System.getProperty(LOG_PARAMS_PROPERTY, "true"));

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
                return Collections.emptyList();
            }

            @Override
            public String getPropertyPrefix() {
                return "sqlguard.p6spy";
            }

            @Override
            public boolean isLogParameterizedSql() {
                return logParameterizedSql;
            }
        };
    }

    /**
     * Loads ViolationStrategy from system property.
     *
     * @return ViolationStrategy instance
     */
    private static ViolationStrategy loadStrategy() {
        String strategyValue = System.getProperty(STRATEGY_PROPERTY, "LOG");
        try {
            ViolationStrategy strategy = ViolationStrategy.valueOf(strategyValue.toUpperCase());
            logger.info("Loaded ViolationStrategy from system property: {}", strategy);
            return strategy;
        } catch (IllegalArgumentException e) {
            logger.warn(
                "Invalid violation strategy '{}', defaulting to LOG. Valid values: BLOCK, WARN, LOG",
                strategyValue);
            return ViolationStrategy.LOG;
        }
    }

    // ========== Testing Support ==========

    /**
     * Gets the shared listener instance for testing purposes.
     *
     * @return P6SpySqlSafetyListener instance or null if initialization failed
     */
    static P6SpySqlSafetyListener getListener() {
        return listener;
    }

    /**
     * Gets the current configuration for testing purposes.
     *
     * @return P6SpyInterceptorConfig instance
     */
    static P6SpyInterceptorConfig getConfig() {
        return config;
    }

    /**
     * Resets the module for testing (allows re-initialization).
     *
     * <p><strong>WARNING:</strong> This method is intended for testing only.</p>
     *
     * @param newListener the new listener instance
     * @param newConfig the new configuration
     */
    static void resetForTesting(P6SpySqlSafetyListener newListener, P6SpyInterceptorConfig newConfig) {
        listener = newListener;
        config = newConfig;
    }
}







