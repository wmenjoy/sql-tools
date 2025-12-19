package com.footstone.sqlguard.interceptor.jdbc.p6spy;

import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.JdbcEventListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * P6Spy JDBC event listener providing universal SQL validation for any JDBC driver/connection pool.
 *
 * <p>P6SpySqlSafetyListener implements P6Spy's JdbcEventListener interface to intercept SQL
 * execution at the JDBC driver level. It composes {@link P6SpyJdbcInterceptor} for actual
 * validation logic, following the composition over inheritance pattern.</p>
 *
 * <h2>Architecture</h2>
 * <pre>
 * Application → Connection Pool (any) → P6Spy Proxy Driver → Real JDBC Driver
 *                                            ↓
 *                                  P6SpySqlSafetyListener.onBeforeAnyExecute()
 *                                            ↓
 *                                  P6SpyJdbcInterceptor.interceptSql()
 *                                            ↓
 *                                  SQL Validation Pipeline
 * </pre>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Universal JDBC interception - works with any driver (MySQL, PostgreSQL, H2, Oracle)</li>
 *   <li>Connection pool agnostic - supports C3P0, DBCP, Tomcat JDBC, bare JDBC</li>
 *   <li>Composition pattern - delegates to P6SpyJdbcInterceptor for validation</li>
 *   <li>Parameter-substituted SQL validation (P6Spy provides values)</li>
 *   <li>Configurable violation strategies (BLOCK/WARN/LOG)</li>
 * </ul>
 *
 * <h2>P6Spy Lifecycle Hooks</h2>
 * <ul>
 *   <li>{@link #onBeforeAnyExecute(StatementInformation)} - Called before any SQL execution</li>
 *   <li>{@link #onAfterAnyExecute(StatementInformation, long, SQLException)} - Called after execution</li>
 *   <li>{@link #onBeforeAddBatch(StatementInformation)} - Called before batch operations</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. The composed interceptor is immutable and uses ThreadLocal
 * for per-request state.</p>
 *
 * @since 2.0.0
 * @see JdbcEventListener
 * @see P6SpyJdbcInterceptor
 * @see P6SpySqlSafetyModule
 */
public class P6SpySqlSafetyListener extends JdbcEventListener {

    private static final Logger logger = LoggerFactory.getLogger(P6SpySqlSafetyListener.class);

    /**
     * Composed interceptor for SQL validation.
     */
    private final P6SpyJdbcInterceptor interceptor;

    /**
     * Configuration for this listener.
     */
    private final P6SpyInterceptorConfig config;

    /**
     * Constructs a P6SpySqlSafetyListener with the specified interceptor.
     *
     * @param interceptor the P6Spy JDBC interceptor for validation
     * @param config the P6Spy interceptor configuration
     * @throws IllegalArgumentException if interceptor or config is null
     */
    public P6SpySqlSafetyListener(P6SpyJdbcInterceptor interceptor, P6SpyInterceptorConfig config) {
        if (interceptor == null) {
            throw new IllegalArgumentException("interceptor cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        this.interceptor = interceptor;
        this.config = config;
    }

    /**
     * Intercepts SQL execution before any JDBC statement execution.
     *
     * <p>This method is called by P6Spy before executing any SQL statement (PreparedStatement,
     * Statement, CallableStatement). It extracts the SQL with parameter values substituted
     * and delegates to the composed interceptor for validation.</p>
     *
     * <p><strong>Execution Flow:</strong></p>
     * <ol>
     *   <li>Check if interceptor is enabled</li>
     *   <li>Extract SQL from StatementInformation (with or without parameter values)</li>
     *   <li>Extract datasource from connection URL</li>
     *   <li>Delegate to P6SpyJdbcInterceptor for validation</li>
     *   <li>Check for pending exception (BLOCK strategy)</li>
     *   <li>Throw SQLException if violation requires blocking</li>
     * </ol>
     *
     * @param statementInfo P6Spy statement information with SQL and connection context
     * @throws SQLException if validation fails and strategy is BLOCK
     */
    public void onBeforeAnyExecute(StatementInformation statementInfo) throws SQLException {
        // Check if interceptor is enabled
        if (!config.isEnabled()) {
            return;
        }

        // Extract SQL (with or without parameter values based on config)
        String sql = config.isLogParameterizedSql()
            ? statementInfo.getSqlWithValues()
            : statementInfo.getSql();

        // Skip if SQL is null or empty
        if (sql == null || sql.trim().isEmpty()) {
            return;
        }

        // Extract datasource from connection URL
        String datasource = extractDatasourceFromUrl(statementInfo);
        interceptor.setCurrentDatasource(datasource);

        // Delegate to interceptor for validation
        interceptor.interceptSql(sql);

        // Check for pending exception (BLOCK strategy)
        SQLException pendingException = P6SpyJdbcInterceptor.getPendingException();
        if (pendingException != null) {
            P6SpyJdbcInterceptor.clearPendingException();
            throw pendingException;
        }
    }

    /**
     * Called after any SQL execution completes.
     *
     * <p>This hook can be used for:</p>
     * <ul>
     *   <li>Performance metrics recording</li>
     *   <li>Audit logging (execution time, success/failure)</li>
     *   <li>Correlation of validation results with execution outcomes</li>
     * </ul>
     *
     * @param statementInfo P6Spy statement information
     * @param timeElapsedNanos execution time in nanoseconds
     * @param e exception if execution failed, null otherwise
     */
    public void onAfterAnyExecute(StatementInformation statementInfo, long timeElapsedNanos, SQLException e) {
        // Clear ThreadLocal state after execution
        P6SpyJdbcInterceptor.clearLastResult();

        if (logger.isDebugEnabled()) {
            logger.debug("SQL executed in {} ms, exception: {}",
                timeElapsedNanos / 1_000_000,
                e != null ? e.getMessage() : "none");
        }
    }

    /**
     * Called before batch operation is added.
     *
     * @param statementInfo P6Spy statement information
     */
    public void onBeforeAddBatch(StatementInformation statementInfo) {
        // Batch operations are validated per-statement
        // No special handling needed here
    }

    // ========== Helper Methods ==========

    /**
     * Extracts datasource identifier from connection URL.
     *
     * <p>Attempts to extract a meaningful datasource name from the JDBC URL.
     * Falls back to "unknown" if extraction fails.</p>
     *
     * @param statementInfo P6Spy statement information
     * @return datasource identifier or "unknown"
     */
    private String extractDatasourceFromUrl(StatementInformation statementInfo) {
        try {
            if (statementInfo.getConnectionInformation() != null) {
                String url = statementInfo.getConnectionInformation().getUrl();
                if (url != null) {
                    // Remove p6spy prefix if present
                    if (url.contains("p6spy:")) {
                        url = url.replace("p6spy:", "");
                    }

                    // Extract database name from JDBC URL
                    // Example: jdbc:mysql://localhost:3306/mydb → mydb
                    // Example: jdbc:h2:mem:test → test
                    if (url.contains("/")) {
                        String[] parts = url.split("/");
                        String lastPart = parts[parts.length - 1];
                        // Remove query parameters if present
                        if (lastPart.contains("?")) {
                            lastPart = lastPart.substring(0, lastPart.indexOf("?"));
                        }
                        // Remove trailing semicolon parameters
                        if (lastPart.contains(";")) {
                            lastPart = lastPart.substring(0, lastPart.indexOf(";"));
                        }
                        return lastPart.isEmpty() ? "unknown" : lastPart;
                    } else if (url.contains(":")) {
                        // Handle H2 in-memory URLs like jdbc:h2:mem:test
                        String[] parts = url.split(":");
                        if (parts.length > 0) {
                            return parts[parts.length - 1];
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to extract datasource from URL: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Gets the composed interceptor.
     *
     * @return the P6Spy JDBC interceptor
     */
    public P6SpyJdbcInterceptor getInterceptor() {
        return interceptor;
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
