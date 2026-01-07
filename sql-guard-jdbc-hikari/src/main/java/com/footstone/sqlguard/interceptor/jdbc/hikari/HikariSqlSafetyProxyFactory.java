package com.footstone.sqlguard.interceptor.jdbc.hikari;

import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;

/**
 * HikariCP-compatible SQL safety proxy factory using DataSource wrapping pattern.
 *
 * <p>Since HikariCP doesn't expose a ProxyFactory interface for custom proxies (it uses
 * Javassist bytecode generation internally), this implementation wraps the DataSource
 * to intercept Connection creation and then wraps Connection/Statement objects using
 * JDK dynamic proxies.</p>
 *
 * <h2>Three-Layer Proxy Architecture</h2>
 * <pre>
 * HikariDataSource (original)
 *     ↓ wrapped by
 * DataSourceProxy (Layer 1 - proxies DataSource.getConnection())
 *     ↓ returns
 * ConnectionProxy (Layer 2 - proxies Connection.prepareStatement())
 *     ↓ returns
 * StatementProxy (Layer 3 - proxies Statement.execute())
 *     ↓ triggers
 * SQL Validation (via HikariJdbcInterceptor)
 * </pre>
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li>Target overhead: &lt;1% for connection acquisition</li>
 *   <li>Target overhead: &lt;5% for SQL execution</li>
 *   <li>JDK dynamic proxies are optimized by JVM</li>
 *   <li>Compatible with HikariCP's microsecond-level performance goals</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * HikariConfig config = new HikariConfig();
 * config.setJdbcUrl("jdbc:mysql://localhost:3306/db");
 * HikariDataSource hikariDs = new HikariDataSource(config);
 *
 * // Wrap with SQL safety proxy
 * DataSource safeDs = HikariSqlSafetyProxyFactory.wrap(
 *     hikariDs,
 *     validator,
 *     ViolationStrategy.BLOCK
 * );
 * }</pre>
 *
 * <h2>Composition Pattern</h2>
 * <p>This factory creates proxy handlers that compose {@link HikariJdbcInterceptor},
 * which extends {@link com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorBase}.
 * This provides a clean separation between interception mechanics and validation logic.</p>
 *
 * @since 2.0.0
 * @see HikariJdbcInterceptor
 * @see DataSourceProxyHandler
 * @see ConnectionProxyHandler
 * @see StatementProxyHandler
 * @see ViolationStrategy
 */
public class HikariSqlSafetyProxyFactory {

    private static final Logger logger = LoggerFactory.getLogger(HikariSqlSafetyProxyFactory.class);

    /**
     * ThreadLocal storage for ValidationResult to coordinate with HikariSqlAuditProxyFactory.
     * <p>This allows audit logging to correlate pre-execution violations with post-execution results.</p>
     * <p>Lifecycle: Set by SafetyProxy during validation, read and cleared by AuditProxy after execution.</p>
     */
    private static final ThreadLocal<ValidationResult> VALIDATION_RESULT = new ThreadLocal<>();

    /**
     * Private constructor to prevent instantiation.
     */
    private HikariSqlSafetyProxyFactory() {
        // Static factory methods only
    }

    /**
     * Gets pre-execution validation result for current thread.
     * Used by HikariSqlAuditProxyFactory for violation correlation.
     *
     * @return the validation result for current thread, or null if not set
     */
    public static ValidationResult getValidationResult() {
        return VALIDATION_RESULT.get();
    }

    /**
     * Sets pre-execution validation result for current thread.
     * Should be called during SQL validation.
     *
     * @param result the validation result to store
     */
    static void setValidationResult(ValidationResult result) {
        VALIDATION_RESULT.set(result);
    }

    /**
     * Clears validation result for current thread.
     * Should be called by audit proxy after reading to prevent memory leaks.
     */
    public static void clearValidationResult() {
        VALIDATION_RESULT.remove();
    }

    /**
     * Wraps a DataSource (typically HikariDataSource) with SQL safety validation.
     *
     * <p>This is the primary entry point for adding SQL safety to a HikariCP DataSource.
     * The returned DataSource wraps all connections and statements with validation proxies.</p>
     *
     * @param dataSource the datasource to wrap (typically HikariDataSource)
     * @param validator the SQL safety validator
     * @param strategy the violation handling strategy
     * @return wrapped DataSource that validates SQL before execution
     * @throws IllegalArgumentException if any parameter is null
     */
    public static DataSource wrap(
            DataSource dataSource,
            DefaultSqlSafetyValidator validator,
            ViolationStrategy strategy) {
        
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource cannot be null");
        }
        if (validator == null) {
            throw new IllegalArgumentException("validator cannot be null");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("strategy cannot be null");
        }

        String datasourceName = dataSource.toString();
        
        logger.info("Wrapping DataSource [{}] with SQL safety validation (strategy: {})",
                   datasourceName, strategy);

        // Create HikariJdbcInterceptor with the validator and strategy
        HikariJdbcInterceptor interceptor = new HikariJdbcInterceptor(
                validator, strategy, datasourceName);

        // Create DataSource proxy using JDK dynamic proxy
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[] { DataSource.class },
                new DataSourceProxyHandler(dataSource, interceptor)
        );
    }

    /**
     * Wraps a DataSource with SQL safety validation using custom configuration.
     *
     * <p>This overload allows more fine-grained control over interceptor behavior.</p>
     *
     * @param dataSource the datasource to wrap
     * @param validator the SQL safety validator
     * @param config the interceptor configuration
     * @return wrapped DataSource that validates SQL before execution
     * @throws IllegalArgumentException if any parameter is null
     */
    public static DataSource wrap(
            DataSource dataSource,
            DefaultSqlSafetyValidator validator,
            HikariInterceptorConfig config) {
        
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource cannot be null");
        }
        if (validator == null) {
            throw new IllegalArgumentException("validator cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }

        String datasourceName = dataSource.toString();
        
        logger.info("Wrapping DataSource [{}] with SQL safety validation (config: enabled={}, strategy={})",
                   datasourceName, config.isEnabled(), config.getStrategy());

        // Create HikariJdbcInterceptor with full configuration
        HikariJdbcInterceptor interceptor = new HikariJdbcInterceptor(
                validator, config, datasourceName);

        // Create DataSource proxy
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[] { DataSource.class },
                new DataSourceProxyHandler(dataSource, interceptor)
        );
    }

    /**
     * Checks if a DataSource is already wrapped with SQL safety proxy.
     *
     * @param dataSource the DataSource to check
     * @return true if already wrapped, false otherwise
     */
    public static boolean isWrapped(DataSource dataSource) {
        if (dataSource == null) {
            return false;
        }
        
        // Check if it's a JDK proxy with our handler
        if (Proxy.isProxyClass(dataSource.getClass())) {
            Object handler = Proxy.getInvocationHandler(dataSource);
            return handler instanceof DataSourceProxyHandler;
        }
        
        return false;
    }

    /**
     * Unwraps a wrapped DataSource to get the original.
     *
     * @param dataSource the potentially wrapped DataSource
     * @return the original DataSource, or the input if not wrapped
     */
    public static DataSource unwrap(DataSource dataSource) {
        if (dataSource == null) {
            return null;
        }
        
        if (Proxy.isProxyClass(dataSource.getClass())) {
            Object handler = Proxy.getInvocationHandler(dataSource);
            if (handler instanceof DataSourceProxyHandler) {
                return ((DataSourceProxyHandler) handler).getTarget();
            }
        }
        
        return dataSource;
    }
}








