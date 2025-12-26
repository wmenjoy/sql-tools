package com.footstone.sqlguard.interceptor.jdbc.hikari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;

/**
 * Layer 1: DataSource proxy handler for HikariCP interception.
 *
 * <p>DataSourceProxyHandler intercepts {@code DataSource.getConnection()} calls to wrap
 * returned connections with {@link ConnectionProxyHandler}, creating the first layer
 * of the three-layer proxy chain.</p>
 *
 * <h2>Proxy Chain Position</h2>
 * <pre>
 * DataSourceProxyHandler (Layer 1) ← You are here
 *     ↓ wraps returned Connection
 * ConnectionProxyHandler (Layer 2)
 *     ↓ wraps returned Statement/PreparedStatement
 * StatementProxyHandler (Layer 3)
 *     ↓ triggers SQL validation
 * HikariJdbcInterceptor
 * </pre>
 *
 * <h2>Intercepted Methods</h2>
 * <ul>
 *   <li>{@code getConnection()} - Wraps connection with ConnectionProxyHandler</li>
 *   <li>{@code getConnection(String, String)} - Wraps connection with ConnectionProxyHandler</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This handler is immutable after construction and thread-safe. Each getConnection()
 * call creates a new ConnectionProxyHandler for the returned connection.</p>
 *
 * @since 2.0.0
 * @see ConnectionProxyHandler
 * @see HikariJdbcInterceptor
 */
public class DataSourceProxyHandler implements InvocationHandler {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceProxyHandler.class);

    private final DataSource target;
    private final HikariJdbcInterceptor interceptor;

    /**
     * Constructs a DataSourceProxyHandler.
     *
     * @param target the original DataSource to wrap
     * @param interceptor the HikariCP interceptor for SQL validation
     * @throws IllegalArgumentException if target or interceptor is null
     */
    public DataSourceProxyHandler(DataSource target, HikariJdbcInterceptor interceptor) {
        if (target == null) {
            throw new IllegalArgumentException("target DataSource cannot be null");
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
            // Intercept getConnection methods
            if ("getConnection".equals(methodName)) {
                Connection conn = (Connection) method.invoke(target, args);
                return wrapConnection(conn);
            }

            // Handle standard Object methods
            if ("toString".equals(methodName)) {
                return "HikariDataSourceProxy[" + target.toString() + "]";
            }
            if ("hashCode".equals(methodName)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }

            // Delegate all other methods to the target DataSource
            return method.invoke(target, args);

        } catch (InvocationTargetException e) {
            // Unwrap and rethrow the original exception
            throw e.getCause();
        }
    }

    /**
     * Wraps a Connection with a proxy handler.
     *
     * @param conn the original Connection
     * @return proxied Connection
     */
    private Connection wrapConnection(Connection conn) {
        if (conn == null) {
            return null;
        }

        logger.debug("Wrapping connection with safety proxy");

        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                new ConnectionProxyHandler(conn, interceptor)
        );
    }

    /**
     * Gets the target DataSource.
     *
     * @return the wrapped DataSource
     */
    public DataSource getTarget() {
        return target;
    }
}







