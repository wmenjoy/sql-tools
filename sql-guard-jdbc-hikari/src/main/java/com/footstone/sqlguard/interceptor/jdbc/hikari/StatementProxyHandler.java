package com.footstone.sqlguard.interceptor.jdbc.hikari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Layer 3: Statement proxy handler for HikariCP interception.
 *
 * <p>StatementProxyHandler is the final layer of the three-layer proxy chain.
 * For plain Statement objects, it intercepts execute methods to validate SQL
 * at execution time. For PreparedStatement/CallableStatement, SQL was already
 * validated at prepare time, so execute methods just pass through.</p>
 *
 * <h2>Proxy Chain Position</h2>
 * <pre>
 * DataSourceProxyHandler (Layer 1)
 *     ↓ wraps returned Connection
 * ConnectionProxyHandler (Layer 2)
 *     ↓ wraps returned Statement/PreparedStatement
 * StatementProxyHandler (Layer 3) ← You are here
 *     ↓ triggers SQL validation
 * HikariJdbcInterceptor
 * </pre>
 *
 * <h2>Intercepted Methods (for plain Statement only)</h2>
 * <ul>
 *   <li>{@code execute(String sql)} - Validates SQL before execution</li>
 *   <li>{@code executeQuery(String sql)} - Validates SQL before execution</li>
 *   <li>{@code executeUpdate(String sql)} - Validates SQL before execution</li>
 *   <li>{@code executeLargeUpdate(String sql)} - Validates SQL before execution</li>
 *   <li>{@code addBatch(String sql)} - Validates SQL before adding to batch</li>
 * </ul>
 *
 * <h2>PreparedStatement/CallableStatement Behavior</h2>
 * <p>For PreparedStatement and CallableStatement, SQL was validated at prepare time
 * by ConnectionProxyHandler. Execute methods simply delegate to the target without
 * re-validation, as the SQL is immutable.</p>
 *
 * @since 2.0.0
 * @see ConnectionProxyHandler
 * @see HikariJdbcInterceptor
 */
public class StatementProxyHandler implements InvocationHandler {

    private static final Logger logger = LoggerFactory.getLogger(StatementProxyHandler.class);

    private final Statement target;
    private final String preparedSql; // null for plain Statement
    private final HikariJdbcInterceptor interceptor;

    /**
     * Constructs a StatementProxyHandler.
     *
     * @param target the original Statement to wrap
     * @param preparedSql the SQL that was prepared (null for plain Statement)
     * @param interceptor the HikariCP interceptor for SQL validation
     * @throws IllegalArgumentException if target or interceptor is null
     */
    public StatementProxyHandler(Statement target, String preparedSql,
                                  HikariJdbcInterceptor interceptor) {
        if (target == null) {
            throw new IllegalArgumentException("target Statement cannot be null");
        }
        if (interceptor == null) {
            throw new IllegalArgumentException("interceptor cannot be null");
        }
        this.target = target;
        this.preparedSql = preparedSql;
        this.interceptor = interceptor;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        try {
            // For plain Statement (preparedSql is null), intercept execute methods
            if (preparedSql == null) {
                // execute(String sql, ...)
                if ("execute".equals(methodName) && args != null && args.length > 0
                        && args[0] instanceof String) {
                    String sql = (String) args[0];
                    validateSql(sql);
                }

                // executeQuery(String sql)
                if ("executeQuery".equals(methodName) && args != null && args.length > 0
                        && args[0] instanceof String) {
                    String sql = (String) args[0];
                    validateSql(sql);
                }

                // executeUpdate(String sql, ...)
                if ("executeUpdate".equals(methodName) && args != null && args.length > 0
                        && args[0] instanceof String) {
                    String sql = (String) args[0];
                    validateSql(sql);
                }

                // executeLargeUpdate(String sql, ...)
                if ("executeLargeUpdate".equals(methodName) && args != null && args.length > 0
                        && args[0] instanceof String) {
                    String sql = (String) args[0];
                    validateSql(sql);
                }

                // addBatch(String sql) for plain Statement
                if ("addBatch".equals(methodName) && args != null && args.length > 0
                        && args[0] instanceof String) {
                    String sql = (String) args[0];
                    validateSql(sql);
                }
            }

            // Handle standard Object methods
            if ("toString".equals(methodName)) {
                return "HikariStatementProxy[" + target.toString() + "]";
            }
            if ("hashCode".equals(methodName)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }

            // Handle unwrap for JDBC 4.0 compatibility
            if ("unwrap".equals(methodName)) {
                Class<?> iface = (Class<?>) args[0];
                if (iface.isInstance(target)) {
                    return target;
                }
                return method.invoke(target, args);
            }

            if ("isWrapperFor".equals(methodName)) {
                Class<?> iface = (Class<?>) args[0];
                if (iface.isInstance(target)) {
                    return true;
                }
                return method.invoke(target, args);
            }

            // Delegate all methods to the target Statement
            return method.invoke(target, args);

        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /**
     * Validates SQL using the interceptor.
     *
     * @param sql the SQL to validate
     * @throws SQLException if validation fails and strategy is BLOCK
     */
    private void validateSql(String sql) throws SQLException {
        // Check if we should skip validation
        if (interceptor.shouldSkip(sql)) {
            return;
        }

        // Run validation through interceptor
        interceptor.interceptSql(sql);

        // Check if blocking exception was set
        SQLException pendingEx = HikariJdbcInterceptor.getPendingException();
        if (pendingEx != null) {
            HikariJdbcInterceptor.clearPendingException();
            throw pendingEx;
        }
    }

    /**
     * Gets the target Statement.
     *
     * @return the wrapped Statement
     */
    public Statement getTarget() {
        return target;
    }

    /**
     * Gets the prepared SQL (if this wraps a PreparedStatement).
     *
     * @return the SQL, or null for plain Statement
     */
    public String getPreparedSql() {
        return preparedSql;
    }
}






