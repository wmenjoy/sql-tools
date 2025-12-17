package com.footstone.sqlguard.interceptor.hikari;

import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * Proxy factory for HikariCP audit logging.
 *
 * <p>Wraps HikariCP connections with JDK dynamic proxy to intercept
 * Statement execution and capture post-execution audit data.</p>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>{@code
 * HikariDataSource dataSource = new HikariDataSource(config);
 * HikariSqlAuditProxyFactory auditFactory = new HikariSqlAuditProxyFactory(auditLogWriter);
 * Connection conn = auditFactory.wrapConnection(dataSource.getConnection());
 * }</pre>
 *
 * <p><strong>ThreadLocal Coordination:</strong></p>
 * <p>When used together with HikariSqlSafetyProxyFactory, retrieves pre-execution 
 * ValidationResult via shared ThreadLocal for violation correlation. The execution 
 * order ensures AuditProxy's finally block reads ThreadLocal before SafetyProxy clears it.</p>
 *
 * <p><strong>Performance:</strong> <5% overhead on SQL execution</p>
 */
public class HikariSqlAuditProxyFactory {

    private static final Logger logger = LoggerFactory.getLogger(HikariSqlAuditProxyFactory.class);

    private final AuditLogWriter auditLogWriter;

    /**
     * Constructs HikariSqlAuditProxyFactory with specified AuditLogWriter.
     *
     * @param auditLogWriter the audit log writer (must not be null)
     */
    public HikariSqlAuditProxyFactory(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = Objects.requireNonNull(auditLogWriter, "auditLogWriter must not be null");
    }

    /**
     * Wraps connection with audit proxy.
     *
     * @param connection the original connection
     * @return proxied connection that intercepts statement creation
     */
    public Connection wrapConnection(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                connection.getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                new ConnectionAuditHandler(connection, auditLogWriter)
        );
    }

    /**
     * InvocationHandler for Connection proxy.
     * Intercepts createStatement/prepareStatement to wrap with audit proxy.
     */
    private static class ConnectionAuditHandler implements InvocationHandler {

        private final Connection target;
        private final AuditLogWriter auditLogWriter;

        ConnectionAuditHandler(Connection target, AuditLogWriter auditLogWriter) {
            this.target = target;
            this.auditLogWriter = auditLogWriter;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();

            // Intercept statement creation methods
            if ("createStatement".equals(methodName)) {
                Statement stmt = (Statement) method.invoke(target, args);
                return wrapStatement(stmt, null);

            } else if ("prepareStatement".equals(methodName)) {
                PreparedStatement pstmt = (PreparedStatement) method.invoke(target, args);
                String sql = (args != null && args.length > 0) ? (String) args[0] : null;
                return wrapPreparedStatement(pstmt, sql);

            } else if ("prepareCall".equals(methodName)) {
                // CallableStatement support (optional)
                return method.invoke(target, args);
            }

            // Delegate other methods
            return method.invoke(target, args);
        }

        /**
         * Wraps Statement with audit proxy.
         */
        private Statement wrapStatement(Statement stmt, String sql) {
            return (Statement) Proxy.newProxyInstance(
                    stmt.getClass().getClassLoader(),
                    new Class<?>[]{Statement.class},
                    new StatementAuditHandler(stmt, sql, auditLogWriter)
            );
        }

        /**
         * Wraps PreparedStatement with audit proxy.
         */
        private PreparedStatement wrapPreparedStatement(PreparedStatement pstmt, String sql) {
            return (PreparedStatement) Proxy.newProxyInstance(
                    pstmt.getClass().getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    new StatementAuditHandler(pstmt, sql, auditLogWriter)
            );
        }
    }

    /**
     * InvocationHandler for Statement/PreparedStatement proxy.
     * Intercepts execute* methods to capture post-execution audit data.
     */
    private static class StatementAuditHandler implements InvocationHandler {

        private final Statement target;
        private final String sql;
        private final AuditLogWriter auditLogWriter;

        StatementAuditHandler(Statement target, String sql, AuditLogWriter auditLogWriter) {
            this.target = target;
            this.sql = sql;
            this.auditLogWriter = auditLogWriter;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();

            // Intercept execute* methods
            if (methodName.startsWith("execute")) {
                return interceptExecute(method, args);
            }

            // Delegate other methods
            return method.invoke(target, args);
        }

        /**
         * Intercepts execute* methods and writes audit event.
         */
        private Object interceptExecute(Method method, Object[] args) throws Throwable {
            long startNano = System.nanoTime();
            Object result = null;
            Throwable exception = null;

            try {
                // Execute original method
                result = method.invoke(target, args);
                return result;

            } catch (InvocationTargetException e) {
                exception = e.getCause();
                throw exception;

            } catch (Throwable t) {
                exception = t;
                throw t;

            } finally {
                // Write audit event
                long durationMs = (System.nanoTime() - startNano) / 1_000_000;
                writeAuditEvent(method.getName(), args, result, durationMs, exception);
            }
        }

        /**
         * Writes audit event based on execution result.
         */
        private void writeAuditEvent(String methodName, Object[] args, Object result,
                                      long durationMs, Throwable exception) {
            ValidationResult validationResult = null;
            try {
                // Determine SQL (from PreparedStatement or Statement.execute(sql))
                String executedSql = sql;
                if (executedSql == null && args != null && args.length > 0 && args[0] instanceof String) {
                    executedSql = (String) args[0];
                }

                if (executedSql == null) {
                    return; // No SQL to audit
                }

                // Retrieve pre-execution validation result from ThreadLocal
                // This correlates safety validation violations with audit logging
                validationResult = HikariSqlSafetyProxyFactory.getValidationResult();

                // Determine SQL type
                SqlCommandType sqlType = determineSqlType(executedSql);

                // Extract rows affected
                int rowsAffected = extractRowsAffected(methodName, result, exception);

                // Build audit event
                AuditEvent.Builder eventBuilder = AuditEvent.builder()
                        .sql(executedSql)
                        .sqlType(sqlType)
                        .mapperId("hikari-jdbc")
                        .timestamp(Instant.now())
                        .executionTimeMs(durationMs)
                        .rowsAffected(rowsAffected);

                // Add error message if exception occurred
                if (exception != null) {
                    String errorMsg = exception.getMessage();
                    eventBuilder.errorMessage(errorMsg);
                }

                // Add pre-execution violations if available
                if (validationResult != null && !validationResult.isPassed()) {
                    eventBuilder.violations(validationResult);
                }

                // Write audit log
                auditLogWriter.writeAuditLog(eventBuilder.build());

            } catch (Exception e) {
                logger.error("Failed to write audit log", e);
                // Don't throw - audit failure should not break SQL execution
            } finally {
                // Clean up ThreadLocal after reading to prevent memory leaks
                // This is safe because we're in the finally block of execute()
                // which runs after SafetyProxy's validation
                HikariSqlSafetyProxyFactory.clearValidationResult();
            }
        }

        /**
         * Extracts rows affected from execution result.
         */
        private int extractRowsAffected(String methodName, Object result, Throwable exception) {
            if (exception != null) {
                return -1; // Error case
            }

            try {
                if ("executeUpdate".equals(methodName)) {
                    // executeUpdate() returns int (rows affected)
                    return (result instanceof Integer) ? (Integer) result : -1;

                } else if ("executeBatch".equals(methodName)) {
                    // executeBatch() returns int[] (per-statement rows)
                    if (result instanceof int[]) {
                        int[] batchResults = (int[]) result;
                        return Arrays.stream(batchResults).sum();
                    }

                } else if ("execute".equals(methodName)) {
                    // execute() returns boolean, need to call getUpdateCount()
                    if (result instanceof Boolean && !(Boolean) result) {
                        // false = update count available
                        return target.getUpdateCount();
                    }
                }

            } catch (Exception e) {
                logger.debug("Failed to extract rows affected: {}", e.getMessage());
            }

            return -1; // Default for SELECT or unknown
        }

        /**
         * Determines SQL command type from SQL string.
         */
        private SqlCommandType determineSqlType(String sql) {
            if (sql == null || sql.isEmpty()) {
                return SqlCommandType.SELECT;
            }

            String trimmed = sql.trim().toUpperCase();
            if (trimmed.startsWith("SELECT")) {
                return SqlCommandType.SELECT;
            } else if (trimmed.startsWith("UPDATE")) {
                return SqlCommandType.UPDATE;
            } else if (trimmed.startsWith("DELETE")) {
                return SqlCommandType.DELETE;
            } else if (trimmed.startsWith("INSERT")) {
                return SqlCommandType.INSERT;
            }

            return SqlCommandType.SELECT; // Default
        }
    }
}
