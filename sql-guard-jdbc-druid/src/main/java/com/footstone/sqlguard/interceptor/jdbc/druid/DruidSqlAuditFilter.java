package com.footstone.sqlguard.interceptor.jdbc.druid;

import com.alibaba.druid.filter.FilterAdapter;
import com.alibaba.druid.filter.FilterChain;
import com.alibaba.druid.proxy.jdbc.ConnectionProxy;
import com.alibaba.druid.proxy.jdbc.DataSourceProxy;
import com.alibaba.druid.proxy.jdbc.ResultSetProxy;
import com.alibaba.druid.proxy.jdbc.StatementProxy;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.interceptor.jdbc.common.JdbcAuditEventBuilder;
import com.footstone.sqlguard.interceptor.jdbc.common.StatementIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * Druid audit filter for logging SQL execution events.
 *
 * <p>DruidSqlAuditFilter extends Druid's FilterAdapter to capture SQL execution
 * results and write audit logs. It coordinates with DruidSqlSafetyFilter via
 * ThreadLocal to include pre-execution validation results in audit events.</p>
 *
 * <h2>Filter Chain Position</h2>
 * <p>This filter should be registered at the END of the filter list to ensure
 * it executes AFTER all other filters and captures the final execution result:</p>
 * <ol>
 *   <li>DruidSqlSafetyFilter (pre-execution validation)</li>
 *   <li>Druid StatFilter, WallFilter, etc.</li>
 *   <li>DruidSqlAuditFilter (post-execution audit)</li>
 * </ol>
 *
 * <h2>Audit Event Data</h2>
 * <p>Each audit event includes:</p>
 * <ul>
 *   <li>SQL statement executed</li>
 *   <li>Execution timestamp and duration</li>
 *   <li>Datasource name</li>
 *   <li>Success/failure status</li>
 *   <li>Validation result (if safety filter was active)</li>
 *   <li>Exception details (if execution failed)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * AuditLogWriter auditWriter = new FileAuditLogWriter("/var/log/sqlguard/audit.log");
 * DruidSqlAuditFilter auditFilter = new DruidSqlAuditFilter(auditWriter);
 *
 * DruidDataSource dataSource = new DruidDataSource();
 * dataSource.getProxyFilters().add(auditFilter); // Add at end
 * }</pre>
 *
 * @since 2.0.0
 * @see FilterAdapter
 * @see AuditLogWriter
 * @see DruidSqlSafetyFilter
 */
public class DruidSqlAuditFilter extends FilterAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DruidSqlAuditFilter.class);

    /**
     * The audit log writer for persisting audit events.
     */
    private final AuditLogWriter auditLogWriter;

    /**
     * Constructs a DruidSqlAuditFilter with the specified audit log writer.
     *
     * @param auditLogWriter the audit log writer
     * @throws IllegalArgumentException if auditLogWriter is null
     */
    public DruidSqlAuditFilter(AuditLogWriter auditLogWriter) {
        if (auditLogWriter == null) {
            throw new IllegalArgumentException("auditLogWriter cannot be null");
        }
        this.auditLogWriter = auditLogWriter;
    }

    /**
     * Intercepts Statement.executeQuery() to audit the execution.
     *
     * @param chain the filter chain
     * @param statement the statement proxy
     * @param sql the SQL that was executed
     * @return the result set proxy
     * @throws SQLException if execution fails
     */
    @Override
    public ResultSetProxy statement_executeQuery(
            FilterChain chain,
            StatementProxy statement,
            String sql) throws SQLException {

        long startTime = System.currentTimeMillis();
        Exception executionException = null;

        try {
            return super.statement_executeQuery(chain, statement, sql);
        } catch (SQLException e) {
            executionException = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            recordAuditEvent(sql, statement, duration, executionException);
        }
    }

    /**
     * Intercepts Statement.executeUpdate() to audit the execution.
     *
     * @param chain the filter chain
     * @param statement the statement proxy
     * @param sql the SQL that was executed
     * @return the update count
     * @throws SQLException if execution fails
     */
    @Override
    public int statement_executeUpdate(
            FilterChain chain,
            StatementProxy statement,
            String sql) throws SQLException {

        long startTime = System.currentTimeMillis();
        Exception executionException = null;

        try {
            return super.statement_executeUpdate(chain, statement, sql);
        } catch (SQLException e) {
            executionException = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            recordAuditEvent(sql, statement, duration, executionException);
        }
    }

    /**
     * Intercepts Statement.execute() to audit the execution.
     *
     * @param chain the filter chain
     * @param statement the statement proxy
     * @param sql the SQL that was executed
     * @return true if the first result is a ResultSet
     * @throws SQLException if execution fails
     */
    @Override
    public boolean statement_execute(
            FilterChain chain,
            StatementProxy statement,
            String sql) throws SQLException {

        long startTime = System.currentTimeMillis();
        Exception executionException = null;

        try {
            return super.statement_execute(chain, statement, sql);
        } catch (SQLException e) {
            executionException = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            recordAuditEvent(sql, statement, duration, executionException);
        }
    }

    /**
     * Records an audit event for the SQL execution.
     *
     * <p>This method leverages Druid's StatementProxy API to:</p>
     * <ul>
     *   <li>Detect SQL type from execution type (avoiding SQL parsing)</li>
     *   <li>Generate unique statementId using SQL hash</li>
     *   <li>Capture actual rows affected from StatementProxy</li>
     * </ul>
     *
     * @param sql the SQL that was executed
     * @param statement the StatementProxy for accessing execution metadata
     * @param durationMs execution duration in milliseconds
     * @param exception any exception that occurred, or null if successful
     */
    private void recordAuditEvent(
            String sql,
            StatementProxy statement,
            long durationMs,
            Exception exception) {

        try {
            // Skip audit for Druid internal validation queries to prevent infinite loop
            if (isValidationQuery(sql)) {
                return;
            }

            ConnectionProxy connection = statement.getConnectionProxy();
            String datasourceName = extractDatasourceName(connection);
            ValidationResult validationResult = DruidJdbcInterceptor.getValidationResult();

            // Use Druid API to detect SQL type (more efficient than parsing)
            SqlCommandType sqlType = detectSqlTypeFromStatement(statement, sql);

            // Generate unique statementId using common generator
            String statementId = StatementIdGenerator.generate("druid", datasourceName, sql);

            // Get actual rows affected from StatementProxy
            int rowsAffected = getRowsAffected(statement, sqlType);

            // Build SqlContext for audit event
            com.footstone.sqlguard.core.model.SqlContext context =
                com.footstone.sqlguard.core.model.SqlContext.builder()
                    .sql(sql != null ? sql : "")
                    .type(sqlType)
                    .executionLayer(com.footstone.sqlguard.core.model.ExecutionLayer.JDBC)
                    .statementId(statementId)
                    .datasource(datasourceName)
                    .build();

            // Use validation result or pass if none available
            ValidationResult result = validationResult != null ? validationResult : ValidationResult.pass();

            // Create audit event
            AuditEvent event;
            if (exception != null) {
                event = JdbcAuditEventBuilder.createErrorEvent(context, exception);
            } else {
                event = JdbcAuditEventBuilder.createEvent(context, result, durationMs, rowsAffected);
            }

            auditLogWriter.writeAuditLog(event);

        } catch (Exception e) {
            logger.warn("Failed to record audit event for SQL: {}",
                    sql != null ? sql.substring(0, Math.min(50, sql.length())) : "null", e);
        } finally {
            // Clear ThreadLocal to prevent memory leaks
            DruidJdbcInterceptor.clearValidationResult();
        }
    }
    
    /**
     * Detects SQL command type using Druid's StatementProxy API.
     *
     * <p>This method uses SQL parsing as the primary detection method.
     * Druid's execution type API varies across versions, so SQL parsing
     * provides better compatibility.</p>
     *
     * @param statement the StatementProxy (reserved for future use)
     * @param sql the SQL statement
     * @return the detected SqlCommandType
     */
    private SqlCommandType detectSqlTypeFromStatement(StatementProxy statement, String sql) {
        // Use SQL parsing for type detection
        // This is fast enough and more compatible across Druid versions
        return detectSqlTypeFromSql(sql);
    }

    /**
     * Detects SQL command type by parsing SQL prefix (fallback method).
     *
     * @param sql the SQL to analyze
     * @return the detected SqlCommandType
     */
    private SqlCommandType detectSqlTypeFromSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return SqlCommandType.UNKNOWN;
        }

        String upperSql = sql.trim().toUpperCase();

        if (upperSql.startsWith("SELECT")) {
            return SqlCommandType.SELECT;
        } else if (upperSql.startsWith("UPDATE")) {
            return SqlCommandType.UPDATE;
        } else if (upperSql.startsWith("DELETE")) {
            return SqlCommandType.DELETE;
        } else if (upperSql.startsWith("INSERT")) {
            return SqlCommandType.INSERT;
        } else {
            return SqlCommandType.UNKNOWN;
        }
    }

    /**
     * Gets the actual number of rows affected from Druid's StatementProxy.
     *
     * <p>This method extracts real execution metrics from StatementProxy:</p>
     * <ul>
     *   <li>Uses {@code getUpdateCount()} for all statement types</li>
     *   <li>Returns -1 if count is unavailable (e.g., for SELECT without fetch)</li>
     * </ul>
     *
     * @param statement the StatementProxy
     * @param sqlType the SQL command type
     * @return actual rows affected, or -1 if unavailable
     */
    private int getRowsAffected(StatementProxy statement, SqlCommandType sqlType) {
        try {
            // getUpdateCount() works for both SELECT and UPDATE/DELETE/INSERT
            // For SELECT it returns -1, for modifications it returns affected rows
            int count = statement.getUpdateCount();
            return count >= 0 ? count : -1;
        } catch (Exception e) {
            logger.debug("Failed to get rows affected from StatementProxy", e);
            return -1;  // Indicates unavailable
        }
    }

    /**
     * Checks if the SQL is a Druid internal validation query.
     *
     * <p>Validation queries are used by connection pools to test connection health.
     * We skip auditing these to prevent infinite loops during connection creation.</p>
     *
     * @param sql the SQL to check
     * @return true if this is a validation query, false otherwise
     */
    private boolean isValidationQuery(String sql) {
        if (sql == null) {
            return false;
        }

        String trimmed = sql.trim().toUpperCase();

        // Common validation queries used by Druid and other pools
        return trimmed.equals("SELECT 1") ||
               trimmed.equals("SELECT 1 FROM DUAL") ||
               trimmed.equals("SELECT 'X'") ||
               trimmed.startsWith("/* ping */") ||
               trimmed.startsWith("/* HEALTH CHECK */");
    }

    /**
     * Extracts datasource name from ConnectionProxy.
     *
     * @param connection the connection proxy
     * @return the datasource name, "default", or "unknown"
     */
    private String extractDatasourceName(ConnectionProxy connection) {
        try {
            DataSourceProxy dataSource = connection.getDirectDataSource();
            String name = dataSource.getName();
            return name != null ? name : "default";
        } catch (Exception e) {
            logger.debug("Failed to extract datasource name", e);
            return "unknown";
        }
    }

    /**
     * Returns the audit log writer.
     *
     * @return the audit log writer
     */
    public AuditLogWriter getAuditLogWriter() {
        return auditLogWriter;
    }
}
