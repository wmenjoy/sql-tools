package com.footstone.sqlguard.interceptor.jdbc.hikari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Layer 2: Connection proxy handler for HikariCP interception.
 *
 * <p>ConnectionProxyHandler intercepts connection methods that create statements,
 * wrapping them with {@link StatementProxyHandler}. For PreparedStatement and
 * CallableStatement, SQL is captured at creation time for validation.</p>
 *
 * <h2>Proxy Chain Position</h2>
 * <pre>
 * DataSourceProxyHandler (Layer 1)
 *     ↓ wraps returned Connection
 * ConnectionProxyHandler (Layer 2) ← You are here
 *     ↓ wraps returned Statement/PreparedStatement
 * StatementProxyHandler (Layer 3)
 *     ↓ triggers SQL validation
 * HikariJdbcInterceptor
 * </pre>
 *
 * <h2>Intercepted Methods</h2>
 * <ul>
 *   <li>{@code prepareStatement(sql)} - Validates SQL, wraps PreparedStatement</li>
 *   <li>{@code prepareStatement(sql, ...)} - Validates SQL, wraps PreparedStatement</li>
 *   <li>{@code prepareCall(sql)} - Validates SQL, wraps CallableStatement</li>
 *   <li>{@code prepareCall(sql, ...)} - Validates SQL, wraps CallableStatement</li>
 *   <li>{@code createStatement()} - Wraps Statement (validation at execute time)</li>
 *   <li>{@code createStatement(...)} - Wraps Statement (validation at execute time)</li>
 * </ul>
 *
 * <h2>SQL Validation Points</h2>
 * <ul>
 *   <li><strong>PreparedStatement/CallableStatement:</strong> SQL validated at prepare time</li>
 *   <li><strong>Statement:</strong> SQL validated at execute time (via StatementProxyHandler)</li>
 * </ul>
 *
 * @since 2.0.0
 * @see DataSourceProxyHandler
 * @see StatementProxyHandler
 * @see HikariJdbcInterceptor
 */
public class ConnectionProxyHandler implements InvocationHandler {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionProxyHandler.class);

    private final Connection target;
    private final HikariJdbcInterceptor interceptor;

    /**
     * Constructs a ConnectionProxyHandler.
     *
     * @param target the original Connection to wrap
     * @param interceptor the HikariCP interceptor for SQL validation
     * @throws IllegalArgumentException if target or interceptor is null
     */
    public ConnectionProxyHandler(Connection target, HikariJdbcInterceptor interceptor) {
        if (target == null) {
            throw new IllegalArgumentException("target Connection cannot be null");
        }
        if (interceptor == null) {
            throw new IllegalArgumentException("interceptor cannot be null");
        }
        this.target = target;
        this.interceptor = interceptor;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        try {
            // Intercept prepareStatement - SQL known at prepare time
            if ("prepareStatement".equals(methodName) && args != null && args.length > 0
                    && args[0] instanceof String) {
                String sql = (String) args[0];
                validateSql(sql);

                // Create actual PreparedStatement
                PreparedStatement ps = (PreparedStatement) method.invoke(target, args);

                // Wrap in proxy (SQL already validated, pass sql for audit)
                return wrapPreparedStatement(ps, sql);
            }

            // Intercept createStatement - SQL not known yet
            if ("createStatement".equals(methodName)) {
                Statement stmt = (Statement) method.invoke(target, args);
                return wrapStatement(stmt);
            }

            // Intercept prepareCall - for stored procedures
            if ("prepareCall".equals(methodName) && args != null && args.length > 0
                    && args[0] instanceof String) {
                String sql = (String) args[0];
                validateSql(sql);

                // Create actual CallableStatement
                CallableStatement cs = (CallableStatement) method.invoke(target, args);

                // Wrap in proxy (SQL already validated)
                return wrapCallableStatement(cs, sql);
            }

            // Handle standard Object methods
            if ("toString".equals(methodName)) {
                return "HikariConnectionProxy[" + target.toString() + "]";
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

            // Delegate all other methods to the target Connection
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
     * Wraps PreparedStatement with a proxy handler.
     *
     * @param ps the original PreparedStatement
     * @param sql the SQL that was validated
     * @return proxied PreparedStatement
     */
    private PreparedStatement wrapPreparedStatement(PreparedStatement ps, String sql) {
        if (ps == null) {
            return null;
        }

        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[] { PreparedStatement.class },
                new StatementProxyHandler(ps, sql, interceptor)
        );
    }

    /**
     * Wraps Statement with a proxy handler.
     *
     * @param stmt the original Statement
     * @return proxied Statement
     */
    private Statement wrapStatement(Statement stmt) {
        if (stmt == null) {
            return null;
        }

        return (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[] { Statement.class },
                new StatementProxyHandler(stmt, null, interceptor)
        );
    }

    /**
     * Wraps CallableStatement with a proxy handler.
     *
     * @param cs the original CallableStatement
     * @param sql the SQL that was validated
     * @return proxied CallableStatement
     */
    private CallableStatement wrapCallableStatement(CallableStatement cs, String sql) {
        if (cs == null) {
            return null;
        }

        return (CallableStatement) Proxy.newProxyInstance(
                CallableStatement.class.getClassLoader(),
                new Class<?>[] { CallableStatement.class },
                new StatementProxyHandler(cs, sql, interceptor)
        );
    }

    /**
     * Gets the target Connection.
     *
     * @return the wrapped Connection
     */
    public Connection getTarget() {
        return target;
    }
}








