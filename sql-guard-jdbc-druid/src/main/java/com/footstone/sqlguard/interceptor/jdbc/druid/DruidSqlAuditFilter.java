package com.footstone.sqlguard.interceptor.jdbc.druid;

import com.alibaba.druid.filter.FilterAdapter;
import com.alibaba.druid.filter.FilterChain;
import com.alibaba.druid.proxy.jdbc.ConnectionProxy;
import com.alibaba.druid.proxy.jdbc.DataSourceProxy;
import com.alibaba.druid.proxy.jdbc.ResultSetProxy;
import com.alibaba.druid.proxy.jdbc.StatementProxy;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.interceptor.jdbc.common.JdbcAuditEventBuilder;
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
            recordAuditEvent(sql, statement.getConnectionProxy(), duration, executionException);
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
            recordAuditEvent(sql, statement.getConnectionProxy(), duration, executionException);
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
            recordAuditEvent(sql, statement.getConnectionProxy(), duration, executionException);
        }
    }

    /**
     * Records an audit event for the SQL execution.
     *
     * @param sql the SQL that was executed
     * @param connection the connection proxy for datasource context
     * @param durationMs execution duration in milliseconds
     * @param exception any exception that occurred, or null if successful
     */
    private void recordAuditEvent(
            String sql,
            ConnectionProxy connection,
            long durationMs,
            Exception exception) {
        
        try {
            String datasourceName = extractDatasourceName(connection);
            ValidationResult validationResult = DruidJdbcInterceptor.getValidationResult();
            
            // Build SqlContext for audit event
            com.footstone.sqlguard.core.model.SqlContext context = 
                com.footstone.sqlguard.core.model.SqlContext.builder()
                    .sql(sql != null ? sql : "")
                    .type(detectSqlType(sql))
                    .mapperId("jdbc.druid:" + datasourceName)
                    .datasource(datasourceName)
                    .build();
            
            // Use validation result or pass if none available
            ValidationResult result = validationResult != null ? validationResult : ValidationResult.pass();
            
            // Create audit event
            AuditEvent event;
            if (exception != null) {
                event = JdbcAuditEventBuilder.createErrorEvent(context, exception);
            } else {
                event = JdbcAuditEventBuilder.createEvent(context, result, durationMs, 0);
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
     * Detects SQL command type from SQL prefix.
     *
     * @param sql the SQL to analyze
     * @return the detected SqlCommandType
     */
    private com.footstone.sqlguard.core.model.SqlCommandType detectSqlType(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return com.footstone.sqlguard.core.model.SqlCommandType.UNKNOWN;
        }

        String upperSql = sql.trim().toUpperCase();

        if (upperSql.startsWith("SELECT")) {
            return com.footstone.sqlguard.core.model.SqlCommandType.SELECT;
        } else if (upperSql.startsWith("UPDATE")) {
            return com.footstone.sqlguard.core.model.SqlCommandType.UPDATE;
        } else if (upperSql.startsWith("DELETE")) {
            return com.footstone.sqlguard.core.model.SqlCommandType.DELETE;
        } else if (upperSql.startsWith("INSERT")) {
            return com.footstone.sqlguard.core.model.SqlCommandType.INSERT;
        } else {
            return com.footstone.sqlguard.core.model.SqlCommandType.UNKNOWN;
        }
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
