package com.footstone.sqlguard.interceptor.p6spy;

import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.JdbcEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;

/**
 * P6Spy listener for universal JDBC audit logging.
 *
 * <p>This listener captures SQL execution results, timing, and errors via
 * P6Spy's JdbcEventListener interface. It provides fallback audit capability
 * for environments without native interceptor support.</p>
 *
 * <p><strong>Universal Compatibility:</strong></p>
 * <ul>
 *   <li>Works with any JDBC driver (MySQL, PostgreSQL, Oracle, SQL Server, H2, etc.)</li>
 *   <li>Works with any connection pool (C3P0, DBCP, Tomcat JDBC, etc.)</li>
 *   <li>Works with any framework (MyBatis, JPA, JdbcTemplate, raw JDBC)</li>
 * </ul>
 *
 * <p><strong>ThreadLocal Coordination:</strong></p>
 * <p>Retrieves pre-execution ValidationResult from P6SpySqlSafetyListener via
 * shared ThreadLocal for violation correlation.</p>
 *
 * <p><strong>Performance:</strong> 12-18% overhead (higher than native solutions)</p>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>
 * # spy.properties
 * modulelist=com.footstone.sqlguard.interceptor.p6spy.P6SpySqlSafetyModule,\
 *            com.footstone.sqlguard.interceptor.p6spy.P6SpySqlAuditModule
 * </pre>
 *
 * @see AuditLogWriter
 * @see P6SpySqlSafetyListener
 * @see JdbcEventListener
 */
public class P6SpySqlAuditListener extends JdbcEventListener {

    private static final Logger logger = LoggerFactory.getLogger(P6SpySqlAuditListener.class);

    private final AuditLogWriter auditLogWriter;

    /**
     * Constructs P6SpySqlAuditListener with specified AuditLogWriter.
     *
     * @param auditLogWriter the audit log writer (must not be null)
     */
    public P6SpySqlAuditListener(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = Objects.requireNonNull(auditLogWriter, "auditLogWriter must not be null");
    }

    /**
     * Intercepts SQL execution after Statement.execute() or PreparedStatement.execute().
     *
     * @param statementInfo P6Spy statement information
     * @param timeElapsedNanos time elapsed in nanoseconds
     * @param sql the SQL statement (for Statement, not PreparedStatement)
     * @param e SQLException if execution failed
     */
    @Override
    public void onAfterExecute(StatementInformation statementInfo, long timeElapsedNanos, String sql, SQLException e) {
        logAuditEvent(statementInfo, sql, timeElapsedNanos, -1, e);
    }

    /**
     * Intercepts SQL execution after Statement.executeUpdate() or PreparedStatement.executeUpdate().
     *
     * @param statementInfo P6Spy statement information
     * @param timeElapsedNanos time elapsed in nanoseconds
     * @param sql the SQL statement (for Statement, not PreparedStatement)
     * @param rowCount number of rows affected
     * @param e SQLException if execution failed
     */
    @Override
    public void onAfterExecuteUpdate(StatementInformation statementInfo, long timeElapsedNanos, String sql, int rowCount, SQLException e) {
        logAuditEvent(statementInfo, sql, timeElapsedNanos, rowCount, e);
    }

    /**
     * Intercepts SQL execution after Statement.executeQuery() or PreparedStatement.executeQuery().
     *
     * @param statementInfo P6Spy statement information
     * @param timeElapsedNanos time elapsed in nanoseconds
     * @param sql the SQL statement (for Statement, not PreparedStatement)
     * @param e SQLException if execution failed
     */
    @Override
    public void onAfterExecuteQuery(StatementInformation statementInfo, long timeElapsedNanos, String sql, SQLException e) {
        logAuditEvent(statementInfo, sql, timeElapsedNanos, -1, e);
    }

    /**
     * Intercepts batch execution after Statement.executeBatch().
     *
     * @param statementInfo P6Spy statement information
     * @param timeElapsedNanos time elapsed in nanoseconds
     * @param updateCounts array of update counts for each command
     * @param e SQLException if execution failed
     */
    @Override
    public void onAfterExecuteBatch(StatementInformation statementInfo, long timeElapsedNanos, int[] updateCounts, SQLException e) {
        // Aggregate batch results
        int totalRowsAffected = 0;
        if (updateCounts != null && updateCounts.length > 0) {
            for (int count : updateCounts) {
                // Skip failed statements (negative values)
                if (count >= 0) {
                    totalRowsAffected += count;
                }
            }
        }

        logAuditEvent(statementInfo, null, timeElapsedNanos, totalRowsAffected, e);
    }

    /**
     * Core audit logging method called by all callback methods.
     *
     * <p><strong>Execution Flow:</strong></p>
     * <ol>
     *   <li>Extract SQL with parameter values from StatementInformation</li>
     *   <li>Skip if SQL is null or empty</li>
     *   <li>Convert nanoseconds to milliseconds</li>
     *   <li>Determine SQL command type</li>
     *   <li>Retrieve pre-execution ValidationResult from ThreadLocal</li>
     *   <li>Build and write AuditEvent</li>
     * </ol>
     *
     * <p><strong>Error Handling:</strong> Audit failures are logged but never
     * thrown, ensuring audit errors don't break SQL execution.</p>
     *
     * @param statementInfo P6Spy statement information
     * @param sql the SQL statement (may be null for PreparedStatement)
     * @param timeElapsedNanos time elapsed in nanoseconds
     * @param rowsAffected number of rows affected, or -1 if not applicable
     * @param e SQLException if execution failed
     */
    private void logAuditEvent(StatementInformation statementInfo, String sql, long timeElapsedNanos, int rowsAffected, SQLException e) {
        try {
            // Extract SQL with parameter substitution
            // WARNING: Contains actual parameter values - sanitize for production
            String actualSql = statementInfo.getSqlWithValues();
            if (actualSql == null || actualSql.isEmpty()) {
                // Fallback to provided SQL for Statement (not PreparedStatement)
                actualSql = sql;
            }

            if (actualSql == null || actualSql.isEmpty()) {
                return; // No SQL to audit
            }

            // Convert nanoseconds to milliseconds
            long durationMs = timeElapsedNanos / 1_000_000;

            // Determine SQL type
            SqlCommandType sqlType = determineSqlType(actualSql);

            // Retrieve pre-execution validation result from ThreadLocal
            ValidationResult validationResult = P6SpySqlSafetyListener.getValidationResult();

            // Build audit event
            AuditEvent.Builder eventBuilder = AuditEvent.builder()
                .sql(actualSql)
                .sqlType(sqlType)
                .mapperId("p6spy-jdbc")
                .timestamp(Instant.now())
                .executionTimeMs(durationMs)
                .rowsAffected(rowsAffected);

            // Add error message if exception occurred
            if (e != null) {
                eventBuilder.errorMessage(e.getMessage());
            }

            // Add pre-execution violations if available
            if (validationResult != null && !validationResult.isPassed()) {
                eventBuilder.violations(validationResult);
            }

            // Write audit log
            auditLogWriter.writeAuditLog(eventBuilder.build());

            // Clean up ThreadLocal to prevent memory leaks
            P6SpySqlSafetyListener.clearValidationResult();

        } catch (Exception ex) {
            logger.error("Failed to write audit log for P6Spy statement", ex);
            // Don't throw - audit failure should not break SQL execution
        }
    }

    /**
     * Determines SQL command type from SQL string.
     *
     * <p>Parses the SQL string to identify the command type (SELECT, UPDATE, DELETE, INSERT).
     * Defaults to SELECT for unrecognized commands.</p>
     *
     * @param sql the SQL string
     * @return the detected SqlCommandType, defaults to SELECT
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
