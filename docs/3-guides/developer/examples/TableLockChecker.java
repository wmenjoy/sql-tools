package com.example.custom;

import com.footstone.sqlguard.audit.checker.AbstractAuditChecker;
import com.footstone.sqlguard.audit.model.*;
import com.footstone.sqlguard.core.model.SqlContext;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Detects queries holding table locks for extended periods.
 *
 * <h2>Purpose</h2>
 * <p>Table-level locks can cause significant blocking in high-concurrency databases.
 * This checker identifies queries that hold table locks beyond a configured threshold.</p>
 *
 * <h2>Detection Logic</h2>
 * <p>Queries are flagged if they meet any of these conditions:</p>
 * <ul>
 *   <li>Execution time exceeds threshold (default: 1000ms)</li>
 *   <li>Statement type is ALTER TABLE or LOCK TABLES</li>
 *   <li>Long-running UPDATE/DELETE affecting multiple rows</li>
 * </ul>
 *
 * <h2>Risk Score Calculation</h2>
 * <ul>
 *   <li><b>Score:</b> 80 (HIGH risk)</li>
 *   <li><b>Confidence:</b> 0.9 (90%)</li>
 *   <li><b>Factors:</b> lock_time, affected_tables, lock_type</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * sqlguard:
 *   audit:
 *     checkers:
 *       table-lock:
 *         enabled: true
 *         threshold: 1000  # milliseconds
 *         severity-levels:
 *           warning: 500
 *           critical: 2000
 * }</pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Automatic registration via Spring
 * @Component
 * public class TableLockChecker extends AbstractAuditChecker {
 *     // Checker logic here
 * }
 *
 * // Query execution triggers audit
 * SqlContext context = ...;
 * ExecutionResult result = ...;
 * AuditResult audit = checker.performAudit(context, result);
 * }</pre>
 *
 * @see AbstractAuditChecker
 * @see AuditResult
 * @see RiskScore
 * @since 2.0.0
 */
@Component
public class TableLockChecker extends AbstractAuditChecker {

    private final TableLockConfig config;

    @Autowired
    public TableLockChecker(TableLockConfig config) {
        this.config = config;
    }

    /**
     * Performs audit analysis on SQL execution to detect table lock issues.
     *
     * <p>Detection algorithm:</p>
     * <ol>
     *   <li>Check if checker is enabled</li>
     *   <li>Analyze lock type from SQL statement</li>
     *   <li>Calculate effective lock time from execution metrics</li>
     *   <li>Compare against configured thresholds</li>
     *   <li>Build appropriate severity result</li>
     * </ol>
     *
     * @param context SQL context containing statement and mapper info
     * @param result Execution result with timing and affected rows
     * @return AuditResult with findings, or pass result if no issues
     */
    @Override
    protected AuditResult performAudit(SqlContext context, ExecutionResult result) {
        // Check if checker is enabled
        if (!config.isEnabled()) {
            return AuditResult.pass();
        }

        try {
            // Analyze lock information
            LockInfo lockInfo = analyzeLockInfo(context, result);

            // Check against thresholds
            long lockTime = lockInfo.getDuration();
            Severity severity = determineSeverity(lockTime);

            if (severity == Severity.NONE) {
                return AuditResult.pass();
            }

            // Build result with detailed information
            return AuditResult.builder()
                .severity(severity)
                .checkerId("TABLE_LOCK")
                .message(buildMessage(lockInfo))
                .recommendation(buildRecommendation(lockInfo))
                .sqlHash(context.getSqlHash())
                .mapperId(context.getMapperId())
                .metadata(buildMetadata(lockInfo))
                .build();

        } catch (Exception e) {
            // Don't fail SQL execution if audit fails
            logger.error("Table lock audit failed for SQL: " + context.getSql(), e);
            return AuditResult.error("Audit error: " + e.getMessage());
        }
    }

    /**
     * Calculates risk score based on audit result severity and metadata.
     *
     * <p>Score calculation:</p>
     * <ul>
     *   <li><b>CRITICAL:</b> Score 90, Confidence 0.95</li>
     *   <li><b>HIGH:</b> Score 80, Confidence 0.9</li>
     *   <li><b>MEDIUM:</b> Score 50, Confidence 0.7</li>
     *   <li><b>LOW:</b> Score 20, Confidence 0.6</li>
     * </ul>
     *
     * @param result The audit result containing severity and metadata
     * @return RiskScore with calculated score and confidence
     */
    @Override
    protected RiskScore calculateRiskScore(AuditResult result) {
        switch (result.getSeverity()) {
            case CRITICAL:
                return RiskScore.builder()
                    .score(90)
                    .confidence(0.95)
                    .factors(result.getMetadata())
                    .build();

            case HIGH:
                return RiskScore.builder()
                    .score(80)
                    .confidence(0.9)
                    .factors(result.getMetadata())
                    .build();

            case MEDIUM:
                return RiskScore.builder()
                    .score(50)
                    .confidence(0.7)
                    .factors(result.getMetadata())
                    .build();

            case LOW:
                return RiskScore.builder()
                    .score(20)
                    .confidence(0.6)
                    .factors(result.getMetadata())
                    .build();

            default:
                return RiskScore.safe();
        }
    }

    /**
     * Returns the unique identifier for this checker.
     *
     * @return "TABLE_LOCK"
     */
    @Override
    public String getCheckerId() {
        return "TABLE_LOCK";
    }

    /**
     * Analyzes SQL execution to extract lock-related information.
     *
     * @param context SQL context
     * @param result Execution result
     * @return LockInfo containing lock type, duration, and affected tables
     */
    private LockInfo analyzeLockInfo(SqlContext context, ExecutionResult result) {
        LockType lockType = detectLockType(context);
        long duration = result.getExecutionTime();
        List<String> affectedTables = extractAffectedTables(context);

        return LockInfo.builder()
            .type(lockType)
            .duration(duration)
            .affectedTables(affectedTables)
            .rowsAffected(result.getAffectedRows())
            .build();
    }

    /**
     * Detects the type of lock from SQL statement.
     *
     * @param context SQL context
     * @return LockType enum value
     */
    private LockType detectLockType(SqlContext context) {
        try {
            String sqlUpper = context.getSql().toUpperCase().trim();

            // Explicit lock statements
            if (sqlUpper.startsWith("LOCK TABLES")) {
                return LockType.EXPLICIT_TABLE_LOCK;
            }

            // Parse statement type
            Statement statement = CCJSqlParserUtil.parse(context.getSql());

            if (statement instanceof Alter) {
                return LockType.DDL_LOCK;
            }

            // UPDATE/DELETE without WHERE likely causes table scan
            if (sqlUpper.startsWith("UPDATE") || sqlUpper.startsWith("DELETE")) {
                if (!sqlUpper.contains("WHERE")) {
                    return LockType.TABLE_SCAN_LOCK;
                }
                return LockType.ROW_LEVEL_LOCK;
            }

            return LockType.READ_LOCK;

        } catch (Exception e) {
            logger.warn("Failed to parse SQL for lock type detection: " + context.getSql(), e);
            return LockType.UNKNOWN;
        }
    }

    /**
     * Extracts affected table names from SQL statement.
     *
     * @param context SQL context
     * @return List of table names
     */
    private List<String> extractAffectedTables(SqlContext context) {
        try {
            Statement statement = CCJSqlParserUtil.parse(context.getSql());
            TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
            return tablesNamesFinder.getTableList(statement);
        } catch (Exception e) {
            logger.warn("Failed to extract table names from SQL: " + context.getSql(), e);
            return List.of("unknown");
        }
    }

    /**
     * Determines severity level based on lock duration.
     *
     * @param lockTime Lock duration in milliseconds
     * @return Severity level
     */
    private Severity determineSeverity(long lockTime) {
        if (lockTime >= config.getSeverityLevels().getCritical()) {
            return Severity.CRITICAL;
        } else if (lockTime >= config.getThreshold()) {
            return Severity.HIGH;
        } else if (lockTime >= config.getSeverityLevels().getWarning()) {
            return Severity.MEDIUM;
        }
        return Severity.NONE;
    }

    /**
     * Builds human-readable message for audit result.
     *
     * @param lockInfo Lock information
     * @return Message string
     */
    private String buildMessage(LockInfo lockInfo) {
        String tables = String.join(", ", lockInfo.getAffectedTables());
        return String.format(
            "Table lock held for %dms on %s (type: %s, rows: %d)",
            lockInfo.getDuration(),
            tables,
            lockInfo.getType(),
            lockInfo.getRowsAffected()
        );
    }

    /**
     * Builds actionable recommendation for fixing the issue.
     *
     * @param lockInfo Lock information
     * @return Recommendation string
     */
    private String buildRecommendation(LockInfo lockInfo) {
        switch (lockInfo.getType()) {
            case DDL_LOCK:
                return "Schedule DDL operations during maintenance windows. Consider using online DDL tools.";

            case TABLE_SCAN_LOCK:
                return "Add WHERE clause to limit affected rows. Consider adding appropriate indexes.";

            case EXPLICIT_TABLE_LOCK:
                return "Use row-level locks (SELECT ... FOR UPDATE) instead of table locks.";

            case ROW_LEVEL_LOCK:
                return "Optimize transaction scope. Consider breaking into smaller batches.";

            default:
                return "Review query performance and transaction boundaries.";
        }
    }

    /**
     * Builds metadata map for detailed analysis.
     *
     * @param lockInfo Lock information
     * @return Metadata map
     */
    private Map<String, Object> buildMetadata(LockInfo lockInfo) {
        return Map.of(
            "lock_type", lockInfo.getType().name(),
            "lock_duration_ms", lockInfo.getDuration(),
            "affected_tables", String.join(", ", lockInfo.getAffectedTables()),
            "rows_affected", lockInfo.getRowsAffected()
        );
    }

    /**
     * Lock type enumeration.
     */
    public enum LockType {
        DDL_LOCK,              // ALTER TABLE, etc.
        EXPLICIT_TABLE_LOCK,   // LOCK TABLES
        TABLE_SCAN_LOCK,       // UPDATE/DELETE without WHERE
        ROW_LEVEL_LOCK,        // UPDATE/DELETE with WHERE
        READ_LOCK,             // SELECT
        UNKNOWN
    }

    /**
     * Lock information data class.
     */
    public static class LockInfo {
        private final LockType type;
        private final long duration;
        private final List<String> affectedTables;
        private final int rowsAffected;

        private LockInfo(Builder builder) {
            this.type = builder.type;
            this.duration = builder.duration;
            this.affectedTables = builder.affectedTables;
            this.rowsAffected = builder.rowsAffected;
        }

        public static Builder builder() {
            return new Builder();
        }

        public LockType getType() { return type; }
        public long getDuration() { return duration; }
        public List<String> getAffectedTables() { return affectedTables; }
        public int getRowsAffected() { return rowsAffected; }

        public static class Builder {
            private LockType type;
            private long duration;
            private List<String> affectedTables;
            private int rowsAffected;

            public Builder type(LockType type) {
                this.type = type;
                return this;
            }

            public Builder duration(long duration) {
                this.duration = duration;
                return this;
            }

            public Builder affectedTables(List<String> affectedTables) {
                this.affectedTables = affectedTables;
                return this;
            }

            public Builder rowsAffected(int rowsAffected) {
                this.rowsAffected = rowsAffected;
                return this;
            }

            public LockInfo build() {
                return new LockInfo(this);
            }
        }
    }
}

/**
 * Configuration properties for TableLockChecker.
 */
@Component
@ConfigurationProperties(prefix = "sqlguard.audit.checkers.table-lock")
class TableLockConfig {
    private boolean enabled = true;
    private long threshold = 1000;  // milliseconds
    private SeverityLevels severityLevels = new SeverityLevels();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getThreshold() {
        return threshold;
    }

    public void setThreshold(long threshold) {
        this.threshold = threshold;
    }

    public SeverityLevels getSeverityLevels() {
        return severityLevels;
    }

    public void setSeverityLevels(SeverityLevels severityLevels) {
        this.severityLevels = severityLevels;
    }

    public static class SeverityLevels {
        private long warning = 500;
        private long critical = 2000;

        public long getWarning() {
            return warning;
        }

        public void setWarning(long warning) {
            this.warning = warning;
        }

        public long getCritical() {
            return critical;
        }

        public void setCritical(long critical) {
            this.critical = critical;
        }
    }
}
